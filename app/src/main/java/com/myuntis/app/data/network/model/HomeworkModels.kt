package com.myuntis.app.data.network.model

import com.google.gson.annotations.SerializedName

// Response from /WebUntis/api/homeworks/lessons
data class HomeworkApiResponse(
    @SerializedName("data") val data: HomeworkData?
)

data class HomeworkData(
    @SerializedName("homeworks") val homeworks: List<HomeworkItem> = emptyList(),
    @SerializedName("lessons")   val lessons: List<HomeworkLesson> = emptyList(),
    @SerializedName("teachers")  val teachers: List<HomeworkTeacher> = emptyList(),
    @SerializedName("records")   val records: List<HomeworkRecord> = emptyList()
)

// A single homework assignment
data class HomeworkItem(
    @SerializedName("id")          val id: Int,
    @SerializedName("lessonId")    val lessonId: Int,
    @SerializedName("date")        val date: Int,       // YYYYMMDD
    @SerializedName("dueDate")     val dueDate: Int,    // YYYYMMDD
    @SerializedName("text")        val text: String,
    @SerializedName("remark")      val remark: String? = null,
    @SerializedName("completed")   val completed: Boolean = false,
    @SerializedName("attachments") val attachments: List<Any> = emptyList()
)

// Lesson info (contains subject name)
data class HomeworkLesson(
    @SerializedName("id")          val id: Int,
    @SerializedName("subject")     val subject: String,      // "M (Mathematik)"
    @SerializedName("lessonType")  val lessonType: String
)

data class HomeworkTeacher(
    @SerializedName("id")        val id: Int,
    @SerializedName("name")      val name: String = "",
    @SerializedName("firstName") val firstName: String = "",
    @SerializedName("lastName")  val lastName: String = ""
)

// Completion record
data class HomeworkRecord(
    @SerializedName("id")          val id: Int,
    @SerializedName("homeworkId")  val homeworkId: Int,
    @SerializedName("completed")   val completed: Boolean = false,
    @SerializedName("studentId")   val studentId: Int = 0
)