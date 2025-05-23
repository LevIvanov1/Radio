package com.example.chap

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Bundle
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var sharedPreferences: SharedPreferences
    var musicService: MusicService? = null
        private set  // Сделаем сеттер приватным

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNavigationView = findViewById(R.id.bottomNavView)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        bottomNavigationView.setupWithNavController(navController)

        // Запускаем MusicService
        Intent(this, MusicService::class.java).also { intent ->
            startService(intent)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        unbindService(serviceConnection) // Отвязываем сервис
    }


    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    private fun applyTheme() {
        if (!::sharedPreferences.isInitialized) {
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        }

        val themeMode = sharedPreferences.getString("theme_preference", "system")

        when (themeMode) {
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }
}