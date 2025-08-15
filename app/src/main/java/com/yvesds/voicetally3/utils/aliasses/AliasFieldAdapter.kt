package com.yvesds.voicetally3.utils.aliasses

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.voicetally3.R

/**
 * 20 losstaande aliasvelden voor één soort.
 * - TextWatcher correct managen (verwijderen voor toevoegen)
 * - Geen notifyDataSetChanged; updates gebeuren per veld
 */
class AliasFieldAdapter(
    private val aliasList: MutableList<String>
) : RecyclerView.Adapter<AliasFieldAdapter.AliasViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AliasViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alias_field, parent, false) as EditText
        return AliasViewHolder(view)
    }

    override fun getItemCount(): Int = aliasList.size

    override fun onBindViewHolder(holder: AliasViewHolder, position: Int) {
        holder.bind(aliasList[position], position)
    }

    fun getUpdatedAliases(): List<String> = aliasList

    inner class AliasViewHolder(private val editText: EditText) : RecyclerView.ViewHolder(editText) {
        private var watcher: TextWatcher? = null

        fun bind(text: String, position: Int) {
            // 1) Watcher verwijderen
            watcher?.let { editText.removeTextChangedListener(it) }

            // 2) Tekst zetten zonder dubbele triggers
            if (editText.text?.toString() != text) {
                editText.setText(text)
                editText.setSelection(editText.text?.length ?: 0)
            }
            editText.hint = "Alias ${position + 1}"

            // 3) Nieuwe watcher toevoegen
            watcher = object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    aliasList[position] = s?.toString() ?: ""
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            }
            editText.addTextChangedListener(watcher)
        }
    }
}
