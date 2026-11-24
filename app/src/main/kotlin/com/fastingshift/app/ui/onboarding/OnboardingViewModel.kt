package com.fastingshift.app.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.fastingshift.domain.service.ScreeningRecord
import com.fastingshift.domain.service.ScreeningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Step { WELCOME, INTRO, GOAL, SCREENING, RESULT, PROTOCOL, NOTIFICATIONS, COMPLETE }

/** Only count setup steps. The welcome and optional tour have their own navigation. */
fun stepProgress(step: Step, rerun: Boolean): Pair<Int, Int>? {
    val first = if (rerun) Step.SCREENING else Step.GOAL
    val last = if (rerun) Step.PROTOCOL else Step.NOTIFICATIONS
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
    val rerun: Boolean = false,
    val goalFromTour: Boolean = false,
    val goal: Goal? = null,
    val form: ScreeningForm = ScreeningForm(),
    val formError: Boolean = false,
    val outcome: ScreeningOutcome? = null,
    val clinicianAck: Boolean = false,
    val protocols: List<Protocol> = emptyList(),
    val selected: Protocol = Presets.W16_8,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val screening: ScreeningRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val handle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(restore() ?: OnboardingState())
    val state: StateFlow<OnboardingState> = _state

    init {
        val fresh = handle.get<String>(KEY_STEP) == null
        // Every change is mirrored into the saved state, so the form survives process death.
        viewModelScope.launch { _state.collect(::save) }
        if (fresh) viewModelScope.launch {
            val s = settings.current()
            if (s.onboardingComplete) {
                // Re-running from settings: go straight to the screening with previous answers.
                val prev = screening.getLatest()?.answers
                _state.update {
                    it.copy(
                        step = Step.SCREENING, rerun = true, goal = s.goal,
                        selected = s.defaultProtocolId?.let(Presets::byId) ?: recommendedProtocol(s.goal),
                        form = prev?.let { a ->
                            ScreeningForm(a.ageYears.toString(), a.pregnantOrBreastfeeding, a.eatingDisorderHistory, a.type1Diabetes, a.medications, a.heightCm.toString(), a.weightKg.toString())
                        } ?: ScreeningForm(),
                    )
                }
            }
        }
    }

    private fun save(st: OnboardingState) {
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
        val safeStep = if (step > Step.SCREENING && outcome == null) Step.SCREENING else step
        val allowed = outcome?.let { allowedProtocols(it.result, it.result == GateResult.SOFT && ack) }.orEmpty()
        return OnboardingState(
            step = safeStep,
            rerun = handle[KEY_RERUN] ?: false,
            // Older versions always led through the tour before the goal.
            goalFromTour = handle[KEY_TOUR] ?: (step >= Step.INTRO),
            goal = handle.get<String>(KEY_GOAL)?.let { n -> Goal.entries.firstOrNull { it.name == n } },
            form = form,
            outcome = outcome,
            clinicianAck = ack,
            protocols = allowed,
            selected = handle.get<String>(KEY_SELECTED)?.let(Presets::byId) ?: Presets.W16_8,
        )
    }

    private fun allowedProtocols(result: GateResult, ack: Boolean): List<Protocol> {
        val max = FastPolicy.maxAllowed(result, ack) ?: Duration.ZERO
        return Presets.all.filter { Duration.ofHours(it.fastHours.toLong()) <= max }
    }

    fun openTour() = _state.update { if (it.step == Step.WELCOME) it.copy(step = Step.INTRO) else it }

    fun openGoal() = _state.update {
        when (it.step) {
            Step.WELCOME, Step.INTRO -> it.copy(step = Step.GOAL, goalFromTour = it.step == Step.INTRO)
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
            it.step == Step.WELCOME || (it.rerun && it.step == Step.SCREENING) -> it
            it.step == Step.GOAL && !it.goalFromTour -> it.copy(step = Step.WELCOME)
            else -> it.copy(step = Step.entries[it.step.ordinal - 1])
        }
    }

    /** Choosing a goal also pre-selects its suggested protocol for the protocol step. */
    fun setGoal(goal: Goal) = _state.update { it.copy(goal = goal, selected = recommendedProtocol(goal)) }

    fun updateForm(f: (ScreeningForm) -> ScreeningForm) = _state.update { it.copy(form = f(it.form), formError = false) }
    fun setClinicianAck(ack: Boolean) = _state.update { it.copy(clinicianAck = ack) }
    fun select(p: Protocol) = _state.update { it.copy(selected = p) }

    fun submitScreening() {
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
            settings.setDefaultProtocol(_state.value.selected.id)
            if (_state.value.rerun) {
                onFinished()
            } else {
                _state.update { it.copy(step = Step.NOTIFICATIONS) }
            }
        }
    }

    fun finish(onFinished: () -> Unit) {
        viewModelScope.launch {
            settings.setOnboardingComplete(true)
            // Coaching runs in the first two weeks after this moment.
            settings.setOnboardedAt(clock.millis())
            onFinished()
        }
    }

    private companion object {
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
    }
}
