package com.myuntis.app.data.network.model

import com.google.gson.annotations.SerializedName

// =============================================================
// GRADES  –  /WebUntis/api/classreg/grade/gradeoverview
// =============================================================
data class GradesApiResponse(
    @SerializedName("data") val data: GradesData?
)

data class GradesData(
    @SerializedName("grades")   val grades: List<GradeItem> = emptyList(),
    @SerializedName("subjects") val subjects: List<GradeSubject> = emptyList()
)

data class GradeItem(
    @SerializedName("id")           val id: Int,
    @SerializedName("grade")        val grade: String,         // "1", "2", "Sehr gut"
    @SerializedName("subject")      val subject: GradeSubjectRef?,
    @SerializedName("date")         val date: Int?,            // YYYYMMDD
    @SerializedName("lastUpdate")   val lastUpdate: Long?,
    @SerializedName("schoolyear")   val schoolyear: Int?,
    @SerializedName("gradeType")    val gradeType: GradeType?,
    @SerializedName("teacher")      val teacher: GradeTeacher?
)

data class GradeSubjectRef(
    @SerializedName("id")        val id: Int,
    @SerializedName("name")      val name: String,
    @SerializedName("longName")  val longName: String = ""
)

data class GradeSubject(
    @SerializedName("id")       val id: Int,
    @SerializedName("name")     val name: String,
    @SerializedName("longName") val longName: String = ""
)

data class GradeType(
    @SerializedName("id")       val id: Int,
    @SerializedName("name")     val name: String = ""   // "SA", "KA", "Mitarbeit"
)

data class GradeTeacher(
    @SerializedName("id")        val id: Int,
    @SerializedName("name")      val name: String = "",
    @SerializedName("firstName") val firstName: String = "",
    @SerializedName("lastName")  val lastName: String = ""
)

// =============================================================
// ABSENCES  –  /WebUntis/api/classreg/absence/students/list
// =============================================================
data class AbsencesApiResponse(
    @SerializedName("data") val data: AbsencesData?
)

data class AbsencesData(
    @SerializedName("absences")       val absences: List<AbsenceItem> = emptyList(),
    @SerializedName("absenceReasons") val reasons: List<AbsenceReason> = emptyList()
)

data class AbsenceItem(
    @SerializedName("id")              val id: Int,
    @SerializedName("startDate")       val startDate: Int,     // YYYYMMDD
    @SerializedName("startTime")       val startTime: Int,     // HHMM
    @SerializedName("endDate")         val endDate: Int,
    @SerializedName("endTime")         val endTime: Int,
    @SerializedName("isExcused")       val isExcused: Boolean,
    @SerializedName("excuse")          val excuse: AbsenceExcuse?,
    @SerializedName("subject")         val subject: AbsenceSubjectRef?,
    @SerializedName("teachers")        val teachers: List<GradeTeacher> = emptyList(),
    @SerializedName("minutesLate")     val minutesLate: Int = 0,
    @SerializedName("absenceReason")   val absenceReason: AbsenceReason?
)

data class AbsenceExcuse(
    @SerializedName("text")         val text: String? = null,
    @SerializedName("excuseStatus") val excuseStatus: AbsenceStatusRef?
)

data class AbsenceStatusRef(
    @SerializedName("id")   val id: Int,
    @SerializedName("name") val name: String = ""
)

data class AbsenceSubjectRef(
    @SerializedName("id")   val id: Int,
    @SerializedName("name") val name: String = ""
)

data class AbsenceReason(
    @SerializedName("id")   val id: Int,
    @SerializedName("name") val name: String = ""
)



// ── Absences Students (correct endpoint) ──────────────────────
data class AbsencesStudentsResponse(
    @SerializedName("data") val data: AbsencesStudentsData?
)

data class AbsencesStudentsData(
    @SerializedName("absences")       val absences: List<AbsenceStudentItem> = emptyList(),
    @SerializedName("absenceReasons") val absenceReasons: List<AbsenceReason> = emptyList()
)

data class AbsenceStudentItem(
    @SerializedName("id")           val id: Int,
    @SerializedName("startDate")    val startDate: Int,
    @SerializedName("endDate")      val endDate: Int,
    @SerializedName("startTime")    val startTime: Int,
    @SerializedName("endTime")      val endTime: Int,
    @SerializedName("reason")       val reason: String = "",
    @SerializedName("text")         val text: String = "",
    @SerializedName("isExcused")    val isExcused: Boolean,
    @SerializedName("excuseStatus") val excuseStatus: String = "",
    @SerializedName("studentName")  val studentName: String = ""
)