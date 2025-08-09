package com.yvesds.voicetally3.utils.aliasses

import android.app.Application
import androidx.lifecycle.*
import com.yvesds.voicetally3.data.AliasRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AliasEditorViewModel @Inject constructor(
    application: Application,
    private val aliasRepository: AliasRepository
) : AndroidViewModel(application) {

    private val _speciesList = MutableLiveData<List<String>>()
    val speciesList: LiveData<List<String>> = _speciesList

    /**
     * 📥 Laad lijst van soorten uit soorten.csv
     */
    fun loadSpeciesNames() {
        viewModelScope.launch(Dispatchers.IO) {
            val names = aliasRepository.getAllSpecies()
            _speciesList.postValue(names)
        }
    }

    /**
     * ➕ Voeg nieuwe soort toe aan soorten.csv + aliasbestand aanmaken
     */
    fun addNewSpecies(name: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val added = aliasRepository.addSpecies(name)
            if (added) {
                aliasRepository.ensureSpeciesFile(name)
                val updatedList = aliasRepository.getAllSpecies()
                _speciesList.postValue(updatedList)
            }
            onResult(added)
        }
    }
}
