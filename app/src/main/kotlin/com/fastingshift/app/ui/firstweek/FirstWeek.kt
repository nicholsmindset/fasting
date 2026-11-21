package com.fastingshift.app.ui.firstweek

import androidx.compose.runtime.Composable
import com.fastingshift.domain.model.Fast

/*
 * First-week journey (owned by the onboarding/first-week work). These are the stable entry
 * points the Timer screen calls; each decides on its own whether to show anything.
 */

/** Shown when not fasting and the user has never started a fast: "Start now" / "Start tonight". */
@Composable
fun FirstFastCard(onStartNow: () -> Unit) {
    // Placeholder: implemented in the first-week work.
}

/** The current coaching tip during one of the user's first fasts (nothing otherwise). */
@Composable
fun CoachingTipCard(fast: Fast) {
    // Placeholder: implemented in the first-week work.
}

/**
 * Calm end-of-fast summary (duration vs plan, water during the fast, "how do you feel?" and a
 * note), shown right after a fast ends from the Timer. [onUndo] re-opens the fast (only offered
 * inside the undo window); [onDismiss] closes it.
 */
@Composable
fun FastSummarySheet(fast: Fast, onUndo: () -> Unit, onDismiss: () -> Unit) {
    // Placeholder: implemented in the first-week work. Until then, dismiss immediately.
    androidx.compose.runtime.LaunchedEffect(fast.id) { onDismiss() }
}
