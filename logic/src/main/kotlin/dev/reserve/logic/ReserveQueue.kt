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

    fun isEmpty(): Boolean = pending.isEmpty()

    /** Adds [video] to the back of the queue. Never touches what is currently playing. */
    fun reserve(video: VideoItem): Reservation {
        val reservation = Reservation(nextId++, video)
        pending.add(reservation)
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

    /**
     * Empties the queue, leaving whatever is playing alone.
     *
     * Deleted once as uncalled API; back because the clear-queue button is a real caller. Without
     * it there is no way to end a party early short of force-quitting the app.
     */
    fun clear() {
        pending.clear()
    }

    /**
     * How many times each queued video appears, keyed by video id — one dot per reservation in
     * the browser.
     *
     * Counted in one pass over the queue rather than per library row: a browser showing 400
     * videos was previously asking the queue about every one of them on every keystroke.
     */
    fun reservedCounts(): Map<Long, Int> = pending.groupingBy { it.video.id }.eachCount()
}
