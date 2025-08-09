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

class TallyAdapter(
    private val onIncrement: (String) -> Unit,
    private val onDecrement: (String) -> Unit,
    private val onReset: (String) -> Unit
) : ListAdapter<Map.Entry<String, Int>, TallyAdapter.TallyViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TallyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tally, parent, false)
        return TallyViewHolder(view)
    }

    override fun onBindViewHolder(holder: TallyViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item.key, item.value, onIncrement, onDecrement, onReset)
    }

    class TallyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textSpeciesName: TextView = itemView.findViewById(R.id.textSpeciesName)
        private val textCount: TextView = itemView.findViewById(R.id.textCount)
        private val buttonIncrement: Button = itemView.findViewById(R.id.buttonIncrement)
        private val buttonDecrement: Button = itemView.findViewById(R.id.buttonDecrement)
        private val buttonReset: Button = itemView.findViewById(R.id.buttonReset)

        fun bind(
            species: String,
            count: Int,
            onIncrement: (String) -> Unit,
            onDecrement: (String) -> Unit,
            onReset: (String) -> Unit
        ) {
            textSpeciesName.text = species.replaceFirstChar { it.uppercase() }
            textCount.text = count.toString()

            buttonIncrement.setOnClickListener {
                onIncrement(species)
            }

            buttonDecrement.setOnClickListener {
                onDecrement(species)
            }

            buttonReset.setOnClickListener {
                onReset(species)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Map.Entry<String, Int>>() {
        override fun areItemsTheSame(
            oldItem: Map.Entry<String, Int>,
            newItem: Map.Entry<String, Int>
        ) = oldItem.key == newItem.key

        override fun areContentsTheSame(
            oldItem: Map.Entry<String, Int>,
            newItem: Map.Entry<String, Int>
        ) = oldItem == newItem
    }
}
