package dev.reserve.logic

/** Builds a video with sensible defaults so each test only states what it actually cares about. */
fun video(
    id: Long,
    title: String = "Video $id",
    folder: String = "Movies",
    durationMs: Long = 60_000L,
): VideoItem = VideoItem(
    id = id,
    title = title,
    uri = "content://media/external/video/media/$id",
    durationMs = durationMs,
    folder = folder,
)
