package com.myuntis.app.domain.model

data class MensaDay(
    val name: String,              // "Montag"
    val dish: String,              // "Farfalle ...; Gemüse / Salat; Fruchtjogurt"
    val allergens: List<String>    // ["A", "G", "D"]
)

data class MensaWeek(val name: String, val days: List<MensaDay>)   // "Woche_1"

data class MensaPlan(val key: String, val title: String, val weeks: List<MensaWeek>)

data class MensaData(
    val plans: List<MensaPlan>,
    val legend: Map<String, String>,   // "A" -> "Glutenhaltiges Getreide"
    val fromCache: Boolean
)

/** Finds e.g. "Sommer_Vegetarisch"; falls back to the closest plan. */
fun MensaData.findPlan(season: String, vegetarian: Boolean): MensaPlan? {
    val type = if (vegetarian) "Vegetarisch" else "Standard"
    return plans.firstOrNull { it.key.equals("${season}_$type", ignoreCase = true) }
        ?: plans.firstOrNull { it.key.contains(season, true) && it.key.contains(type, true) }
        ?: plans.firstOrNull { it.key.contains(season, true) }
        ?: plans.firstOrNull()
}