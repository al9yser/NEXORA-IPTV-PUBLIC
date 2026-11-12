package com.nexora.iptv.data

enum class MediaType { LIVE, MOVIE, SERIES }

data class Channel(
    val name: String,
    val url: String,
    val group: String = "Other",
    val logo: String? = null,
    val tvgId: String? = null,
    val type: MediaType = MediaType.LIVE,
    val streamId: String? = null,
    val extension: String? = null
)

data class EpgProgram(
    val title: String,
    val description: String = "",
    val start: String = "",
    val end: String = ""
)
