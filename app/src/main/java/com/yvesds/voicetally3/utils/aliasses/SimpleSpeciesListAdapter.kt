package com.yvesds.voicetally3.utils.aliasses

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.voicetally3.R

/**
 * Eenvoudige lijst voor soorten (klikbaar).
 * - ListAdapter + DiffUtil
 * - Stabiele IDs
 * - Haptische/visuele feedback laten we aan de standaard ripple over
 */
class SimpleSpeciesListAdapter(
    speciesList: List<String>,
    private val onItemClicked: (String) -> Unit
) : ListAdapter<String, SimpleSpeciesListAdapter.SpeciesViewHolder>(Diff) {

    init {
        submitList(speciesList)
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).hashCode().toLong()

    fun updateData(newList: List<String>) = submitList(newList)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpeciesViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_species_name, parent, false)
        return SpeciesViewHolder(view, onItemClicked)
    }

    override fun onBindViewHolder(holder: SpeciesViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class SpeciesViewHolder(
        itemView: View,
        private val onItemClicked: (String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val textSpecies: TextView = itemView.findViewById(R.id.textSpeciesName)

        fun bind(speciesName: String) {
            textSpecies.text = speciesName
            itemView.setOnClickListener { onItemClicked(speciesName) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String) = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String) = oldItem == newItem
    }
}
