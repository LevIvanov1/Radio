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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class FavoriteFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private var musicService: MusicService? = null
    private var isServiceBound = false
    private lateinit var favoriteStationsAdapter: FavoriteStationsAdapter

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isServiceBound = true

            favoriteStationsAdapter = FavoriteStationsAdapter(emptyList(), { station ->
                toggleFavorite(station)
            }, musicService)

            recyclerView.adapter = favoriteStationsAdapter

            musicService?.getFavoriteStationsLiveData()?.observe(viewLifecycleOwner, Observer { stations ->
                favoriteStationsAdapter.updateData(stations)
            })

            musicService?.getIsPlayingLiveData()?.observe(viewLifecycleOwner, Observer { isPlaying ->
                favoriteStationsAdapter.notifyDataSetChanged()
            })
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isServiceBound = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_favorite, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.favoriteRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        favoriteStationsAdapter = FavoriteStationsAdapter(emptyList(), { station ->
            toggleFavorite(station)
        }, musicService)

        recyclerView.adapter = favoriteStationsAdapter

        Intent(requireContext(), MusicService::class.java).also { intent ->
            requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun toggleFavorite(station: RadioStation) {
        musicService?.toggleFavorite(station)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isServiceBound) {
            requireContext().unbindService(serviceConnection)
            isServiceBound = false
        }
    }
}

class FavoriteStationsAdapter(
    private var stations: List<RadioStation>,
    private val onFavoriteClick: (RadioStation) -> Unit,
    private val musicService: MusicService? = null
) : RecyclerView.Adapter<FavoriteStationsAdapter.FavoriteViewHolder>() {

    inner class FavoriteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.stationImageView)
        val nameTextView: TextView = itemView.findViewById(R.id.stationNameTextView)
        val favoriteButton: ImageButton = itemView.findViewById(R.id.favoriteButton)
        val playButton: ImageButton = itemView.findViewById(R.id.playButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.favorite_list_item, parent, false)
        return FavoriteViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        val station = stations[position]
        holder.nameTextView.text = station.name
        Glide.with(holder.itemView.context)
            .load(station.imageUrl)
            .into(holder.imageView)

        holder.favoriteButton.setImageResource(if (station.isFavorite) androidx.media3.session.R.drawable.media3_icon_star_filled else androidx.media3.session.R.drawable.media3_icon_star_unfilled)

        holder.favoriteButton.setOnClickListener {
            onFavoriteClick(station)
            holder.favoriteButton.setImageResource(if (station.isFavorite) androidx.media3.session.R.drawable.media3_icon_star_unfilled else androidx.media3.session.R.drawable.media3_icon_star_filled)
        }

        holder.playButton.setImageResource(
            if (musicService?.isPlaying() == true && musicService.getCurrentStation()?.streamUrl == station.streamUrl) {
                R.drawable.ic_pause
            } else {
                androidx.media3.session.R.drawable.media3_icon_play
            }
        )

        holder.playButton.setOnClickListener {
            musicService?.let { service ->
                if (service.isPlaying() && service.getCurrentStation()?.streamUrl == station.streamUrl) {
                    service.pauseRadio()
                } else {
                    service.setStation(station)
                }
                notifyItemChanged(position)
            }
        }
    }

    override fun getItemCount(): Int = stations.size

    fun updateData(newStations: List<RadioStation>) {
        stations = newStations
        notifyDataSetChanged()
    }
}