package dev.reserve.logic

/**
 * One playable video already on the device.
 *
 * [uri] is kept as a string so this module stays free of Android types and can be
 * unit-tested on a plain JVM.
 */
data class VideoItem(
    val id: Long,
    val title: String,
    val uri: String,
    val durationMs: Long,
    val folder: String,
)
