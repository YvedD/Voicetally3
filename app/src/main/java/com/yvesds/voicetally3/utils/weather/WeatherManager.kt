package com.yvesds.voicetally3.utils.weather

import android.app.AlertDialog
import android.content.Context
import android.location.Geocoder
import android.util.Log
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class WeatherManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    @Named("IO") private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        private const val TAG = "WeatherManager"
        private const val WEATHER_API = "https://api.open-meteo.com/v1/forecast"
    }

    data class FullWeather(
        val locationName: String,
        val temperature: Double,
        val windspeed: Double,
        val winddirection: Int,
        val precipitation: Double,
        val pressure: Int,
        val cloudcover: Int,
        val visibility: Int,
        val weathercode: Int,
        val time: String
    )

    /**
     * Haal huidige weerdata op voor de *laatste* bekende GPS-locatie van het toestel.
     * - Off-main (IO dispatcher)
     * - Robuuste HTTP-verbinding met timeouts
     * - Fix: correcte query-parameter (was corrupt in oudere versie)
     */
    suspend fun fetchFullWeather(context: Context = appContext): FullWeather? = withContext(ioDispatcher) {
        try {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            val location = fused.lastLocation.await() ?: return@withContext null
            val lat = location.latitude
            val lon = location.longitude

            // Open-Meteo "current" parameter met gewenste variabelen
            val urlStr = "$WEATHER_API?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,precipitation,weathercode,windspeed_10m,winddirection_10m,cloudcover,visibility,pressure_msl" +
                    "&timezone=auto"

            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
            }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(response)
            val current = json.getJSONObject("current")

            val locality = getLocalityName(context, lat, lon)
            return@withContext FullWeather(
                locationName = locality ?: "Onbekend",
                temperature = current.getDouble("temperature_2m"),
                precipitation = current.optDouble("precipitation", 0.0),
                weathercode = current.getInt("weathercode"),
                windspeed = current.optDouble("windspeed_10m", 0.0),
                winddirection = current.optInt("winddirection_10m", 0),
                pressure = current.optDouble("pressure_msl", 0.0).roundToInt(),
                cloudcover = current.optInt("cloudcover", 0),
                visibility = current.optInt("visibility", 0),
                time = current.getString("time")
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fout bij ophalen weer: ${e.message}")
            null
        }
    }

    suspend fun showWeatherDialog(context: Context) {
        val weather = fetchFullWeather(context)
        withContext(Dispatchers.Main) {
            AlertDialog.Builder(context)
                .setTitle("Weerbericht")
                .setMessage(
                    if (weather != null)
                        """
                        Locatie: ${weather.locationName}
                        Tijdstip: ${weather.time}
                        ️Temp: ${"%.1f".format(weather.temperature)} °C
                        ️Neerslag: ${weather.precipitation} mm
                        ️Wind: ${weather.windspeed} km/u (${toBeaufort(weather.windspeed)} Bf), ${toCompass(weather.winddirection)}
                        ☁️ Bewolking: ${toOctas(weather.cloudcover)}/8
                        ️Zicht: ${weather.visibility} m
                        Luchtdruk: ${weather.pressure} hPa
                        Weer: ${getWeatherDescription(weather.weathercode)}
                        """.trimIndent()
                    else
                        "❌ Kon geen weergegevens ophalen voor deze locatie."
                )
                .setPositiveButton("OK", null)
                .show()
        }
    }

    fun toBeaufort(speedKmh: Double): Int = when {
        speedKmh < 1.0 -> 0
        speedKmh < 6.0 -> 1
        speedKmh < 12.0 -> 2
        speedKmh < 20.0 -> 3
        speedKmh < 29.0 -> 4
        speedKmh < 39.0 -> 5
        speedKmh < 50.0 -> 6
        speedKmh < 62.0 -> 7
        speedKmh < 75.0 -> 8
        speedKmh < 89.0 -> 9
        speedKmh < 103.0 -> 10
        speedKmh < 118.0 -> 11
        else -> 12
    }

    fun toCompass(degrees: Int): String {
        val dirs = listOf(
            "N", "NNO", "NO", "ONO", "O", "OZO", "ZO", "ZZO",
            "Z", "ZZW", "ZW", "WZW", "W", "WNW", "NW", "NNW"
        )
        val index = ((degrees / 22.5) + 0.5).toInt() % 16
        return dirs[index]
    }

    fun toOctas(percent: Int): Int = (percent / 12.5).roundToInt().coerceIn(0, 8)

    fun getWeatherDescription(code: Int): String = when (code) {
        0 -> "Zonnig"
        1, 2 -> "Gedeeltelijk bewolkt"
        3 -> "Bewolkt"
        45, 48 -> "Mist"
        51, 53, 55 -> "Motregen"
        61, 63, 65 -> "Regen"
        66, 67 -> "Ijzel"
        71, 73, 75 -> "Sneeuw"
        77 -> "Sneeuwkorrels"
        80, 81, 82 -> "Buien"
        85, 86 -> "Sneeuwbuien"
        95 -> "Onweer"
        96, 99 -> "Onweer met hagel"
        else -> "Onbekend"
    }

    fun getLocalityName(context: Context, lat: Double, lon: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val results = geocoder.getFromLocation(lat, lon, 1)
            results?.firstOrNull()?.locality
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fout bij Geocoder: ${e.message}")
            null
        }
    }
}
