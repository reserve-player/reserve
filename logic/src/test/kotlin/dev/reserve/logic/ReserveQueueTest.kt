package dev.reserve.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

        queue.cancel(first.id)

        assertEquals(listOf(second.id), queue.reservations.map { it.id })
        assertEquals(7L, queue.reservations.single().video.id)
    }

    @Test
    fun `cancel removes only the named reservation`() {
        val queue = ReserveQueue()
        queue.reserve(video(1))
        val middle = queue.reserve(video(2))
        queue.reserve(video(3))

        assertTrue(queue.cancel(middle.id))

        assertEquals(listOf(1L, 3L), queue.reservations.map { it.video.id })
    }

    @Test
    fun `cancel reports false for an id that is not queued`() {
        val queue = ReserveQueue()
        queue.reserve(video(1))

        assertFalse(queue.cancel(9999L))
        assertEquals(1, queue.reservations.size)
    }

    @Test
    fun `moveUp swaps a reservation with the one before it`() {
        val queue = ReserveQueue()
        queue.reserve(video(1))
        val second = queue.reserve(video(2))

        assertTrue(queue.moveUp(second.id))

        assertEquals(listOf(2L, 1L), queue.reservations.map { it.video.id })
    }

    @Test
    fun `moveUp at the head is a no-op rather than an error`() {
        val queue = ReserveQueue()
        val first = queue.reserve(video(1))
        queue.reserve(video(2))

        assertFalse(queue.moveUp(first.id))

        assertEquals(listOf(1L, 2L), queue.reservations.map { it.video.id })
    }

    @Test
    fun `moveDown swaps a reservation with the one after it`() {
        val queue = ReserveQueue()
        val first = queue.reserve(video(1))
        queue.reserve(video(2))

        assertTrue(queue.moveDown(first.id))

        assertEquals(listOf(2L, 1L), queue.reservations.map { it.video.id })
    }

    @Test
    fun `moveDown at the tail is a no-op rather than an error`() {
        val queue = ReserveQueue()
        queue.reserve(video(1))
        val last = queue.reserve(video(2))

        assertFalse(queue.moveDown(last.id))

        assertEquals(listOf(1L, 2L), queue.reservations.map { it.video.id })
    }

    @Test
    fun `bumpToNext moves an existing reservation to the front`() {
        val queue = ReserveQueue()
        queue.reserve(video(1))
        queue.reserve(video(2))
        val third = queue.reserve(video(3))

        assertTrue(queue.bumpToNext(third.id))

        assertEquals(listOf(3L, 1L, 2L), queue.reservations.map { it.video.id })
    }

    @Test
    fun `bumpToNext on the head reports false and changes nothing`() {
        val queue = ReserveQueue()
        val first = queue.reserve(video(1))
        queue.reserve(video(2))

        assertFalse(queue.bumpToNext(first.id))

        assertEquals(listOf(1L, 2L), queue.reservations.map { it.video.id })
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
    fun `countOf reports how many times a video is queued, for the reserved dots`() {
        val queue = ReserveQueue()
        val encore = video(7)
        queue.reserve(encore)
        queue.reserve(video(8))
        queue.reserve(encore)
        queue.reserve(encore)

        assertEquals(3, queue.countOf(7L))
        assertEquals(1, queue.countOf(8L))
        assertEquals(0, queue.countOf(999L))
    }

    @Test
    fun `countOf ignores what is playing — the dots mean still to come`() {
        val queue = ReserveQueue()
        queue.reserve(video(5))
        queue.advance()

        assertEquals(0, queue.countOf(5L))
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
