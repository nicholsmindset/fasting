package com.fastingshift.app.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.R
import com.fastingshift.app.ui.firstweek.EveningOffer
import com.fastingshift.app.ui.firstweek.FirstWeekRules
import com.fastingshift.app.ui.firstweek.FirstFastPlanner
import com.fastingshift.app.ui.firstweek.minuteTicker
import com.fastingshift.data.settings.AppSettings
import com.fastingshift.data.settings.SetupDraft
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.model.Protocol
import com.fastingshift.domain.onboarding.Goal
import com.fastingshift.domain.protocol.Presets
import com.fastingshift.domain.safety.FastPolicy
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.Medication
import com.fastingshift.domain.safety.ScreeningAnswers
import com.fastingshift.domain.safety.ScreeningEvaluator
import com.fastingshift.domain.safety.ScreeningOutcome
import com.fastingshift.domain.safety.StartRequirement
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.FastService
import com.fastingshift.domain.service.ScreeningRecord
import com.fastingshift.domain.service.ScreeningRepository
import com.fastingshift.domain.service.StartResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FirstIntent { START_NOW, PLAN_LATER, EXPLORE }

enum class Step { WELCOME, INTRO, GOAL, SCREENING, RESULT, PROTOCOL, FIRST_PLAN, NOTIFICATIONS, COMPLETE }

/** Only count setup steps. The welcome and optional tour have their own navigation. */
fun stepProgress(step: Step, rerun: Boolean): Pair<Int, Int>? {
    val first = if (rerun) Step.SCREENING else Step.GOAL
    val last = if (rerun) Step.PROTOCOL else Step.FIRST_PLAN
    if (step !in first..last) return null
    return (step.ordinal - first.ordinal + 1) to (last.ordinal - first.ordinal + 1)
}

/**
 * The protocol suggested for a goal. Longer-fast goals still start on 16:8: people build up to
 * longer fasts after a few weeks, never on day one.
 */
fun recommendedProtocol(goal: Goal?): Protocol = when (goal) {
    Goal.CONSISTENCY -> Presets.W14_10
    Goal.WEIGHT, Goal.METABOLIC_HEALTH, Goal.LONG_FASTS, null -> Presets.W16_8
}

internal enum class ScreeningNumber(val bounds: IntRange) {
    AGE(1..120), HEIGHT(50..260), WEIGHT(20..400);

    fun accepts(value: Double): Boolean = value >= bounds.first && value <= bounds.last
}

/** Raw form input; validated into [ScreeningAnswers] on submit. */
data class ScreeningForm(
    val age: String = "",
    val pregnant: Boolean = false,
    val eatingDisorder: Boolean = false,
    val type1: Boolean = false,
    val medications: Set<Medication> = emptySet(),
    val heightCm: String = "",
    val weightKg: String = "",
) {
    private val ageYears = age.trim().toIntOrNull()?.takeIf { it in ScreeningNumber.AGE.bounds }
    private val height = heightCm.trim().replace(',', '.').toDoubleOrNull()?.takeIf(ScreeningNumber.HEIGHT::accepts)
    private val weight = weightKg.trim().replace(',', '.').toDoubleOrNull()?.takeIf(ScreeningNumber.WEIGHT::accepts)

    internal val invalidNumber: ScreeningNumber?
        get() = when {
            ageYears == null -> ScreeningNumber.AGE
            height == null -> ScreeningNumber.HEIGHT
            weight == null -> ScreeningNumber.WEIGHT
            else -> null
        }

    fun toAnswers(): ScreeningAnswers? {
        return ScreeningAnswers(ageYears ?: return null, pregnant, eatingDisorder, type1, medications,
            height ?: return null, weight ?: return null)
    }
}

data class OnboardingState(
    val step: Step = Step.WELCOME,
    val intent: FirstIntent = FirstIntent.PLAN_LATER,
    val rerun: Boolean = false,
    val goalFromTour: Boolean = false,
    val goal: Goal? = null,
    val form: ScreeningForm = ScreeningForm(),
    val formError: Boolean = false,
    val outcome: ScreeningOutcome? = null,
    val clinicianAck: Boolean = false,
    val protocols: List<Protocol> = emptyList(),
    val selected: Protocol = Presets.W16_8,
    val firstStart: ZonedDateTime? = null,
    val busy: Boolean = false,
    val planError: Int? = null,
    val checklist: Set<StartRequirement>? = null,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val screening: ScreeningRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val handle: SavedStateHandle,
    private val planner: FirstFastPlanner,
    private val service: FastService,
    private val fasts: FastRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(restore() ?: OnboardingState())
    val state: StateFlow<OnboardingState> = _state
    val minuteClock = minuteTicker(clock)
    fun now() = clock.instant()

    private val _ready = MutableStateFlow(handle.get<String>(KEY_STEP) != null)
    val ready: StateFlow<Boolean> = _ready

    init {
        viewModelScope.launch {
            if (!_ready.value) {
                val preferences = settings.current()
                if (preferences.onboardingComplete || preferences.setupDraft != null) {
                    _state.value = resumeLocalSetup(preferences)
                }
                _ready.value = true
            }
            launch { _state.collect(::save) }
            _state.map { st ->
                if (st.rerun || st.step == Step.WELCOME) null else SetupDraft(
                    st.step.name, st.intent.name, st.goal, st.selected.id, st.firstStart?.toString(), st.goalFromTour,
                )
            }.distinctUntilChanged().collect { settings.saveSetupDraft(it) }
        }
    }

    private suspend fun resumeLocalSetup(preferences: AppSettings): OnboardingState {
        val draft = preferences.setupDraft
        val previous = screening.getLatest()
        val form = previous?.answers?.let { a ->
            ScreeningForm(a.ageYears.toString(), a.pregnantOrBreastfeeding, a.eatingDisorderHistory,
                a.type1Diabetes, a.medications, a.heightCm.toString(), a.weightKg.toString())
        } ?: ScreeningForm()
        if (preferences.onboardingComplete) return OnboardingState(
            step = Step.SCREENING, rerun = true, goal = preferences.goal, form = form,
            selected = preferences.defaultProtocolId?.let(Presets::byId) ?: recommendedProtocol(preferences.goal),
        )
        draft ?: return OnboardingState()
        val desired = Step.entries.firstOrNull { it.name == draft.step } ?: Step.WELCOME
        val outcome = previous?.answers?.let(ScreeningEvaluator::evaluate)
        val ack = previous?.clinicianAcknowledged == true
        val eligible = if (outcome != null && outcome.result == previous?.gateResult)
            allowedProtocols(outcome.result, ack) else emptyList()
        val selected = Presets.byId(draft.protocolId)
        val step = when {
            desired <= Step.GOAL -> desired
            draft.goal == null -> Step.GOAL
            desired <= Step.RESULT || outcome == null -> Step.SCREENING
            outcome.result != previous?.gateResult -> Step.SCREENING
            eligible.isEmpty() -> Step.RESULT
            desired >= Step.FIRST_PLAN && fasts.getAll().isNotEmpty() -> Step.COMPLETE
            desired == Step.FIRST_PLAN && planner.planned() != null -> Step.COMPLETE
            desired == Step.FIRST_PLAN && (selected == null || selected !in eligible) -> Step.PROTOCOL
            else -> desired
        }
        return OnboardingState(
            step = step, intent = intentFrom(draft.intent), goalFromTour = draft.fromTour, goal = draft.goal,
            form = form, outcome = outcome, clinicianAck = ack, protocols = eligible,
            selected = selected?.takeIf { it in eligible || step < Step.PROTOCOL } ?: eligible.firstOrNull() ?: recommendedProtocol(draft.goal),
            firstStart = draft.start?.let { runCatching { ZonedDateTime.parse(it) }.getOrNull() },
        )
    }

    private fun intentFrom(name: String?) = FirstIntent.entries.firstOrNull { it.name == name } ?: FirstIntent.PLAN_LATER

    private fun save(st: OnboardingState) {
        handle[KEY_INTENT] = st.intent.name
        handle[KEY_STEP] = st.step.name
        handle[KEY_RERUN] = st.rerun
        handle[KEY_TOUR] = st.goalFromTour
        handle[KEY_GOAL] = st.goal?.name
        handle[KEY_AGE] = st.form.age
        handle[KEY_PREGNANT] = st.form.pregnant
        handle[KEY_ED] = st.form.eatingDisorder
        handle[KEY_T1D] = st.form.type1
        handle[KEY_MEDS] = ArrayList(st.form.medications.map { it.name })
        handle[KEY_HEIGHT] = st.form.heightCm
        handle[KEY_WEIGHT] = st.form.weightKg
        handle[KEY_ACK] = st.clinicianAck
        handle[KEY_SELECTED] = st.selected.id
        handle[KEY_FIRST_START] = st.firstStart?.toString()
    }

    private fun restore(): OnboardingState? {
        val savedStep = handle.get<String>(KEY_STEP) ?: return null
        // The paid offer used to follow completed setup. Resume an older saved flow
        // at its original free destination while keeping the validation below.
        val step = if (savedStep == "OFFER") Step.COMPLETE else Step.entries.firstOrNull { it.name == savedStep } ?: return null
        val form = ScreeningForm(
            age = handle[KEY_AGE] ?: "",
            pregnant = handle[KEY_PREGNANT] ?: false,
            eatingDisorder = handle[KEY_ED] ?: false,
            type1 = handle[KEY_T1D] ?: false,
            medications = handle.get<ArrayList<String>>(KEY_MEDS).orEmpty().mapNotNull { n -> Medication.entries.firstOrNull { it.name == n } }.toSet(),
            heightCm = handle[KEY_HEIGHT] ?: "",
            weightKg = handle[KEY_WEIGHT] ?: "",
        )
        val outcome = form.toAnswers()?.let(ScreeningEvaluator::evaluate)
        val ack = handle[KEY_ACK] ?: false
        // Steps after the result need its outcome; without valid answers, go back to the form.
        val firstStart = handle.get<String>(KEY_FIRST_START)?.let { runCatching { ZonedDateTime.parse(it) }.getOrNull() }
        val allowed = outcome?.let { allowedProtocols(it.result, it.result == GateResult.SOFT && ack) }.orEmpty()
        val safeStep = when {
            step > Step.SCREENING && outcome == null -> Step.SCREENING
            step == Step.FIRST_PLAN && allowed.isEmpty() -> Step.RESULT
            // Previous builds offered notifications before a plan existed. Resume into the free app.
            step == Step.NOTIFICATIONS && firstStart == null -> Step.COMPLETE
            else -> step
        }
        val savedProtocol = handle.get<String>(KEY_SELECTED)?.let(Presets::byId) ?: Presets.W16_8
        return OnboardingState(
            step = safeStep,
            intent = intentFrom(handle[KEY_INTENT]),
            firstStart = firstStart,
            rerun = handle[KEY_RERUN] ?: false,
            // Older versions always led through the tour before the goal.
            goalFromTour = handle[KEY_TOUR] ?: (step >= Step.INTRO),
            goal = handle.get<String>(KEY_GOAL)?.let { n -> Goal.entries.firstOrNull { it.name == n } },
            form = form,
            outcome = outcome,
            clinicianAck = ack,
            protocols = allowed,
            selected = if (safeStep == Step.FIRST_PLAN && savedProtocol !in allowed) allowed.first() else savedProtocol,
        )
    }

    private fun allowedProtocols(result: GateResult, ack: Boolean): List<Protocol> {
        val max = FastPolicy.maxAllowed(result, ack) ?: Duration.ZERO
        return Presets.all.filter { Duration.ofHours(it.fastHours.toLong()) <= max }
    }

    fun openTour() = _state.update { if (it.step == Step.WELCOME) it.copy(step = Step.INTRO, intent = FirstIntent.EXPLORE) else it }

    fun openGoal(intent: FirstIntent = FirstIntent.PLAN_LATER) = _state.update {
        when (it.step) {
            Step.WELCOME, Step.INTRO -> it.copy(step = Step.GOAL, intent = intent, goalFromTour = it.step == Step.INTRO)
            else -> it
        }
    }

    fun continueFromGoal() = _state.update {
        if (it.step == Step.GOAL && it.goal != null) it.copy(step = Step.SCREENING) else it
    }

    fun completeNotifications() = _state.update {
        if (it.step == Step.NOTIFICATIONS) it.copy(step = Step.COMPLETE) else it
    }

    fun back() = _state.update {
        when {
            it.busy -> it
            it.step == Step.NOTIFICATIONS -> it.copy(step = Step.COMPLETE)
            it.step == Step.WELCOME || (it.rerun && it.step == Step.SCREENING) -> it
            it.step == Step.GOAL && it.goalFromTour -> it.copy(step = Step.INTRO, intent = FirstIntent.EXPLORE)
            it.step == Step.GOAL && !it.goalFromTour -> it.copy(step = Step.WELCOME)
            else -> it.copy(step = Step.entries[it.step.ordinal - 1], planError = null, checklist = null)
        }
    }

    /** Choosing a goal also pre-selects its suggested protocol for the protocol step. */
    fun setGoal(goal: Goal) = _state.update { it.copy(goal = goal, selected = recommendedProtocol(goal)) }

    fun updateForm(f: (ScreeningForm) -> ScreeningForm) = _state.update {
        it.copy(form = f(it.form), formError = false, outcome = null, clinicianAck = false)
    }
    fun setClinicianAck(ack: Boolean) = _state.update { it.copy(clinicianAck = ack) }
    fun select(p: Protocol) = _state.update { it.copy(selected = p) }

    fun submitScreening() {
        if (_state.value.step != Step.SCREENING) return
        val answers = _state.value.form.toAnswers()
        if (answers == null) {
            _state.update { it.copy(formError = true) }
            return
        }
        _state.update { it.copy(outcome = ScreeningEvaluator.evaluate(answers), step = Step.RESULT) }
    }

    /** Saves the screening (with acknowledgement) and moves to protocol choice. */
    fun confirmResult(onEducationOnly: () -> Unit) {
        val st = _state.value
        if (st.step != Step.RESULT) return
        val outcome = st.outcome ?: return
        val answers = st.form.toAnswers() ?: return
        viewModelScope.launch {
            val ack = outcome.result == GateResult.SOFT && st.clinicianAck
            screening.save(ScreeningRecord(clock.instant(), answers, outcome.result, ack))
            st.goal?.let { settings.setGoal(it) }
            if (outcome.result == GateResult.BLOCK) {
                settings.setOnboardingComplete(true)
                onEducationOnly()
                return@launch
            }
            val allowed = allowedProtocols(outcome.result, ack)
            _state.update {
                it.copy(
                    step = Step.PROTOCOL, protocols = allowed,
                    selected = if (it.selected in allowed) it.selected else allowed.firstOrNull() ?: Presets.W12_12,
                )
            }
        }
    }

    fun confirmProtocol(onFinished: () -> Unit) {
        viewModelScope.launch {
            val st = _state.value
            if (st.step != Step.PROTOCOL || st.selected !in st.protocols) return@launch
            settings.setDefaultProtocol(st.selected.id)
            if (st.rerun) {
                onFinished()
            } else {
                val next = if (fasts.getAll().isEmpty()) Step.FIRST_PLAN else Step.COMPLETE
                _state.update {
                    val start = if (next == Step.FIRST_PLAN && it.intent == FirstIntent.PLAN_LATER)
                        it.firstStart ?: FirstWeekRules.nextEvening(now(), ZoneId.systemDefault()) else it.firstStart
                    it.copy(step = next, firstStart = start)
                }
            }
        }
    }

    fun reviewStartNow() = _state.update {
        if (it.step == Step.FIRST_PLAN && !it.busy) it.copy(intent = FirstIntent.START_NOW, planError = null) else it
    }

    fun pickFirstStart(start: ZonedDateTime) = _state.update {
        if (it.step == Step.FIRST_PLAN && !it.busy) it.copy(firstStart = start, planError = null) else it
    }

    fun leaveFirstPlanOpen() = _state.update {
        if (it.step == Step.FIRST_PLAN && !it.busy) it.copy(step = Step.COMPLETE) else it
    }

    fun saveFirstPlan(offerNotifications: Boolean) = runFirstAction { st ->
        if (st.intent != FirstIntent.PLAN_LATER) return@runFirstAction
        val start = st.firstStart ?: return@runFirstAction
        val askForPermission = offerNotifications && settings.current().reminders.fastStartEnabled
        when (planner.planAt(st.selected, start)) {
            EveningOffer.AVAILABLE, EveningOffer.COVERED -> {
                val next = if (askForPermission) Step.NOTIFICATIONS else Step.COMPLETE
                _state.update { it.copy(step = next) }
            }
            EveningOffer.UNAVAILABLE -> _state.update { it.copy(planError = R.string.first_plan_conflict) }
        }
    }

    fun startFirstFast(confirmChecklist: Boolean = false) = runFirstAction { st ->
        if (st.intent != FirstIntent.START_NOW) return@runFirstAction
        val confirmed = if (confirmChecklist) st.checklist ?: return@runFirstAction else emptySet()
        when (val result = service.start(st.selected, confirmed)) {
            is StartResult.Started, is StartResult.AlreadyActive -> _state.update { it.copy(step = Step.COMPLETE, checklist = null) }
            is StartResult.NeedsConfirmation -> _state.update { it.copy(checklist = result.requirements) }
            is StartResult.Blocked -> _state.update { it.copy(checklist = null, planError = R.string.first_plan_blocked) }
            StartResult.InvalidStartTime -> _state.update { it.copy(checklist = null, planError = R.string.timer_invalid_start) }
        }
    }

    fun dismissFirstChecklist() = _state.update { it.copy(checklist = null) }

    private fun runFirstAction(action: suspend (OnboardingState) -> Unit) {
        val st = _state.value
        if (st.step != Step.FIRST_PLAN || st.busy || st.rerun || st.selected !in st.protocols) return
        _state.update { it.copy(busy = true, planError = null) }
        viewModelScope.launch {
            try {
                action(st)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(planError = R.string.first_plan_retry) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun finish(onFinished: () -> Unit) {
        val st = _state.value
        if (st.step != Step.COMPLETE && !(st.step == Step.PROTOCOL && st.outcome != null && st.protocols.isEmpty())) return
        viewModelScope.launch {
            // Coaching runs in the first two weeks after this moment.
            settings.setOnboardedAt(clock.millis())
            settings.setOnboardingComplete(true)
            onFinished()
        }
    }

    private companion object {
        const val KEY_INTENT = "first_intent"
        const val KEY_STEP = "step"
        const val KEY_RERUN = "rerun"
        const val KEY_TOUR = "goal_from_tour"
        const val KEY_GOAL = "goal"
        const val KEY_AGE = "age"
        const val KEY_PREGNANT = "pregnant"
        const val KEY_ED = "ed"
        const val KEY_T1D = "t1d"
        const val KEY_MEDS = "meds"
        const val KEY_HEIGHT = "height"
        const val KEY_WEIGHT = "weight"
        const val KEY_ACK = "ack"
        const val KEY_SELECTED = "selected"
        const val KEY_FIRST_START = "first_plan_start"
    }
}
