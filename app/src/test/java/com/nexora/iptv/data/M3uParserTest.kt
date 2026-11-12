package com.nexora.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {
    @Test
    fun parsesQuotedMetadataAndCommaInDisplayName() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="news.id" tvg-logo="https://img.example/logo.png" group-title="News",News, International
            https://stream.example/live.m3u8
        """.trimIndent()

        val result = M3uParser.parse(playlist)

        assertEquals(1, result.size)
        assertEquals("News, International", result[0].name)
        assertEquals("News", result[0].group)
        assertEquals("news.id", result[0].tvgId)
        assertEquals("https://img.example/logo.png", result[0].logo)
    }

    @Test
    fun classifiesVodAndSeriesGroups() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 group-title="Movies",A Movie
            https://stream.example/movie.mp4
            #EXTINF:-1 group-title="TV Series",Episode One
            https://stream.example/episode.mp4
        """.trimIndent()

        val result = M3uParser.parse(playlist)

        assertEquals(2, result.size)
        assertEquals(MediaType.MOVIE, result[0].type)
        assertEquals(MediaType.SERIES, result[1].type)
    }

    @Test
    fun ignoresDuplicateStreamUrls() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1,Channel One
            https://stream.example/one
            #EXTINF:-1,Channel One Duplicate
            https://stream.example/one
        """.trimIndent()

        assertEquals(1, M3uParser.parse(playlist).size)
        assertTrue(M3uParser.parse(playlist).isNotEmpty())
    }
}
