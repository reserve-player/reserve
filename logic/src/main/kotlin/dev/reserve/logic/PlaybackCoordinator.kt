package dev.reserve.logic

/**
 * Whatever actually renders video. Implemented on Android by ExoPlayer; implemented in tests
 * by a fake, which is what lets the auto-advance rules be verified without a device.
 */
interface VideoSink {
    fun play(item: VideoItem)
    fun stop()
}

/**
 * Drives the queue into the sink: starts videos, advances when one ends, and skips past one
 * that will not play.
 *
 * @param onStateChanged fired after every change so the UI can redraw the queue.
 */
class PlaybackCoordinator(
    private val queue: ReserveQueue,
    private val sink: VideoSink,
    private val onStateChanged: () -> Unit = {},
) {

    /**
     * Reserves [video] for later. If nothing is playing the queue is idle, so it starts
     * immediately rather than waiting for an end event that will never arrive.
     */
    fun reserve(video: VideoItem): Reservation {
        val reservation = queue.reserve(video)
        if (queue.nowPlaying == null) advance() else onStateChanged()
        return reservation
    }

    /** The current video finished on its own. */
    fun onItemEnded() = advanceIfPlaying()

    /** The current video could not be played; move on rather than dead-ending the session. */
    fun onItemFailed() = advanceIfPlaying()

    /** The user asked for the next video now. */
    fun skip() = advanceIfPlaying()

    /**
     * Guards against advancing twice for one video: a player can report the end of a stream
     * more than once, and without this the queue would silently eat a reservation.
     */
    private fun advanceIfPlaying() {
        if (queue.nowPlaying == null) return
        advance()
    }

    private fun advance() {
        val next = queue.advance()
        if (next == null) sink.stop() else sink.play(next.video)
        onStateChanged()
    }
}
