package dev.reserve

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.reserve.logic.ReserveQueue
import dev.reserve.logic.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds the library and the queue.
 *
 * Rotation is handled by the activity's `configChanges`, so this exists for the harder case:
 * the process being killed in the background mid-session. The pending reservations are kept in
 * saved state as plain video ids and re-resolved once the library reloads.
 */
class LibraryViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    enum class LoadState { IDLE, LOADING, READY, EMPTY }

    val queue = ReserveQueue()

    private val mutableLibrary = MutableLiveData<List<VideoItem>>(emptyList())
    val library: LiveData<List<VideoItem>> = mutableLibrary

    private val mutableState = MutableLiveData(LoadState.IDLE)
    val state: LiveData<LoadState> = mutableState

    fun load() {
        if (mutableState.value == LoadState.LOADING) return
        mutableState.value = LoadState.LOADING
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                MediaStoreVideoSource.query(getApplication())
            }
            mutableLibrary.value = items
            restorePendingReservations(items)
            mutableState.value = if (items.isEmpty()) LoadState.EMPTY else LoadState.READY
        }
    }

    /** Called after every queue change so a background kill cannot lose the party's queue. */
    fun rememberPendingReservations() {
        savedState[KEY_PENDING] = queue.reservations.map { it.video.id }.toLongArray()
    }

    private fun restorePendingReservations(items: List<VideoItem>) {
        val savedIds: LongArray = savedState[KEY_PENDING] ?: return
        // Only restore into a queue nobody has touched yet, so a reload never duplicates entries.
        if (savedIds.isEmpty() || !queue.isEmpty()) return
        val byId = items.associateBy { it.id }
        savedIds.forEach { id -> byId[id]?.let { queue.reserve(it) } }
    }

    private companion object {
        const val KEY_PENDING = "pending_reservation_video_ids"
    }
}
