package com.myuntis.app.domain.model

import java.time.LocalDate
import java.time.LocalTime

data class Absence(
    val id: Int,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val subjectName: String = "",
    val reason: String = "",
    val detail: String = "",       // extra text / Bemerkung
    val isExcused: Boolean,
    val teacherName: String = ""
)

data class AbsenceStatistics(
    val totalHours: Int,
    val excusedHours: Int,
    val unexcusedHours: Int,
    val absencesBySubject: Map<String, Int> = emptyMap()
)