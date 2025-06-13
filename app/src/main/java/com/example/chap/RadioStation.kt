import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class RadioStation(
    val name: String,
    val imageUrl: String,
    val streamUrl: String,
    var isFavorite: Boolean = false
) : Parcelable
