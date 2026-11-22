package com.fastingshift.domain.ramadan

/** Voluntary (Sunnah) fasts the user can follow and be reminded about. Stored by name. */
enum class SunnahType {
    /** Every Monday and Thursday. */
    MON_THU,

    /** The white days: 13th, 14th and 15th of each Hijri month. */
    AYYAMUL_BIDH,

    /** 9 Dhul Hijjah. */
    ARAFAH,

    /** 9 and 10 Muharram (Tasu'a and Ashura). */
    ASHURA,

    /** Six days in Shawwal, after Eid. */
    SHAWWAL,
}
