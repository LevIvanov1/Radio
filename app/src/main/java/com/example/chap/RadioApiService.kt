
import retrofit2.http.GET
import retrofit2.Call

interface RadioApiService {
    @GET("stations.json")
    fun getRadioStations(): Call<List<RadioStation>>
}