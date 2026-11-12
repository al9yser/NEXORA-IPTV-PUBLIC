package com.nexora.iptv.data

object M3uParser {
    fun parse(text: String): List<Channel> {
        val lines = text.removePrefix("\uFEFF").lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        val result = mutableListOf<Channel>()
        var pendingName: String? = null
        var pendingGroup = "Other"
        var pendingLogo: String? = null
        var pendingId: String? = null
        for (line in lines) {
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                val comma = line.lastIndexOf(',')
                pendingName = if (comma >= 0 && comma < line.lastIndex) line.substring(comma + 1).trim()
                    else attribute(line, "tvg-name") ?: "Unnamed channel"
                pendingGroup = attribute(line, "group-title")?.takeIf(String::isNotBlank) ?: "Other"
                pendingLogo = attribute(line, "tvg-logo")
                pendingId = attribute(line, "tvg-id")
            } else if (!line.startsWith("#") && (line.startsWith("https://") || line.startsWith("http://"))) {
                result += Channel(
                    name = pendingName?.takeIf(String::isNotBlank) ?: line.substringAfterLast('/').ifBlank { "Channel" },
                    url = line, group = pendingGroup, logo = pendingLogo, tvgId = pendingId
                )
                pendingName = null; pendingGroup = "Other"; pendingLogo = null; pendingId = null
            }
        }
        return result
    }
    private fun attribute(line: String, key: String): String? {
        val regex = Regex("""$key\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        return regex.find(line)?.groupValues?.getOrNull(1)
    }
}
