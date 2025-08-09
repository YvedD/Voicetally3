package com.yvesds.voicetally3.ui.species

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.voicetally3.databinding.ItemSpeciesBinding

class SpeciesAdapter(
    private val onToggle: (String, Boolean) -> Unit
) : ListAdapter<String, SpeciesAdapter.SpeciesViewHolder>(SpeciesDiffCallback()) {

    private val selectedSpecies: MutableSet<String> = mutableSetOf()

    fun setSelectedSpecies(newSet: Set<String>) {
        selectedSpecies.clear()
        selectedSpecies.addAll(newSet)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpeciesViewHolder {
        val binding = ItemSpeciesBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SpeciesViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SpeciesViewHolder, position: Int) {
        holder.bind(getItem(position).trim())
    }

    inner class SpeciesViewHolder(private val binding: ItemSpeciesBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(species: String) {
            binding.checkBox.text = species
            binding.checkBox.setOnCheckedChangeListener(null)
            binding.checkBox.isChecked = selectedSpecies.contains(species)

            binding.checkBox.setOnCheckedChangeListener { _, isChecked ->
                onToggle(species, isChecked)
            }
        }
    }

    class SpeciesDiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String) = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String) = oldItem == newItem
    }
}
