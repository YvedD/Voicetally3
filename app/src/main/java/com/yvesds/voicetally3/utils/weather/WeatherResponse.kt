package com.yvesds.voicetally3.utils.weather

data class WeatherResponse(
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