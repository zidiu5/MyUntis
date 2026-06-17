package com.myuntis.app.domain.model

import java.time.LocalDate
import java.time.LocalTime

data class ClassRegEntry(
    val id: Int,
    val date: LocalDate,
    val time: LocalTime,
    val subjectName: String,
    val teacherName: String,
    val text: String,
    val category: String,
    val reason: String,
    val isPersonal: Boolean
)