package com.myuntis.app.domain.model

import java.time.LocalDate

// =============================================================
// GRADE - Domain Model
// =============================================================
data class Grade(
    val id: Int,
    val subjectName: String,
    val subjectShortName: String,
    val grade: Float,                       // e.g., 1.0, 2.5, 4.0
    val gradeText: String,                  // e.g., "Sehr gut", "Gut"
    val examType: String = "",              // "SA", "KA", "Mitarbeit"
    val date: LocalDate,
    val teacherName: String = "",
    val weight: Float = 1.0f               // Gewichtung
)

// Group grades by subject for the grades screen
data class SubjectGrades(
    val subjectName: String,
    val subjectShortName: String,
    val grades: List<Grade>,
    val average: Float = grades
        .map { it.grade * it.weight }
        .sum() / grades.map { it.weight }.sum().coerceAtLeast(0.01f)
)