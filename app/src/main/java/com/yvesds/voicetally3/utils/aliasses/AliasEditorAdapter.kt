package com.yvesds.voicetally3.utils.aliasses

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.voicetally3.R
import com.yvesds.voicetally3.databinding.ItemAliasRowBinding

/**
 * Editor-overzicht: één rij = 20 aliasvelden + klikbare soortnaam (nav naar detail).
 * - ListAdapter + DiffUtil voor soepele updates
 * - TextWatcher correct beheren om leaks/dubbele callbacks te vermijden
 */
class AliasEditorAdapter(
    entries: List<BirdEntry>,
    private val onEntryUpdated: (index: Int, updatedEntry: BirdEntry) -> Unit
) : ListAdapter<BirdEntry, AliasEditorAdapter.AliasViewHolder>(Diff) {

    init {
        submitList(entries)
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).canonicalName.hashCode().toLong()

    fun updateData(newEntries: List<BirdEntry>) = submitList(newEntries)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AliasViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemAliasRowBinding.inflate(inflater, parent, false)
        return AliasViewHolder(binding, onEntryUpdated)
    }

    override fun onBindViewHolder(holder: AliasViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class AliasViewHolder(
        private val binding: ItemAliasRowBinding,
        private val onEntryUpdated: (index: Int, updatedEntry: BirdEntry) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        // Bewaar watchers per veld om ze te kunnen verwijderen bij rebind
        private val watchers = Array<TextWatcher?>(20) { null }

        fun bind(entry: BirdEntry, position: Int) {
            binding.textSpeciesName.text = entry.canonicalName

            // Navigatie naar detail bij klik op soortnaam (behoudt huidige UX)
            binding.textSpeciesName.setOnClickListener {
                val navController = it.findNavController()
                val bundle = bundleOf("speciesName" to entry.canonicalName)
                navController.navigate(
                    R.id.action_aliasEditorFragment_to_speciesAliasEditorFragment,
                    bundle
                )
            }

            val aliasFields = listOf(
                binding.editAlias1, binding.editAlias2, binding.editAlias3, binding.editAlias4,
                binding.editAlias5, binding.editAlias6, binding.editAlias7, binding.editAlias8,
                binding.editAlias9, binding.editAlias10, binding.editAlias11, binding.editAlias12,
                binding.editAlias13, binding.editAlias14, binding.editAlias15, binding.editAlias16,
                binding.editAlias17, binding.editAlias18, binding.editAlias19, binding.editAlias20
            )

            // Zet tekst én watchers correct
            aliasFields.forEachIndexed { index, editText ->
                // 1) Verwijder oude watcher (indien aanwezig)
                watchers[index]?.let { editText.removeTextChangedListener(it) }

                // 2) Zet actuele tekst zonder triggers
                val alias = entry.aliases.getOrNull(index) ?: ""
                if (editText.text?.toString() != alias) {
                    editText.setText(alias)
                    editText.setSelection(editText.text?.length ?: 0)
                }

                // 3) Voeg nieuwe watcher toe
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
                watchers[index] = watcher
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<BirdEntry>() {
        override fun areItemsTheSame(oldItem: BirdEntry, newItem: BirdEntry) =
            oldItem.canonicalName == newItem.canonicalName

        override fun areContentsTheSame(oldItem: BirdEntry, newItem: BirdEntry) =
            oldItem.aliases == newItem.aliases
    }
}
