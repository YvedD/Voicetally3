package com.yvesds.voicetally3.utils.aliasses

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yvesds.voicetally3.data.AliasRepository
import com.yvesds.voicetally3.data.SpeciesCacheManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named

/**
 * Lijst van soorten beheren en nieuwe soorten toevoegen.
 * - I/O via suspend-API's op IO dispatcher
 * - Na toevoegen: cache invalidatie
 */
@HiltViewModel
class AliasEditorViewModel @Inject constructor(
    private val aliasRepository: AliasRepository,
    private val speciesCacheManager: SpeciesCacheManager,
    @Named("IO") private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _speciesList = MutableLiveData<List<String>>()
    val speciesList: LiveData<List<String>> = _speciesList

    /** Laad lijst van soorten uit soorten.csv */
    fun loadSpeciesNames() {
        viewModelScope.launch {
            val names = withContext(ioDispatcher) { aliasRepository.getAllSpeciesSuspend() }
            _speciesList.value = names
        }
    }

    /**
     * ➕ Voeg nieuwe soort toe aan soorten.csv + aliasbestand aanmaken
     * - Roept callback op main thread terug met succes/failure.
     */
    fun addNewSpecies(name: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val added = withContext(ioDispatcher) {
                val ok = aliasRepository.addSpecies(name)
                if (ok) {
                    // Zorg dat aliasbestand bestaat
                    aliasRepository.ensureSpeciesFile(name)
                    // Caches ongeldig maken
                    speciesCacheManager.invalidate()
                }
                ok
            }
            if (added) {
                // lijst verversen
                val updated = withContext(ioDispatcher) { aliasRepository.getAllSpeciesSuspend() }
                _speciesList.value = updated
            }
            onResult(added)
        }
    }
}
