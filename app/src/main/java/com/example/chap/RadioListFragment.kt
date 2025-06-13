package com.example.chap

import RadioStation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
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
    private lateinit var viewModel: RadioViewModel
    private var musicService: MusicService? = null
    private var isServiceBound = false
    private var allStations: List<RadioStation> = emptyList()
    private lateinit var searchEditText: EditText
    private lateinit var clearSearchButton: ImageButton
    private var currentAdapter: RadioListAdapter? = null
    private var filteredStations: List<RadioStation> = emptyList()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            musicService?.setRadioStations(allStations)
            isServiceBound = true
            setupRecyclerView()
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
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        searchEditText = view.findViewById(R.id.searchEditText)
        clearSearchButton = view.findViewById(R.id.clearSearchButton)

        allStations = loadRadioStationsFromAssets()
        viewModel.setRadioStations(allStations)
        filteredStations = allStations

        Intent(requireContext(), MusicService::class.java).also { intent ->
            requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(searchEditText.text.toString())
                true
            } else {
                false
            }
        }

        searchEditText.setOnFocusChangeListener { _, hasFocus ->
            clearSearchButton.isVisible = hasFocus && searchEditText.text.isNotEmpty()
        }

        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                clearSearchButton.isVisible = s?.isNotEmpty() == true
                performSearch(s.toString())
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        clearSearchButton.setOnClickListener {
            searchEditText.text.clear()
            performSearch("")
        }
    }

    private fun performSearch(query: String) {
        filteredStations = if (query.isNotEmpty()) {
            allStations.filter { it.name.contains(query, ignoreCase = true) }
        } else {
            allStations
        }
        updateRecyclerView()
    }

    private fun setupRecyclerView() {
        val recentlyPlayedStations = musicService?.getRecentlyPlayedStations() ?: emptyList()
        val combinedList = mutableListOf<RadioStation>()

        if (recentlyPlayedStations.isNotEmpty()) {
            combinedList.add(RadioStation("Недавно прослушанные", "", ""))
            combinedList.addAll(recentlyPlayedStations)
        }

        if (recentlyPlayedStations.isNotEmpty()) {
            combinedList.add(RadioStation("Все станции", "", ""))
        }
        combinedList.addAll(filteredStations)

        currentAdapter = RadioListAdapter(combinedList, { position ->
            val selectedStation = combinedList[position]
            if (selectedStation.imageUrl.isEmpty()) {
                return@RadioListAdapter
            }

            val originalIndex = allStations.indexOfFirst { it.streamUrl == selectedStation.streamUrl }

            musicService?.let { service ->
                val isSameStationPlaying = service.isPlaying() &&
                        service.getCurrentStation()?.streamUrl == selectedStation.streamUrl

                if (!isSameStationPlaying) {
                    if (service.isPlaying()) {
                        service.changeStation(selectedStation)
                    } else {
                        service.setRadioStations(allStations)
                        service.setStation(selectedStation)
                    }
                }
            }
            findNavController().navigate(
                R.id.action_radioListFragment_to_listenFragment,
                Bundle().apply { putInt("SELECTED_POSITION", originalIndex) }
            )
        }, musicService, { station ->
            toggleFavorite(station)
        })

        recyclerView.adapter = currentAdapter

        musicService?.getIsPlayingLiveData()?.observe(viewLifecycleOwner) { _ ->
            recyclerView.adapter?.notifyDataSetChanged()
        }
    }

    private fun updateRecyclerView() {
        val recentlyPlayedStations = musicService?.getRecentlyPlayedStations() ?: emptyList()
        val combinedList = mutableListOf<RadioStation>()

        if (recentlyPlayedStations.isNotEmpty()) {
            combinedList.add(RadioStation("Недавно прослушанные", "", ""))
            combinedList.addAll(recentlyPlayedStations)
        }

        val allStationsHeaderAdded = recentlyPlayedStations.isNotEmpty()
        if (allStationsHeaderAdded) {
            combinedList.add(RadioStation("Все станции", "", ""))
        }

        filteredStations.forEach { filteredStation ->
            val isFavorite = musicService?.getFavoriteStations()?.contains(filteredStation) ?: false
            val index = combinedList.indexOfFirst { it.streamUrl == filteredStation.streamUrl }
            if (index != -1) {
                combinedList[index] = combinedList[index].copy(isFavorite = isFavorite)
            } else {
                combinedList.add(filteredStation.copy(isFavorite = isFavorite))
            }
        }

        currentAdapter?.updateData(combinedList)
    }

    private fun toggleFavorite(station: RadioStation) {
        musicService?.toggleFavorite(station)

        val favoriteStations = musicService?.getFavoriteStations()
        allStations.forEach { st ->
            st.isFavorite = favoriteStations?.contains(st) ?: false
        }
        filteredStations.forEach { st ->
            st.isFavorite = favoriteStations?.contains(st) ?: false
        }
        currentAdapter?.notifyDataSetChanged()
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
            return emptyList()
        }

        val gson = Gson()
        val typeToken = object : TypeToken<List<RadioStation>>() {}.type
        return gson.fromJson<List<RadioStation>>(jsonString, typeToken)
    }
    override fun onResume() {
        super.onResume()
        updateRadioStationsWithFavoriteStatus()
    }

    private fun updateRadioStationsWithFavoriteStatus() {
        musicService?.let { service ->
            val favoriteStations = service.getFavoriteStations()
            allStations.forEach { station ->
                station.isFavorite = favoriteStations.any { it.streamUrl == station.streamUrl }
            }
            filteredStations.forEach { station ->
                station.isFavorite = favoriteStations.any { it.streamUrl == station.streamUrl }
            }
            updateRecyclerView()
        }
    }
}

class RadioListAdapter(
    private var stations: List<RadioStation>,
    private val onItemClick: (Int) -> Unit,
    private val musicService: MusicService?,
    private val onFavoriteClick: (RadioStation) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val TYPE_STATION = 0
    private val TYPE_HEADER = 1

    inner class RadioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.stationImageView) ?: error("stationImageView not found")
        val nameTextView: TextView = itemView.findViewById(R.id.stationNameTextView) ?: error("stationNameTextView not found")
        val playButton: ImageButton = itemView.findViewById(R.id.playButton) ?: error("playButton not found")
        val favoriteButton: ImageButton = itemView.findViewById(R.id.favoriteButton) ?: error("favoriteButton not found")
    }

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val headerTextView: TextView = itemView.findViewById(R.id.headerTextView) ?: error("headerTextView not found")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val itemView = LayoutInflater.from(parent.context)
        if (viewType == TYPE_STATION) {
            return RadioViewHolder(itemView.inflate(R.layout.radio_list_item, parent, false))
        } else {
            return HeaderViewHolder(itemView.inflate(R.layout.header_list_item, parent, false))
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (stations[position].imageUrl.isEmpty()) {
            TYPE_HEADER
        } else {
            TYPE_STATION
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val station = stations[position]
        if (holder is RadioViewHolder) {
            holder.nameTextView.text = station.name
            Glide.with(holder.itemView.context)
                .load(station.imageUrl)
                .into(holder.imageView)

            musicService?.let { service ->
                val isCurrentStationPlaying = service.isPlaying() &&
                        service.getCurrentStation()?.streamUrl == station.streamUrl

                holder.playButton.setImageResource(
                    if (isCurrentStationPlaying) R.drawable.ic_pause else androidx.media3.session.R.drawable.media3_icon_play
                )
            }

            holder.playButton.setOnClickListener {
                musicService?.let { service ->
                    if (service.isPlaying() && service.getCurrentStation()?.streamUrl == station.streamUrl) {
                        service.pauseRadio()
                        holder.playButton.setImageResource(androidx.media3.session.R.drawable.media3_icon_play)
                    } else {
                        service.setStation(station)
                        holder.playButton.setImageResource(R.drawable.ic_pause)
                        notifyDataSetChanged()
                    }
                }
            }

            holder.itemView.setOnClickListener {
                onItemClick(position)
            }

            holder.favoriteButton.setImageResource(if (station.isFavorite) androidx.media3.session.R.drawable.media3_icon_star_filled else androidx.media3.session.R.drawable.media3_icon_star_unfilled)

            holder.favoriteButton.setOnClickListener {
                onFavoriteClick(station)
                holder.favoriteButton.setImageResource(if (station.isFavorite) androidx.media3.session.R.drawable.media3_icon_star_unfilled else androidx.media3.session.R.drawable.media3_icon_star_filled)
            }
        } else if (holder is HeaderViewHolder) {
            holder.headerTextView.text = station.name
        }
    }

    override fun getItemCount(): Int = stations.size

    fun updateData(newStations: List<RadioStation>) {
        stations = newStations
        notifyDataSetChanged()
    }

    fun getStations(): List<RadioStation> = stations
}