package com.yvesds.voicetally3.data

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeciesCacheManager @Inject constructor(
    private val aliasRepository: AliasRepository,
    private val sharedPrefsHelper: SharedPrefsHelper
) {

    private var speciesList: List<String>? = null
    private var aliasMap: Map<String, List<String>>? = null
    private var aliasToDisplayMap: Map<String, String>? = null

    /**
     * 🚀 Laad of geef bestaande soortenlijst uit soorten.csv
     */
    fun getSpeciesList(): List<String> {
        if (speciesList == null) {
            speciesList = aliasRepository.getAllSpecies()
            Log.d("SpeciesCacheManager", "✅ Soortenlijst geladen (${speciesList?.size} soorten)")
        }
        return speciesList!!
    }

    /**
     * 📥 Bouw een map van soortnaam → lijst van aliassen (inclusief zichzelf)
     */
    fun getAliasMap(): Map<String, List<String>> {
        if (aliasMap == null) {
            val result = mutableMapOf<String, List<String>>()
            getSpeciesList().forEach { species ->
                val aliases = aliasRepository.loadAliasesForSpecies(species)
                val combined = (aliases + species).map { it.lowercase().trim() }.distinct()
                result[species.lowercase()] = combined
            }
            aliasMap = result
            Log.d("SpeciesCacheManager", "✅ AliasMap opgebouwd (${aliasMap?.size} soorten)")
        }
        return aliasMap!!
    }

    /**
     * 🔁 Bouw alias → soort display map op
     */
    fun getAliasToDisplayMap(): Map<String, String> {
        if (aliasToDisplayMap == null) {
            val map = getAliasMap().flatMap { (species, aliases) ->
                aliases.map { alias -> alias to species }
            }.toMap()
            aliasToDisplayMap = map
            Log.d("SpeciesCacheManager", "✅ AliasToDisplayMap opgebouwd (${map.size} aliassen)")
        }
        return aliasToDisplayMap!!
    }

    /**
     * ♻️ Leeg alle caches
     */
    fun invalidate() {
        speciesList = null
        aliasMap = null
        aliasToDisplayMap = null
        Log.d("SpeciesCacheManager", "♻️ Caches gewist")
    }
}
