package com.yvesds.voicetally3.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.voicetally3.databinding.ItemSettingLanguageBinding
import com.yvesds.voicetally3.databinding.ItemSettingSwitchBinding

/**
 * Toon instellingen: switches + taalkeuze.
 * - ListAdapter + DiffUtil (instellingen kunnen wijzigen)
 * - ViewType per item-type
 * - Geen I/O; callbacks naar fragment
 */
class SettingsAdapter(
    currentItems: List<SettingItem>,
    private var currentLanguage: String,
    private val onToggle: (String, Boolean) -> Unit,
    private val onLanguageSelected: (String) -> Unit
) : ListAdapter<SettingItem, RecyclerView.ViewHolder>(Diff) {

    init {
        submitList(currentItems)
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        // stabiel ID op key + type
        val it = getItem(position)
        return (31L * it.key.hashCode() + it.type.ordinal).toLong()
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position).type) {
            SettingType.SWITCH -> 0
            SettingType.LANGUAGE -> 1
        }
    }

    fun updateLanguage(lang: String) {
        currentLanguage = lang
        // Alleen language-item updaten
        notifyItemRangeChanged(0, itemCount, PAYLOAD_LANGUAGE_ONLY)
    }

    fun replaceAll(items: List<SettingItem>) {
        submitList(items)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == 0) {
            val binding = ItemSettingSwitchBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            SwitchViewHolder(binding, onToggle)
        } else {
            val binding = ItemSettingLanguageBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            LanguageViewHolder(binding) { lang ->
                onLanguageSelected(lang)
                updateLanguage(lang)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SwitchViewHolder -> holder.bind(getItem(position))
            is LanguageViewHolder -> holder.bind(currentLanguage)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_LANGUAGE_ONLY) && holder is LanguageViewHolder) {
            holder.bind(currentLanguage)
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    class SwitchViewHolder(
        private val binding: ItemSettingSwitchBinding,
        private val onToggle: (String, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SettingItem) {
            binding.textViewTitle.text = item.title
            binding.textViewDescription.text = item.description
            binding.checkboxOption.setOnCheckedChangeListener(null)
            binding.checkboxOption.isChecked = item.isChecked
            binding.checkboxOption.setOnCheckedChangeListener { _, isChecked ->
                onToggle(item.key, isChecked)
            }
        }
    }

    class LanguageViewHolder(
        private val binding: ItemSettingLanguageBinding,
        private val onLanguageSelected: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(selectedLanguage: String) {
            // Zet standaard selectie
            binding.radioNl.isChecked = selectedLanguage == "nl-BE"
            binding.radioEn.isChecked = selectedLanguage == "en-US"
            binding.radioFr.isChecked = selectedLanguage == "fr-FR"

            binding.radioGroupLanguages.setOnCheckedChangeListener { _, checkedId ->
                val lang = when (checkedId) {
                    binding.radioNl.id -> "nl-BE"
                    binding.radioEn.id -> "en-US"
                    binding.radioFr.id -> "fr-FR"
                    else -> selectedLanguage
                }
                onLanguageSelected(lang)
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<SettingItem>() {
        override fun areItemsTheSame(oldItem: SettingItem, newItem: SettingItem): Boolean {
            return oldItem.key == newItem.key && oldItem.type == newItem.type
        }

        override fun areContentsTheSame(oldItem: SettingItem, newItem: SettingItem): Boolean {
            return oldItem == newItem
        }
    }

    private companion object {
        const val PAYLOAD_LANGUAGE_ONLY = "payload_language"
    }
}
