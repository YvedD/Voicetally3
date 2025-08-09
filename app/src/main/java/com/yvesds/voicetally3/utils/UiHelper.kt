package com.yvesds.voicetally3.utils

import android.view.View
import com.google.android.material.snackbar.Snackbar
import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText


object UiHelper {
    fun showSnackbar(view: View, message: String) {
        Snackbar.make(view, message, Snackbar.LENGTH_LONG).show()
    }
    fun showExitConfirmationDialog(
        context: Context,
        onConfirmed: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle("App afsluiten")
            .setMessage("Weet je zeker dat je VoiceTally volledig wilt afsluiten?")
            .setPositiveButton("JA") { _, _ -> onConfirmed() }
            .setNegativeButton("NEEN") { dialog, _ -> dialog.dismiss() }
            .show()
    }
    fun showInputDialog(
        context: Context,
        title: String,
        hint: String,
        onResult: (String?) -> Unit
    ) {
        val input = EditText(context).apply {
            this.hint = hint
            inputType = InputType.TYPE_CLASS_TEXT
        }

        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("OK") { _, _ -> onResult(input.text.toString()) }
            .setNegativeButton("Annuleer") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
