package dev.reserve.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReserveQueueTest {

    @Test
    fun `a new queue is empty and nothing is playing`() {
        val queue = ReserveQueue()

        assertTrue(queue.isEmpty())
        assertNull(queue.nowPlaying)
        assertEquals(emptyList<Reservation>(), queue.reservations)
    }

    @Test
    fun `reserve appends in the order the videos were reserved`() {
        val queue = ReserveQueue()

        queue.reserve(video(1))
        queue.reserve(video(2))
        queue.reserve(video(3))

        assertEquals(listOf(1L, 2L, 3L), queue.reservations.map { it.video.id })
    }

    @Test
    fun `reserving does not disturb what is currently playing`() {
        val queue = ReserveQueue()
        queue.reserve(video(1))
        queue.advance()

        queue.reserve(video(2))

        assertEquals(1L, queue.nowPlaying?.video?.id)
        assertEquals(listOf(2L), queue.reservations.map { it.video.id })
    }

    @Test
    fun `the same video can be reserved twice and each entry is addressable`() {
        val queue = ReserveQueue()
        val song = video(7, title = "Bohemian Rhapsody")

        val first = queue.reserve(song)
        val second = queue.reserve(song)

        assertNotEquals(first.id, second.id)
        assertEquals(2, queue.reservations.size)
        assertEquals(listOf(7L, 7L), queue.reservations.map { it.video.id })
    }

    /**
     * The running order is fixed once it is taken, which is how a karaoke machine behaves and
     * what OP asked for: reorder and per-row remove are gone, so a queue only ever changes by
     * reserving at the back, advancing off the front, or being cleared outright.
     */
    @Test
    fun `reserving never disturbs the order already taken`() {
        val queue = ReserveQueue()
        queue.reserve(video(1))
        queue.reserve(video(2))

        queue.reserve(video(3))

        assertEquals(listOf(1L, 2L, 3L), queue.reservations.map { it.video.id })
    }

    @Test
    fun `advance promotes the head of the queue to now playing`() {
        val queue = ReserveQueue()
        queue.reserve(video(1))
        queue.reserve(video(2))

        val started = queue.advance()

        assertEquals(1L, started?.video?.id)
        assertEquals(1L, queue.nowPlaying?.video?.id)
        assertEquals(listOf(2L), queue.reservations.map { it.video.id })
    }

    @Test
    fun `advance through the whole queue ends with nothing playing`() {
        val queue = ReserveQueue()
        queue.reserve(video(1))
        queue.reserve(video(2))

        queue.advance()
        queue.advance()
        val afterLast = queue.advance()

        assertNull(afterLast)
        assertNull(queue.nowPlaying)
        assertTrue(queue.isEmpty())
    }

    @Test
    fun `clear empties the queue but leaves what is playing alone`() {
        val queue = ReserveQueue()
        queue.reserve(video(1))
        queue.advance()
        queue.reserve(video(2))
        queue.reserve(video(3))

        queue.clear()

        assertTrue(queue.isEmpty())
        // Clearing the queue must not stop the song someone is mid-way through.
        assertEquals(1L, queue.nowPlaying?.video?.id)
    }

    @Test
    fun `reservedCounts reports how many times each video is queued, for the reserved dots`() {
        val queue = ReserveQueue()
        val encore = video(7)
        queue.reserve(encore)
        queue.reserve(video(8))
        queue.reserve(encore)
        queue.reserve(encore)

        val counts = queue.reservedCounts()

        assertEquals(3, counts[7L])
        assertEquals(1, counts[8L])
        assertNull(counts[999L], "a video nobody reserved must not appear at all")
    }

    @Test
    fun `reservedCounts ignores what is playing — the dots mean still to come`() {
        val queue = ReserveQueue()
        queue.reserve(video(5))
        queue.advance()

        assertTrue(queue.reservedCounts().isEmpty())
    }

    @Test
    fun `reservations is a snapshot that later mutation does not alter`() {
        val queue = ReserveQueue()
        queue.reserve(video(1))

        val snapshot = queue.reservations
        queue.reserve(video(2))

        assertEquals(1, snapshot.size)
        assertEquals(2, queue.reservations.size)
    }
}
