package com.yvesds.voicetally3.ui.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yvesds.voicetally3.data.AliasRepository
import com.yvesds.voicetally3.utils.LogEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named

/**
 * Gedeelde VM tussen meerdere schermen.
 * - Beheert geselecteerde soorten, tally-map, sessie-info, GPS, speech-logs.
 * - Bouwt 2 alias-maps:
 *   1) actieveAliasMap = alias -> soort (enkel voor geselecteerde soorten)
 *   2) fallbackAliasMap = alias -> soort (alle soorten, voor herkenning buiten selectie)
 *
 * Performance:
 * - Atomic updates met StateFlow.update voor minimale allocaties.
 * - (Optioneel) tallyEntriesSorted: gesorteerde entries in de VM i.p.v. in de UI.
 */
@HiltViewModel
class SharedSpeciesViewModel @Inject constructor(
    private val aliasRepository: AliasRepository,
    @Named("IO") private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val maxLogBuffer = 500
    private fun normalize(name: String) = name.trim().lowercase()

    // ==== UI state ====
    private val _selectedSpecies = MutableStateFlow<Set<String>>(emptySet())
    val selectedSpecies: StateFlow<Set<String>> get() = _selectedSpecies

    private val _tallyMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val tallyMap: StateFlow<Map<String, Int>> get() = _tallyMap

    // Optioneel te gebruiken in UI (sneller: geen sort per update in Fragment nodig).
    // Backwards-compatible: tallyMap blijft bestaan.
    val tallyEntriesSorted: StateFlow<List<Map.Entry<String, Int>>> =
        _tallyMap
            .map { it.entries.sortedBy { e -> e.key }.toList() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _sessionStart = MutableStateFlow<Long?>(null)
    val sessionStart: StateFlow<Long?> get() = _sessionStart

    private val _gpsLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val gpsLocation: StateFlow<Pair<Double, Double>?> get() = _gpsLocation

    private val _speechLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val speechLogs: StateFlow<List<LogEntry>> get() = _speechLogs

    // Alias-maps
    private val _actieveAliasMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val actieveAliasMap: StateFlow<Map<String, String>> get() = _actieveAliasMap

    private val _fallbackAliasMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val fallbackAliasMap: StateFlow<Map<String, String>> get() = _fallbackAliasMap

    /**
     * Volledige soortenlijst (read-only snapshot).
     * Wordt eenmalig lazy geladen via suspend call.
     */
    @Volatile
    var speciesList: List<String> = emptyList()
        private set

    init {
        // Laad fallback aliasmap + speciesList in background
        preloadSpeciesAndFallbackAliasMap()
    }

    private fun preloadSpeciesAndFallbackAliasMap() {
        viewModelScope.launch {
            // Laad alle soorten (IO)
            val allSpecies = withContext(ioDispatcher) { aliasRepository.getAllSpeciesSuspend() }
            // Normaliseer namen 1x (lowercase) voor consistente lookups
            speciesList = allSpecies.map { normalize(it) }

            // Bouw fallback aliasmap (IO)
            val map = withContext(ioDispatcher) { aliasRepository.buildFallbackAliasToSpeciesMapSuspend() }
            // Zorg dat values ook genormaliseerd zijn
            _fallbackAliasMap.value = map.mapValues { (_, v) -> normalize(v) }
        }
    }

    fun setGpsLocation(lat: Double, lon: Double) {
        _gpsLocation.value = lat to lon
    }

    fun setSessionStart(time: Long) {
        _sessionStart.value = time
    }

    /**
     * Vervang de selectie; tally’s worden behouden waar mogelijk.
     * Bouwt tegelijk de actieve aliasmap (IO).
     */
    fun setSelectedSpecies(species: Set<String>) {
        // Normaliseer selectie
        val normalizedSel = species.map { normalize(it) }.toSet()

        // Behoud bestaande teller-waarden waar mogelijk
        val normalizedTally = _tallyMap.value // keys zijn al normalized in onze updates
        val updated: Map<String, Int> = normalizedSel.associateWith { normalizedTally[it] ?: 0 }

        _selectedSpecies.value = normalizedSel
        _tallyMap.value = updated

        // Actieve aliasmap voor enkel de geselecteerde soorten
        viewModelScope.launch(ioDispatcher) {
            val aliasMap = aliasRepository.buildAliasToSpeciesMapSuspend(normalizedSel)
            // Map values normaliseren
            _actieveAliasMap.value = aliasMap.mapValues { (_, v) -> normalize(v) }
        }
    }

    /**
     * Voeg soort toe aan selectie en voer een bijkomende increment uit.
     * Bouwt de actieve aliasmap opnieuw op.
     */
    fun addSpeciesToSelection(species: String, amount: Int) {
        val key = normalize(species)

        var added = false
        _selectedSpecies.update { current ->
            if (current.contains(key)) {
                current
            } else {
                added = true
                current + key
            }
        }

        // Herbouw aliasmap alleen als de set effectief groeide
        if (added) {
            viewModelScope.launch(ioDispatcher) {
                val aliasMap = aliasRepository.buildAliasToSpeciesMapSuspend(_selectedSpecies.value)
                _actieveAliasMap.value = aliasMap.mapValues { (_, v) -> normalize(v) }
            }
        }

        // Tally bijwerken (atomic)
        _tallyMap.update { current ->
            val next = current.toMutableMap()
            next[key] = (next[key] ?: 0) + amount
            next
        }
    }

    fun increment(species: String) = bump(species, +1)

    fun decrement(species: String) = _tallyMap.update { current ->
        val key = normalize(species)
        val cur = current[key] ?: 0
        if (cur == 0) return@update current // no-op
        val next = current.toMutableMap()
        next[key] = (cur - 1).coerceAtLeast(0)
        next
    }

    fun reset(species: String) = _tallyMap.update { current ->
        val key = normalize(species)
        if (!current.containsKey(key)) return@update current // no-op
        val next = current.toMutableMap()
        next[key] = 0
        next
    }

    fun resetAll() {
        _tallyMap.update { it.mapValues { 0 } }
        _sessionStart.value = System.currentTimeMillis()
        _speechLogs.value = emptyList()
    }

    /**
     * Batch-updates in één atomic emit (sneller bij meerdere wijzigingen).
     */
    fun updateTallies(updates: List<Pair<String, Int>>) {
        if (updates.isEmpty()) return
        _tallyMap.update { current ->
            val next = current.toMutableMap()
            updates.forEach { (species, amount) ->
                val key = normalize(species)
                val prev = next[key] ?: 0
                next[key] = prev + amount
            }
            next
        }
    }

    private fun bump(species: String, delta: Int) {
        if (delta == 0) return
        _tallyMap.update { current ->
            val key = normalize(species)
            val prev = current[key] ?: 0
            val newVal = (prev + delta).coerceAtLeast(0)
            if (newVal == prev) return@update current // no-op
            val next = current.toMutableMap()
            next[key] = newVal
            next
        }
    }

    fun addSpeechLog(entry: LogEntry) {
        if (!entry.showInUi) return
        _speechLogs.update { cur ->
            val appended = cur + entry
            if (appended.size <= maxLogBuffer) appended else appended.takeLast(maxLogBuffer)
        }
    }

    fun exportAllSpeechLogs(): String {
        return _speechLogs.value
            .reversed()
            .joinToString("\n") { it.text }
    }

    /**
     * Vind soort bij alias; doorzoekt eerst actieve aliasmap, dan fallback.
     */
    fun findSpeciesForAlias(aliasInput: String): String? {
        val normalized = normalize(aliasInput)
        return _actieveAliasMap.value[normalized] ?: _fallbackAliasMap.value[normalized]
    }
}
