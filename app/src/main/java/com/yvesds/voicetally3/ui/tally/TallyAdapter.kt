package com.yvesds.voicetally3.ui.tally

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.voicetally3.R
import java.util.Map

/**
 * Tellingen per soort.
 * - ListAdapter met DiffUtil op Map.Entry<String, Int>
 * - Payload update: bij count-verandering alleen de teller updaten
 * - Stabiele IDs op soortnaam
 */
class TallyAdapter(
    private val onIncrement: (String) -> Unit,
    private val onDecrement: (String) -> Unit,
    private val onReset: (String) -> Unit
) : ListAdapter<Map.Entry<String, Int>, TallyAdapter.TallyViewHolder>(Diff) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).key.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TallyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tally, parent, false)
        return TallyViewHolder(view)
    }

    override fun onBindViewHolder(holder: TallyViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item.key, item.value, onIncrement, onDecrement, onReset)
    }

    override fun onBindViewHolder(
        holder: TallyViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_COUNT_ONLY)) {
            holder.updateCountOnly(getItem(position).value)
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    class TallyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textSpeciesName: TextView = itemView.findViewById(R.id.textSpeciesName)
        private val textCount: TextView = itemView.findViewById(R.id.textCount)
        private val buttonIncrement: Button = itemView.findViewById(R.id.buttonIncrement)
        private val buttonDecrement: Button = itemView.findViewById(R.id.buttonDecrement)
        private val buttonReset: Button = itemView.findViewById(R.id.buttonReset)

        private lateinit var species: String

        fun bind(
            species: String,
            count: Int,
            onIncrement: (String) -> Unit,
            onDecrement: (String) -> Unit,
            onReset: (String) -> Unit
        ) {
            this.species = species
            textSpeciesName.text = species.replaceFirstChar { it.uppercase() }
            textCount.text = count.toString()

            buttonIncrement.setOnClickListener { onIncrement(species) }
            buttonDecrement.setOnClickListener { onDecrement(species) }
            buttonReset.setOnClickListener { onReset(species) }
        }

        fun updateCountOnly(newCount: Int) {
            textCount.text = newCount.toString()
        }
    }

    private object Diff : DiffUtil.ItemCallback<Map.Entry<String, Int>>() {
        override fun areItemsTheSame(
            oldItem: Map.Entry<String, Int>,
            newItem: Map.Entry<String, Int>
        ) = oldItem.key == newItem.key

        override fun areContentsTheSame(
            oldItem: Map.Entry<String, Int>,
            newItem: Map.Entry<String, Int>
        ) = oldItem.value == newItem.value

        override fun getChangePayload(
            oldItem: Map.Entry<String, Int>,
            newItem: Map.Entry<String, Int>
        ): Any? {
            return if (oldItem.value != newItem.value) PAYLOAD_COUNT_ONLY else null
        }
    }

    private companion object {
        const val PAYLOAD_COUNT_ONLY = "payload_count"
    }
}
