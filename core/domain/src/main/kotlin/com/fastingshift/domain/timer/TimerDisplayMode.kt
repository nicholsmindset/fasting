package com.fastingshift.domain.timer

/** What the big timer shows. Tapping the timer cycles through these (user request). */
enum class TimerDisplayMode {
    /** Time fasted so far, counting up. */
    ELAPSED,

    /** Time left until the goal, counting down; after the goal, time past it. */
    REMAINING,

    /** The clock time the fast reaches its goal: when you can break it. */
    BREAK_AT,
    ;

    fun next(): TimerDisplayMode = entries[(ordinal + 1) % entries.size]
}
