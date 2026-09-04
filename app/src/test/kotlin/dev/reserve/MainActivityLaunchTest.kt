package dev.reserve

import android.content.Context
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ApplicationProvider
import dev.reserve.logic.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowDialog
import org.robolectric.annotation.Config

/**
 * Stands the real activity up.
 *
 * Without this a green build only proves the code compiles; a broken manifest, a missing
 * theme attribute or a layout that fails to inflate would all still ship.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainActivityLaunchTest {

    // ---- playback across backgrounding ---------------------------------------------------
    //
    // The worst bug found on real hardware: leave the app mid-video, come back, and it is
    // stuck paused for good - recoverable only by force-quitting, which took the whole
    // reserved queue with it. Driven through the player the PlayerView exposes, so no
    // test-only hook is added to production code.

    private fun ActivityController<MainActivity>.playWhenReady(): Boolean =
        get().findViewById<PlayerView>(R.id.playerView).player!!.playWhenReady

    private fun ActivityController<MainActivity>.setPlaying(playing: Boolean) {
        get().findViewById<PlayerView>(R.id.playerView).player!!.playWhenReady = playing
    }

    private fun ActivityController<MainActivity>.queue() =
        ViewModelProvider(get())[LibraryViewModel::class.java].queue

    private fun video(id: Long) =
        VideoItem(id, "Video $id", "content://video/$id", 1_000L, "Movies")

    @Test
    fun `a video that was playing resumes after the app is backgrounded and reopened`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller.setPlaying(true)

        controller.pause().stop()
        assertFalse("backgrounding must pause so audio does not run on", controller.playWhenReady())
        controller.start().resume()

        assertTrue("returning must resume — this is the stuck-paused bug", controller.playWhenReady())

        controller.destroy()
    }

    /**
     * The discriminating case: a blanket "resume on return" would pass the test above and still
     * be wrong. A video the user deliberately paused must not spring back to life.
     */
    @Test
    fun `a video the user paused on purpose is still paused after returning`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller.setPlaying(false)

        controller.pause().stop().start().resume()

        assertFalse("a deliberate pause must survive backgrounding", controller.playWhenReady())

        controller.destroy()
    }

    /**
     * A phone has no OK key, and OK/tap now summons the controls rather than the browser — so
     * without these buttons a touch user would have no reliable way into either panel, which is
     * exactly the gap the previous round had to fix. They live in the controls on purpose, so
     * touch never depends on an edge-swipe gesture landing.
     */
    @Test
    fun `the controls carry buttons into both panels, so touch never needs a gesture`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        activity.findViewById<View>(R.id.controlReserve).performClick()
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.browserPanel).visibility)

        activity.findViewById<View>(R.id.controlQueue).performClick()
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.queuePanel).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.browserPanel).visibility)

        controller.destroy()
    }

    @Test
    fun `the skip button advances the queue rather than the player's own playlist`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val queue = ViewModelProvider(controller.get())[LibraryViewModel::class.java].queue
        queue.reserve(video(1L))
        queue.advance()
        queue.reserve(video(2L))

        controller.get().findViewById<View>(R.id.controlSkip).performClick()

        assertEquals("skip must promote the next reservation", 2L, queue.nowPlaying?.video?.id)

        controller.destroy()
    }

    // ---- the persistent HUD OP asked for -------------------------------------------------

    @Test
    fun `the badge counts the queue and hides when there is nothing reserved`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val badge = activity.findViewById<TextView>(R.id.resBadge)
        assertEquals("an empty queue should not clutter the picture", View.GONE, badge.visibility)

        val queue = ViewModelProvider(activity)[LibraryViewModel::class.java].queue
        queue.reserve(video(1L))
        queue.reserve(video(2L))
        activity.findViewById<View>(R.id.controlQueue).performClick()

        assertEquals(View.VISIBLE, badge.visibility)
        assertEquals("Res. 2", badge.text.toString())

        controller.destroy()
    }

    @Test
    fun `the UI button hides the badge and the up-next line together`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        ViewModelProvider(activity)[LibraryViewModel::class.java].queue.reserve(video(1L))
        activity.findViewById<View>(R.id.controlQueue).performClick()
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.resBadge).visibility)

        activity.findViewById<View>(R.id.controlToggleHud).performClick()

        assertEquals(View.GONE, activity.findViewById<View>(R.id.resBadge).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.upNext).visibility)

        controller.destroy()
    }

    /**
     * Clearing a party's queue cannot be undone, so the button must do nothing on its own.
     */
    @Test
    fun `the clear button does not empty the queue until it is confirmed`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val queue = ViewModelProvider(activity)[LibraryViewModel::class.java].queue
        queue.reserve(video(1L))
        queue.reserve(video(2L))

        activity.findViewById<View>(R.id.controlClear).performClick()

        assertEquals("the queue must survive until the user says yes", 2, queue.reservations.size)

        controller.destroy()
    }

    @Test
    fun `the transport controls are enabled so touch can pause and resume`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()

        val player = controller.get().findViewById<PlayerView>(R.id.playerView)

        assertTrue("without the controller a phone user cannot pause", player.useController)

        controller.destroy()
    }

    /**
     * Back asks first, and asking must not itself end the session — the dialog is the whole
     * point, so a Back that backgrounds immediately would defeat it.
     */
    @Test
    fun `back asks before backgrounding a live session`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        ViewModelProvider(activity)[LibraryViewModel::class.java].queue.reserve(video(1L))

        activity.onBackPressedDispatcher.onBackPressed()

        // ShadowDialog, not ShadowAlertDialog: the app uses AppCompat's AlertDialog, which is a
        // different class from the framework one and is invisible to getLatestAlertDialog().
        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull("Back must put the question up rather than acting on it", dialog)
        assertTrue("and the question must actually be on screen", dialog.isShowing)
        assertFalse("and it must not finish the activity", activity.isFinishing)

        controller.destroy()
    }

    @Test
    fun `back keeps a live session alive instead of destroying the queue`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller.queue().reserve(video(1L))

        controller.get().onBackPressedDispatcher.onBackPressed()

        assertFalse(
            "back must background a live session, not finish it — finishing releases the " +
                "player and clears the reserved queue",
            controller.get().isFinishing,
        )

        controller.destroy()
    }

    @Test
    fun `back still leaves the app when nothing is playing and nothing is queued`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()

        controller.get().onBackPressedDispatcher.onBackPressed()

        assertTrue("with no session, back behaves normally", controller.get().isFinishing)

        controller.destroy()
    }

    @Test
    fun `the app starts without crashing`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()

        assertNotNull(controller.get())

        controller.destroy()
    }

    @Test
    fun `with no permission granted it opens on the access prompt, not a blank player`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.statusPanel).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.browserPanel).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.queuePanel).visibility)
        assertEquals(
            activity.getString(R.string.permission_needed),
            activity.findViewById<android.widget.TextView>(R.id.statusMessage).text.toString(),
        )

        controller.destroy()
    }

    /**
     * Robolectric denies the permission and returns false from
     * `shouldShowRequestPermissionRationale` by default, so recording that the dialog has already
     * been shown is exactly the permanently-denied state a real device reaches after two refusals.
     */
    private fun markAlreadyAsked() {
        MediaStoreVideoSource.markRequested(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    fun `a permanently denied permission says so and offers settings instead of a dead button`() {
        markAlreadyAsked()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        assertEquals(
            activity.getString(R.string.permission_denied),
            activity.findViewById<TextView>(R.id.statusMessage).text.toString(),
        )
        assertEquals(
            activity.getString(R.string.action_settings),
            activity.findViewById<TextView>(R.id.statusAction).text.toString(),
        )

        controller.destroy()
    }

    @Test
    fun `the settings button opens this app's own settings page`() {
        markAlreadyAsked()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        activity.findViewById<View>(R.id.statusAction).performClick()

        val started = shadowOf(activity).nextStartedActivity
        assertNotNull(started)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.action)
        assertEquals(activity.packageName, started.data?.schemeSpecificPart)

        controller.destroy()
    }

    /**
     * The discriminating case: if the permanently-denied check were inverted or dropped the
     * rationale flag, a first-time user would be sent to settings instead of being asked.
     */
    @Test
    fun `a first run asks for permission rather than sending the user to settings`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        assertEquals(
            activity.getString(R.string.action_grant),
            activity.findViewById<TextView>(R.id.statusAction).text.toString(),
        )
        activity.findViewById<View>(R.id.statusAction).performClick()

        // Asserted against the settings action rather than "nothing started", so the test holds
        // however the permission launcher chooses to dispatch its own request.
        assertNotEquals(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            shadowOf(activity).nextStartedActivity?.action,
        )

        controller.destroy()
    }

    @Test
    fun `the player surface is present and wired to a player`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val playerView = activity.findViewById<androidx.media3.ui.PlayerView>(R.id.playerView)
        assertNotNull(playerView)
        assertNotNull(playerView.player)

        controller.destroy()
    }
}
