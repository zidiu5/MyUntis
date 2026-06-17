package com.myuntis.app.data.network.model

import com.google.gson.annotations.SerializedName

data class ExamsResponse(
    @SerializedName("data") val data: ExamsData?
)

data class ExamsData(
    @SerializedName("exams") val exams: List<ExamItem> = emptyList()
)

data class ExamItem(
    @SerializedName("id")           val id: Int,
    @SerializedName("examType")     val examType: String = "",
    @SerializedName("name")         val name: String = "",
    @SerializedName("examDate")     val examDate: Int,
    @SerializedName("startTime")    val startTime: Int,
    @SerializedName("endTime")      val endTime: Int,
    @SerializedName("subject")      val subject: String = "",
    @SerializedName("teachers")     val teachers: List<String> = emptyList(),
    @SerializedName("rooms")        val rooms: List<String> = emptyList(),
    @SerializedName("text")         val text: String = "",
    @SerializedName("grade")        val grade: String = "",
    @SerializedName("studentClass") val studentClass: List<String> = emptyList()
)