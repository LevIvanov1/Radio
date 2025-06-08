package com.example.chap

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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException

class RadioListFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchView: SearchView
    private lateinit var viewModel: RadioViewModel
    private var musicService: MusicService? = null
    private var isServiceBound = false
    private lateinit var radioStations: List<RadioStation>
    private var allStations: List<RadioStation> = emptyList()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isServiceBound = true
            setupRecyclerView(allStations)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isServiceBound = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_radio_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity()).get(RadioViewModel::class.java)
        recyclerView = view.findViewById(R.id.radioRecyclerView)
        searchView = view.findViewById(R.id.searchView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        allStations = loadRadioStationsFromAssets()
        viewModel.setRadioStations(allStations)

        setupSearchView()

        Intent(requireContext(), MusicService::class.java).also { intent ->
            requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterStations(newText.orEmpty())
                return true
            }
        })
    }

    private fun filterStations(query: String) {
        (recyclerView.adapter as? RadioListAdapter)?.filter(query)
    }

    private fun setupRecyclerView(stations: List<RadioStation>) {
        radioStations = stations

        val adapter = RadioListAdapter(
            originalStations = stations,
            onPlayPauseClick = { clickedStation ->
                val service = musicService // Copy to a local variable
                if (service?.isPlaying() == true && service.getCurrentStation()?.streamUrl == clickedStation.streamUrl) {
                    service.pauseRadio()
                } else {
                    service?.setStation(clickedStation)
                }
            },
            onItemClick = { clickedStation ->
                val originalPosition = radioStations.indexOf(clickedStation)
                if (originalPosition != -1) {
                    viewModel.setCurrentStationIndex(originalPosition)
                    findNavController().navigate(
                        R.id.action_radioListFragment_to_listenFragment,
                        Bundle().apply { putInt("SELECTED_POSITION", originalPosition) }
                    )
                }
            },
            musicService = musicService
        )

        recyclerView.adapter = adapter

        musicService?.getIsPlayingLiveData()?.observe(viewLifecycleOwner) { _ ->
            adapter.notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isServiceBound) {
            requireContext().unbindService(serviceConnection)
            isServiceBound = false
        }
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

class RadioListAdapter(
    private var originalStations: List<RadioStation>,
    private val onPlayPauseClick: (RadioStation) -> Unit,
    private val onItemClick: (RadioStation) -> Unit,
    private val musicService: MusicService?
) : RecyclerView.Adapter<RadioListAdapter.RadioViewHolder>() {

    private var filteredStations: List<RadioStation> = originalStations

    fun updateStations(newStations: List<RadioStation>) {
        originalStations = newStations
        filteredStations = newStations
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        filteredStations = if (query.isEmpty()) {
            originalStations
        } else {
            originalStations.filter { it.name.contains(query, ignoreCase = true) }
        }
        notifyDataSetChanged()
    }

    inner class RadioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.stationImageView)
        val nameTextView: TextView = itemView.findViewById(R.id.stationNameTextView)
        val playButton: ImageButton = itemView.findViewById(R.id.playButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RadioViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.radio_list_item, parent, false)
        return RadioViewHolder(view)
    }

    override fun onBindViewHolder(holder: RadioViewHolder, position: Int) {
        val station = filteredStations[position]

        holder.nameTextView.text = station.name
        Glide.with(holder.itemView.context)
            .load(station.imageUrl)
            .into(holder.imageView)

        val isPlaying = musicService?.isPlaying() == true && musicService.getCurrentStation()?.streamUrl == station.streamUrl

        holder.playButton.setImageResource(
            if (isPlaying) R.drawable.ic_pause else androidx.media3.session.R.drawable.media3_icon_play
        )

        holder.playButton.setOnClickListener {
            onPlayPauseClick(station)
            notifyDataSetChanged()
        }

        holder.itemView.setOnClickListener {
            onItemClick(station)
        }
    }

    override fun getItemCount() = filteredStations.size
}