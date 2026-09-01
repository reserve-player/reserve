package dev.reserve.logic

import java.util.Locale

/** Formats a video length for display. */
object DurationFormat {

    /** Shown when a file reports no usable duration, which happens on damaged media. */
    const val UNKNOWN: String = "--:--"

    fun format(durationMs: Long): String {
        if (durationMs <= 0L) return UNKNOWN
        val totalSeconds = durationMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }
}
