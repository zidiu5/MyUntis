package com.myuntis.app.data.network.model

import com.google.gson.annotations.SerializedName

// =============================================================
// GRADING LIST  –  /WebUntis/api/classreg/grade/grading/list
//                  ?studentId=X&schoolyearId=Y
// =============================================================
data class GradingListResponse(
    @SerializedName("data") val data: GradingListData?
)

data class GradingListData(
    @SerializedName("lessons")              val lessons: List<GradingLesson> = emptyList(),
    @SerializedName("finalMarkByLessonId")  val finalMarkByLessonId: Map<String, GradingFinalMark> = emptyMap()
)

data class GradingLesson(
    @SerializedName("id")       val id: Int,
    @SerializedName("subjects") val subjects: String = "",   // "M", "ENGL", "Bew.Sport"
    @SerializedName("teachers") val teachers: String = "",
    @SerializedName("klassen")  val klassen: String = "",
    @SerializedName("text")     val text: String = ""
)

data class GradingFinalMark(
    @SerializedName("schoolyear")      val schoolyear: GradingSchoolYear?,
    @SerializedName("suggestedMark")   val suggestedMark: GradingMarkValue?,
    @SerializedName("assignedMark")    val assignedMark: GradingMarkValue?
)

data class GradingSchoolYear(
    @SerializedName("id")           val id: Int,
    @SerializedName("name")         val name: String = "",
    @SerializedName("schoolyearId") val schoolyearId: Int,
    @SerializedName("startDate")    val startDate: String = "",
    @SerializedName("endDate")      val endDate: String = ""
)

// =============================================================
// GRADING LESSON  –  /WebUntis/api/classreg/grade/grading/lesson
//                    ?studentId=X&lessonId=Y
// =============================================================
data class GradingLessonResponse(
    @SerializedName("data") val data: GradingLessonData?
)

data class GradingLessonData(
    @SerializedName("lesson")      val lesson: GradingLesson?,
    @SerializedName("grades")      val grades: List<GradingItem> = emptyList(),
    @SerializedName("finalMarks")  val finalMarks: List<GradingFinalMarkEntry> = emptyList()
)

// A single grade entry (one exam/test)
data class GradingItem(
    @SerializedName("id")           val id: Int,
    @SerializedName("text")         val text: String = "",          // "Frisbee: Zielwerfen"
    @SerializedName("date")         val date: Int,                  // YYYYMMDD
    @SerializedName("mark")         val mark: GradingMarkValue?,
    @SerializedName("examType")     val examType: GradingExamType?,
    @SerializedName("markSchemaId") val markSchemaId: Int = 1
)

data class GradingMarkValue(
    @SerializedName("name")             val name: String,            // "8", "9-", "1", "2"
    @SerializedName("markDisplayValue") val markDisplayValue: Float, // 8.0, 9.0, 1.0
    @SerializedName("markValue")        val markValue: Int           // 800, 900, 100
)

data class GradingExamType(
    @SerializedName("name")     val name: String = "",
    @SerializedName("longname") val longname: String = ""
)

data class GradingFinalMarkEntry(
    @SerializedName("suggestedMark")      val suggestedMark: GradingMarkValue?,
    @SerializedName("assignedMark")       val assignedMark: GradingMarkValue?,
    @SerializedName("suggestedMarkValue") val suggestedMarkValue: Float = 0f
)

// =============================================================
// SCHOOL YEAR  –  JSON-RPC getCurrentSchoolYear
// =============================================================
data class ApiSchoolYear(
    @SerializedName("id")        val id: Int,
    @SerializedName("name")      val name: String = "",
    @SerializedName("startDate") val startDate: Int = 0,
    @SerializedName("endDate")   val endDate: Int = 0
)