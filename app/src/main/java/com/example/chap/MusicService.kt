package com.example.chap

import RadioStation
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player

class MusicService : Service() {

    private var player: ExoPlayer? = null
    private var currentStation: RadioStation? = null
    private var isPlaying = false

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onBind(intent: Intent): IBinder {
        return MusicBinder()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> play()
            ACTION_PAUSE -> pause()
            else -> Log.w(TAG, "Unknown intent action")
        }
        return START_STICKY
    }

    fun play() {
        if (currentStation == null) {
            Log.w(TAG, "No station to play")
            return
        }

        if (player == null) {
            player = ExoPlayer.Builder(this).build()
            player?.addListener(playerListener) // Добавляем слушателя
        }

        val mediaItem = MediaItem.fromUri(currentStation!!.streamUrl)
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play()
        isPlaying = true
        showNotification()
    }

    fun pause() {
        player?.pause()
        isPlaying = false
        showNotification()
    }

    fun stop() {
        player?.stop()
        player?.release()
        player = null
        isPlaying = false
    }

    fun setStation(station: RadioStation) {
        currentStation = station
        stop()
        play()
    }

    fun isPlaying(): Boolean {
        return player?.isPlaying ?: false
    }


    private val playerListener = object : Player.Listener { // Слушатель ExoPlayer
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_IDLE -> {
                    isPlaying = false
                    showNotification()
                }
                Player.STATE_ENDED -> {
                    isPlaying = false
                    showNotification()
                }
                Player.STATE_READY -> {
                    // Player is ready to play
                }
                Player.STATE_BUFFERING -> {
                    // Player is buffering
                }
            }
        }

        override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
            Log.e(TAG, "ExoPlayer error: ${error.message}, error code: ${error.errorCodeName}")
            isPlaying = false
            showNotification()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@MusicService.isPlaying = isPlaying
            showNotification()
        }
    }

    private fun showNotification() {
        // ... (Твой существующий код для создания уведомления) ...
    }

    override fun onDestroy() {
        super.onDestroy()
        stop()
    }

    companion object {
        private const val TAG = "MusicService"
        const val CHANNEL_ID = "radio_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY = "com.example.chap.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.chap.ACTION_PAUSE"
    }
}