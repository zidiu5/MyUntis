package com.myuntis.app.data.network.mapper

import com.myuntis.app.data.network.model.*
import com.myuntis.app.data.network.untisToLocalDate
import com.myuntis.app.data.network.untisToLocalTime
import com.myuntis.app.domain.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// ── null/blank guard ──────────────────────────────────────────
private fun String?.clean(): String? =
    this?.trim()?.takeIf { it.isNotBlank() && it.lowercase() != "null" }

// =============================================================
// REST TIMETABLE  (modern WebUntis API)
// =============================================================

fun RestTimetableResponse.toLessons(): List<Lesson> =
    days.flatMap { day ->
        val date = try {
            LocalDate.parse(day.date)
        } catch (e: Exception) { return@flatMap emptyList() }

        day.gridEntries.mapNotNull { entry ->
            entry.toLesson(date)
        }
    }

fun RestGridEntry.toLesson(date: LocalDate): Lesson? {
    // Parse ISO time strings ("2026-06-03T07:50" → "07:50")
    val startTime = try {
        LocalTime.parse(duration.start.substringAfter("T"))
    } catch (e: Exception) { return null }

    val endTime = try {
        LocalTime.parse(duration.end.substringAfter("T"))
    } catch (e: Exception) { return null }

    if (endTime <= startTime) return null

    // Teachers are always in position1
    val teachers = position1
        ?.mapNotNull { item -> item.current?.shortName.clean() }
        ?.distinct()
        ?: emptyList()

    val isNormal = type.uppercase() in listOf(
        "NORMAL_TEACHING_PERIOD", "EXAM", "ADDITIONAL_PERIOD"
    )

    val subject: Subject
    val rooms: List<String>

    if (isNormal) {
        // position2 = Subject, position3 = Rooms
        val subjectEntity = position2
            ?.firstOrNull { it.current?.type == "SUBJECT" }
            ?.current

        val short = subjectEntity?.shortName.clean() ?: "-"
        val long  = subjectEntity?.longName.clean()  ?: short

        subject = Subject(id = ids.firstOrNull() ?: 0, shortName = short, longName = long)
        rooms   = position3?.mapNotNull { it.current?.shortName.clean() } ?: emptyList()

    } else {
        // EVENT / unknown type:
        // Use a single fixed subject so ALL events share one color in Settings.
        // The actual event name goes into longName for display in Day view.
        val eventName = position2?.firstOrNull()?.current?.shortName
            ?.takeIf { it.length < 40 }  // reject suspiciously long strings
            ?: "Veranstaltung"

        subject = Subject(
            id        = 0,
            shortName = "EVT",          // fixed key → one entry in Settings
            longName  = eventName       // real name shown in Day view
        )
        rooms = emptyList()
    }

    return Lesson(
        id         = ids.firstOrNull() ?: 0,
        date       = date,
        startTime  = startTime,
        endTime    = endTime,
        subject    = subject,
        teachers   = teachers,
        rooms      = rooms,
        lessonType = when (type.uppercase()) {
            "EXAM" -> LessonType.EXAM   // Prüfung → gelbe Rahmenlinie
            else   -> LessonType.REGULAR
        },
        code = when {
            status.uppercase() == "CANCELLED"         -> LessonCode.CANCELLED   // Entfällt → rot
            type.uppercase()   == "ADDITIONAL_PERIOD" -> LessonCode.ADDITIONAL  // hinzugef. Stunde → grün
            status.uppercase() == "CHANGED"           -> LessonCode.IRREGULAR   // Vertretung → grün
            else                                      -> LessonCode.NONE
        }
    )
}

// =============================================================
// OLD JSON-RPC MAPPERS  (kept for homework/grades/absences)
// =============================================================

fun ApiHomework.toHomework(): Homework {
    val subjectRef = subjects.firstOrNull()
    return Homework(
        id               = id,
        subjectName      = subjectRef?.longName.clean() ?: subjectRef?.name.clean() ?: "-",
        subjectShortName = subjectRef?.name.clean() ?: "-",
        text             = text,
        dueDate          = dueDate.untisToLocalDate(),
        assignedDate     = date.untisToLocalDate(),
        teacherName      = teachers.firstOrNull()?.name.clean() ?: "",
        isCompleted      = completed
    )
}

fun ApiGrade.toGrade(): Grade {
    val gradeFloat = grade.name.toFloatOrNull() ?: 0f
    return Grade(
        id               = id,
        subjectName      = subject.longName.clean() ?: subject.name.clean() ?: "-",
        subjectShortName = subject.name.clean() ?: "-",
        grade            = gradeFloat,
        gradeText        = grade.name,
        examType         = examType?.name.clean() ?: "",
        date             = date.untisToLocalDate(),
        teacherName      = teachers.firstOrNull()?.name.clean() ?: ""
    )
}

fun ApiAbsence.toAbsence(): Absence {
    return Absence(
        id          = id,
        date        = startDate.untisToLocalDate(),
        startTime   = startTime.untisToLocalTime(),
        endTime     = endTime.untisToLocalTime(),
        subjectName = subject?.name.clean() ?: "",
        reason      = excuse?.text.clean() ?: excuse?.excuseStatus?.name.clean() ?: "",
        isExcused   = isExcused,
        teacherName = teachers.firstOrNull()?.name.clean() ?: ""
    )
}

fun ApiMessage.toMessage(): Message {
    val dateTime = try {
        LocalDateTime.parse(sentDateTime, DateTimeFormatter.ISO_DATE_TIME)
    } catch (e: Exception) { LocalDateTime.now() }
    return Message(
        id           = id,
        subject      = subject,
        body         = bodyContent ?: "",
        sender       = sender?.displayName.clean() ?: "Unbekannt",
        sentDateTime = dateTime,
        isRead       = isRead
    )
}
// =============================================================
// HOMEWORK REST MAPPER
// =============================================================
// Converts the new homework API response to domain Homework objects.
// The subject is stored as "SHORT (Long Name)" in the lesson object.
// We need to cross-reference homework.lessonId → lesson.subject
// =============================================================

fun HomeworkData.toDomainHomework(completedIds: Set<Int>): List<Homework> {
    // Build lookup map: lessonId → HomeworkLesson
    val lessonMap = lessons.associateBy { it.id }

    return homeworks.map { hw ->
        val lesson = lessonMap[hw.lessonId]

        // Parse "ENGL (Englisch)" → shortName="ENGL", longName="Englisch"
        val (shortName, longName) = parseSubjectString(lesson?.subject)

        Homework(
            id               = hw.id,
            subjectName      = longName,
            subjectShortName = shortName,
            text             = hw.text.trim().ifBlank { hw.remark?.trim() ?: "Keine Details" },
            dueDate          = hw.dueDate.untisToLocalDate(),
            assignedDate     = hw.date.untisToLocalDate(),
            teacherName      = "",   // Not in this API endpoint
            isCompleted      = completedIds.contains(hw.id) || hw.completed
        )
    }
}

// Parse "ENGL (Englisch)" → Pair("ENGL", "Englisch")
// Parse "M (Mathematik)"  → Pair("M", "Mathematik")
// Parse "M4 (M4)"         → Pair("M4", "M4")
// Parse ""                → Pair("-", "-")
private fun parseSubjectString(raw: String?): Pair<String, String> {
    if (raw.isNullOrBlank()) return Pair("-", "-")

    val parenStart = raw.lastIndexOf('(')
    val parenEnd   = raw.lastIndexOf(')')

    return if (parenStart > 0 && parenEnd > parenStart) {
        val short = raw.substring(0, parenStart).trim()
        val long  = raw.substring(parenStart + 1, parenEnd).trim()
        Pair(
            short.ifBlank { long },
            long.ifBlank { short }
        )
    } else {
        Pair(raw.trim(), raw.trim())
    }
}
// =============================================================
// GRADES MAPPER
// =============================================================
fun GradesData.toDomainGrades(): List<Grade> {
    val subjectMap = subjects.associateBy { it.id }

    return grades.mapNotNull { item ->
        val gradeFloat = item.grade.toFloatOrNull() ?: return@mapNotNull null
        if (gradeFloat <= 0f || gradeFloat > 5f) return@mapNotNull null

        val subj = item.subject
        val subjectName = when {
            subj?.longName?.isNotBlank() == true -> subj.longName
            subj?.name?.isNotBlank()     == true -> subj.name
            else -> subjectMap[subj?.id]?.longName ?: "-"
        }
        val subjectShort = subj?.name.clean() ?: "-"

        Grade(
            id               = item.id,
            subjectName      = subjectName,
            subjectShortName = subjectShort,
            grade            = gradeFloat,
            gradeText        = gradeFloatToText(gradeFloat),
            examType         = item.gradeType?.name.clean() ?: "",
            date             = item.date?.untisToLocalDate() ?: java.time.LocalDate.now(),
            teacherName      = item.teacher?.let {
                "${it.firstName} ${it.lastName}".trim()
                    .ifBlank { it.name }
            } ?: ""
        )
    }
}

private fun gradeFloatToText(grade: Float): String = when {
    grade <= 1.5f -> "Sehr gut"
    grade <= 2.5f -> "Gut"
    grade <= 3.5f -> "Befriedigend"
    grade <= 4.5f -> "Genügend"
    else          -> "Nicht genügend"
}

// =============================================================
// ABSENCES MAPPER
// =============================================================
fun AbsencesData.toDomainAbsences(): List<Absence> {
    return absences.map { item ->
        Absence(
            id          = item.id,
            date        = item.startDate.untisToLocalDate(),
            startTime   = item.startTime.untisToLocalTime(),
            endTime     = item.endTime.untisToLocalTime(),
            subjectName = item.subject?.name.clean() ?: "",
            reason      = item.excuse?.text.clean()
                ?: item.absenceReason?.name.clean()
                ?: "",
            isExcused   = item.isExcused,
            teacherName = item.teachers.firstOrNull()?.name.clean() ?: ""
        )
    }
}

// =============================================================
// ABSENCES STUDENTS MAPPER  (new correct endpoint)
// =============================================================
// Mapper for the correct absences endpoint response
fun com.myuntis.app.data.network.model.AbsencesStudentsData.toDomainAbsences(): List<Absence> {
    return absences.map { item ->
        Absence(
            id          = item.id,
            date        = item.startDate.untisToLocalDate(),
            startTime   = item.startTime.untisToLocalTime(),
            endTime     = item.endTime.untisToLocalTime(),
            subjectName = "",
            reason      = item.reason.trim(),
            detail      = item.text.trim(),
            isExcused   = item.isExcused,
            teacherName = ""
        )
    }
}