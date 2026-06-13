package com.myuntis.app.data.network

import com.myuntis.app.data.network.model.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// =============================================================
// TOP-LEVEL EXTENSION FUNCTIONS
// =============================================================

fun Int.untisToLocalDate(): LocalDate {
    val str = this.toString().padStart(8, '0')
    return LocalDate.parse(str, DateTimeFormatter.ofPattern("yyyyMMdd"))
}

fun Int.untisToLocalTime(): LocalTime {
    val str = this.toString().padStart(4, '0')
    return LocalTime.of(str.substring(0, 2).toInt(), str.substring(2, 4).toInt())
}

fun LocalDate.toUntisDate(): Int =
    format(DateTimeFormatter.ofPattern("yyyyMMdd")).toInt()

fun String.toSessionCookie(): String = "JSESSIONID=$this"

// =============================================================
// UNTIS API HELPER
// =============================================================
object UntisApiHelper {

    private var requestCounter = 1
    private fun nextId() = (requestCounter++).toString()

    // ── JSON-RPC URL ──────────────────────────────────────────
    fun buildJsonRpcUrl(server: String, school: String): String {
        val host = server.trim().lowercase().removeSuffix(".webuntis.com")
        return "https://$host.webuntis.com/WebUntis/jsonrpc.do?school=${school.trim()}"
    }

    // ── REST Timetable URL ────────────────────────────────────
    // Matches exactly: /api/rest/view/v1/timetable/entries
    //   ?start=2026-06-01&end=2026-06-06&format=1
    //   &resourceType=STUDENT&resources=6425
    //   &periodTypes=&timetableType=MY_TIMETABLE&layout=START_TIME
    fun buildRestTimetableUrl(
        server: String,
        studentId: Int,
        startDate: LocalDate,
        endDate: LocalDate
    ): String {
        val host  = server.trim().lowercase().removeSuffix(".webuntis.com")
        val start = startDate.toString()   // ISO: "2026-06-01"
        val end   = endDate.toString()     // ISO: "2026-06-06"
        return "https://$host.webuntis.com/WebUntis/api/rest/view/v1/timetable/entries" +
                "?start=$start&end=$end&format=1&resourceType=STUDENT" +
                "&resources=$studentId&periodTypes=&timetableType=MY_TIMETABLE&layout=START_TIME"
    }

    // ── Token URL ─────────────────────────────────────────────
    // Returns a short-lived JWT Bearer token from the active session
    fun buildTokenUrl(server: String): String {
        val host = server.trim().lowercase().removeSuffix(".webuntis.com")
        return "https://$host.webuntis.com/WebUntis/api/token/new"
    }

    // ── Messages URL ──────────────────────────────────────────
    fun buildMessagesUrl(server: String): String {
        val host = server.trim().lowercase().removeSuffix(".webuntis.com")
        return "https://$host.webuntis.com/WebUntis/api/rest/view/v1/messages"
    }

    // ── JSON-RPC Request Builders ─────────────────────────────

    fun buildLoginRequest(username: String, password: String) = JsonRpcRequest(
        id = nextId(), method = "authenticate",
        params = LoginParams(user = username, password = password)
    )

    fun buildLogoutRequest() = JsonRpcRequest(
        id = nextId(), method = "logout",
        params = emptyMap<String, String>()
    )

    fun buildGetStudentRequest() = JsonRpcRequest(
        id = nextId(), method = "getStudents",
        params = emptyMap<String, String>()
    )

    fun buildGetKlassenRequest() = JsonRpcRequest(
        id = nextId(), method = "getKlassen",
        params = emptyMap<String, String>()
    )

    fun buildHomeworkRequest(startDate: LocalDate, endDate: LocalDate) = JsonRpcRequest(
        id = nextId(), method = "getHomeWork",
        params = HomeworkParams(
            startDate = startDate.toUntisDate(),
            endDate   = endDate.toUntisDate()
        )
    )

    fun buildGradesRequest() = JsonRpcRequest(
        id = nextId(), method = "getStudentGrades",
        params = emptyMap<String, String>()
    )

    fun buildAbsencesRequest(startDate: LocalDate, endDate: LocalDate) = JsonRpcRequest(
        id = nextId(), method = "getAbsences",
        params = mapOf(
            "startDate" to startDate.toUntisDate(),
            "endDate"   to endDate.toUntisDate()
        )
    )
    // Homework API URL
// Example: https://lbs-brixen.webuntis.com/WebUntis/api/homeworks/lessons
//          ?startDate=20260601&endDate=20260630
    fun buildHomeworkRestUrl(
        server: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): String {
        val host  = server.trim().lowercase().removeSuffix(".webuntis.com")
        val start = startDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val end   = endDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        return "https://$host.webuntis.com/WebUntis/api/homeworks/lessons" +
                "?startDate=$start&endDate=$end"
    }
    // Grades overview
    fun buildGradesRestUrl(server: String): String {
        val host = server.trim().lowercase().removeSuffix(".webuntis.com")
        return "https://$host.webuntis.com/WebUntis/api/classreg/grade/gradeoverview"
    }

    // Absences list
// FIXED: correct URL + studentId + excuseStatusId param
    fun buildAbsencesRestUrl(
        server: String,
        studentId: Int,
        startDate: LocalDate,
        endDate: LocalDate
    ): String {
        val host  = server.trim().lowercase().removeSuffix(".webuntis.com")
        val start = startDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val end   = endDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        return "https://$host.webuntis.com/WebUntis/api/classreg/absences/students" +
                "?startDate=$start&endDate=$end&studentId=$studentId&excuseStatusId=-1"
    }

    // FIXED: schoolyearId is now optional (call without it first to detect current year)
    fun buildGradingListUrl(
        server: String,
        studentId: Int,
        schoolyearId: Int = 0
    ): String {
        val host      = server.trim().lowercase().removeSuffix(".webuntis.com")
        val yearParam = if (schoolyearId > 0) "&schoolyearId=$schoolyearId" else ""
        return "https://$host.webuntis.com/WebUntis/api/classreg/grade/grading/list" +
                "?studentId=$studentId$yearParam"
    }

    // Grading lesson: all grades for one subject
    fun buildGradingLessonUrl(server: String, studentId: Int, lessonId: Int): String {
        val host = server.trim().lowercase().removeSuffix(".webuntis.com")
        return "https://$host.webuntis.com/WebUntis/api/classreg/grade/grading/lesson" +
                "?studentId=$studentId&lessonId=$lessonId"
    }

    // JSON-RPC: getCurrentSchoolYear
    fun buildGetCurrentSchoolYearRequest() = JsonRpcRequest(
        id = nextId(), method = "getCurrentSchoolYear",
        params = emptyMap<String, String>()
    )
}