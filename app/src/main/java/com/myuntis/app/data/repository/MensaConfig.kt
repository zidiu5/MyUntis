package com.myuntis.app.data.repository

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

object MensaConfig {
    const val URL = "https://api.zidiu1.com/api/read/mensa_plaene.json"

    // Read-only key. It can only read this one JSON, so it is fine in the code.
    const val API_KEY = "i1FoznAJYqjaj6pm-m1F5VBSgN0_73i3FHNs9wboMwE"

    /**
     * Monday of "Woche_1", e.g. LocalDate.of(2026, 9, 14).
     * null = guess from the ISO calendar week (same as mensa.py).
     */
    val WEEK_ONE_START: LocalDate? = LocalDate.of(2026, 10, 26)

    // ── Which schools have a mensa plan ───────────────────────
    private val LOGIN_NAMES = setOf("lbs-brixen")                      // BBZ Ch. J. Tschuggmall
    private val NAME_PARTS  = listOf("tschuggmall", "hellenstainer")   // matched in the school name

    fun isSupportedSchool(loginName: String, displayName: String): Boolean {
        val login = loginName.trim().lowercase()
        val name  = displayName.trim().lowercase()
        return login in LOGIN_NAMES || NAME_PARTS.any { login.contains(it) || name.contains(it) }
    }

    /** 1 Apr – 31 Oct = Sommer, 1 Nov – 31 Mar = Winter */
    fun seasonFor(date: LocalDate): String =
        if (date.monthValue in 4..10) "Sommer" else "Winter"

    /** Index of the plan week (0-based) for the week that starts on [displayedMonday]. */
    fun weekIndex(displayedMonday: LocalDate, count: Int, today: LocalDate = LocalDate.now()): Int {
        if (count <= 0) return 0
        val todayMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val base = WEEK_ONE_START?.let {
            Math.floorMod(ChronoUnit.WEEKS.between(it, todayMonday), count.toLong()).toInt()
        } ?: Math.floorMod(today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) - 1, count)

        val offset = ChronoUnit.WEEKS.between(todayMonday, displayedMonday)
        return Math.floorMod(base.toLong() + offset, count.toLong()).toInt()
    }
}