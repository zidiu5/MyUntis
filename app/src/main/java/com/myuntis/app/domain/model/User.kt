package com.myuntis.app.domain.model

// =============================================================
// USER - Domain Model
// =============================================================
// Represents a logged-in student or teacher.
// This is OUR model - independent of the WebUntis API format.
// The API might call fields differently (e.g., "longName" instead
// of "fullName"), but we always work with THIS class in the UI.
// =============================================================
data class User(
    val id: Int,
    val username: String,
    val firstName: String,
    val lastName: String,
    val fullName: String = "$firstName $lastName",  // Computed default
    val schoolClass: String = "",           // e.g., "3AHIT"
    val schoolName: String = "",
    val personType: Int = 5,               // 2=Teacher, 5=Student
    val sessionId: String = ""             // Active session token
)

// Extension: Is this user a student?
val User.isStudent: Boolean get() = personType == 5

// Extension: Is this user a teacher?
val User.isTeacher: Boolean get() = personType == 2

// Extension: Short display name (e.g., "Max M.")
val User.shortName: String get() = "$firstName ${lastName.firstOrNull() ?: ""}."