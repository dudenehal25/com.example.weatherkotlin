package com.example.weatherkotlin

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.weatherkotlin.models.WeatherResponse
import com.example.weatherkotlin.netwok.WeatherService
import com.google.android.gms.location.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import retrofit.*
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient
    private var mLatitude: Double = 0.0

    // A global variable for Current Longitude
    private var mLongitude: Double = 0.0

    private var mProgressBar:Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this@MainActivity)


        if (!isLocationEnabled()) {
            Toast.makeText(this, "please turn on the Location", Toast.LENGTH_SHORT).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withContext(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {
                            requestLocationData()
                        }
                        if (report.isAnyPermissionPermanentlyDenied)
                            Toast.makeText(
                                this@MainActivity,
                                "not given permission",
                                Toast.LENGTH_SHORT
                            ).show()
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermissions()
                    }
                }).onSameThread().check()
        }
    }

    @SuppressLint("MissingPermission")
    fun requestLocationData() {
        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationProviderClient.requestLocationUpdates(
            mLocationRequest,
            mLocationCallback,
            Looper.myLooper()
        )

    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {

            val mLastLocation: Location = locationResult.lastLocation
            mLatitude = mLastLocation.latitude
            Log.e("Current Latitude", "$mLatitude")
            mLongitude = mLastLocation.longitude
            Log.e("Current Longitude", "$mLongitude")
            getLocationWeatherDetails(mLatitude , mLongitude)

        }
    }

    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton(
                "GO TO SETTINGS"
            ) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }.show()
    }


    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )

    }


    private fun getLocationWeatherDetails(latitude:Double , longitude:Double) {
        if (Constants.isNetworkAvailable(this)) {
            val retrofit: Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service = retrofit.create(WeatherService::class.java)

            val listcall:Call<WeatherResponse> = service.getWeather(latitude , longitude,Constants.METRIC_UNIT , Constants.APP_ID)

            showCustomdialog()
            listcall.enqueue(object : Callback<WeatherResponse>{

                override fun onFailure(t: Throwable?) {
                    hidePRogressbAr()
                    Log.e("Errorr" , t!!.message.toString())

                }

                override fun onResponse(response: Response<WeatherResponse>?, retrofit: Retrofit?) {
                    if (response!!.isSuccess){
                        hidePRogressbAr()
                        val weatherList = response.body()
                        setupUI(weatherList)
                        Log.i("Response Result", "$weatherList")
                    }
                    else{
                        val rc = response.code()
                        when(rc){
                            400 -> {Log.e("Error 400", "Bad COnnection")}
                            404 -> {Log.e("Error 404", "Not Found")}
                            else -> {Log.e("generic" ,"generic error")}
                        }
                    }
                }
            })
        }
        else {
            Toast.makeText(this, "No Internet!!!!", Toast.LENGTH_SHORT).show()
        }

    }

    private fun setupUI(weatherList : WeatherResponse){
        for (i in weatherList.weather.indices){
            Log.e("hey" ,weatherList.weather.toString())
            tvWeatherTitle.text = weatherList.weather[i].main
            tvWeatherDescription.text = weatherList.weather[i].description

            tvCurrentTemp.text = weatherList.main.temp.toString() + "°C"
            tvHumdityPercent.text = weatherList.main.humidity.toString() + "%"

            tvMaxTemp.text = weatherList.main.temp_max.toString() + "°C"
            tvfeelslike.text = weatherList.main.feels_like.toString()+"°C"
            tvMinTemp.text = weatherList.main.temp_min.toString()+"°C"

            tvWindspeed.text = weatherList.wind.speed.toString() + "km/hr"
            tvWindValue.text = weatherList.wind.deg.toString() + "°"

            tvSunrise.text = timex(weatherList.sys.sunrise)
            tvSunset.text = timex(weatherList.sys.sunset)

            tv_name.text = weatherList.name
            tv_country.text = weatherList.sys.country

            // Here we update the main icon
            when (weatherList.weather[i].icon) {
                "01d" -> iv_main.setImageResource(R.drawable.sunny)
                "02d" -> iv_main.setImageResource(R.drawable.cloud)
                "03d" -> iv_main.setImageResource(R.drawable.cloud)
                "04d" -> iv_main.setImageResource(R.drawable.cloud)
                "04n" -> iv_main.setImageResource(R.drawable.cloud)
                "10d" -> iv_main.setImageResource(R.drawable.rain)
                "11d" -> iv_main.setImageResource(R.drawable.storm)
                "13d" -> iv_main.setImageResource(R.drawable.snowflake)
                "01n" -> iv_main.setImageResource(R.drawable.cloud)
                "02n" -> iv_main.setImageResource(R.drawable.cloud)
                "03n" -> iv_main.setImageResource(R.drawable.cloud)
                "10n" -> iv_main.setImageResource(R.drawable.cloud)
                "11n" -> iv_main.setImageResource(R.drawable.rain)
                "13n" -> iv_main.setImageResource(R.drawable.snowflake)
            }


        }
    }

    private fun showCustomdialog(){
     mProgressBar = Dialog(this)

        mProgressBar!!.setContentView(R.layout.progress_activity)
        mProgressBar!!.show()

    }
    private fun hidePRogressbAr(){
        mProgressBar!!.dismiss()
    }
    private fun timex(timex:Long) : String?{
        val date = Date(timex)
        val simpleDateFormat = SimpleDateFormat("HH:mm")
        simpleDateFormat.timeZone = TimeZone.getDefault()
        return simpleDateFormat.format(date)
    }
}