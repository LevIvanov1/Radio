import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.chap.R

class RadioPagerAdapter(private val stations: List<RadioStation>) :
    RecyclerView.Adapter<RadioPagerAdapter.RadioViewHolder>() {

    inner class RadioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.radioImageView)
        val nameTextView: TextView = itemView.findViewById(R.id.radioNameTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RadioViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.radio_station_item, parent, false)
        return RadioViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: RadioViewHolder, position: Int) {
        val station = stations[position]
        holder.nameTextView.text = station.name

        Glide.with(holder.itemView.context)
            .load(station.imageUrl)
            .into(holder.imageView)
    }

    override fun getItemCount(): Int {
        return stations.size
    }
}