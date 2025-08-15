package com.yvesds.voicetally3.ui.tally

import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.voicetally3.R

/**
 * Toont de tellingen per soort.
 * - Werkt met item_tally.xml (AppCompatImageButton).
 * - Accepteert Kotlin Map.Entry zodat call-sites geen conversies hoeven.
 * - Payload-diffs: alleen count-text updaten voor maximale snelheid.
 * - Optimistic UI + haptic feedback voor directe respons bij tikken.
 */
class TallyAdapter(
    private val onIncrement: (String) -> Unit,
    private val onDecrement: (String) -> Unit,
    private val onReset: (String) -> Unit
) : ListAdapter<Map.Entry<String, Int>, TallyAdapter.TallyViewHolder>(Diff) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        // Stabiel id op basis van soortnaam
        return getItem(position).key.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TallyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tally, parent, false)
        return TallyViewHolder(view, onIncrement, onDecrement, onReset)
    }

    override fun onBindViewHolder(holder: TallyViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(
        holder: TallyViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        val payloadCount = payloads.lastOrNull() as? Int
        if (payloadCount != null) {
            holder.updateCountOnly(payloadCount)
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    class TallyViewHolder(
        itemView: View,
        private val onIncrement: (String) -> Unit,
        private val onDecrement: (String) -> Unit,
        private val onReset: (String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val txtName: TextView = itemView.findViewById(R.id.textSpeciesName)
        private val txtCount: TextView = itemView.findViewById(R.id.textCount)

        private val btnDec: AppCompatImageButton = itemView.findViewById(R.id.buttonDecrement)
        private val btnInc: AppCompatImageButton = itemView.findViewById(R.id.buttonIncrement)
        private val btnReset: AppCompatImageButton = itemView.findViewById(R.id.buttonReset)

        private var currentSpecies: String? = null
        private var localCount: Int = 0

        fun bind(entry: Map.Entry<String, Int>) {
            val species = entry.key
            val count = entry.value

            currentSpecies = species
            localCount = count

            txtName.text = species.replaceFirstChar { it.uppercase() }
            txtCount.text = localCount.toString()

            btnDec.setOnClickListener {
                val s = currentSpecies ?: return@setOnClickListener
                if (localCount > 0) {
                    localCount -= 1
                    txtCount.text = localCount.toString() // Optimistic UI
                }
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onDecrement(s)
            }
            btnInc.setOnClickListener {
                val s = currentSpecies ?: return@setOnClickListener
                localCount += 1
                txtCount.text = localCount.toString() // Optimistic UI
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onIncrement(s)
            }
            btnReset.setOnClickListener {
                val s = currentSpecies ?: return@setOnClickListener
                localCount = 0
                txtCount.text = "0" // Optimistic UI
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onReset(s)
            }
        }

        fun updateCountOnly(newCount: Int) {
            localCount = newCount
            txtCount.text = newCount.toString()
        }
    }

    private object Diff : DiffUtil.ItemCallback<Map.Entry<String, Int>>() {
        override fun areItemsTheSame(
            oldItem: Map.Entry<String, Int>,
            newItem: Map.Entry<String, Int>
        ): Boolean = oldItem.key == newItem.key

        override fun areContentsTheSame(
            oldItem: Map.Entry<String, Int>,
            newItem: Map.Entry<String, Int>
        ): Boolean = oldItem.value == newItem.value

        override fun getChangePayload(
            oldItem: Map.Entry<String, Int>,
            newItem: Map.Entry<String, Int>
        ): Any? {
            // Alleen count gewijzigd → geef nieuwe count als payload
            return if (oldItem.value != newItem.value) newItem.value else null
        }
    }
}
