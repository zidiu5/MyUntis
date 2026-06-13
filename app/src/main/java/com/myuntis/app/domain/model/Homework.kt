package com.myuntis.app.domain.model

import java.time.LocalDate

// =============================================================
// HOMEWORK - Domain Model
// =============================================================
data class Homework(
    val id: Int,
    val subjectName: String,                // "Mathematik"
    val subjectShortName: String,           // "M"
    val text: String,                       // The assignment description
    val dueDate: LocalDate,                 // When it's due
    val assignedDate: LocalDate,            // When it was assigned
    val teacherName: String = "",
    val isCompleted: Boolean = false        // Checked off by student
)

// Extension: days until due
val Homework.daysUntilDue: Long
    get() = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), dueDate)

// Extension: is it overdue?
val Homework.isOverdue: Boolean
    get() = dueDate.isBefore(LocalDate.now()) && !isCompleted

// Extension: is it due today?
val Homework.isDueToday: Boolean
    get() = dueDate == LocalDate.now()

// Extension: is it due tomorrow?
val Homework.isDueTomorrow: Boolean
    get() = dueDate == LocalDate.now().plusDays(1)