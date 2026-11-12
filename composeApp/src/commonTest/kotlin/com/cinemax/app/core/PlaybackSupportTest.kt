package com.cinemax.app.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackSupportTest {
    @Test
    fun detectsContainersTheNativePlayerCannotOpen() {
        assertEquals("mkv", unsupportedVideoContainer("https://cdn.example/movie.mkv?token=1", null))
        assertEquals("mkv", unsupportedVideoContainer("https://cdn.example/stream/1", "Movie.2024.1080p.MKV"))
        assertEquals("avi", unsupportedVideoContainer("https://cdn.example/a.avi", null))
    }

    @Test
    fun acceptsStreamsAvPlayerHandles() {
        assertNull(unsupportedVideoContainer("https://cdn.example/hls/index.m3u8", null))
        assertNull(unsupportedVideoContainer("https://cdn.example/movie.mp4", null))
        assertNull(unsupportedVideoContainer("https://cdn.example/movie", "movie.mov"))
        assertNull(unsupportedVideoContainer(null, null))
    }
}
