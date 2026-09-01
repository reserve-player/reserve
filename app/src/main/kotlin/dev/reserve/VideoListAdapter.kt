package dev.reserve

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dev.reserve.databinding.ItemVideoBinding
import dev.reserve.logic.DurationFormat
import dev.reserve.logic.VideoItem

/** The library list inside the reserve browser. Pressing a row reserves that video. */
class VideoListAdapter(
    private val onReserve: (VideoItem) -> Unit,
) : RecyclerView.Adapter<VideoListAdapter.ViewHolder>() {

    private var items: List<VideoItem> = emptyList()

    // Every search keystroke replaces the whole result set, so a full rebind is the honest call.
    @SuppressLint("NotifyDataSetChanged")
    fun submit(newItems: List<VideoItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onReserve)
    }

    class ViewHolder(
        private val binding: ItemVideoBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: VideoItem, onReserve: (VideoItem) -> Unit) {
            binding.videoTitle.text = item.title
            binding.videoFolder.text = item.folder
            binding.videoFolder.visibility = if (item.folder.isBlank()) View.GONE else View.VISIBLE
            binding.videoDuration.text = DurationFormat.format(item.durationMs)
            binding.root.setOnClickListener { onReserve(item) }
        }
    }
}
