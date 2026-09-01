package dev.reserve.logic

/**
 * A video reserved for later, with an id unique to this reservation.
 *
 * The id is per-reservation rather than per-video on purpose: a karaoke queue must let the
 * same video be reserved twice in one session, and each entry has to be addressable on its own.
 */
data class Reservation(val id: Long, val video: VideoItem)

/**
 * The karaoke queue — the single source of truth for what plays next.
 *
 * The player is only ever handed one video at a time, so this class owns the ordering and
 * nothing has to stay in sync with a player-side playlist while the queue mutates mid-playback.
 */
class ReserveQueue {

    private val pending = mutableListOf<Reservation>()
    private var nextId = 1L

    /** The reservation currently on screen, or null when nothing is playing. */
    var nowPlaying: Reservation? = null
        private set

    /** Everything waiting behind [nowPlaying], in play order. */
    val reservations: List<Reservation> get() = pending.toList()

    val size: Int get() = pending.size

    fun isEmpty(): Boolean = pending.isEmpty()

    /** Adds [video] to the back of the queue. Never touches what is currently playing. */
    fun reserve(video: VideoItem): Reservation {
        val reservation = Reservation(nextId++, video)
        pending.add(reservation)
        return reservation
    }

    /** Adds [video] at the front, so it plays as soon as the current video ends. */
    fun reserveNext(video: VideoItem): Reservation {
        val reservation = Reservation(nextId++, video)
        pending.add(0, reservation)
        return reservation
    }

    /** Moves an existing reservation to the front of the queue. */
    fun bumpToNext(reservationId: Long): Boolean {
        val index = indexOf(reservationId)
        if (index <= 0) return false
        pending.add(0, pending.removeAt(index))
        return true
    }

    /** Drops a reservation. Returns false when the id is not queued. */
    fun cancel(reservationId: Long): Boolean = pending.removeAll { it.id == reservationId }

    /** Moves a reservation one place earlier. A no-op at the head. */
    fun moveUp(reservationId: Long): Boolean = swap(indexOf(reservationId), -1)

    /** Moves a reservation one place later. A no-op at the tail. */
    fun moveDown(reservationId: Long): Boolean = swap(indexOf(reservationId), 1)

    /**
     * Starts [video] immediately, bypassing the queue.
     *
     * Used when the user picks a video with nothing playing; the pending reservations are
     * deliberately left alone so a running queue survives someone starting a different video.
     */
    fun playNow(video: VideoItem): Reservation {
        val reservation = Reservation(nextId++, video)
        nowPlaying = reservation
        return reservation
    }

    /**
     * Promotes the head of the queue to [nowPlaying] and returns it, or null when the queue
     * is empty — in which case nothing is playing any more.
     */
    fun advance(): Reservation? {
        nowPlaying = if (pending.isEmpty()) null else pending.removeAt(0)
        return nowPlaying
    }

    fun clear() {
        pending.clear()
        nowPlaying = null
    }

    private fun indexOf(reservationId: Long): Int = pending.indexOfFirst { it.id == reservationId }

    private fun swap(index: Int, delta: Int): Boolean {
        if (index < 0) return false
        val target = index + delta
        if (target < 0 || target >= pending.size) return false
        val moved = pending[index]
        pending[index] = pending[target]
        pending[target] = moved
        return true
    }
}
