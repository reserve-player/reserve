package dev.reserve

import android.content.Context
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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
