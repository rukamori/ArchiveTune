package moe.rukamori.archivetune.qobuz

import org.json.JSONObject

/**
 * Shared parsers for bulk-adding streaming sources. Used by both Tidal and Qobuz instance managers
 * (URLs) and by the Qobuz token manager (pasted token blocks). Everything is tolerant of messy
 * copy/paste: mixed separators, decorative arrows, emoji, and label lines are all handled.
 */
object SourceInputParsing {
    private val SEPARATORS = Regex("[\\s,;]+")

    /**
     * Splits a blob of pasted text into candidate instance URLs. Accepts newline/space/comma
     * separated lists, strips surrounding junk, ensures an https scheme, and de-dupes while
     * preserving order.
     */
    fun parseUrls(input: String): List<String> {
        if (input.isBlank()) return emptyList()
        val seen = LinkedHashSet<String>()
        return input
            .split(SEPARATORS)
            .asSequence()
            .map { it.trim().trim('"', '\'', '<', '>', '(', ')', ',') }
            .filter { it.isNotEmpty() }
            .mapNotNull { normalizeUrl(it) }
            .filter { seen.add(it.lowercase()) }
            .toList()
    }

    private fun normalizeUrl(raw: String): String? {
        var url = raw
        if (!url.contains("://")) url = "https://$url"
        // Must look like a host with a dot; otherwise it's probably a stray word.
        val host = url.substringAfter("://").substringBefore("/")
        if (!host.contains('.')) return null
        return url.trimEnd('/')
    }

    /**
     * Parses one or more Qobuz token blocks from pasted text. A block is delimited by a line
     * carrying a token, and can be preceded by a label line (e.g. "Qobuz - JP"). Recognizes the
     * common share format:
     *
     *   Qobuz - JP
     *   Token ➠  BpfgA3...
     *   User ID ➠ 13193690
     *   Subscription ➠ Qobuz Studio
     *   ⚠️ ... app_id: 312369995 & app_secret: e79f8b9be485692b0e5f9dd895826368
     *
     * A token is only emitted once it has a token string, app_id and app_secret.
     */
    fun parseQobuzTokens(input: String): List<QobuzToken> {
        if (input.isBlank()) return emptyList()
        val blocks = mutableListOf<JSONObject>()
        var current: JSONObject? = null
        var pendingLabel = ""

        fun startBlock() {
            current = JSONObject()
            if (pendingLabel.isNotEmpty()) {
                current?.put("label", pendingLabel)
                pendingLabel = ""
            }
            blocks.add(current!!)
        }

        for (rawLine in input.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            val token = matchField(line, "token")
            val userId = matchField(line, "user id", "user_id", "userid", "user")
            val subscription = matchField(line, "subscription", "plan")
            val appId = Regex("app_id:?\\s*([0-9]+)", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1)
            val appSecret =
                Regex("app_secret:?\\s*([a-f0-9]{16,})", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1)

            when {
                token != null -> {
                    startBlock()
                    current?.put("token", token)
                }
                userId != null -> current?.put("userId", userId)
                subscription != null -> current?.put("subscription", subscription)
                // A plain line with no field markers/separators becomes the label for the next block
                // (e.g. "Qobuz - JP"). Lines like "Lossless Streaming ➠ ✅" carry a separator and are
                // ignored so they don't get mistaken for a label.
                !line.contains(Regex("[➠→➔⇒:]")) && !line.contains("app_") && current == null ->
                    pendingLabel = line.take(40)
                else -> {}
            }

            // app_id / app_secret can share a line and apply to the current block.
            if (appId != null) current?.put("appId", appId)
            if (appSecret != null) current?.put("appSecret", appSecret)
        }

        return blocks.mapNotNull { QobuzToken.fromJson(it) }
    }

    /**
     * Extracts the value after a "label ➠ value" / "label: value" style separator, matching any of
     * the provided [names] (case-insensitive) as the field label.
     */
    private fun matchField(
        line: String,
        vararg names: String,
    ): String? {
        val lower = line.lowercase()
        for (name in names) {
            if (!lower.startsWith(name)) continue
            val after = line.substring(name.length)
            val value = after.dropWhile { it == ' ' || it in "➠→➔⇒:=\t" }.trim()
            if (value.isNotEmpty()) return value.trim('"', '\'')
        }
        return null
    }
}
