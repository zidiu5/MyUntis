package com.myuntis.app.domain.model

import java.time.LocalDate
import java.time.LocalTime

data class Exam(
    val id: Int,
    val name: String,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val subject: String,
    val teachers: List<String>,
    val rooms: List<String>,
    val description: String,
    val grade: String,
    val isPast: Boolean
)