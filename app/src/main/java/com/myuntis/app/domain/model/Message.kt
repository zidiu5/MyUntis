package com.myuntis.app.domain.model

import java.time.LocalDateTime

// =============================================================
// MESSAGE - Domain Model
// =============================================================
data class Message(
    val id: Int,
    val subject: String,                    // Betreff
    val body: String,                       // Nachrichtentext
    val sender: String,                     // Absender
    val sentDateTime: LocalDateTime,
    val isRead: Boolean = false,
    val attachments: List<String> = emptyList()
)