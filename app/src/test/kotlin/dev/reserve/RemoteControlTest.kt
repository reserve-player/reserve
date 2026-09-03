package dev.reserve

import android.view.KeyEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Proves the app is driveable from a remote, not only by touch.
 *
 * A TV user has four buttons that matter - OK, Back, Menu and the transport keys - so each one
 * is pressed here for real and the resulting screen state is checked. Where a press delegates to
 * logic that is already covered in `logic/` (skip, play/pause), the assertion is that the app
 * CLAIMS the key: an unclaimed media key silently goes to whatever app the system picks instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RemoteControlTest {

    private fun ActivityController<MainActivity>.press(keyCode: Int): Boolean =
        get().onKeyDown(keyCode, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))

    private fun ActivityController<MainActivity>.visibilityOf(id: Int): Int =
        get().findViewById<View>(id).visibility

    private fun launch(): ActivityController<MainActivity> =
        Robolectric.buildActivity(MainActivity::class.java).setup()

    /**
     * The regression guard for the whole touch fix.
     *
     * Enabling the transport controls is what makes the app usable on a phone, but a FOCUSABLE
     * PlayerView takes the D-pad and its controller swallows DPAD_CENTER - which would silently
     * kill the only way a remote opens the browser. The controller is a touch affordance; the
     * player must stay out of the focus order.
     */
    @Test
    fun `the player never takes D-pad focus, so the controls cannot swallow OK`() {
        val controller = launch()
        val player = controller.get().findViewById<View>(R.id.playerView)

        assertFalse("a focusable player would eat DPAD_CENTER and break OK", player.isFocusable)
        assertTrue(
            "OK must still reach the activity with the controls enabled",
            controller.press(KeyEvent.KEYCODE_DPAD_CENTER),
        )
        assertEquals(View.VISIBLE, controller.visibilityOf(R.id.browserPanel))

        controller.destroy()
    }

    @Test
    fun `the OK key opens the browser so videos can be reserved without a touchscreen`() {
        val controller = launch()

        val handled = controller.press(KeyEvent.KEYCODE_DPAD_CENTER)

        assertTrue("OK must open the browser on a device with no touchscreen", handled)
        assertEquals(View.VISIBLE, controller.visibilityOf(R.id.browserPanel))

        controller.destroy()
    }

    @Test
    fun `once the browser is open OK belongs to the focused row, not the activity`() {
        val controller = launch()
        controller.press(KeyEvent.KEYCODE_DPAD_CENTER)

        val handledAgain = controller.press(KeyEvent.KEYCODE_DPAD_CENTER)

        assertFalse(
            "Swallowing OK here would make every row in the library unselectable by remote",
            handledAgain,
        )

        controller.destroy()
    }

    @Test
    fun `the menu key opens the queue and pressing it again closes it`() {
        val controller = launch()

        assertTrue(controller.press(KeyEvent.KEYCODE_MENU))
        assertEquals(View.VISIBLE, controller.visibilityOf(R.id.queuePanel))

        assertTrue(controller.press(KeyEvent.KEYCODE_MENU))
        assertEquals(View.GONE, controller.visibilityOf(R.id.queuePanel))

        controller.destroy()
    }

    @Test
    fun `back closes an open panel instead of quitting mid-session`() {
        val controller = launch()
        controller.press(KeyEvent.KEYCODE_DPAD_CENTER)

        controller.get().onBackPressedDispatcher.onBackPressed()

        assertEquals(View.GONE, controller.visibilityOf(R.id.browserPanel))
        assertFalse(
            "Back must dismiss the overlay first - quitting here would kill the party",
            controller.get().isFinishing,
        )

        controller.destroy()
    }

    @Test
    fun `back with nothing open still leaves the app`() {
        val controller = launch()

        controller.get().onBackPressedDispatcher.onBackPressed()

        assertTrue(
            "With no panel open Back must behave normally, or the app traps the user",
            controller.get().isFinishing,
        )
    }

    @Test
    fun `the app claims the transport keys a remote actually sends`() {
        val controller = launch()

        listOf(
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_NEXT,
        ).forEach { keyCode ->
            assertTrue(
                "Key ${KeyEvent.keyCodeToString(keyCode)} went unhandled, so the system would " +
                    "hand it to some other media app",
                controller.press(keyCode),
            )
        }

        controller.destroy()
    }

    @Test
    fun `keys the app has no business with are passed on`() {
        val controller = launch()

        assertFalse(
            "Claiming unrelated keys breaks volume, channel and every other remote button",
            controller.press(KeyEvent.KEYCODE_VOLUME_UP),
        )

        controller.destroy()
    }
}
