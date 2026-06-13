package com.myuntis.app.data.network.model

import com.google.gson.annotations.SerializedName

// =============================================================
// WEBUNTIS JSON-RPC REQUEST/RESPONSE MODELS
// =============================================================
// WebUntis uses JSON-RPC 2.0 protocol.
// Every request is a POST with this structure:
// {
//   "id": "unique-id",
//   "method": "methodName",
//   "params": { ... },
//   "jsonrpc": "2.0"
// }
// =============================================================

// ----- JSON-RPC BASE STRUCTURES -----

// Generic request wrapper for all JSON-RPC calls
data class JsonRpcRequest(
    @SerializedName("id") val id: String = "1",
    @SerializedName("method") val method: String,
    @SerializedName("params") val params: Any,
    @SerializedName("jsonrpc") val jsonrpc: String = "2.0"
)

// Generic response wrapper - result can be any type T
data class JsonRpcResponse<T>(
    @SerializedName("id") val id: String?,
    @SerializedName("result") val result: T?,
    @SerializedName("error") val error: JsonRpcError?,
    @SerializedName("jsonrpc") val jsonrpc: String?
)

// Error object returned when something goes wrong
data class JsonRpcError(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: Any? = null
)

// ----- AUTHENTICATION -----

// Login request parameters
data class LoginParams(
    @SerializedName("user") val user: String,
    @SerializedName("password") val password: String,
    @SerializedName("client") val client: String = "MyUntis-App"
)

// Login response result
data class LoginResult(
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("personType") val personType: Int,    // 2=Teacher, 5=Student
    @SerializedName("personId") val personId: Int,
    @SerializedName("klasseId") val klasseId: Int? = null  // Class ID for students
)

// ----- CURRENT USER INFO -----

data class ApiStudent(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,             // Username
    @SerializedName("longName") val longName: String,     // Full name
    @SerializedName("foreName") val foreName: String = "", // First name
    @SerializedName("gender") val gender: String? = null
)

data class ApiKlasse(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,             // e.g., "3AHIT"
    @SerializedName("longName") val longName: String = ""
)

// ----- TIMETABLE -----

// A single period/lesson from the API
data class ApiPeriod(
    @SerializedName("id") val id: Int,
    @SerializedName("date") val date: Int,                // YYYYMMDD format
    @SerializedName("startTime") val startTime: Int,      // HHMM format (e.g., 800)
    @SerializedName("endTime") val endTime: Int,          // HHMM format (e.g., 850)
    @SerializedName("su") val subjects: List<ApiRef> = emptyList(),
    @SerializedName("te") val teachers: List<ApiRef> = emptyList(),
    @SerializedName("ro") val rooms: List<ApiRef> = emptyList(),
    @SerializedName("kl") val classes: List<ApiRef> = emptyList(),
    @SerializedName("lstype") val lessonType: String? = null, // "ls","oh","sb","bs","ex"
    @SerializedName("code") val code: String? = null,     // "cancelled","irregular"
    @SerializedName("activityType") val activityType: String? = null
)

// Reference object (subject, teacher, room) - contains id and short name
// Gson ignoriert Kotlin's null safety - alle String-Felder MÜSSEN nullable sein
// sonst setzt Gson bei fehlenden Werten null auf nicht-nullable Felder → Runtime Crash / "null"-String
data class ApiRef(
    @SerializedName("id")       val id: Int = 0,
    @SerializedName("name")     val name: String? = null,
    @SerializedName("longname") val longName: String? = null,
    @SerializedName("orgname")  val orgName: String? = null,
    @SerializedName("state")    val state: String? = null
)

// Timetable request params (for a date range)
data class TimetableParams(
    @SerializedName("options") val options: TimetableOptions
)

data class TimetableOptions(
    @SerializedName("id") val id: Int,
    @SerializedName("startDate") val startDate: Int,      // YYYYMMDD
    @SerializedName("endDate") val endDate: Int,          // YYYYMMDD
    @SerializedName("element") val element: TimetableElement,
    @SerializedName("showLsText") val showLsText: Boolean = true,
    @SerializedName("showStudentgroup") val showStudentgroup: Boolean = true,
    @SerializedName("showLsNumber") val showLsNumber: Boolean = true,
    @SerializedName("showSubstText") val showSubstText: Boolean = true,
    @SerializedName("showInfo") val showInfo: Boolean = true,
    @SerializedName("showBooking") val showBooking: Boolean = false
)

data class TimetableElement(
    @SerializedName("id") val id: Int,
    @SerializedName("type") val type: Int              // 1=class, 2=teacher, 5=student
)

// ----- HOMEWORK -----

data class ApiHomework(
    @SerializedName("homeworkId") val id: Int,
    @SerializedName("lessonId") val lessonId: Int,
    @SerializedName("date") val date: Int,              // YYYYMMDD
    @SerializedName("dueDate") val dueDate: Int,        // YYYYMMDD
    @SerializedName("text") val text: String,
    @SerializedName("remark") val remark: String? = null,
    @SerializedName("completed") val completed: Boolean = false,
    @SerializedName("teachers") val teachers: List<ApiRef> = emptyList(),
    @SerializedName("subjects") val subjects: List<ApiRef> = emptyList()
)

data class HomeworkParams(
    @SerializedName("startDate") val startDate: Int,
    @SerializedName("endDate") val endDate: Int
)

// ----- GRADES -----

data class ApiGrade(
    @SerializedName("id") val id: Int,
    @SerializedName("subject") val subject: ApiRef,
    @SerializedName("schoolyearId") val schoolyearId: Int? = null,
    @SerializedName("grade") val grade: ApiGradeValue,
    @SerializedName("date") val date: Int,              // YYYYMMDD
    @SerializedName("lastUpdate") val lastUpdate: Long? = null,
    @SerializedName("examType") val examType: ApiRef? = null,
    @SerializedName("teachers") val teachers: List<ApiRef> = emptyList()
)

data class ApiGradeValue(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,           // e.g., "1", "Sehr gut"
    @SerializedName("longname") val longName: String? = null
)

// ----- ABSENCES -----

data class ApiAbsence(
    @SerializedName("id") val id: Int,
    @SerializedName("startDate") val startDate: Int,    // YYYYMMDD
    @SerializedName("startTime") val startTime: Int,    // HHMM
    @SerializedName("endDate") val endDate: Int,
    @SerializedName("endTime") val endTime: Int,
    @SerializedName("isExcused") val isExcused: Boolean,
    @SerializedName("excuse") val excuse: ApiExcuse? = null,
    @SerializedName("subject") val subject: ApiRef? = null,
    @SerializedName("teachers") val teachers: List<ApiRef> = emptyList()
)

data class ApiExcuse(
    @SerializedName("text") val text: String? = null,
    @SerializedName("excuseStatus") val excuseStatus: ApiRef? = null
)

// ----- MESSAGES -----

data class ApiMessage(
    @SerializedName("id") val id: Int,
    @SerializedName("subject") val subject: String,
    @SerializedName("bodyContent") val bodyContent: String? = null,
    @SerializedName("sender") val sender: ApiMessagePerson? = null,
    @SerializedName("sentDateTime") val sentDateTime: String,
    @SerializedName("isRead") val isRead: Boolean = false,
    @SerializedName("hasAttachments") val hasAttachments: Boolean = false
)

data class ApiMessagePerson(
    @SerializedName("id") val id: Int,
    @SerializedName("displayName") val displayName: String,
    @SerializedName("type") val type: String? = null
)