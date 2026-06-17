package com.myuntis.app.data.network.model

import com.google.gson.annotations.SerializedName

data class ClassRegResponse(
    @SerializedName("data") val data: ClassRegData?
)

data class ClassRegData(
    @SerializedName("rows") val rows: List<ClassRegRow> = emptyList()
)

data class ClassRegRow(
    @SerializedName("id")               val id: Int,
    @SerializedName("elementName")      val elementName: String = "",
    @SerializedName("subjectName")      val subjectName: String = "",
    @SerializedName("creatorName")      val creatorName: String = "",
    @SerializedName("createDate")       val createDate: Int,
    @SerializedName("createTime")       val createTime: Int,
    @SerializedName("eventReasonName")  val eventReasonName: String? = null,
    @SerializedName("categoryName")     val categoryName: String? = null,
    @SerializedName("text")             val text: String = "",
    @SerializedName("elemType")         val elemType: String = ""
)