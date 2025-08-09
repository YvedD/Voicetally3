package com.yvesds.voicetally3.utils.aliasses

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.voicetally3.R

class AliasFieldAdapter(
    private val aliasList: MutableList<String>
) : RecyclerView.Adapter<AliasFieldAdapter.AliasViewHolder>() {

    fun getUpdatedAliases(): List<String> = aliasList

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AliasViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alias_field, parent, false)
        return AliasViewHolder(view as EditText)
    }

    override fun getItemCount(): Int = aliasList.size

    override fun onBindViewHolder(holder: AliasViewHolder, position: Int) {
        holder.bind(aliasList[position], position)
    }

    inner class AliasViewHolder(private val editText: EditText) :
        RecyclerView.ViewHolder(editText) {

        fun bind(text: String, position: Int) {
            editText.setText(text)
            editText.hint = "Alias ${position + 1}"

            // ✅ Visuele feedback bij tik
            editText.setOnFocusChangeListener { _, hasFocus ->
                editText.setBackgroundResource(
                    if (hasFocus) R.drawable.bg_edittext_highlighted else R.drawable.bg_edittext_container
                )
            }

            editText.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    aliasList[position] = s?.toString() ?: ""
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
    }
}
