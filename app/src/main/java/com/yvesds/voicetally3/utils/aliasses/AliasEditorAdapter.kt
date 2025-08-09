package com.yvesds.voicetally3.utils.aliasses

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.voicetally3.R
import com.yvesds.voicetally3.databinding.ItemAliasRowBinding


class AliasEditorAdapter(
    private var entries: List<BirdEntry>,
    private val onEntryUpdated: (index: Int, updatedEntry: BirdEntry) -> Unit
) : RecyclerView.Adapter<AliasEditorAdapter.AliasViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AliasViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemAliasRowBinding.inflate(inflater, parent, false)
        return AliasViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AliasViewHolder, position: Int) {
        holder.bind(entries[position], position)
    }

    override fun getItemCount(): Int = entries.size

    fun updateData(newEntries: List<BirdEntry>) {
        entries = newEntries
        notifyDataSetChanged()
    }

    inner class AliasViewHolder(
        private val binding: ItemAliasRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: BirdEntry, position: Int) {
            binding.textSpeciesName.text = entry.canonicalName

            // 👉 Navigeer naar detail editor bij klik op soortnaam
            binding.textSpeciesName.setOnClickListener {
                val navController = it.findNavController()
                val bundle = bundleOf("speciesName" to entry.canonicalName)
                navController.navigate(R.id.action_aliasEditorFragment_to_speciesAliasEditorFragment, bundle)
            }

            // Maak aliasvelden klaar
            val aliasFields = listOf(
                binding.editAlias1, binding.editAlias2, binding.editAlias3, binding.editAlias4,
                binding.editAlias5, binding.editAlias6, binding.editAlias7, binding.editAlias8,
                binding.editAlias9, binding.editAlias10, binding.editAlias11, binding.editAlias12,
                binding.editAlias13, binding.editAlias14, binding.editAlias15, binding.editAlias16,
                binding.editAlias17, binding.editAlias18, binding.editAlias19, binding.editAlias20
            )

            aliasFields.forEachIndexed { index, editText ->
                editText.setTag(null)
                val alias = entry.aliases.getOrNull(index) ?: ""
                editText.setText(alias)

                val watcher = object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        val updatedAliases = aliasFields.map { it.text.toString().trim() }
                        val updated = entry.copy(aliases = updatedAliases.toMutableList())
                        onEntryUpdated(position, updated)
                    }
                }

                editText.addTextChangedListener(watcher)
                editText.setTag(watcher)
            }
        }
    }
}
