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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.bumptech.glide.Glide
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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
    private val sleepTimerHandler = Handler(Looper.getMainLooper())
    private var sleepTimerRunnable: Runnable? = null
    private var sleepTimerMinutes: Int = 0
    private val recentlyPlayedStations = mutableListOf<RadioStation>()
    private val sharedPreferences by lazy { getSharedPreferences("radio_prefs", Context.MODE_PRIVATE) }
    private val gson = Gson()

    fun getCurrentStation(): RadioStation? = currentStation

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        isPlayingLiveData.postValue(false)
        loadRecentlyPlayed()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}, stations.size=${radioStations.size}, currentIndex=$currentStationIndex")
        when (intent?.action) {
            ACTION_PLAY -> playRadio()
            ACTION_PAUSE -> pauseRadio()
            ACTION_NEXT -> {
                Log.d(TAG, "ACTION_NEXT: currentIndex=$currentStationIndex, stations.size=${radioStations.size}")
                playNextStation()
            }
            ACTION_PREVIOUS -> {
                Log.d(TAG, "ACTION_PREVIOUS: currentIndex=$currentStationIndex, stations.size=${radioStations.size}")
                playPreviousStation()
            }
            ACTION_STOP -> stopRadio()
            ACTION_SET_SLEEP_TIMER -> {
                val minutes = intent.getIntExtra(EXTRA_SLEEP_TIMER_MINUTES, 0)
                setSleepTimer(minutes)
            }
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

    fun setStationWithoutPlay(station: RadioStation) {
        currentStation = station
        currentStationIndex = radioStations.indexOf(station)
    }

    fun changeStation(station: RadioStation) {
        currentStation = station
        currentStationIndex = radioStations.indexOf(station)
        addToRecentlyPlayed(station)
        if (isPlaying()) {
            player?.stop()
            player?.clearMediaItems()
            val mediaItem = MediaItem.fromUri(station.streamUrl)
            player?.setMediaItem(mediaItem)
            player?.prepare()
            player?.play()
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
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
        audioSessionIdLiveData.postValue(audioSessionId)
    }

    fun playRadio() {
        if (currentStation == null) {
            return
        }
        if (!requestAudioFocus()) {
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
        currentStationIndex = radioStations.indexOf(station)
        addToRecentlyPlayed(station)
        playRadio()
    }

    fun setRadioStations(stations: List<RadioStation>) {
        radioStations = stations
    }

    fun isPlaying(): Boolean {
        return player?.isPlaying ?: false
    }

    private fun playNextStation() {
        Log.d(TAG, "playNextStation: before - currentIndex=$currentStationIndex, stations.size=${radioStations.size}")
        if (radioStations.isNotEmpty()) {
            currentStationIndex = (currentStationIndex + 1) % radioStations.size
            Log.d(TAG, "playNextStation: after - currentIndex=$currentStationIndex")
            setStation(radioStations[currentStationIndex])
        }
    }

    private fun playPreviousStation() {
        Log.d(TAG, "playPreviousStation: before - currentIndex=$currentStationIndex, stations.size=${radioStations.size}")
        if (radioStations.isNotEmpty()) {
            currentStationIndex = (currentStationIndex - 1 + radioStations.size) % radioStations.size
            Log.d(TAG, "playPreviousStation: after - currentIndex=$currentStationIndex")
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

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentStation?.name ?: "Radio Station")
            .setContentText(if (isPlaying()) "Воспроизводится" else "На паузе")
            .setSmallIcon(R.mipmap.ic_launcher)
            .addAction(androidx.media3.session.R.drawable.media3_icon_previous, "Previous", prevPendingIntent)
            .addAction(playPauseIcon, if (isPlaying()) "Pause" else "Play", if (isPlaying()) pausePendingIntent else playPendingIntent)
            .addAction(androidx.media3.session.R.drawable.media3_icon_next, "Next", nextPendingIntent)
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

    fun setSleepTimer(minutes: Int) {
        cancelSleepTimer()

        if (minutes <= 0) return

        sleepTimerMinutes = minutes
        sleepTimerRunnable = Runnable {
            stopRadio()
            showSleepTimerNotification()
        }

        val delayMillis = minutes * 60 * 1000L
        sleepTimerHandler.postDelayed(sleepTimerRunnable!!, delayMillis)

        showSleepTimerNotification()
    }

    private fun cancelSleepTimer() {
        sleepTimerRunnable?.let {
            sleepTimerHandler.removeCallbacks(it)
            sleepTimerRunnable = null
        }
        cancelSleepTimerNotification()
    }

    private fun showSleepTimerNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Таймер сна")
            .setContentText("Радио выключится через $sleepTimerMinutes минут")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun cancelSleepTimerNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID + 1)
    }

    fun addToRecentlyPlayed(station: RadioStation) {
        if (recentlyPlayedStations.contains(station)) {
            recentlyPlayedStations.remove(station)
        }
        recentlyPlayedStations.add(0, station)
        if (recentlyPlayedStations.size > 3) {
            recentlyPlayedStations.removeAt(recentlyPlayedStations.size - 1)
        }
        saveRecentlyPlayed()
    }

    fun getRecentlyPlayedStations(): List<RadioStation> {
        return recentlyPlayedStations
    }

    private fun saveRecentlyPlayed() {
        val recentlyPlayedJson = gson.toJson(recentlyPlayedStations)
        sharedPreferences.edit().putString("recently_played", recentlyPlayedJson).apply()
    }

    private fun loadRecentlyPlayed() {
        val recentlyPlayedJson = sharedPreferences.getString("recently_played", null)
        if (recentlyPlayedJson != null) {
            val typeToken = object : TypeToken<List<RadioStation>>() {}.type
            try {
                val loadedList: List<RadioStation> = gson.fromJson(recentlyPlayedJson, typeToken)
                recentlyPlayedStations.addAll(loadedList)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading recently played stations: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        abandonAudioFocus()
        cancelSleepTimer()
        player?.release()
        player = null
    }

    companion object {
        const val ACTION_SET_SLEEP_TIMER = "com.example.chap.ACTION_SET_SLEEP_TIMER"
        const val EXTRA_SLEEP_TIMER_MINUTES = "sleep_timer_minutes"
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
