package com.nexora.iptv.data

object M3uParser {
    fun parse(text: String): List<Channel> {
        val lines = text.removePrefix("\uFEFF")
            .lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        val result = mutableListOf<Channel>()
        var pendingName: String? = null
        var pendingGroup = "Other"
        var pendingLogo: String? = null
        var pendingId: String? = null

        for (line in lines) {
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val comma = displayNameSeparator(line)
                    val displayName = if (comma >= 0) line.substring(comma + 1).trim() else ""
                    pendingName = displayName.ifBlank {
                        attribute(line, "tvg-name") ?: "Unnamed channel"
                    }
                    pendingGroup = attribute(line, "group-title")?.takeIf(String::isNotBlank) ?: "Other"
                    pendingLogo = attribute(line, "tvg-logo")?.takeIf(String::isNotBlank)
                    pendingId = attribute(line, "tvg-id")?.takeIf(String::isNotBlank)
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    pendingGroup = line.substringAfter(':').trim().ifBlank { pendingGroup }
                }
                !line.startsWith("#") && (line.startsWith("https://", true) || line.startsWith("http://", true)) -> {
                    val normalizedGroup = pendingGroup.lowercase()
                    val mediaType = when {
                        normalizedGroup.contains("movie") || normalizedGroup.contains("vod") ||
                            normalizedGroup.contains("film") -> MediaType.MOVIE
                        normalizedGroup.contains("series") || normalizedGroup.contains("tv show") -> MediaType.SERIES
                        else -> MediaType.LIVE
                    }
                    result += Channel(
                        name = pendingName?.takeIf(String::isNotBlank)
                            ?: line.substringAfterLast('/').substringBefore('?').ifBlank { "Channel" },
                        url = line,
                        group = pendingGroup,
                        logo = pendingLogo,
                        tvgId = pendingId,
                        type = mediaType
                    )
                    pendingName = null
                    pendingGroup = "Other"
                    pendingLogo = null
                    pendingId = null
                }
            }
        }
        return result.distinctBy { it.url }
    }

    private fun displayNameSeparator(line: String): Int {
        var quote: Char? = null
        line.forEachIndexed { index, char ->
            if (quote != null) {
                if (char == quote) quote = null
            } else if (char == '"' || char == '\'') {
                quote = char
            } else if (char == ',') {
                return index
            }
        }
        return -1
    }

    private fun attribute(line: String, key: String): String? {
        val regex = Regex("""(?:^|\s)$key\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,]+))""", RegexOption.IGNORE_CASE)
        val match = regex.find(line) ?: return null
        return match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
    }
}
