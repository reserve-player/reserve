package dev.reserve

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.widget.doOnTextChanged
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import dev.reserve.databinding.ActivityMainBinding
import dev.reserve.logic.PlaybackCoordinator
import dev.reserve.logic.VideoItem
import dev.reserve.logic.VideoSearch

/**
 * The whole app: a full-screen player with two side sheets drawn over it.
 *
 * The sheets are overlays rather than separate screens precisely because the point of the app is
 * that browsing and reserving never interrupt whatever is on screen.
 *
 * **The one rule for keys**, which has caused a bug in each of the last two rounds: this activity
 * handles a key ONLY while the transport controls are hidden. `OK` shows the controls; the
 * controls then own the keys until they hide again. `PlayerView` stays non-focusable so keys reach
 * [onKeyDown] at all, and the controls' own buttons are focusable CHILDREN, so a remote can still
 * drive them — the parent's flag never blocked that.
 */
@UnstableApi
class MainActivity : AppCompatActivity() {

    private enum class Panel { NONE, BROWSER, QUEUE }

    private lateinit var binding: ActivityMainBinding
    private lateinit var sink: ExoVideoSink
    private lateinit var coordinator: PlaybackCoordinator
    private lateinit var queueAdapter: ReservationListAdapter

    private val viewModel: LibraryViewModel by viewModels()
    private val libraryAdapter = VideoListAdapter(::reserve)
    private var panel = Panel.NONE

    /** Whether the Res badge and Up Next line are shown — toggled by the UI button. */
    private var hudVisible = true

    /**
     * Whether the transport controls are on screen, tracked from the visibility callback.
     *
     * Deliberately NOT read from `isControllerFullyVisible`: that reports false while the show
     * animation is still running, so it is not a reliable answer to "who owns the keys right
     * now" — the callback is.
     */
    private var controlsShowing = false

    /** Whether playback was actually running when the activity was last stopped. */
    private var wasPlayingWhenStopped = false

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onPermissionResult() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // A karaoke session is long stretches of nobody touching the device.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sink = ExoVideoSink(
            context = this,
            onEnded = { coordinator.onItemEnded() },
            onFailed = ::onPlaybackFailed,
        )
        binding.playerView.player = sink.player
        coordinator = PlaybackCoordinator(viewModel.queue, sink, ::onQueueChanged)

        queueAdapter = ReservationListAdapter(
            onCancel = { changeQueue { viewModel.queue.cancel(it) } },
            onMoveUp = { changeQueue { viewModel.queue.moveUp(it) } },
            onMoveDown = { changeQueue { viewModel.queue.moveDown(it) } },
            onPlayNext = { changeQueue { viewModel.queue.bumpToNext(it) } },
        )

        binding.libraryList.layoutManager = LinearLayoutManager(this)
        binding.libraryList.adapter = libraryAdapter
        binding.queueList.layoutManager = LinearLayoutManager(this)
        binding.queueList.adapter = queueAdapter

        binding.searchInput.doOnTextChanged { _, _, _, _ -> applyFilter() }
        binding.statusAction.setOnClickListener { onStatusAction() }

        wireControls()

        // When the controls hide, focus may still sit on one of their buttons — now invisible.
        // Returning it to the root is what stops OK being stranded and never working again.
        binding.playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                controlsShowing = visibility == View.VISIBLE
                if (!controlsShowing) binding.root.requestFocus()
            },
        )

        onBackPressedDispatcher.addCallback(this) {
            when {
                panel != Panel.NONE -> closePanel()
                // Back must not end a running session: finishing releases the player AND clears
                // the ViewModel, which is how the reserved queue was being lost.
                isSessionLive() -> confirmExit()
                else -> {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }

        viewModel.library.observe(this) { applyFilter() }
        viewModel.state.observe(this) { render() }

        if (MediaStoreVideoSource.canReadVideos(this)) {
            viewModel.load()
        }
        render()
    }

    /** The controls live in `controls.xml`; these are the buttons that act on the QUEUE. */
    private fun wireControls() {
        val controls = binding.playerView
        controls.findViewById<View>(R.id.controlSkip)?.setOnClickListener { coordinator.skip() }
        controls.findViewById<View>(R.id.controlQueue)?.setOnClickListener { showPanel(Panel.QUEUE) }
        controls.findViewById<View>(R.id.controlReserve)
            ?.setOnClickListener { showPanel(Panel.BROWSER) }
        controls.findViewById<View>(R.id.controlToggleHud)?.setOnClickListener {
            hudVisible = !hudVisible
            render()
        }
        controls.findViewById<View>(R.id.controlClear)?.setOnClickListener { confirmClearQueue() }
    }

    // ---- queue ----------------------------------------------------------------------------

    /** Reserving deliberately leaves the browser open so several videos can be queued in a row. */
    private fun reserve(video: VideoItem) {
        coordinator.reserve(video)
        Toast.makeText(this, getString(R.string.reserved_toast, video.title), Toast.LENGTH_SHORT)
            .show()
    }

    private fun changeQueue(mutate: () -> Unit) {
        mutate()
        onQueueChanged()
    }

    private fun onQueueChanged() {
        viewModel.rememberPendingReservations()
        queueAdapter.submit(viewModel.queue.reservations)
        applyFilter()
        render()
    }

    private fun onPlaybackFailed() {
        viewModel.queue.nowPlaying?.let {
            Toast.makeText(
                this,
                getString(R.string.playback_failed, it.video.title),
                Toast.LENGTH_SHORT,
            ).show()
        }
        coordinator.onItemFailed()
    }

    /** Clearing a party's queue is destructive and cannot be undone, so it is confirmed. */
    private fun confirmClearQueue() {
        val count = viewModel.queue.reservations.size
        if (count == 0) return
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_clear_title)
            .setMessage(getString(R.string.confirm_clear_message, count))
            .setNegativeButton(R.string.action_cancel_dialog, null)
            .setPositiveButton(R.string.confirm_clear_yes) { _, _ ->
                changeQueue { viewModel.queue.clear() }
            }
            .show()
    }

    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_exit_title)
            .setMessage(R.string.confirm_exit_message)
            .setNegativeButton(R.string.action_cancel_dialog, null)
            .setPositiveButton(R.string.confirm_exit_yes) { _, _ -> moveTaskToBack(true) }
            .show()
    }

    // ---- panels ---------------------------------------------------------------------------

    private fun showPanel(target: Panel) {
        panel = target
        render()
        // Focus the list, not the search box: on a TV, focusing the field pops the keyboard over
        // the video before the user has asked to type anything.
        if (target == Panel.BROWSER) binding.libraryList.requestFocus()
        if (target == Panel.QUEUE) binding.queueList.requestFocus()
    }

    private fun togglePanel(target: Panel) {
        if (panel == target) closePanel() else showPanel(target)
    }

    private fun closePanel() {
        panel = Panel.NONE
        render()
        binding.root.requestFocus()
    }

    private fun onStatusAction() {
        when {
            MediaStoreVideoSource.canReadVideos(this) -> showPanel(Panel.BROWSER)
            // The dialog is gone for good, so asking again would be a button that does nothing.
            isPermanentlyDenied() ->
                startActivity(MediaStoreVideoSource.appSettingsIntent(packageName))

            else -> {
                MediaStoreVideoSource.markRequested(this)
                requestPermission.launch(MediaStoreVideoSource.requiredPermission())
            }
        }
    }

    private fun isPermanentlyDenied(): Boolean = MediaStoreVideoSource.isPermanentlyDenied(
        this,
        ActivityCompat.shouldShowRequestPermissionRationale(
            this,
            MediaStoreVideoSource.requiredPermission(),
        ),
    )

    private fun onPermissionResult() {
        if (MediaStoreVideoSource.canReadVideos(this)) {
            if (MediaStoreVideoSource.hasPartialAccess(this)) {
                Toast.makeText(this, R.string.partial_access, Toast.LENGTH_LONG).show()
            }
            viewModel.load()
        }
        render()
    }

    private fun applyFilter() {
        val query = binding.searchInput.text?.toString().orEmpty()
        val results = VideoSearch.search(viewModel.library.value.orEmpty(), query)
        // Counted once per submit rather than per row bind, so a long library stays cheap.
        libraryAdapter.submit(results, results.associate { it.id to viewModel.queue.countOf(it.id) })
        binding.browserEmpty.visibility = visibleIf(results.isEmpty())
    }

    /** Single place that decides what is on screen, so no two paths can disagree. */
    private fun render() {
        binding.browserPanel.visibility = visibleIf(panel == Panel.BROWSER)
        binding.queuePanel.visibility = visibleIf(panel == Panel.QUEUE)
        binding.queueEmpty.visibility = visibleIf(viewModel.queue.isEmpty())
        renderHud()

        val idle = viewModel.queue.nowPlaying == null
        val showStatus = idle && panel == Panel.NONE
        binding.statusPanel.visibility = visibleIf(showStatus)
        if (!showStatus) return

        binding.statusAction.visibility = View.VISIBLE
        when {
            !MediaStoreVideoSource.canReadVideos(this) -> if (isPermanentlyDenied()) {
                binding.statusMessage.setText(R.string.permission_denied)
                binding.statusAction.setText(R.string.action_settings)
            } else {
                binding.statusMessage.setText(R.string.permission_needed)
                binding.statusAction.setText(R.string.action_grant)
            }

            viewModel.state.value == LibraryViewModel.LoadState.EMPTY -> {
                binding.statusMessage.setText(R.string.library_empty)
                binding.statusAction.visibility = View.GONE
            }

            else -> {
                binding.statusMessage.setText(R.string.queue_empty)
                binding.statusAction.setText(R.string.action_browse)
            }
        }
    }

    /**
     * The always-on state: how many are queued, and what plays next.
     *
     * Persistent rather than a timed banner — the old one showed the next title for four seconds
     * as a video started, which is exactly when nobody is looking.
     */
    private fun renderHud() {
        val queued = viewModel.queue.reservations
        val next = queued.firstOrNull()
        binding.resBadge.text = getString(R.string.res_badge, queued.size)
        binding.resBadge.visibility = visibleIf(hudVisible && queued.isNotEmpty())

        binding.upNext.text = next?.let { getString(R.string.up_next_short, shorten(it.video.title)) }
        binding.upNext.visibility =
            visibleIf(hudVisible && next != null && viewModel.queue.nowPlaying != null)
    }

    /** Enough of the title to recognise it, not enough to compete with the video. */
    private fun shorten(title: String): String {
        val words = title.split(" ").filter { it.isNotEmpty() }
        if (words.size <= TITLE_WORDS) return title
        return words.take(TITLE_WORDS).joinToString(" ") + "…"
    }

    private fun visibleIf(condition: Boolean): Int = if (condition) View.VISIBLE else View.GONE

    // ---- remote control -------------------------------------------------------------------

    /**
     * The controls own the keys while they are showing; this activity owns them the rest of the
     * time. Without that rule the two fight: either the controls swallow OK and the browser
     * becomes unreachable by remote, or the activity swallows the D-pad and the controls cannot
     * be driven. Both have shipped as bugs.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            -> {
                sink.togglePlayPause()
                return true
            }

            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                coordinator.skip()
                return true
            }

            KeyEvent.KEYCODE_MENU -> {
                togglePanel(Panel.QUEUE)
                return true
            }

            // LEFT toggles the queue, so it must still be claimed WHILE the queue is open —
            // otherwise the panel opens and can never be closed the same way it was opened.
            KeyEvent.KEYCODE_DPAD_LEFT -> if (!controlsShowing && panel != Panel.BROWSER) {
                togglePanel(Panel.QUEUE)
                return true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> if (!controlsShowing && panel != Panel.QUEUE) {
                togglePanel(Panel.BROWSER)
                return true
            }

            // OK summons the controls — the thing a remote previously had no way to do at all.
            // Unlike LEFT/RIGHT it is NOT claimed while a panel is open, or every row in the
            // lists would become unselectable by remote.
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            -> if (!controlsShowing && panel == Panel.NONE) {
                binding.playerView.showController()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ---- lifecycle ------------------------------------------------------------------------

    /** True while there is something playing or something waiting to play. */
    private fun isSessionLive(): Boolean =
        viewModel.queue.nowPlaying != null || !viewModel.queue.isEmpty()

    /**
     * Restores playback that backgrounding interrupted.
     *
     * [onStop] pauses so the audio does not keep running behind another app; without this the
     * pause was one-way and the video could never be restarted — the session was dead until a
     * force-quit, which took the whole reserved queue with it.
     */
    override fun onStart() {
        super.onStart()
        // Only if it was actually running when we left: a video the user deliberately paused must
        // stay paused rather than springing back to life. No queue check is needed —
        // ExoVideoSink.stop() clears the flag, so an exhausted queue can never look "playing".
        if (wasPlayingWhenStopped) sink.resume()
        wasPlayingWhenStopped = false
    }

    /**
     * Granting from the settings screen is the one way access can change with no result callback
     * to catch it, so the state is re-read on the way back in.
     */
    override fun onResume() {
        super.onResume()
        if (MediaStoreVideoSource.canReadVideos(this) &&
            viewModel.state.value == LibraryViewModel.LoadState.IDLE
        ) {
            viewModel.load()
        }
        render()
    }

    override fun onStop() {
        super.onStop()
        wasPlayingWhenStopped = sink.isPlaying
        sink.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.playerView.player = null
        sink.release()
    }

    private companion object {
        const val TITLE_WORDS = 3
    }
}
