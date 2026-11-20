package com.fastingshift.domain.timer

import java.time.Duration

/** Formats a duration as H:MM:SS (hours can exceed 24, e.g. 36:04:09). */
fun Duration.toClockString(): String {
    val total = seconds.coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return "%d:%02d:%02d".format(h, m, s)
}
