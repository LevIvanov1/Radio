package com.example.chap

import RadioStation
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.bumptech.glide.Glide
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player

class MusicService : Service(), AudioManager.OnAudioFocusChangeListener {

    private var player: ExoPlayer? = null
    private var currentStation: RadioStation? = null
    private val isPlayingLiveData = MutableLiveData<Boolean>()
    private val audioSessionIdLiveData = MutableLiveData<Int?>()
    private val binder = MusicBinder()
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isAudioFocusGranted = false
    private var radioStations: List<RadioStation> = emptyList()
    private var currentStationIndex = 0

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        isPlayingLiveData.postValue(false)
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> playRadio()
            ACTION_PAUSE -> pauseRadio()
            ACTION_NEXT -> playNextStation()
            ACTION_PREVIOUS -> playPreviousStation()
            ACTION_STOP -> stopRadio()
        }
        return START_STICKY
    }

    private fun requestAudioFocus(): Boolean {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(this)
            .build()

        val result = audioManager?.requestAudioFocus(audioFocusRequest!!)
        isAudioFocusGranted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return isAudioFocusGranted
    }

    private fun abandonAudioFocus() {
        if (isAudioFocusGranted) {
            audioManager?.abandonAudioFocusRequest(audioFocusRequest!!)
            isAudioFocusGranted = false
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Resume playback
                player?.volume = 1.0f
                if (player?.isPlaying == false) {
                    playRadio()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                pauseRadio()
                stopRadio()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pauseRadio()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (player?.isPlaying == true) {
                    player?.volume = 0.1f
                }
            }
        }
    }

    fun initializePlayer() {
        player = ExoPlayer.Builder(this).build().apply {
            addListener(playerListener)
        }

        val audioSessionId = player?.audioSessionId
        Log.d("Equalizer", "AudioSessionId in initializePlayer: $audioSessionId")
        audioSessionIdLiveData.postValue(audioSessionId)
    }

    fun playRadio() {
        if (currentStation == null) {
            Log.w(TAG, "No station to play")
            return
        }

        if (!requestAudioFocus()) {
            Log.w(TAG, "Audio focus not granted")
            return
        }

        if (player == null) {
            initializePlayer()
        } else {
            player?.stop()
            player?.clearMediaItems()
        }

        val mediaItem = MediaItem.fromUri(currentStation!!.streamUrl)
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play()
        isPlayingLiveData.postValue(true)
        startRadioForeground()
        updateNotification()
    }

    fun pauseRadio() {
        player?.pause()
        isPlayingLiveData.postValue(false)
        updateNotification()
    }

    private fun stopRadio() {
        abandonAudioFocus()

        player?.stop()
        player?.clearMediaItems()
        player?.release()
        player = null
        isPlayingLiveData.postValue(false)
        stopForeground(true)
        stopSelf()
    }

    fun setStation(station: RadioStation) {
        currentStation = station
        playRadio()
    }

    fun setRadioStations(stations: List<RadioStation>) {
        radioStations = stations
    }

    fun isPlaying(): Boolean {
        return player?.isPlaying ?: false
    }

    private fun playNextStation() {
        if (radioStations.isNotEmpty()) {
            currentStationIndex = (currentStationIndex + 1) % radioStations.size
            setStation(radioStations[currentStationIndex])
        }
    }

    private fun playPreviousStation() {
        if (radioStations.isNotEmpty()) {
            currentStationIndex = (currentStationIndex - 1 + radioStations.size) % radioStations.size
            setStation(radioStations[currentStationIndex])
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Radio Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Radio playback controls"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val playIntent = Intent(this, MusicService::class.java).apply { action = ACTION_PLAY }
        val pauseIntent = Intent(this, MusicService::class.java).apply { action = ACTION_PAUSE }
        val nextIntent = Intent(this, MusicService::class.java).apply { action = ACTION_NEXT }
        val prevIntent = Intent(this, MusicService::class.java).apply { action = ACTION_PREVIOUS }
        val stopIntent = Intent(this, MusicService::class.java).apply { action = ACTION_STOP }

        val playPendingIntent = PendingIntent.getService(this, 0, playIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val pausePendingIntent = PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val nextPendingIntent = PendingIntent.getService(this, 2, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val prevPendingIntent = PendingIntent.getService(this, 3, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopPendingIntent = PendingIntent.getService(this, 4, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val playPauseIcon = if (isPlaying()) R.drawable.ic_pause else R.drawable.ic_start
        val stationName = currentStation?.name ?: "Radio Station"

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(stationName)
            .setContentText("Playing...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .addAction(R.drawable.ic_cancel, "Previous", prevPendingIntent)
            .addAction(playPauseIcon, if (isPlaying()) "Pause" else "Play", if (isPlaying()) pausePendingIntent else playPendingIntent)
            .addAction(R.drawable.ic_forward, "Next", nextPendingIntent)
            .addAction(R.drawable.ic_close, "Stop", stopPendingIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2))
            .setOngoing(true)
            .setContentIntent(createContentIntent())

        currentStation?.imageUrl?.let { imageUrl ->
            try {
                val futureTarget = Glide.with(this)
                    .asBitmap()
                    .load(imageUrl)
                    .submit()

                val bitmap = futureTarget.get()
                notificationBuilder.setLargeIcon(bitmap)
                futureTarget.cancel(false)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading notification image: ${e.message}")
            }
        }

        return notificationBuilder.build()
    }

    private fun createContentIntent(): PendingIntent {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun startRadioForeground() {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_IDLE -> {
                    isPlayingLiveData.postValue(false)
                    updateNotification()
                }
                Player.STATE_ENDED -> {
                    isPlayingLiveData.postValue(false)
                    updateNotification()
                }
                Player.STATE_READY -> {
                }
                Player.STATE_BUFFERING -> {
                }
            }
        }

        override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
            Log.e(TAG, "ExoPlayer error: ${error.message}, error code: ${error.errorCodeName}")
            isPlayingLiveData.postValue(false)
            updateNotification()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            isPlayingLiveData.postValue(isPlaying)
            updateNotification()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        abandonAudioFocus()
        player?.release()
        player = null
    }

    companion object {
        private const val TAG = "MusicService"
        const val CHANNEL_ID = "radio_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY = "com.example.chap.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.chap.ACTION_PAUSE"
        const val ACTION_NEXT = "com.example.chap.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.example.chap.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.example.chap.ACTION_STOP"
    }

    fun getIsPlayingLiveData(): MutableLiveData<Boolean> {
        return isPlayingLiveData
    }

    fun getAudioSessionIdLiveData(): MutableLiveData<Int?> {
        return audioSessionIdLiveData
    }
}

