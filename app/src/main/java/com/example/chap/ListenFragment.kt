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
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException
import java.nio.charset.Charset

class ListenFragment : Fragment() {

    private lateinit var viewPager: ViewPager2
    private lateinit var playPauseButton: Button
    private lateinit var radioStations: List<RadioStation>
    private var musicService: MusicService? = null
    private var isServiceBound = false
    private var currentStationIndex = 0
    private var isPlaying = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isServiceBound = true
            updatePlayPauseButton()
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
        radioStations = loadRadioStationsFromAssets() // Call the function here

        val adapter = RadioPagerAdapter(radioStations)
        viewPager.adapter = adapter

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playPauseButton.setOnClickListener {
            if (isServiceBound && musicService != null) {
                if (musicService!!.isPlaying()) {
                    musicService?.pause()
                } else {
                    if (radioStations.isNotEmpty()) {
                        musicService?.setStation(radioStations[currentStationIndex])
                    }
                }
                updatePlayPauseButton()
            } else {
                Log.w("ListenFragment", "Service not bound yet")
            }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentStationIndex = position
                if (isServiceBound && radioStations.isNotEmpty()) {
                    musicService?.setStation(radioStations[position])
                    updatePlayPauseButton()
                }
            }
        })
    }

    private fun initializePlayer(streamUrl: String) {
        val player = ExoPlayer.Builder(requireContext()).build() // Изменено на val
        //val mediaItem = MediaItem.fromUri(streamUrl.toUri())
        val mediaItem = MediaItem.Builder().setUri(streamUrl.toUri()).build() // Более новый способ создания MediaItem
        player.setMediaItem(mediaItem)

        player.prepare()
        player.play()
        isPlaying = true
        playPauseButton.text = "Pause"

        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                    when (player.playbackState) {
                        Player.STATE_IDLE -> {
                            isPlaying = false
                            playPauseButton.text = "Play"
                        }
                        Player.STATE_ENDED -> {
                            isPlaying = false
                            playPauseButton.text = "Play"
                        }
                        Player.STATE_READY -> {
                            // Player is ready to play
                        }
                        Player.STATE_BUFFERING -> {
                            // Player is buffering
                        }
                    }
                }
                if (events.contains(Player.EVENT_PLAYER_ERROR)) {
                    Log.e("ExoPlayer", "Playback error: ${player.playerError}")
                    isPlaying = false
                    playPauseButton.text = "Play"
                }
            }

            // Добавляем обработку ошибок
            override fun onPlayerError(error: PlaybackException) {
                Log.e("ExoPlayer", "Playback error: ${error.message}, error code: ${error.errorCodeName}")
                isPlaying = false
                playPauseButton.text = "Play"
            }
        })
    }


    private fun releasePlayer() {
        if (isServiceBound) {
            musicService?.pause()
            updatePlayPauseButton()
        }
    }

    private fun updatePlayPauseButton() {
        if (isServiceBound && musicService != null) {
            isPlaying = musicService!!.isPlaying()
            playPauseButton.text = if (isPlaying) "Pause" else "Play"
        } else {
            playPauseButton.text = "Play"
        }
    }

    private fun loadRadioStationsFromAssets(): List<RadioStation> {
        val jsonString = try {
            val inputStream = requireContext().assets.open("stations.json")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            String(buffer, Charset.forName("UTF-8"))
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("ListenFragment", "Error reading stations.json: ${e.message}")
            return emptyList()
        }

        val gson = Gson()
        val typeToken = object : TypeToken<List<RadioStation>>() {}.type
        val stations = gson.fromJson<List<RadioStation>>(jsonString, typeToken)

        Log.d("ListenFragment", "Loaded ${stations.size} radio stations")

        return stations
    }
}