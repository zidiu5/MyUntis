package com.myuntis.app.domain.model

data class SavedAccount(
    val id: String,
    val displayName: String,
    val className: String,
    val schoolName: String,
    val username: String,
    val password: String,
    val school: String,   // loginName, e.g. "lbs-brixen"
    val server: String    // e.g. "lbs-brixen.webuntis.com"
) {
    companion object {
        fun makeId(server: String, username: String): String =
            server.trim().lowercase().removeSuffix(".webuntis.com") +
                    "|" + username.trim().lowercase()
    }
}