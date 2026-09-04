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
     * The player must stay OUT of the focus order so keys reach the activity at all.
     *
     * Its controls are focusable CHILDREN, which the parent's flag never blocked - the earlier
     * belief that focusability had to be toggled to let a remote drive them was simply wrong,
     * and this test pins the real arrangement.
     */
    @Test
    fun `the player never takes D-pad focus, so keys still reach the activity`() {
        val controller = launch()
        val player = controller.get().findViewById<View>(R.id.playerView)

        assertFalse("a focusable player would eat DPAD_CENTER", player.isFocusable)
        assertTrue(
            "OK must reach the activity so it can summon the controls",
            controller.press(KeyEvent.KEYCODE_DPAD_CENTER),
        )

        controller.destroy()
    }

    /**
     * OP's actual complaint: on a TV the transport controls could not be summoned AT ALL. The
     * app never called showController(), so the only way in was minimising and reopening, which
     * re-rendered the view. OK is now the way in, matching a screen tap on mobile.
     */
    @Test
    fun `the OK key summons the transport controls a remote could not otherwise reach`() {
        val controller = launch()

        val handled = controller.press(KeyEvent.KEYCODE_DPAD_CENTER)

        assertTrue("OK must be claimed while the controls are hidden", handled)
        // Asserted as a HANDOVER rather than by reading isControllerFullyVisible: that flag stays
        // false while the show animation runs, which never completes without a real frame, so it
        // answers "is the animation done" rather than "who owns the keys now".
        assertFalse(
            "having shown the controls, the activity must hand OK over to them",
            controller.press(KeyEvent.KEYCODE_DPAD_CENTER),
        )

        controller.destroy()
    }

    /**
     * The rule that stops the activity and the controls fighting, which has caused a shipped bug
     * in each of the last two rounds: while the controls are showing they own the keys, so the
     * activity must NOT claim OK, LEFT or RIGHT and steal them from a focused button.
     */
    @Test
    fun `while the controls are showing the activity claims no D-pad keys`() {
        val controller = launch()
        controller.press(KeyEvent.KEYCODE_DPAD_CENTER)

        listOf(
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
        ).forEach { keyCode ->
            assertFalse(
                "Claiming ${KeyEvent.keyCodeToString(keyCode)} here makes the controls undriveable",
                controller.press(keyCode),
            )
        }
        assertEquals(
            "no panel should have opened behind the controls",
            View.GONE,
            controller.visibilityOf(R.id.queuePanel),
        )

        controller.destroy()
    }

    /**
     * OP asked for the same key to open AND close each panel. The second press is the part that
     * broke first time round: gating LEFT/RIGHT on "no panel is open" let the panel open and then
     * left no way to close it with the key that opened it.
     */
    @Test
    fun `left opens the queue and left again closes it`() {
        val controller = launch()

        assertTrue(controller.press(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(View.VISIBLE, controller.visibilityOf(R.id.queuePanel))

        assertTrue("the key that opened it must also close it", controller.press(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(View.GONE, controller.visibilityOf(R.id.queuePanel))

        controller.destroy()
    }

    @Test
    fun `right opens the reserve panel and right again closes it`() {
        val controller = launch()

        assertTrue(controller.press(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(View.VISIBLE, controller.visibilityOf(R.id.browserPanel))

        assertTrue("the key that opened it must also close it", controller.press(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(View.GONE, controller.visibilityOf(R.id.browserPanel))

        controller.destroy()
    }

    /**
     * The opposite key must NOT be claimed while a panel is open — inside a list, left/right
     * belong to the list, and swallowing them would strand a remote user in the panel.
     */
    @Test
    fun `the opposite key is left alone while a panel is open`() {
        val controller = launch()
        controller.press(KeyEvent.KEYCODE_DPAD_LEFT)

        assertFalse(controller.press(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(
            "the queue must still be open — RIGHT was not ours to act on",
            View.VISIBLE,
            controller.visibilityOf(R.id.queuePanel),
        )

        controller.destroy()
    }

    @Test
    fun `with a panel open the activity stops claiming OK, so rows stay selectable`() {
        val controller = launch()
        controller.press(KeyEvent.KEYCODE_DPAD_RIGHT)

        assertFalse(
            "Swallowing OK here would make every row in the library unselectable by remote",
            controller.press(KeyEvent.KEYCODE_DPAD_CENTER),
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
        controller.press(KeyEvent.KEYCODE_DPAD_RIGHT)

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
