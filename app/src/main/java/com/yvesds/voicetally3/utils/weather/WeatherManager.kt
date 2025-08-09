package com.yvesds.voicetally3.utils.weather

import android.app.AlertDialog
import android.content.Context
import android.location.Geocoder
import android.util.Log
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class WeatherManager @Inject constructor() {

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

    suspend fun fetchFullWeather(context: Context): FullWeather? = withContext(Dispatchers.IO) {
        try {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            val location = fused.lastLocation.await() ?: return@withContext null

            val lat = location.latitude
            val lon = location.longitude

            val url = "$WEATHER_API?latitude=$lat&longitude=$lon&current=temperature_2m,precipitation,weathercode,windspeed_10m,winddirection_10m,cloudcover,visibility,pressure_msl&timezone=auto"
            val response = URL(url).readText()
            val json = JSONObject(response)
            val current = json.getJSONObject("current")

            val locality = getLocalityName(context, lat, lon)

            FullWeather(
                locationName = locality ?: "Onbekend",
                temperature = current.getDouble("temperature_2m"),
                precipitation = current.getDouble("precipitation"),
                weathercode = current.getInt("weathercode"),
                windspeed = current.getDouble("windspeed_10m"),
                winddirection = current.getInt("winddirection_10m"),
                pressure = current.getDouble("pressure_msl").roundToInt(),
                cloudcover = current.getInt("cloudcover"),
                visibility = current.getInt("visibility"),
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
                        📍 Locatie: ${weather.locationName}
                        🕒 Tijdstip: ${weather.time}
                        🌡️ Temp: ${"%.1f".format(weather.temperature)} °C
                        🌧️ Neerslag: ${weather.precipitation} mm
                        🌬️ Wind: ${weather.windspeed} km/u (${toBeaufort(weather.windspeed)} Bf), ${toCompass(weather.winddirection)}
                        ☁️ Bewolking: ${toOctas(weather.cloudcover)}/8
                        👁️ Zicht: ${weather.visibility} m
                        🧭 Luchtdruk: ${weather.pressure} hPa
                        📝 Weer: ${getWeatherDescription(weather.weathercode)}
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
