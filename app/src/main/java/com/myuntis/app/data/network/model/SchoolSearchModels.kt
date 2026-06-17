package com.myuntis.app.data.network.model

import com.google.gson.annotations.SerializedName

data class SchoolSearchRequest(
    @SerializedName("jsonrpc") val jsonrpc: String = "2.0",
    @SerializedName("method")  val method: String  = "searchSchool",
    @SerializedName("params")  val params: List<SchoolSearchParam>,
    @SerializedName("id")      val id: String = "wu-${System.currentTimeMillis()}"
)

data class SchoolSearchParam(
    @SerializedName("search") val search: String
)

data class SchoolSearchResponse(
    @SerializedName("result") val result: SchoolSearchResultData?,
    @SerializedName("error")  val error: Any? = null
)

data class SchoolSearchResultData(
    @SerializedName("schools") val schools: List<SchoolResult> = emptyList()
)

data class SchoolResult(
    @SerializedName("server")      val server: String,       // "lbs-brixen.webuntis.com"
    @SerializedName("displayName") val displayName: String,  // "BBZ Ch. J. Tschuggmall"
    @SerializedName("loginName")   val loginName: String,    // "lbs-brixen"
    @SerializedName("address")     val address: String = ""
)