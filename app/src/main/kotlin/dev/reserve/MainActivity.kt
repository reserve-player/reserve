package dev.reserve

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.widget.doOnTextChanged
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import dev.reserve.databinding.ActivityMainBinding
import dev.reserve.logic.PlaybackCoordinator
import dev.reserve.logic.VideoItem
import dev.reserve.logic.VideoSearch

/**
 * The whole app: a full-screen player with two panels drawn over it.
 *
 * The panels are overlays rather than separate screens precisely because the point of the app
 * is that browsing and reserving never interrupt whatever is on screen.
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

    private val hideBanner = Runnable { binding.upNextBanner.visibility = View.GONE }

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

        onBackPressedDispatcher.addCallback(this) {
            if (panel == Panel.NONE) {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            } else {
                closePanel()
            }
        }

        viewModel.library.observe(this) { applyFilter() }
        viewModel.state.observe(this) { render() }

        if (MediaStoreVideoSource.canReadVideos(this)) {
            viewModel.load()
        }
        render()
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
        showUpNext()
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

    private fun showUpNext() {
        val next = viewModel.queue.reservations.firstOrNull()
        if (next == null || viewModel.queue.nowPlaying == null) {
            binding.upNextBanner.visibility = View.GONE
            return
        }
        binding.upNextBanner.text = getString(R.string.up_next_banner, next.video.title)
        binding.upNextBanner.visibility = View.VISIBLE
        binding.upNextBanner.removeCallbacks(hideBanner)
        binding.upNextBanner.postDelayed(hideBanner, UP_NEXT_MS)
    }

    // ---- panels ---------------------------------------------------------------------------

    private fun openBrowser() {
        panel = Panel.BROWSER
        render()
        // Focus the list, not the search box: on a TV, focusing the field pops the keyboard
        // over the video before the user has asked to type anything.
        binding.libraryList.requestFocus()
    }

    private fun toggleQueuePanel() {
        panel = if (panel == Panel.QUEUE) Panel.NONE else Panel.QUEUE
        render()
        if (panel == Panel.QUEUE) binding.queueList.requestFocus()
    }

    private fun closePanel() {
        panel = Panel.NONE
        render()
    }

    private fun onStatusAction() {
        when {
            MediaStoreVideoSource.canReadVideos(this) -> openBrowser()
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
        libraryAdapter.submit(results)
        binding.browserEmpty.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Single place that decides what is on screen, so no two paths can disagree. */
    private fun render() {
        binding.browserPanel.visibility = visibleIf(panel == Panel.BROWSER)
        binding.queuePanel.visibility = visibleIf(panel == Panel.QUEUE)
        binding.queueEmpty.visibility = visibleIf(viewModel.queue.isEmpty())

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

    private fun visibleIf(condition: Boolean): Int = if (condition) View.VISIBLE else View.GONE

    // ---- remote control -------------------------------------------------------------------

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
                toggleQueuePanel()
                return true
            }

            // Only claim OK when no panel is open, or it would steal presses from the lists.
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            -> if (panel == Panel.NONE) {
                openBrowser()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ---- lifecycle ------------------------------------------------------------------------

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
        sink.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.upNextBanner.removeCallbacks(hideBanner)
        binding.playerView.player = null
        sink.release()
    }

    private companion object {
        const val UP_NEXT_MS = 4_000L
    }
}
