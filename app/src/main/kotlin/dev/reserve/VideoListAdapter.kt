package dev.reserve

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import dev.reserve.databinding.ItemVideoBinding
import dev.reserve.logic.DurationFormat
import dev.reserve.logic.VideoItem

/** The library list inside the reserve browser. Pressing a row reserves that video. */
class VideoListAdapter(
    private val onReserve: (VideoItem) -> Unit,
) : RecyclerView.Adapter<VideoListAdapter.ViewHolder>() {

    private var items: List<VideoItem> = emptyList()

    /** How many times each video is already queued, keyed by video id. */
    private var reservedCounts: Map<Long, Int> = emptyMap()

    // Every search keystroke replaces the whole result set, so a full rebind is the honest call.
    @SuppressLint("NotifyDataSetChanged")
    fun submit(newItems: List<VideoItem>, counts: Map<Long, Int> = emptyMap()) {
        items = newItems
        reservedCounts = counts
        notifyDataSetChanged()
    }

    /**
     * Reserving changes the dots and nothing else, so the rows are updated IN PLACE.
     *
     * Going through submit() here meant a full rebind on every reserve, which threw away the row
     * the remote was standing on — focus then fell out of the list and landed in the search box,
     * so queueing a run of videos meant walking back down the list each time. A payload update
     * reuses the same view, so the focus stays exactly where the user left it.
     */
    fun updateCounts(counts: Map<Long, Int>) {
        reservedCounts = counts
        notifyItemRangeChanged(0, items.size, COUNTS_CHANGED)
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, reservedCounts[item.id] ?: 0, onReserve)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
        if (!payloads.contains(COUNTS_CHANGED)) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }
        val item = items[position]
        holder.rebindDots(reservedCounts[item.id] ?: 0)
    }

    class ViewHolder(
        private val binding: ItemVideoBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: VideoItem, reservedCount: Int, onReserve: (VideoItem) -> Unit) {
            binding.videoTitle.text = item.title
            binding.videoFolder.text = item.folder
            binding.videoFolder.visibility = if (item.folder.isBlank()) View.GONE else View.VISIBLE
            binding.videoDuration.text = DurationFormat.format(item.durationMs)
            bindDots(reservedCount)
            binding.root.setOnClickListener { onReserve(item) }
        }

        /** The dots only, for an in-place update that must not disturb the row's focus. */
        fun rebindDots(reservedCount: Int) = bindDots(reservedCount)

        /**
         * One dot per reservation, rebuilt each bind because a recycled row carries the previous
         * video's dots otherwise — the classic RecyclerView trap.
         */
        private fun bindDots(count: Int) {
            val row = binding.videoDots
            row.removeAllViews()
            row.visibility = if (count == 0) View.GONE else View.VISIBLE
            row.contentDescription =
                row.context.getString(R.string.cd_reserved_count, count)
            repeat(count) { index ->
                val dot = ImageView(row.context)
                dot.setImageResource(R.drawable.reserved_dot)
                val size = row.resources.getDimensionPixelSize(R.dimen.reserved_dot_size)
                val params = LinearLayout.LayoutParams(size, size)
                if (index > 0) params.leftMargin = size / 2
                row.addView(dot, params)
            }
        }
    }

    companion object {
        /** Marks a rebind as "the reserved dots moved, nothing else did". */
        const val COUNTS_CHANGED = "counts"
    }
}
