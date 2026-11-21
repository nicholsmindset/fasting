package com.fastingshift.app

import com.fastingshift.app.widget.WidgetStartMode
import com.fastingshift.domain.safety.GateResult
import java.time.Duration
import kotlin.test.assertEquals
import org.junit.Test

class WidgetStartModeTest {
    private val h16 = Duration.ofHours(16)
    private val h24 = Duration.ofHours(24)
    private val h36 = Duration.ofHours(36)

    @Test fun activeFastEndsInBackground() {
        assertEquals(WidgetStartMode.END, WidgetStartMode.decide(true, GateResult.OK, false, h16))
        // Even for a blocked user, an active fast can always be ended.
        assertEquals(WidgetStartMode.END, WidgetStartMode.decide(true, GateResult.BLOCK, false, h16))
    }

    @Test fun unscreenedAndEducationOnlyGetNoStartButton() {
        assertEquals(WidgetStartMode.OPEN_APP_ONLY, WidgetStartMode.decide(false, null, false, h16))
        assertEquals(WidgetStartMode.OPEN_APP_ONLY, WidgetStartMode.decide(false, GateResult.BLOCK, true, h16))
    }

    @Test fun shortFastStartsInBackground() {
        assertEquals(WidgetStartMode.START_IN_BACKGROUND, WidgetStartMode.decide(false, GateResult.OK, false, h16))
        assertEquals(WidgetStartMode.START_IN_BACKGROUND, WidgetStartMode.decide(false, GateResult.SOFT, true, h16))
    }

    @Test fun checklistFastsOpenTheApp() {
        assertEquals(WidgetStartMode.START_IN_APP, WidgetStartMode.decide(false, GateResult.OK, false, h24))
        assertEquals(WidgetStartMode.START_IN_APP, WidgetStartMode.decide(false, GateResult.OK, false, h36))
    }

    @Test fun gateThatDoesNotAllowTheProtocolOpensTheApp() {
        // Soft gate without the clinician acknowledgement.
        assertEquals(WidgetStartMode.START_IN_APP, WidgetStartMode.decide(false, GateResult.SOFT, false, h16))
    }
}
