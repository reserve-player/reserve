package dev.reserve

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dev.reserve.databinding.ItemReservationBinding
import dev.reserve.logic.DurationFormat
import dev.reserve.logic.Reservation

/** The "coming up" list, with the controls to reorder or drop a reservation. */
class ReservationListAdapter(
    private val onCancel: (Long) -> Unit,
    private val onMoveUp: (Long) -> Unit,
    private val onMoveDown: (Long) -> Unit,
) : RecyclerView.Adapter<ReservationListAdapter.ViewHolder>() {

    private var items: List<Reservation> = emptyList()

    // Reordering shifts positions for every following row, so the whole list rebinds.
    @SuppressLint("NotifyDataSetChanged")
    fun submit(newItems: List<Reservation>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemReservationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position, onCancel, onMoveUp, onMoveDown)
    }

    class ViewHolder(
        private val binding: ItemReservationBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            reservation: Reservation,
            position: Int,
            onCancel: (Long) -> Unit,
            onMoveUp: (Long) -> Unit,
            onMoveDown: (Long) -> Unit,
        ) {
            val context = binding.root.context
            binding.reservationPosition.text =
                context.getString(R.string.queue_position, position + 1)
            binding.reservationTitle.text = reservation.video.title
            binding.reservationDuration.text = DurationFormat.format(reservation.video.durationMs)
            binding.reservationUp.setOnClickListener { onMoveUp(reservation.id) }
            binding.reservationDown.setOnClickListener { onMoveDown(reservation.id) }
            binding.reservationCancel.setOnClickListener { onCancel(reservation.id) }
        }
    }
}
