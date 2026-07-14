package moe.rukamori.archivetune.qobuz

import org.json.JSONArray
import org.json.JSONObject

/**
 * A single direct Qobuz API credential set. Unlike a proxy [instance] (a base URL), a token talks to
 * www.qobuz.com directly using the user auth token plus the app_id/app_secret pair that signs each
 * request. Stored as JSON under [moe.rukamori.archivetune.constants.QobuzTokensKey].
 */
data class QobuzToken(
    val token: String,
    val userId: String = "",
    val appId: String,
    val appSecret: String,
    val label: String = "",
    val subscription: String = "",
) {
    /** Stable identifier used for health-cache keys and dedupe (the auth token is unique per account). */
    val id: String get() = token.take(12)

    fun toJson(): JSONObject =
        JSONObject().apply {
            put("token", token)
            put("userId", userId)
            put("appId", appId)
            put("appSecret", appSecret)
            put("label", label)
            put("subscription", subscription)
        }

    companion object {
        fun fromJson(obj: JSONObject): QobuzToken? {
            val token = obj.optString("token").trim()
            val appId = obj.optString("appId").trim()
            val appSecret = obj.optString("appSecret").trim()
            if (token.isEmpty() || appId.isEmpty() || appSecret.isEmpty()) return null
            return QobuzToken(
                token = token,
                userId = obj.optString("userId").trim(),
                appId = appId,
                appSecret = appSecret,
                label = obj.optString("label").trim(),
                subscription = obj.optString("subscription").trim(),
            )
        }

        fun listToJson(tokens: List<QobuzToken>): String =
            JSONArray().apply { tokens.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(raw: String?): List<QobuzToken> {
            if (raw.isNullOrBlank()) return emptyList()
            val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
            return (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.let { fromJson(it) }
            }
        }
    }
}
