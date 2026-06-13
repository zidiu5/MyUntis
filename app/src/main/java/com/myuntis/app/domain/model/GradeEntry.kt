package com.myuntis.app.domain.model

import java.time.LocalDate

// Represents one individual grade entry within a subject
data class GradeEntry(
    val id: Int,
    val markName: String,           // "8", "9+", "1", "2" – displayed as-is
    val markValue: Float,           // numeric: 8.0, 9.0, 1.0
    val description: String,        // "Frisbee: Zielwerfen", "Mitarbeit"
    val examType: String,           // "Prüfung", "SA"
    val date: LocalDate,
    val markSchemaId: Int = 1       // 1 = 1-5 scale, 2 = 1-10 scale (Sport)
)

// A subject with all its grade entries
data class SubjectWithGrades(
    val lessonId: Int,
    val subjectShort: String,       // "M", "ENGL", "Bew.Sport"
    val subjectLong: String,        // displayed name (same as short for now)
    val teachers: String,           // "Pf-Mi/Fe-Sa"
    val entries: List<GradeEntry>,
    val finalMarkName: String = ""  // assigned final mark, if any
) {
    // Average of mark values (shown with context)
    val average: Float
        get() = if (entries.isEmpty()) 0f
        else entries.map { it.markValue }.average().toFloat()
}