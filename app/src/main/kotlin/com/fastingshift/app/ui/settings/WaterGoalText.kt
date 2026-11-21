package com.fastingshift.app.ui.settings

import com.fastingshift.domain.hydration.WaterGoal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** The daily water goal as Settings shows it: "Automatic (2.8 L)" or a custom "2.5 L". */
data class WaterGoalDisplay(val automatic: Boolean, val ml: Int, val automaticMl: Int) {
    companion object {
        /** [customMl] null means automatic, from the latest weight ([WaterGoal.defaultMl]). */
        fun of(customMl: Int?, weightKg: Double?): WaterGoalDisplay {
            val auto = WaterGoal.defaultMl(weightKg)
            return if (customMl == null) WaterGoalDisplay(true, auto, auto) else WaterGoalDisplay(false, customMl, auto)
        }

        /** Step for the custom goal stepper, and its bounds (kept within what settings store). */
        const val STEP_ML = 250
        const val MIN_ML = 500
        const val MAX_ML = 6_000

        fun stepped(ml: Int, steps: Int): Int = (ml + steps * STEP_ML).coerceIn(MIN_ML, MAX_ML)
    }
}

/** "2.8", "2.75", "2.0": litres with one or two decimals in the user's number format. */
fun formatLiters(ml: Int, locale: Locale = Locale.getDefault()): String =
    DecimalFormat("0.0#", DecimalFormatSymbols.getInstance(locale)).format(ml / 1000.0)
