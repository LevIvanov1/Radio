package com.example.chap

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class StatsFragment : Fragment() {

    private lateinit var statsTextView: TextView
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_stats, container, false)
        statsTextView = view.findViewById(R.id.statsTextView)
        sharedPreferences = requireContext().getSharedPreferences("listening_stats", Context.MODE_PRIVATE)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        displayStats()
    }

    private fun displayStats() {
        val stats = loadStats()
        val sb = StringBuilder()
        stats.forEach { (stationName, duration) ->
            val minutes = duration / (60 * 1000)
            sb.append("$stationName: $minutes мин.\n")
        }
        statsTextView.text = if (sb.isNotEmpty()) sb.toString() else "Радио не воспроизводилось ранее"
    }

    private fun loadStats(): Map<String, Long> {
        val gson = Gson()
        val json = sharedPreferences.getString("stats", null)
        return if (json != null) {
            val type = object : TypeToken<Map<String, Long>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } else {
            emptyMap()
        }
    }
}