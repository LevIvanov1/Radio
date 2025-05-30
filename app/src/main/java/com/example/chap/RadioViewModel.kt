package com.example.chap

import RadioStation
import androidx.lifecycle.ViewModel

class RadioViewModel : ViewModel() {
    private var radioStations: List<RadioStation> = emptyList()
    private var currentStationIndex = 0

    fun setRadioStations(stations: List<RadioStation>) {
        radioStations = stations
    }

    fun getRadioStations(): List<RadioStation> = radioStations

    fun setCurrentStationIndex(index: Int) {
        currentStationIndex = index
    }

    fun getCurrentStationIndex(): Int = currentStationIndex

    fun getCurrentStation(): RadioStation? {
        return if (radioStations.isNotEmpty()) radioStations[currentStationIndex] else null
    }
}