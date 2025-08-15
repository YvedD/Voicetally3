package com.yvesds.voicetally3.ui.species

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.voicetally3.databinding.ItemSpeciesBinding

/**
 * Lijst van soorten met checkbox.
 * - ListAdapter + DiffUtil
 * - Losse selectie-set (geen notifyDataSetChanged; payloads gebruiken)
 * - Stabiele IDs op basis van soortnaam
 */
class SpeciesAdapter(
    private val onToggle: (String, Boolean) -> Unit
) : ListAdapter<String, SpeciesAdapter.SpeciesViewHolder>(Diff) {

    private val selectedSpecies: MutableSet<String> = mutableSetOf()

    init {
        setHasStableIds(true)
    }

    fun setSelectedSpecies(newSet: Set<String>) {
        // Eenvoudige, robuuste check zonder 'xor' operator
        val changed = selectedSpecies != newSet
        if (!changed) return

        selectedSpecies.clear()
        selectedSpecies.addAll(newSet)
        // Herbind enkel checked-state via payload
        notifyItemRangeChanged(0, itemCount, PAYLOAD_CHECKED_STATE)
    }

    override fun getItemId(position: Int): Long = getItem(position).hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpeciesViewHolder {
        val binding = ItemSpeciesBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SpeciesViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SpeciesViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(
        holder: SpeciesViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_CHECKED_STATE)) {
            holder.updateCheckedOnly(getItem(position))
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    inner class SpeciesViewHolder(
        private val binding: ItemSpeciesBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(species: String) {
            binding.checkBox.text = species
            binding.checkBox.setOnCheckedChangeListener(null)
            binding.checkBox.isChecked = selectedSpecies.contains(species)
            binding.checkBox.setOnCheckedChangeListener { _, isChecked ->
                onToggle(species, isChecked)
            }
        }

        fun updateCheckedOnly(species: String) {
            binding.checkBox.setOnCheckedChangeListener(null)
            binding.checkBox.isChecked = selectedSpecies.contains(species)
            binding.checkBox.setOnCheckedChangeListener { _, isChecked ->
                onToggle(species, isChecked)
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String) = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String) = oldItem == newItem
    }

    private companion object {
        const val PAYLOAD_CHECKED_STATE = "payload_checked"
    }
}
