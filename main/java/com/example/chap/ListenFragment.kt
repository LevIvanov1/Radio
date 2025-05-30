package com.example.chap

import RadioPagerAdapter
import RadioStation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.viewpager2.widget.ViewPager2
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException

class ListenFragment : Fragment() {

    private lateinit var viewPager: ViewPager2
    private lateinit var playPauseButton: Button
    private lateinit var radioStations: List<RadioStation>
    private var musicService: MusicService? = null
    private var isServiceBound = false
    private var currentStationIndex = 0
    private var isViewCreated = false
    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            currentStationIndex = position
            if (isServiceBound && radioStations.isNotEmpty()) {
                musicService?.setStation(radioStations[position])
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            musicService?.setRadioStations(radioStations)
            isServiceBound = true

            if (isViewCreated) {
                updatePlayPauseButton(musicService?.isPlaying() ?: false)
                musicService?.getIsPlayingLiveData()?.observe(viewLifecycleOwner, Observer { isPlaying ->
                    updatePlayPauseButton(isPlaying)
                })
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isServiceBound = false
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(requireContext(), MusicService::class.java).also { intent ->
            requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isServiceBound) {
            requireContext().unbindService(serviceConnection)
            isServiceBound = false
            musicService = null
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_listen, container, false)
        viewPager = view.findViewById(R.id.viewPager)
        playPauseButton = view.findViewById(R.id.playPauseButton)
        radioStations = loadRadioStationsFromAssets()

        val adapter = RadioPagerAdapter(radioStations)
        viewPager.adapter = adapter

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isViewCreated = true

        playPauseButton.setOnClickListener {
            if (isServiceBound && musicService != null) {
                val isPlaying = musicService?.isPlaying() ?: false
                if (isPlaying) {
                    musicService?.pauseRadio()
                } else {
                    if (radioStations.isNotEmpty()) {
                        musicService?.setStation(radioStations[currentStationIndex])
                    }
                }
            } else {
                Log.w("ListenFragment", "Service not bound yet")
            }
        }

        viewPager.registerOnPageChangeCallback(pageChangeCallback)

        if (isServiceBound && musicService != null) {
            musicService?.getIsPlayingLiveData()?.observe(viewLifecycleOwner, Observer { isPlaying ->
                updatePlayPauseButton(isPlaying)
            })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isViewCreated = false
        viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        playPauseButton.text = if (isPlaying) "Pause" else "Play"
    }

    private fun loadRadioStationsFromAssets(): List<RadioStation> {
        val jsonString = try {
            val inputStream = requireContext().assets.open("stations.json")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            String(buffer, Charsets.UTF_8)
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("ListenFragment", "Error reading stations.json: ${e.message}")
            return emptyList()
        }

        val gson = Gson()
        val typeToken = object : TypeToken<List<RadioStation>>() {}.type
        return gson.fromJson<List<RadioStation>>(jsonString, typeToken)
    }
}