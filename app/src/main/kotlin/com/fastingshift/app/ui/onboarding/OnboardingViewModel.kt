package com.fastingshift.app.ui.onboarding

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

enum class Step { WELCOME, GOAL, SCREENING, RESULT, PROTOCOL, NOTIFICATIONS, OFFER }

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
    fun toAnswers(): ScreeningAnswers? {
        val a = age.trim().toIntOrNull()?.takeIf { it in 1..120 } ?: return null
        val h = heightCm.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in 50.0..260.0 } ?: return null
        val w = weightKg.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in 20.0..400.0 } ?: return null
        return ScreeningAnswers(a, pregnant, eatingDisorder, type1, medications, h, w)
    }
}

data class OnboardingState(
    val step: Step = Step.WELCOME,
    val rerun: Boolean = false,
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
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state

    init {
        viewModelScope.launch {
            val s = settings.current()
            if (s.onboardingComplete) {
                // Re-running from settings: go straight to the screening with previous answers.
                val prev = screening.getLatest()?.answers
                _state.update {
                    it.copy(
                        step = Step.SCREENING, rerun = true, goal = s.goal,
                        form = prev?.let { a ->
                            ScreeningForm(a.ageYears.toString(), a.pregnantOrBreastfeeding, a.eatingDisorderHistory, a.type1Diabetes, a.medications, a.heightCm.toString(), a.weightKg.toString())
                        } ?: ScreeningForm(),
                    )
                }
            }
        }
    }

    fun next() = _state.update { it.copy(step = Step.entries[it.step.ordinal + 1]) }
    fun back() = _state.update { if (it.step.ordinal > 0 && !(it.rerun && it.step == Step.SCREENING)) it.copy(step = Step.entries[it.step.ordinal - 1]) else it }

    fun setGoal(goal: Goal) = _state.update { it.copy(goal = goal) }
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
            val max = FastPolicy.maxAllowed(outcome.result, ack) ?: Duration.ZERO
            val allowed = Presets.all.filter { Duration.ofHours(it.fastHours.toLong()) <= max }
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
            onFinished()
        }
    }
}
