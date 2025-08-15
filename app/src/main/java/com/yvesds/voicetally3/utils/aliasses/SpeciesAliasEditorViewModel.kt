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
 * Bewerken van de aliassen voor één specifieke soort.
 * - Gebruikt AliasRepository i.p.v. rechtstreeks CSVManager (duidelijkere laag).
 * - Invalidate caches na opslaan.
 */
@HiltViewModel
class SpeciesAliasEditorViewModel @Inject constructor(
    private val aliasRepository: AliasRepository,
    private val speciesCacheManager: SpeciesCacheManager,
    @Named("IO") private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _aliases = MutableLiveData<List<String>>()
    val aliases: LiveData<List<String>> = _aliases

    /** Laad aliassen van een specifieke soort uit assets/<soort>.csv */
    fun loadAliases(speciesName: String) {
        viewModelScope.launch {
            val list = withContext(ioDispatcher) {
                aliasRepository.loadAliasesForSpeciesSuspend(speciesName)
            }
            _aliases.value = list
        }
    }

    /** Sla aliassen op in assets/<soort>.csv en invalideer caches */
    fun saveAliases(speciesName: String, newAliases: List<String>) {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                aliasRepository.saveAliasesForSpeciesSuspend(speciesName, newAliases)
                speciesCacheManager.invalidate()
            }
            // UI-best bevestiging kan in Fragment gebeuren; hier eventueel herladen:
            loadAliases(speciesName)
        }
    }
}
