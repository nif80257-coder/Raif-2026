package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamEmptyAnswersTest {

    private fun group(
        name: String,
        streams: List<StreamItem> = emptyList(),
        isLoading: Boolean = false,
        error: String? = null,
    ) = AddonStreamGroup(
        addonName = name,
        addonId = "id-$name",
        streams = streams,
        isLoading = isLoading,
        error = error,
    )

    @Test
    fun `a source that answered with nothing is reported by name`() {
        val answers = listOf(group(name = "Quiet"), group(name = "Loud", streams = listOf(stream()))).emptyAnswerSources()

        assertEquals(listOf("Quiet"), answers.map { answer -> answer.addonName })
        assertEquals(listOf("id-Quiet"), answers.map { answer -> answer.addonId })
    }

    @Test
    fun `a source still loading has not answered yet`() {
        val answers = listOf(group(name = "Slow", isLoading = true)).emptyAnswerSources()

        assertTrue(answers.isEmpty())
    }

    @Test
    fun `a source that failed already says why and is not counted as empty`() {
        val answers = listOf(group(name = "Dead", error = "timeout")).emptyAnswerSources()

        assertTrue(answers.isEmpty())
    }

    @Test
    fun `the state exposes the empty answers in the list's own order`() {
        val state = StreamsUiState(
            groups = listOf(
                group(name = "First"),
                group(name = "Second", streams = listOf(stream())),
                group(name = "Third"),
            ),
        )

        assertEquals(listOf("First", "Third"), state.emptyAnswerSources.map { answer -> answer.addonName })
        assertTrue(state.hasAnyStreams)
    }

    @Test
    fun `no groups means nothing to explain`() {
        assertTrue(StreamsUiState().emptyAnswerSources.isEmpty())
    }

    private fun stream() = StreamItem(
        addonName = "Test source",
        addonId = "test",
        title = "1080p",
        url = "https://example.com/video.mkv",
    )
}
