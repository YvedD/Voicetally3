package com.yvesds.voicetally3.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.voicetally3.databinding.ItemSettingSwitchBinding
import com.yvesds.voicetally3.databinding.ItemSettingLanguageBinding

class SettingsAdapter(
    private val items: List<SettingItem>,
    private val currentLanguage: String,
    private val onToggle: (String, Boolean) -> Unit,
    private val onLanguageSelected: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    inner class SwitchViewHolder(val binding: ItemSettingSwitchBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SettingItem) {
            binding.textViewTitle.text = item.title
            binding.textViewDescription.text = item.description
            binding.checkboxOption.isChecked = item.isChecked
            binding.checkboxOption.setOnCheckedChangeListener { _, isChecked ->
                onToggle(item.key, isChecked)
            }
        }
    }

    inner class LanguageViewHolder(val binding: ItemSettingLanguageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(selectedLanguage: String) {
            // ✅ Zet de juiste taal als geselecteerd
            when (selectedLanguage) {
                "nl-BE" -> binding.radioNl.isChecked = true
                "en-US" -> binding.radioEn.isChecked = true
                "fr-FR" -> binding.radioFr.isChecked = true
            }

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

    override fun getItemViewType(position: Int): Int {
        return when (items[position].type) {
            SettingType.SWITCH -> 0
            SettingType.LANGUAGE -> 1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == 0) {
            val binding = ItemSettingSwitchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            SwitchViewHolder(binding)
        } else {
            val binding = ItemSettingLanguageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            LanguageViewHolder(binding)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is SwitchViewHolder) {
            holder.bind(item)
        } else if (holder is LanguageViewHolder) {
            holder.bind(currentLanguage)
        }
    }
}
