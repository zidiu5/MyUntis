package com.myuntis.app.data.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class NotifType(
    val key: String,
    val channelId: String,
    val title: String,
    val description: String,
    val route: String                 // where a tap on the notification leads
) {
    HOMEWORK_TAB("hw_tab", "hw_tab", "Hausaufgaben (Aufgaben-Tab)",
        "Am Vortag erinnern, wenn eine Hausaufgabe noch nicht erledigt ist", "homework"),
    HOMEWORK_BOARD("hw_board", "hw_board", "Hausaufgaben (Schwarzes Brett)",
        "Am Vortag erinnern bei Einträgen deiner Klasse, die du nicht abgehakt hast", "board"),
    NEW_GRADE("grade", "new_grade", "Neue Note",
        "Sobald eine neue Note eingetragen wird", "grades"),
    NEW_CLASSREG("classreg", "new_classreg", "Neuer Klassenbucheintrag",
        "Sobald ein neuer Eintrag im Klassenbuch steht", "classreg"),
    NEW_EXAM("exam", "new_exam", "Neue Prüfung",
        "Sobald ein neuer Prüfungstermin eingetragen wird", "exams")
}

data class NotificationSettings(
    val enabled: Set<NotifType> = emptySet(),
    val reminderMinutes: Int = 17 * 60            // minutes after midnight
) {
    val anyEnabled: Boolean get() = enabled.isNotEmpty()
    fun isOn(type: NotifType) = type in enabled
    val reminderText: String get() = "%02d:%02d".format(reminderMinutes / 60, reminderMinutes % 60)
}

/** true while the app is on screen → the background job stays quiet. */
object AppForeground {
    @Volatile var isForeground = false
}

/** Route requested by a tapped notification; MainScreen consumes it. */
object NavRequests {
    const val EXTRA_ROUTE = "open_route"
    private val _route = MutableStateFlow<String?>(null)
    val route: StateFlow<String?> = _route
    fun request(route: String?) { _route.value = route }
    fun consume() { _route.value = null }
}