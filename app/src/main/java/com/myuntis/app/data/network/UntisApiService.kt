package com.myuntis.app.data.network

import com.myuntis.app.data.network.model.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

// =============================================================
// UNTIS API SERVICE
// =============================================================
interface UntisApiService {

    // ── JSON-RPC (Auth, Homework, Grades, Absences) ───────────

    @POST
    suspend fun authenticate(
        @Url url: String,
        @Body request: JsonRpcRequest
    ): Response<JsonRpcResponse<LoginResult>>

    @POST
    suspend fun logout(
        @Url url: String,
        @Header("Cookie") sessionCookie: String,
        @Body request: JsonRpcRequest
    ): Response<JsonRpcResponse<Any>>

    @POST
    suspend fun getCurrentStudent(
        @Url url: String,
        @Header("Cookie") sessionCookie: String,
        @Body request: JsonRpcRequest
    ): Response<JsonRpcResponse<ApiStudent>>

    @POST
    suspend fun getKlassen(
        @Url url: String,
        @Header("Cookie") sessionCookie: String,
        @Body request: JsonRpcRequest
    ): Response<JsonRpcResponse<List<ApiKlasse>>>

    @POST
    suspend fun getHomeworks(
        @Url url: String,
        @Header("Cookie") sessionCookie: String,
        @Body request: JsonRpcRequest
    ): Response<JsonRpcResponse<List<ApiHomework>>>

    @POST
    suspend fun getGrades(
        @Url url: String,
        @Header("Cookie") sessionCookie: String,
        @Body request: JsonRpcRequest
    ): Response<JsonRpcResponse<List<ApiGrade>>>

    @POST
    suspend fun getAbsences(
        @Url url: String,
        @Header("Cookie") sessionCookie: String,
        @Body request: JsonRpcRequest
    ): Response<JsonRpcResponse<List<ApiAbsence>>>

    @GET
    suspend fun getMessages(
        @Url url: String,
        @Header("Cookie") sessionCookie: String,
        @Query("pageIndex") pageIndex: Int = 0,
        @Query("pageSize") pageSize: Int = 50
    ): Response<List<ApiMessage>>

    // ── REST Timetable API (modern WebUntis) ──────────────────
    // Uses Cookie authentication (JSESSIONID from JSON-RPC login)

    @GET
    suspend fun getTimetableRest(
        @Url url: String,
        @Header("Cookie") sessionCookie: String
    ): Response<RestTimetableResponse>

    // Fallback: Bearer token authentication
    @GET
    suspend fun getTimetableRestBearer(
        @Url url: String,
        @Header("Authorization") bearerToken: String
    ): Response<RestTimetableResponse>

    // Fetches a short-lived JWT Bearer token from the active session
    // Response body is a raw JWT string (not JSON-wrapped)
    @GET
    suspend fun getNewToken(
        @Url url: String,
        @Header("Cookie") sessionCookie: String
    ): Response<ResponseBody>
    // ── Homework REST API ─────────────────────────────────────────
// URL: /WebUntis/api/homeworks/lessons?startDate=YYYYMMDD&endDate=YYYYMMDD
// Authentication: Cookie (JSESSIONID)

    @GET
    suspend fun getHomeworksRest(
        @Url url: String,
        @Header("Cookie") sessionCookie: String
    ): Response<HomeworkApiResponse>

    // Fallback with Bearer token
    @GET
    suspend fun getHomeworksRestBearer(
        @Url url: String,
        @Header("Authorization") bearerToken: String
    ): Response<HomeworkApiResponse>
    // ── Grades REST API ───────────────────────────────────────────
    @GET
    suspend fun getGradesRest(
        @Url url: String,
        @Header("Cookie") sessionCookie: String
    ): Response<GradesApiResponse>

    @GET
    suspend fun getGradesRestBearer(
        @Url url: String,
        @Header("Authorization") bearerToken: String
    ): Response<GradesApiResponse>

    // ── Absences REST API ─────────────────────────────────────────
    @GET
    suspend fun getAbsencesRest(
        @Url url: String,
        @Header("Cookie") sessionCookie: String
    ): Response<AbsencesApiResponse>

    @GET
    suspend fun getAbsencesRestBearer(
        @Url url: String,
        @Header("Authorization") bearerToken: String
    ): Response<AbsencesApiResponse>


    // ── School Year (JSON-RPC) ────────────────────────────────────
    @POST
    suspend fun getCurrentSchoolYear(
        @Url url: String,
        @Header("Cookie") sessionCookie: String,
        @Body request: JsonRpcRequest
    ): Response<JsonRpcResponse<ApiSchoolYear>>

    // ── Grading List REST ─────────────────────────────────────────
    @GET
    suspend fun getGradingList(
        @Url url: String,
        @Header("Cookie") sessionCookie: String
    ): Response<GradingListResponse>

    @GET
    suspend fun getGradingListBearer(
        @Url url: String,
        @Header("Authorization") bearer: String
    ): Response<GradingListResponse>

    // ── Grading per Lesson REST ───────────────────────────────────
    @GET
    suspend fun getGradingLesson(
        @Url url: String,
        @Header("Cookie") sessionCookie: String
    ): Response<GradingLessonResponse>

    @GET
    suspend fun getGradingLessonBearer(
        @Url url: String,
        @Header("Authorization") bearer: String
    ): Response<GradingLessonResponse>

    // ── Absences Students (correct endpoint) ─────────────────────
    @GET
    suspend fun getAbsencesStudents(
        @Url url: String,
        @Header("Cookie") sessionCookie: String
    ): Response<AbsencesStudentsResponse>

    @GET
    suspend fun getAbsencesStudentsBearer(
        @Url url: String,
        @Header("Authorization") bearer: String
    ): Response<AbsencesStudentsResponse>
}