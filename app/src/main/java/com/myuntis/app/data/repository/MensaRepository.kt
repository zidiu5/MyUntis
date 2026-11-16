package com.myuntis.app.data.repository

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.myuntis.app.data.local.DataStoreManager
import com.myuntis.app.data.network.NetworkResult
import com.myuntis.app.data.network.UntisApiService
import com.myuntis.app.domain.model.MensaData
import com.myuntis.app.domain.model.MensaDay
import com.myuntis.app.domain.model.MensaPlan
import com.myuntis.app.domain.model.MensaWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MensaRepository @Inject constructor(
    private val apiService: UntisApiService,
    private val dataStore: DataStoreManager
) {
    /** true if the active account belongs to a school with a mensa plan */
    val supportedFlow: Flow<Boolean> = combine(
        dataStore.loginCredentials,
        dataStore.accounts,
        dataStore.activeAccountId
    ) { creds, accounts, activeId ->
        val account = accounts.firstOrNull { it.id == activeId }
        MensaConfig.isSupportedSchool(creds.school, account?.schoolName.orEmpty())
    }.distinctUntilChanged()

    suspend fun getMensa(): NetworkResult<MensaData> {
        var errorText = "Mensaplan konnte nicht geladen werden."

        try {
            val resp = apiService.getMensaPlans(MensaConfig.URL, MensaConfig.API_KEY)
            when {
                resp.isSuccessful -> {
                    val body   = resp.body()
                    val parsed = if (body != null) parse(body, fromCache = false) else null
                    if (body != null && parsed != null) {
                        dataStore.saveMensaCache(body.toString())
                        return NetworkResult.Success(parsed)
                    }
                    errorText = "Der Mensaplan hat ein unerwartetes Format."
                }
                resp.code() == 401 || resp.code() == 403 ->
                    errorText = "Der Zugriffsschlüssel für den Mensaplan wurde abgelehnt."
                else ->
                    errorText = "Mensaplan nicht erreichbar (HTTP ${resp.code()})."
            }
        } catch (_: java.net.UnknownHostException) {
            errorText = "Keine Internetverbindung."
        } catch (e: Exception) {
            errorText = e.localizedMessage ?: errorText
        }

        // Fallback: last saved plan (offline)
        dataStore.loadMensaCache()?.let { raw ->
            val cached = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
                ?.let { parse(it, fromCache = true) }
            if (cached != null) return NetworkResult.Success(cached)
        }
        return NetworkResult.Error(errorText)
    }

    // ── JSON → domain ─────────────────────────────────────────

    private val weekdays = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag")

    private fun JsonElement?.text(): String =
        if (this != null && this.isJsonPrimitive) this.asString.trim() else ""

    private fun parse(root: JsonObject, fromCache: Boolean): MensaData? {
        val legend = LinkedHashMap<String, String>()
        val legendEl = root.get("_Legende_Allergene")
        if (legendEl != null && legendEl.isJsonObject) {
            for ((k, v) in legendEl.asJsonObject.entrySet()) legend[k] = v.text()
        }

        val plans = root.entrySet()
            .filter { (key, value) ->
                !key.startsWith("_") && value.isJsonObject && value.asJsonObject.has("Wochen")
            }
            .mapNotNull { (key, value) -> parsePlan(key, value.asJsonObject) }

        return if (plans.isEmpty()) null else MensaData(plans, legend, fromCache)
    }

    private fun parsePlan(key: String, obj: JsonObject): MensaPlan? {
        val weeksEl = obj.get("Wochen")
        if (weeksEl == null || !weeksEl.isJsonObject) return null

        val weeks = weeksEl.asJsonObject.entrySet().mapNotNull { (name, el) ->
            if (el.isJsonObject) MensaWeek(name, parseDays(el.asJsonObject)) else null
        }
        if (weeks.isEmpty()) return null

        return MensaPlan(key, obj.get("Titel").text().ifBlank { key }, weeks)
    }

    private fun parseDays(week: JsonObject): List<MensaDay> {
        // Mon–Fri always, extra days (e.g. "Samstag") only if present
        val names = weekdays + week.keySet().filter { it !in weekdays }

        return names.mapNotNull { name ->
            val entry = week.get(name)
            if (entry == null || !entry.isJsonObject) {
                if (name in weekdays) MensaDay(name, "", emptyList()) else null
            } else {
                val o = entry.asJsonObject
                val a = o.get("Allergene")
                val allergens: List<String> = when {
                    a == null        -> emptyList()
                    a.isJsonArray    -> a.asJsonArray.map { it.text() }.filter { it.isNotBlank() }
                    a.isJsonObject   -> a.asJsonObject.keySet().toList()   // old format
                    else             -> emptyList()
                }
                MensaDay(name, o.get("Gericht").text(), allergens)
            }
        }
    }
}