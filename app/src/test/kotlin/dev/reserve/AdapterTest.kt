package dev.reserve

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dev.reserve.logic.Reservation
import dev.reserve.logic.ReserveQueue
import dev.reserve.logic.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AdapterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val parent = FrameLayout(context)

    private fun video(
        id: Long = 1L,
        title: String = "Bohemian Rhapsody",
        folder: String = "Karaoke",
        durationMs: Long = 355_000L,
    ) = VideoItem(id, title, "content://video/$id", durationMs, folder)

    @Test
    fun `a library row shows title, folder and duration`() {
        val adapter = VideoListAdapter { }
        adapter.submit(listOf(video()))

        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        assertEquals("Bohemian Rhapsody", holder.itemView.text(R.id.videoTitle))
        assertEquals("Karaoke", holder.itemView.text(R.id.videoFolder))
        assertEquals("5:55", holder.itemView.text(R.id.videoDuration))
    }

    @Test
    fun `a library row with no folder hides the folder line`() {
        val adapter = VideoListAdapter { }
        adapter.submit(listOf(video(folder = "")))

        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        assertEquals(View.GONE, holder.itemView.findViewById<TextView>(R.id.videoFolder).visibility)
    }

    @Test
    fun `a damaged file shows the unknown duration marker`() {
        val adapter = VideoListAdapter { }
        adapter.submit(listOf(video(durationMs = 0L)))

        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        assertEquals("--:--", holder.itemView.text(R.id.videoDuration))
    }

    @Test
    fun `pressing a library row reserves that video`() {
        var reserved: VideoItem? = null
        val adapter = VideoListAdapter { reserved = it }
        adapter.submit(listOf(video(id = 42L)))

        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)
        assertNull(reserved)
        holder.itemView.performClick()

        assertEquals(42L, reserved?.id)
    }

    @Test
    fun `submitting a new result set replaces the old one`() {
        val adapter = VideoListAdapter { }
        adapter.submit(listOf(video(id = 1L), video(id = 2L)))
        assertEquals(2, adapter.itemCount)

        adapter.submit(listOf(video(id = 3L)))

        assertEquals(1, adapter.itemCount)
    }

    @Test
    fun `a queue row is numbered from one and shows the title`() {
        val adapter = ReservationListAdapter({ }, { }, { }, { })
        adapter.submit(
            listOf(
                Reservation(100L, video(id = 1L, title = "First")),
                Reservation(101L, video(id = 2L, title = "Second")),
            ),
        )

        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 1)

        assertEquals("2.", holder.itemView.text(R.id.reservationPosition))
        assertEquals("Second", holder.itemView.text(R.id.reservationTitle))
    }

    @Test
    fun `the queue row buttons report the reservation they belong to`() {
        var cancelled: Long? = null
        var movedUp: Long? = null
        var movedDown: Long? = null
        var playNext: Long? = null
        val adapter = ReservationListAdapter(
            onCancel = { cancelled = it },
            onMoveUp = { movedUp = it },
            onMoveDown = { movedDown = it },
            onPlayNext = { playNext = it },
        )
        adapter.submit(listOf(Reservation(77L, video())))

        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)
        holder.itemView.findViewById<View>(R.id.reservationUp).performClick()
        holder.itemView.findViewById<View>(R.id.reservationDown).performClick()
        holder.itemView.findViewById<View>(R.id.reservationCancel).performClick()
        holder.itemView.findViewById<View>(R.id.reservationPlayNext).performClick()

        assertEquals(77L, movedUp)
        assertEquals(77L, movedDown)
        assertEquals(77L, cancelled)
        assertEquals(77L, playNext)
    }

    /**
     * The queue owns the ordering, so this is the seam where a wrong wiring would hide: the
     * button could fire and still call the wrong queue method. Pressing it on the third row must
     * put exactly that reservation at the front.
     */
    @Test
    fun `play next on a queued row moves that video to the front`() {
        val queue = ReserveQueue()
        queue.playNow(video(id = 1L, title = "Playing"))
        queue.reserve(video(id = 2L, title = "First"))
        queue.reserve(video(id = 3L, title = "Second"))
        val third = queue.reserve(video(id = 4L, title = "Third"))

        val adapter = ReservationListAdapter({ }, { }, { }, onPlayNext = { queue.bumpToNext(it) })
        adapter.submit(queue.reservations)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 2)
        holder.itemView.findViewById<View>(R.id.reservationPlayNext).performClick()

        assertEquals(third.id, queue.reservations.first().id)
        assertEquals(listOf("Third", "First", "Second"), queue.reservations.map { it.video.title })
        assertEquals("Playing", queue.nowPlaying?.video?.title)
    }

    private fun View.text(id: Int): String = findViewById<TextView>(id).text.toString()
}
