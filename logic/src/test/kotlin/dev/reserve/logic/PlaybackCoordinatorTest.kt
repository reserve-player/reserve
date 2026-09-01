package dev.reserve.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Stands in for ExoPlayer so the auto-advance rules can be verified without a device. */
private class FakeVideoSink : VideoSink {
    val played = mutableListOf<VideoItem>()
    var stopCount = 0

    override fun play(item: VideoItem) {
        played.add(item)
    }

    override fun stop() {
        stopCount++
    }
}

class PlaybackCoordinatorTest {

    private val queue = ReserveQueue()
    private val sink = FakeVideoSink()
    private var stateChanges = 0
    private val coordinator = PlaybackCoordinator(queue, sink) { stateChanges++ }

    @Test
    fun `playNow hands the video straight to the player`() {
        coordinator.playNow(video(1))

        assertEquals(listOf(1L), sink.played.map { it.id })
        assertEquals(1L, queue.nowPlaying?.video?.id)
    }

    @Test
    fun `reserving while a video plays does not interrupt it`() {
        coordinator.playNow(video(1))

        coordinator.reserve(video(2))
        coordinator.reserve(video(3))

        assertEquals(listOf(1L), sink.played.map { it.id })
        assertEquals(1L, queue.nowPlaying?.video?.id)
        assertEquals(listOf(2L, 3L), queue.reservations.map { it.video.id })
    }

    @Test
    fun `reserving with nothing playing starts the video immediately`() {
        coordinator.reserve(video(1))

        assertEquals(listOf(1L), sink.played.map { it.id })
        assertEquals(1L, queue.nowPlaying?.video?.id)
        assertTrue(queue.isEmpty())
    }

    @Test
    fun `the next reservation starts when the current video ends`() {
        coordinator.playNow(video(1))
        coordinator.reserve(video(2))

        coordinator.onItemEnded()

        assertEquals(listOf(1L, 2L), sink.played.map { it.id })
        assertEquals(2L, queue.nowPlaying?.video?.id)
    }

    @Test
    fun `reservations play in the order they were made`() {
        coordinator.playNow(video(1))
        coordinator.reserve(video(2))
        coordinator.reserve(video(3))
        coordinator.reserve(video(4))

        coordinator.onItemEnded()
        coordinator.onItemEnded()
        coordinator.onItemEnded()

        assertEquals(listOf(1L, 2L, 3L, 4L), sink.played.map { it.id })
    }

    @Test
    fun `playback stops cleanly once the queue runs out`() {
        coordinator.playNow(video(1))

        coordinator.onItemEnded()

        assertEquals(1, sink.stopCount)
        assertNull(queue.nowPlaying)
    }

    @Test
    fun `a repeated end event cannot swallow a reservation`() {
        coordinator.playNow(video(1))
        coordinator.reserve(video(2))

        coordinator.onItemEnded()
        coordinator.onItemEnded()
        coordinator.onItemEnded()

        assertEquals(listOf(1L, 2L), sink.played.map { it.id })
        assertEquals(1, sink.stopCount)
        assertNull(queue.nowPlaying)
    }

    @Test
    fun `a video that fails to play skips to the next reservation`() {
        coordinator.playNow(video(1))
        coordinator.reserve(video(2))

        coordinator.onItemFailed()

        assertEquals(listOf(1L, 2L), sink.played.map { it.id })
        assertEquals(2L, queue.nowPlaying?.video?.id)
    }

    @Test
    fun `a failure with an empty queue stops instead of dead-ending`() {
        coordinator.playNow(video(1))

        coordinator.onItemFailed()

        assertEquals(1, sink.stopCount)
        assertNull(queue.nowPlaying)
    }

    @Test
    fun `several unplayable videos in a row are skipped until one works`() {
        coordinator.playNow(video(1))
        coordinator.reserve(video(2))
        coordinator.reserve(video(3))

        coordinator.onItemFailed()
        coordinator.onItemFailed()

        assertEquals(listOf(1L, 2L, 3L), sink.played.map { it.id })
        assertEquals(3L, queue.nowPlaying?.video?.id)
        assertEquals(0, sink.stopCount)
    }

    @Test
    fun `skip moves to the next reservation on demand`() {
        coordinator.playNow(video(1))
        coordinator.reserve(video(2))

        coordinator.skip()

        assertEquals(listOf(1L, 2L), sink.played.map { it.id })
    }

    @Test
    fun `skip with nothing playing does nothing`() {
        coordinator.skip()

        assertTrue(sink.played.isEmpty())
        assertEquals(0, sink.stopCount)
    }

    @Test
    fun `the same video reserved twice plays twice`() {
        val encore = video(7, title = "Bohemian Rhapsody")
        coordinator.playNow(encore)
        coordinator.reserve(encore)

        coordinator.onItemEnded()

        assertEquals(listOf(7L, 7L), sink.played.map { it.id })
    }

    @Test
    fun `every change notifies the UI so the queue can redraw`() {
        coordinator.playNow(video(1))
        coordinator.reserve(video(2))
        coordinator.onItemEnded()

        assertEquals(3, stateChanges)
    }
}
