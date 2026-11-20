package com.myuntis.app.data.network.model

// All fields have defaults → Gson fills missing JSON fields with them.

data class CalendarEntryDetailResponse(
    val calendarEntries: List<CalendarEntryDetail> = emptyList()
)

data class CalendarEntryDetail(
    val id: Long = 0,
    val startDateTime: String = "",
    val endDateTime: String = "",
    val status: String? = null,                  // TAKING_PLACE, CANCELLED, ...
    val type: String? = null,                    // NORMAL_TEACHING_PERIOD, EXAM, EVENT ...
    val subject: CalendarRef? = null,
    val teachers: List<CalendarRef> = emptyList(),
    val rooms: List<CalendarRef> = emptyList(),
    val klasses: List<CalendarRef> = emptyList(),
    val exam: CalendarExam? = null,
    val homeworks: List<CalendarHomework> = emptyList(),
    val lessonInfo: String? = null,
    val substText: String? = null,
    val notesAll: String? = null,
    val teachingContent: String? = null
)

data class CalendarRef(
    val id: Long = 0,
    val displayName: String? = null,
    val longName: String? = null,
    val shortName: String? = null,
    val status: String? = null                   // REGULAR, ADDED, REMOVED
)

data class CalendarExam(
    val id: Long = 0,
    val name: String? = null,
    val description: String? = null,
    val typeLongName: String? = null
)

data class CalendarHomework(
    val id: Long = 0,
    val text: String? = null,
    val remark: String? = null,
    val completed: Boolean = false,
    val dueDateTime: String? = null
)