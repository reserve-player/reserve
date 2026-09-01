package dev.reserve

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dev.reserve.logic.VideoItem
import dev.reserve.logic.VideoSink

/**
 * The ExoPlayer half of [VideoSink].
 *
 * It plays exactly one video at a time and reports back when that video ends or fails —
 * the queue itself lives in the pure-Kotlin layer, so there is only ever one source of truth
 * about what plays next.
 */
@UnstableApi
class ExoVideoSink(
    context: Context,
    private val onEnded: () -> Unit,
    private val onFailed: () -> Unit,
) : VideoSink {

    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    init {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            // Duck and pause for calls and other apps rather than talking over them.
            /* handleAudioFocus = */ true,
        )
        // Unplugging headphones in a room full of people should not blast the speakers.
        player.setHandleAudioBecomingNoisy(true)
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onEnded()
            }

            override fun onPlayerError(error: PlaybackException) {
                onFailed()
            }
        })
    }

    override fun play(item: VideoItem) {
        player.setMediaItem(MediaItem.fromUri(Uri.parse(item.uri)))
        player.prepare()
        player.playWhenReady = true
    }

    override fun stop() {
        player.stop()
        player.clearMediaItems()
    }

    fun togglePlayPause() {
        player.playWhenReady = !player.playWhenReady
    }

    fun pause() {
        player.playWhenReady = false
    }

    fun release() {
        player.release()
    }
}
