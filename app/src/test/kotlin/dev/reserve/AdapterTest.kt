package dev.reserve

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import dev.reserve.logic.Reservation
import dev.reserve.logic.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        val adapter = ReservationListAdapter()
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

    /**
     * OP's bug: every row in the coming-up list rendered blank on both his phone and his Mi Box.
     *
     * The row is a horizontal LinearLayout. A LinearLayout measures its `wrap_content` children
     * FIRST and hands the weighted one only what is LEFT OVER — so once the row's buttons
     * outgrew a third-of-a-screen panel, the title column was allotted exactly ZERO pixels.
     * `reservationTitle.text` was correct the entire time, which is precisely why the test above
     * passed while the feature was visibly broken.
     *
     * So this one lays the row out at the width it really gets on a TV and asserts the title is
     * ON SCREEN. Half the row is the bar: a title that cannot show a few words is a blank row.
     */
    @Test
    fun `a queue row's title is laid out on screen, not merely set`() {
        val adapter = ReservationListAdapter()
        adapter.submit(listOf(Reservation(1L, video(title = "Bohemian Rhapsody"))))

        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)
        val row = holder.itemView.layoutAt(tvPanelRowWidthPx())

        val title = row.findViewById<TextView>(R.id.reservationTitle)
        assertTrue(
            "the title was set but laid out ${title.width}px wide inside a ${row.width}px row," +
                " which is the blank coming-up list",
            title.width >= row.width / 2,
        )
    }

    /** The same row on a phone, where the panel is wider but the title still has to fit. */
    @Test
    fun `a queue row keeps its position number and duration beside the title`() {
        val adapter = ReservationListAdapter()
        adapter.submit(listOf(Reservation(1L, video(title = "Bohemian Rhapsody"))))

        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)
        val row = holder.itemView.layoutAt(tvPanelRowWidthPx())

        assertTrue(row.findViewById<TextView>(R.id.reservationPosition).width > 0)
        assertTrue(row.findViewById<TextView>(R.id.reservationDuration).width > 0)
        assertEquals("5:55", row.text(R.id.reservationDuration))
    }

    /**
     * OP asked for the coming-up list to be "literally just a non-interactive list". A row that
     * still answers a click is one stray remote press away from reordering somebody's party.
     */
    @Test
    fun `a queue row does nothing when it is pressed`() {
        val adapter = ReservationListAdapter()
        adapter.submit(listOf(Reservation(1L, video())))

        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        assertFalse("a plain list row must not be clickable", holder.itemView.isClickable)
        assertTrue(
            "but it must still take D-pad focus, or a long queue cannot be scrolled on a TV",
            holder.itemView.isFocusable,
        )
    }

    /**
     * A third of a 960dp-wide TV screen less the panel's own 20dp padding either side — the width
     * a queued row actually gets on OP's Mi Box.
     */
    private fun tvPanelRowWidthPx(): Int {
        val density = context.resources.displayMetrics.density
        return (((960 * 34 / 100) - 40) * density).toInt()
    }

    private fun View.layoutAt(widthPx: Int): View {
        measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        layout(0, 0, measuredWidth, measuredHeight)
        return this
    }

    // ---- the reserved dots ----------------------------------------------------------------
    //
    // OP's idea: one dot per time a video is already queued, so pressing a row is never in
    // doubt. The count is passed in per submit rather than read per bind, so a long library
    // does not pay for it on every scroll.

    private fun dotsIn(holder: RecyclerView.ViewHolder): Int =
        holder.itemView.findViewById<LinearLayout>(R.id.videoDots).childCount

    @Test
    fun `a video that is not reserved shows no dots at all`() {
        val adapter = VideoListAdapter { }
        adapter.submit(listOf(video(id = 1L)), counts = emptyMap())

        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        assertEquals(0, dotsIn(holder))
        assertEquals(View.GONE, holder.itemView.findViewById<View>(R.id.videoDots).visibility)
    }

    @Test
    fun `the dot count matches how many times the video is queued`() {
        val adapter = VideoListAdapter { }
        adapter.submit(listOf(video(id = 1L), video(id = 2L)), counts = mapOf(1L to 1, 2L to 3))

        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)
        assertEquals(1, dotsIn(holder))

        adapter.onBindViewHolder(holder, 1)
        assertEquals(3, dotsIn(holder))
    }

    /**
     * The RecyclerView trap: a recycled row must not keep the previous video's dots. Binding a
     * reserved row and then an unreserved one through the SAME holder is exactly what recycling
     * does, and a naive implementation leaves the old dots behind.
     */
    @Test
    fun `a recycled row does not keep the previous video's dots`() {
        val adapter = VideoListAdapter { }
        adapter.submit(listOf(video(id = 1L), video(id = 2L)), counts = mapOf(1L to 3))

        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)
        assertEquals(3, dotsIn(holder))

        adapter.onBindViewHolder(holder, 1)

        assertEquals("the unreserved video must show a clean row", 0, dotsIn(holder))
    }

    /**
     * Reserving changes nothing but the dot counts, so it must not replace the rows.
     *
     * Rebuilding the list tears down whichever row the remote was sitting on, and focus then
     * falls out of the list entirely — OP hit that as "the focus switches to the text box" after
     * every single reserve. Updating through the payload keeps the same view, and the same focus.
     */
    @Test
    fun `updating the counts redraws the dots on the row that is already there`() {
        val adapter = VideoListAdapter { }
        adapter.submit(listOf(video(id = 1L)), counts = emptyMap())
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)
        assertEquals(0, dotsIn(holder))

        adapter.updateCounts(mapOf(1L to 2))
        adapter.onBindViewHolder(holder, 0, listOf(VideoListAdapter.COUNTS_CHANGED))

        assertEquals("the second reserve must show as a second dot", 2, dotsIn(holder))
    }

    /** A payload rebind touches the dots only — the rest of the row is already correct. */
    @Test
    fun `a payload rebind leaves the title alone`() {
        val adapter = VideoListAdapter { }
        adapter.submit(listOf(video(id = 1L, title = "Bohemian Rhapsody")))
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        adapter.updateCounts(mapOf(1L to 1))
        adapter.onBindViewHolder(holder, 0, listOf(VideoListAdapter.COUNTS_CHANGED))

        assertEquals("Bohemian Rhapsody", holder.itemView.text(R.id.videoTitle))
        assertEquals(1, dotsIn(holder))
    }

    private fun View.text(id: Int): String = findViewById<TextView>(id).text.toString()
}
