package dev.reserve

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
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
