package com.yvesds.voicetally3.utils.aliasses

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.voicetally3.R

class SimpleSpeciesListAdapter(
    private var speciesList: List<String>,
    private val onItemClicked: (String) -> Unit
) : RecyclerView.Adapter<SimpleSpeciesListAdapter.SpeciesViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpeciesViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_species_name, parent, false)
        return SpeciesViewHolder(view)
    }

    override fun onBindViewHolder(holder: SpeciesViewHolder, position: Int) {
        holder.bind(speciesList[position])
    }

    override fun getItemCount(): Int = speciesList.size

    fun updateData(newList: List<String>) {
        speciesList = newList
        notifyDataSetChanged()
    }

    inner class SpeciesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textSpecies: TextView = itemView.findViewById(R.id.textSpeciesName)

        fun bind(speciesName: String) {
            textSpecies.text = speciesName

            // Touch feedback: kleurflits bij klik
            itemView.setOnClickListener {
                textSpecies.setBackgroundColor(
                    ContextCompat.getColor(itemView.context, R.color.light_green)
                )
                it.postDelayed({
                    textSpecies.setBackgroundColor(Color.BLACK)
                }, 100)

                onItemClicked(speciesName)
            }
        }
    }
}
