package com.yvesds.voicetally3.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread-safe, lazy caches:
 * - speciesListCache: List<String>
 * - aliasMapCache: Map<species(lowercase) -> List<alias(lowercase) inclusief canonical>>
 * - aliasToDisplayMapCache: Map<alias(lowercase) -> species (displaynaam zoals in soorten.csv)>
 *
 * Sync-methodes blijven bestaan (back-compat). Gebruik waar mogelijk de suspend-varianten.
 */
@Singleton
class SpeciesCacheManager @Inject constructor(
    private val aliasRepository: AliasRepository,
    private val sharedPrefsHelper: SharedPrefsHelper
) {

    @Volatile private var speciesListCache: List<String>? = null
    @Volatile private var aliasMapCache: Map<String, List<String>>? = null
    @Volatile private var aliasToDisplayMapCache: Map<String, String>? = null

    private val buildMutex = Mutex()

    /** Sync: kan main thread blokkeren; bij voorkeur de suspend-variant gebruiken. */
    fun getSpeciesList(): List<String> {
        if (speciesListCache == null) {
            speciesListCache = aliasRepository.getAllSpecies()
            Log.d("SpeciesCacheManager", "✅ Soortenlijst geladen (${speciesListCache?.size} soorten)")
        }
        return speciesListCache!!
    }

    /** Suspend: laadt veilig op IO dispatcher en cachet. */
    suspend fun getSpeciesListSuspend(): List<String> = withContext(Dispatchers.IO) {
        speciesListCache ?: buildMutex.withLock {
            speciesListCache ?: aliasRepository.getAllSpeciesSuspend().also { speciesListCache = it }
        }
    }

    /** Sync: alias-map (species -> aliases inclusief canonical). */
    fun getAliasMap(): Map<String, List<String>> {
        if (aliasMapCache == null) {
            val result = LinkedHashMap<String, List<String>>()
            for (species in getSpeciesList()) {
                val aliases = aliasRepository.loadAliasesForSpecies(species)
                val combined = (aliases + species).map { it.lowercase().trim() }.distinct()
                result[species.lowercase()] = combined
            }
            aliasMapCache = result
            Log.d("SpeciesCacheManager", "✅ AliasMap opgebouwd (${aliasMapCache?.size} soorten)")
        }
        return aliasMapCache!!
    }

    /** Suspend: alias-map bouwen off-main. */
    suspend fun getAliasMapSuspend(): Map<String, List<String>> = withContext(Dispatchers.IO) {
        aliasMapCache ?: buildMutex.withLock {
            aliasMapCache ?: run {
                val result = LinkedHashMap<String, List<String>>()
                for (species in getSpeciesListSuspend()) {
                    val aliases = aliasRepository.loadAliasesForSpeciesSuspend(species)
                    val combined = (aliases + species).map { it.lowercase().trim() }.distinct()
                    result[species.lowercase()] = combined
                }
                result.toMap().also { aliasMapCache = it }
            }
        }
    }

    /** Sync: alias(lowercase) -> species(display). */
    fun getAliasToDisplayMap(): Map<String, String> {
        if (aliasToDisplayMapCache == null) {
            val map = getAliasMap().flatMap { (species, aliases) ->
                aliases.map { alias -> alias to species }
            }.toMap()
            aliasToDisplayMapCache = map
            Log.d("SpeciesCacheManager", "✅ AliasToDisplayMap opgebouwd (${map.size} aliassen)")
        }
        return aliasToDisplayMapCache!!
    }

    /** Suspend: alias(lowercase) -> species(display). */
    suspend fun getAliasToDisplayMapSuspend(): Map<String, String> = withContext(Dispatchers.IO) {
        aliasToDisplayMapCache ?: buildMutex.withLock {
            aliasToDisplayMapCache ?: run {
                val base = getAliasMapSuspend()
                base.flatMap { (species, aliases) ->
                    aliases.map { alias -> alias to species }
                }.toMap().also { aliasToDisplayMapCache = it }
            }
        }
    }

    /** Caches leegmaken. */
    fun invalidate() {
        speciesListCache = null
        aliasMapCache = null
        aliasToDisplayMapCache = null
        Log.d("SpeciesCacheManager", "♻️ Caches gewist")
    }
}
