package com.myuntis.app.data.repository

import com.myuntis.app.data.local.DataStoreManager
import com.myuntis.app.data.network.NetworkResult
import com.myuntis.app.data.network.UntisApiHelper
import com.myuntis.app.data.network.UntisApiService
import com.myuntis.app.data.network.toSessionCookie
import com.myuntis.app.data.network.untisToLocalDate
import com.myuntis.app.data.network.untisToLocalTime
import com.myuntis.app.domain.model.Exam
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExamRepository @Inject constructor(
    private val apiService: UntisApiService,
    private val dataStore: DataStoreManager
) {
    suspend fun getExams(): NetworkResult<List<Exam>> {
        return try {
            val credentials = dataStore.loginCredentials.first()
            val profile     = dataStore.userProfile.first()
            val sessionId   = dataStore.sessionId.first()
                ?: return NetworkResult.Error("Keine aktive Session.")

            val cookie    = sessionId.toSessionCookie()
            val server    = credentials.server
            val studentId = profile.personId

            val now       = LocalDate.now()
            val startDate = if (now.monthValue >= 9)
                LocalDate.of(now.year, 9, 1)
            else
                LocalDate.of(now.year - 1, 9, 1)
            val endDate = startDate.plusMonths(10)

            val url      = UntisApiHelper.buildExamsUrl(server, studentId, startDate, endDate)
            val response = apiService.getExams(url, cookie)

            if (!response.isSuccessful) {
                return NetworkResult.Error("HTTP ${response.code()}")
            }

            val items = response.body()?.data?.exams ?: emptyList()
            val today = LocalDate.now()

            val exams: List<Exam> = items.mapNotNull { item ->
                try {
                    val date = item.examDate.untisToLocalDate()
                    Exam(
                        id          = item.id,
                        name        = item.name.trim(),
                        date        = date,
                        startTime   = item.startTime.untisToLocalTime(),
                        endTime     = item.endTime.untisToLocalTime(),
                        subject     = item.subject.trim(),
                        teachers    = item.teachers,
                        rooms       = item.rooms,
                        description = item.text.trim(),
                        grade       = item.grade.trim(),
                        isPast      = date < today
                    )
                } catch (_: Exception) { null }
            }.sortedBy { it.date }

            NetworkResult.Success(exams)
        } catch (e: Exception) {
            NetworkResult.Error(e.localizedMessage ?: "Fehler beim Laden")
        }
    }
}