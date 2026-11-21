package com.fastingshift.app.widget

import com.fastingshift.domain.safety.FastPolicy
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.StartDecision
import java.time.Duration

/**
 * What the widget's action button does, decided when the widget is drawn. Activities can't be
 * started from a background callback on Android 14+, so anything that needs the app must be
 * an activity launch from the click itself.
 */
enum class WidgetStartMode {
    /** A fast is active: the button ends it in the background. */
    END,

    /** The default protocol may start right away: the button starts it in the background. */
    START_IN_BACKGROUND,

    /** Starting needs the app (checklist, clinician acknowledgement, limit): "Start" opens it. */
    START_IN_APP,

    /** Unscreened or education-only: no Start button, only an "Open app" link. */
    OPEN_APP_ONLY,
    ;

    companion object {
        /**
         * @param gate the latest screening result, or null if the user has not been screened.
         * @param planned the default protocol's fasting length.
         */
        fun decide(hasActiveFast: Boolean, gate: GateResult?, clinicianAcknowledged: Boolean, planned: Duration): WidgetStartMode {
            if (hasActiveFast) return END
            if (gate == null || gate == GateResult.BLOCK) return OPEN_APP_ONLY
            if (FastPolicy.requirementsFor(planned).isNotEmpty()) return START_IN_APP
            return when (val decision = FastPolicy.evaluateStart(gate, clinicianAcknowledged, planned)) {
                is StartDecision.Blocked -> START_IN_APP
                is StartDecision.Allowed -> if (decision.requirements.isEmpty()) START_IN_BACKGROUND else START_IN_APP
            }
        }
    }
}
