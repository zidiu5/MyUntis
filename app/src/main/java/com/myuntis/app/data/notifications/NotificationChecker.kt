package com.myuntis.app.data.notifications

import com.myuntis.app.data.local.DataStoreManager
import com.myuntis.app.data.network.NetworkResult
import com.myuntis.app.data.repository.BoardRepository
import com.myuntis.app.data.repository.CacheKeys
import com.myuntis.app.data.repository.ClassRegRepository
import com.myuntis.app.data.repository.DataCache
import com.myuntis.app.data.repository.ExamRepository
import com.myuntis.app.data.repository.GradesRepository
import com.myuntis.app.data.repository.HomeworkRepository
import com.myuntis.app.data.repository.SessionManager
import com.myuntis.app.domain.model.ClassRegEntry
import com.myuntis.app.domain.model.Exam
import com.myuntis.app.domain.model.SubjectWithGrades
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationChecker @Inject constructor(
    private val dataStore: DataStoreManager,
    private val sessionManager: SessionManager,
    private val notifier: Notifier,
    private val grades: GradesRepository,
    private val classReg: ClassRegRepository,
    private val exams: ExamRepository,
    private val homework: HomeworkRepository,
    private val board: BoardRepository,
    private val cache: DataCache
) {
    private val stamp = DateTimeFormatter.ofPattern("dd.MM. HH:mm")

    // ─────────────────────────────────────────────────────────
    // Entry points
    // ─────────────────────────────────────────────────────────

    /** Periodic job and "Jetzt prüfen": everything that is switched on. */
    suspend fun runChecks() {
        val (s, accId) = prepare() ?: return
        val notes = Collections.synchronizedList(mutableListOf<String>())

        coroutineScope {
            if (s.isOn(NotifType.NEW_GRADE))    launch { notes += guarded("Noten") { checkGrades(accId) } }
            if (s.isOn(NotifType.NEW_CLASSREG)) launch { notes += guarded("Klassenbuch") { checkClassReg(accId) } }
            if (s.isOn(NotifType.NEW_EXAM))     launch { notes += guarded("Prüfungen") { checkExams(accId) } }
            if (s.isOn(NotifType.HOMEWORK_TAB) || s.isOn(NotifType.HOMEWORK_BOARD)) {
                launch { notes += guarded("Hausaufgaben") { checkHomework(s, accId) } }
            }
        }
        finish(notes)
    }

    /** Exact-time job: only the homework reminders. */
    suspend fun runReminders() {
        val (s, accId) = prepare() ?: return
        if (!s.isOn(NotifType.HOMEWORK_TAB) && !s.isOn(NotifType.HOMEWORK_BOARD)) return
        finish(listOf(guarded("Hausaufgaben") { checkHomework(s, accId) }))
    }

    suspend fun recordStatus(text: String) {
        dataStore.saveNotifStatus("${LocalDateTime.now().format(stamp)} · $text")
    }

    private suspend fun finish(notes: List<String>) {
        recordStatus(notes.joinToString(" · ").ifBlank { "nichts zu tun" })
    }

    private suspend fun prepare(): Pair<NotificationSettings, String>? {
        val s = dataStore.notificationSettings.first()
        if (!s.anyEnabled) return null

        val accId = dataStore.activeAccountId.first()
        if (accId == null || dataStore.accounts.first().none { it.id == accId }) {
            recordStatus("nicht angemeldet"); return null
        }
        if (!notifier.canNotify()) {
            recordStatus("Benachrichtigungen im System ausgeschaltet"); return null
        }
        sessionManager.ensureFresh()      // fresh process → logs in again if needed
        return s to accId
    }

    // ─────────────────────────────────────────────────────────
    // New grades
    // ─────────────────────────────────────────────────────────
    private suspend fun checkGrades(accId: String): String {
        val subjects = when (val r = grades.getSubjectGrades(null)) {
            is NetworkResult.Success -> r.data
            else -> return "Noten: konnten nicht geladen werden"
        }
        val all = subjects.flatMap { s -> s.entries.map { e -> s to e } }
        val ids = all.map { (_, e) -> "g${e.id}" }.toSet()

        val seen = dataStore.getSeen(NotifType.NEW_GRADE, accId)
        if (seen == null) {
            dataStore.saveSeen(NotifType.NEW_GRADE, accId, ids)
            return "Noten: Stand gespeichert (${ids.size})"
        }
        val fresh = all.filter { (_, e) -> "g${e.id}" !in seen }
        if (fresh.isNotEmpty()) {
            val lines = fresh.map { (s, e) ->
                "${s.subjectShort}: ${e.markName}" +
                        e.description.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            }
            notifier.show(
                NotifType.NEW_GRADE, newId(NotifType.NEW_GRADE),
                plural(fresh.size, "Neue Note", "Neue Noten"), lines.limited()
            )
        }
        dataStore.saveSeen(NotifType.NEW_GRADE, accId, seen + ids)
        return if (fresh.isEmpty()) "Noten: keine neuen" else "Noten: ${fresh.size} gemeldet"
    }

    // ─────────────────────────────────────────────────────────
    // New class register entries
    // ─────────────────────────────────────────────────────────
    private suspend fun checkClassReg(accId: String): String {
        val entries = when (val r = classReg.getEntries()) {
            is NetworkResult.Success -> r.data
            else -> return "Klassenbuch: konnte nicht geladen werden"
        }
        val ids = entries.map { "c${it.id}" }.toSet()

        val seen = dataStore.getSeen(NotifType.NEW_CLASSREG, accId)
        if (seen == null) {
            dataStore.saveSeen(NotifType.NEW_CLASSREG, accId, ids)
            return "Klassenbuch: Stand gespeichert (${ids.size})"
        }
        val fresh = entries.filter { "c${it.id}" !in seen }
        if (fresh.isNotEmpty()) {
            val lines = fresh.map { e ->
                e.subjectName.substringBefore(" (") + ": " + e.text.ifBlank { "Eintrag" }.take(80)
            }
            val title = if (fresh.any { it.isPersonal }) "Neuer Klassenbucheintrag für dich"
            else plural(fresh.size, "Neuer Klassenbucheintrag", "Neue Klassenbucheinträge")
            notifier.show(NotifType.NEW_CLASSREG, newId(NotifType.NEW_CLASSREG), title, lines.limited())
        }
        dataStore.saveSeen(NotifType.NEW_CLASSREG, accId, seen + ids)
        return if (fresh.isEmpty()) "Klassenbuch: keine neuen" else "Klassenbuch: ${fresh.size} gemeldet"
    }

    // ─────────────────────────────────────────────────────────
    // New exam dates
    // ─────────────────────────────────────────────────────────
    private suspend fun checkExams(accId: String): String {
        val list = when (val r = exams.getExams()) {
            is NetworkResult.Success -> r.data
            else -> return "Prüfungen: konnten nicht geladen werden"
        }
        val keys = list.map { examKey(it) }.toSet()

        val seen = dataStore.getSeen(NotifType.NEW_EXAM, accId)
        if (seen == null) {
            dataStore.saveSeen(NotifType.NEW_EXAM, accId, keys)
            return "Prüfungen: Stand gespeichert (${keys.size})"
        }
        val today = LocalDate.now()
        val fresh = list.filter { examKey(it) !in seen && !it.date.isBefore(today) }
        if (fresh.isNotEmpty()) {
            val fmt = DateTimeFormatter.ofPattern("EEE, dd.MM.", Locale.GERMAN)
            val lines = fresh.sortedBy { it.date }.map { e ->
                "${e.subject} · ${e.date.format(fmt)} ${e.startTime.toString().take(5)}" +
                        e.name.takeIf { it.isNotBlank() && it != e.subject }?.let { " – $it" }.orEmpty()
            }
            notifier.show(
                NotifType.NEW_EXAM, newId(NotifType.NEW_EXAM),
                plural(fresh.size, "Neuer Prüfungstermin", "Neue Prüfungstermine"), lines.limited()
            )
        }
        dataStore.saveSeen(NotifType.NEW_EXAM, accId, seen + keys)
        return if (fresh.isEmpty()) "Prüfungen: keine neuen" else "Prüfungen: ${fresh.size} gemeldet"
    }

    // ─────────────────────────────────────────────────────────
    // Homework reminders (the day before)
    // ─────────────────────────────────────────────────────────
    private suspend fun checkHomework(s: NotificationSettings, accId: String): String {
        val now = LocalDateTime.now()
        if (now.hour * 60 + now.minute < s.reminderMinutes) {
            return "Erinnerung erst ab ${s.reminderText}"
        }

        val today    = now.toLocalDate()
        val tomorrow = today.plusDays(1)
        // what we already reminded about (old days are dropped)
        val reminded = dataStore.getReminded(accId)
            .filter { it.substringBefore('|') >= today.toString() }.toMutableSet()
        val parts = mutableListOf<String>()

        // Aufgaben-Tab
        if (s.isOn(NotifType.HOMEWORK_TAB)) {
            when (val r = homework.getHomework()) {
                is NetworkResult.Success -> {
                    val open = r.data.filter { it.dueDate == tomorrow && !it.isCompleted }
                    val due  = open.filter { "$tomorrow|tab|${it.id}" !in reminded }
                    if (due.isNotEmpty()) {
                        notifier.show(
                            NotifType.HOMEWORK_TAB, 1001,
                            "Morgen fällig: " + plural(due.size, "1 Hausaufgabe", "${due.size} Hausaufgaben"),
                            due.map { "${it.subjectShortName}: ${it.text.take(80)}" }.limited()
                        )
                        due.forEach { reminded.add("$tomorrow|tab|${it.id}") }
                    }
                    parts += "Aufgaben-Tab: ${open.size} offen für morgen, ${due.size} gemeldet"
                }
                else -> parts += "Aufgaben-Tab: konnte nicht geladen werden"
            }
        }

        // Schwarzes Brett (students only)
        if (s.isOn(NotifType.HOMEWORK_BOARD)) {
            if (!board.isStudentFlow.first()) {
                parts += "Brett: nur für Schüler-Konten"
            } else {
                when (val r = board.sync()) {
                    is NetworkResult.Success -> {
                        val done = board.doneIds().first()
                        val forTomorrow = r.data.posts.filter {
                            !it.deleted && it.type == "HOMEWORK" && it.dueDate == tomorrow.toString()
                        }
                        val open = forTomorrow.filter { !it.hidden && it.id !in done }
                        val due  = open.filter { "$tomorrow|board|${it.id}" !in reminded }
                        if (due.isNotEmpty()) {
                            notifier.show(
                                NotifType.HOMEWORK_BOARD, 1002,
                                "Morgen fällig (Brett): " +
                                        plural(due.size, "1 Hausaufgabe", "${due.size} Hausaufgaben"),
                                due.map { p ->
                                    listOfNotNull(p.subject?.takeIf { it.isNotBlank() }, p.title)
                                        .joinToString(": ")
                                }.limited()
                            )
                            due.forEach { reminded.add("$tomorrow|board|${it.id}") }
                        }
                        parts += "Brett: ${open.size} offen für morgen (${forTomorrow.size} insgesamt), " +
                                "${due.size} gemeldet"
                    }
                    else -> parts += "Brett: konnte nicht geladen werden"
                }
            }
        }

        dataStore.saveReminded(accId, reminded)
        return parts.joinToString(", ")
    }

    // ─────────────────────────────────────────────────────────
    // Called by the app: what the user saw must not be announced again
    // ─────────────────────────────────────────────────────────
    suspend fun markSeenFromApp() {
        val s = dataStore.notificationSettings.first()
        if (!s.anyEnabled) return
        val accId = dataStore.activeAccountId.first() ?: return

        if (s.isOn(NotifType.NEW_GRADE)) {
            cache.get<List<SubjectWithGrades>>(CacheKeys.grades(null))?.let { list ->
                addSeen(NotifType.NEW_GRADE, accId, list.flatMap { it.entries }.map { "g${it.id}" })
            }
        }
        if (s.isOn(NotifType.NEW_CLASSREG)) {
            cache.get<List<ClassRegEntry>>(CacheKeys.CLASSREG)?.let { list ->
                addSeen(NotifType.NEW_CLASSREG, accId, list.map { "c${it.id}" })
            }
        }
        if (s.isOn(NotifType.NEW_EXAM)) {
            cache.get<List<Exam>>(CacheKeys.EXAMS)?.let { list ->
                addSeen(NotifType.NEW_EXAM, accId, list.map { examKey(it) })
            }
        }
    }

    private suspend fun addSeen(type: NotifType, accId: String, ids: Collection<String>) {
        val old = dataStore.getSeen(type, accId) ?: emptySet()
        dataStore.saveSeen(type, accId, old + ids)
    }

    // ── helpers ───────────────────────────────────────────────
    // Untis returns id = 0 for all exams → identify them by date, time, subject and name
    private fun examKey(e: Exam) = "${e.date}|${e.startTime}|${e.subject}|${e.name}"

    private fun newId(type: NotifType) =
        type.ordinal * 100_000 + (System.currentTimeMillis() / 1000 % 100_000).toInt()

    private fun plural(n: Int, one: String, many: String) = if (n == 1) one else many

    private fun List<String>.limited(max: Int = 6): List<String> =
        if (size <= max) this else take(max) + "+ ${size - max} weitere"

    private suspend fun guarded(label: String, block: suspend () -> String): String =
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { "$label: Fehler (${e.javaClass.simpleName})" }
}