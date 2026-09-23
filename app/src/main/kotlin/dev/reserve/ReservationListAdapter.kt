package dev.reserve

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dev.reserve.databinding.ItemReservationBinding
import dev.reserve.logic.DurationFormat
import dev.reserve.logic.Reservation

/**
 * The "coming up" list: a read-only running order, the way a karaoke machine shows one.
 *
 * It had per-row Play next / Up / Down / Remove buttons; OP asked for them gone because reserves
 * are always taken in order, and they were crowding the title off the row entirely. Nothing here
 * mutates the queue any more — skip and clear, both on the transport controls, are the only ways
 * a queue changes.
 */
class ReservationListAdapter : RecyclerView.Adapter<ReservationListAdapter.ViewHolder>() {

    private var items: List<Reservation> = emptyList()

    // Every row carries its position number, so one change renumbers all of them.
    @SuppressLint("NotifyDataSetChanged")
    fun submit(newItems: List<Reservation>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemReservationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    class ViewHolder(
        private val binding: ItemReservationBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(reservation: Reservation, position: Int) {
            val context = binding.root.context
            binding.reservationPosition.text =
                context.getString(R.string.queue_position, position + 1)
            binding.reservationTitle.text = reservation.video.title
            binding.reservationDuration.text = DurationFormat.format(reservation.video.durationMs)
        }
    }
}
