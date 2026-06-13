package com.myuntis.app.data.network.model

import com.google.gson.annotations.SerializedName

data class RestTimetableResponse(
    @SerializedName("format") val format: Int = 1,
    @SerializedName("days")   val days: List<RestDay> = emptyList(),
    @SerializedName("errors") val errors: List<Any> = emptyList()
)

data class RestDay(
    @SerializedName("date")         val date: String,        // "2026-06-03"
    @SerializedName("resourceType") val resourceType: String,
    @SerializedName("resource")     val resource: RestResource?,
    @SerializedName("status")       val status: String,
    @SerializedName("gridEntries")  val gridEntries: List<RestGridEntry> = emptyList(),
    @SerializedName("backEntries")  val backEntries: List<Any> = emptyList()
)

data class RestResource(
    @SerializedName("id")          val id: Int,
    @SerializedName("shortName")   val shortName: String,
    @SerializedName("longName")    val longName: String,
    @SerializedName("displayName") val displayName: String
)

// One lesson/event block in the timetable grid
data class RestGridEntry(
    @SerializedName("ids")                 val ids: List<Int>,
    @SerializedName("duration")            val duration: RestDuration,
    @SerializedName("type")                val type: String,   // NORMAL_TEACHING_PERIOD | EVENT
    @SerializedName("status")              val status: String, // REGULAR | CANCELLED | CHANGED
    @SerializedName("statusDetail")        val statusDetail: String?,
    @SerializedName("name")                val name: String?,
    @SerializedName("layoutStartPosition") val layoutStartPosition: Int = 0,
    @SerializedName("layoutWidth")         val layoutWidth: Int = 1000,
    @SerializedName("layoutGroup")         val layoutGroup: Int = 0,
    @SerializedName("color")               val color: String = "",
    @SerializedName("notesAll")            val notesAll: String?,
    // position1 = always Teachers
    @SerializedName("position1")           val position1: List<RestPositionItem>?,
    // position2 = Subject (NORMAL) or Room (EVENT)
    @SerializedName("position2")           val position2: List<RestPositionItem>?,
    // position3 = Room (NORMAL) or null (EVENT)
    @SerializedName("position3")           val position3: List<RestPositionItem>?,
    @SerializedName("position4")           val position4: List<RestPositionItem>?,
    @SerializedName("lessonText")          val lessonText: String?,
    @SerializedName("substitutionText")    val substitutionText: String?
)

data class RestDuration(
    @SerializedName("start") val start: String,  // "2026-06-03T07:50"
    @SerializedName("end")   val end: String     // "2026-06-03T08:40"
)

data class RestPositionItem(
    @SerializedName("current") val current: RestPositionEntity?,
    @SerializedName("removed") val removed: RestPositionEntity?
)

data class RestPositionEntity(
    @SerializedName("type")             val type: String,      // TEACHER | SUBJECT | ROOM
    @SerializedName("status")           val status: String,    // REGULAR | ADDED | REMOVED
    @SerializedName("shortName")        val shortName: String,
    @SerializedName("longName")         val longName: String,
    @SerializedName("displayName")      val displayName: String,
    @SerializedName("displayNameLabel") val displayNameLabel: String? = null
)