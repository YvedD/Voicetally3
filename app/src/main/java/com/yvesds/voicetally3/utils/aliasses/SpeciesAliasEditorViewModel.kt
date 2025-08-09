package com.yvesds.voicetally3.utils.aliasses

import android.app.Application
import androidx.lifecycle.*
import com.yvesds.voicetally3.managers.StorageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.yvesds.voicetally3.data.CSVManager

@HiltViewModel
class SpeciesAliasEditorViewModel @Inject constructor(
    application: Application,
    private val csvManager: CSVManager,
    private val storageManager: StorageManager
) : AndroidViewModel(application) {

    private val _aliases = MutableLiveData<List<String>>()
    val aliases: LiveData<List<String>> = _aliases

    /**
     * 🚀 Laad aliassen van een specifieke soort uit diens eigen CSV-bestand.
     * Bestand: assets/[soortnaam].csv
     */
    fun loadAliases(speciesName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val fileName = "${speciesName.trim().lowercase()}.csv"
            val rawLines = csvManager.readCsv("assets", fileName)

            val aliases = rawLines.flatten()
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            _aliases.postValue(aliases)
        }
    }

    /**
     * 💾 Sla aliassen op in eigen CSV-bestand van de soort
     */
    fun saveAliases(speciesName: String, newAliases: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val fileName = "${speciesName.trim().lowercase()}.csv"
            val content = newAliases.joinToString(";")
            csvManager.writeCsv("assets/$fileName", content)
        }
    }
}
