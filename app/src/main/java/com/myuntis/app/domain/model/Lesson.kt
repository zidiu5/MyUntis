package com.myuntis.app.domain.model

import java.time.LocalDate
import java.time.LocalTime

// =============================================================
// LESSON - Domain Model
// =============================================================
// Represents a single lesson/period in the timetable.
// WebUntis uses integer times (e.g., 800 = 08:00, 1345 = 13:45)
// We convert these to LocalTime for easier handling.
// =============================================================
data class Lesson(
    val id: Int,
    val date: LocalDate,                    // The date of this lesson
    val startTime: LocalTime,               // Start time (e.g., 08:00)
    val endTime: LocalTime,                 // End time (e.g., 08:50)
    val subject: Subject,                   // Which subject
    val teachers: List<String> = emptyList(), // Teacher short names
    val rooms: List<String> = emptyList(),  // Room numbers
    val lessonType: LessonType = LessonType.REGULAR,
    val code: LessonCode = LessonCode.NONE  // Cancelled/substitution
)

// Subject information
data class Subject(
    val id: Int,
    val shortName: String,                  // e.g., "M" for Mathematik
    val longName: String,                   // e.g., "Mathematik"
    val color: Long = 0xFF1565C0            // Display color (hex)
)

// Type of lesson
enum class LessonType {
    REGULAR,        // Normal lesson
    EXAM,           // Test/exam
    OFFICE_HOUR,    // Sprechstunde
    BREAK_SUPERVISION // Pausenaufsicht
}

// Code indicates if something special is happening
enum class LessonCode {
    NONE,           // Normal lesson
    CANCELLED,      // Entfallen/Ausfall
    IRREGULAR,      // Unregelmäßig (substitution etc.)
    ADDITIONAL      // Extra lesson
}

// Extension: formatted time range string "08:00 - 08:50"
val Lesson.timeRange: String
    get() = "${startTime.toString().take(5)} - ${endTime.toString().take(5)}"

// Extension: is this lesson cancelled?
val Lesson.isCancelled: Boolean
    get() = code == LessonCode.CANCELLED