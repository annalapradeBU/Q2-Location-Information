package com.example.q2locationinformation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import java.util.*
// https://developers.google.com/android/reference/com/google/android/gms/maps/model/BitmapDescriptorFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory


class MainActivity : ComponentActivity() {

    // the "phone", talks to the GPS hardware
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    // helps with how the app want the data (high accuracy, power saving, etc.)
    private lateinit var locationRequest: LocationRequest
    // "messenger" for the data
    private lateinit var locationCallback: LocationCallback

    // state variables that UI will observe
    private var userLatLng by mutableStateOf<LatLng?>(null)
    private var addressText by mutableStateOf("Locating...")
    //ch 58
    private val customMarkers = mutableStateListOf<LatLng>()

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // high-accuracy settings configuration for location updates
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).apply {
            setMinUpdateIntervalMillis(5000)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val newLatLng = LatLng(location.latitude, location.longitude)
                    userLatLng = newLatLng

                    // requirement 4: geocoding logic
                    val geocoder = Geocoder(this@MainActivity, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    addressText = addresses?.firstOrNull()?.getAddressLine(0) ?: "Address not found"
                }
            }
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MapScreen(userLatLng, addressText, customMarkers)
                }
            }
        }
    }

    // manage lifecycle to start/stop GPS to save battery
    override fun onResume(){
        super.onResume()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)

    }

    private fun startLocationUpdates(){
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}


@Composable
fun MapScreen(userPos: LatLng?, address: String, customMarkers: MutableList<LatLng>) {
    val context = LocalContext.current

    // requirement 1: request permission
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) { /* activity onResume will handle the update start, yay! */ }
    }

    // auto launch the permission
    // ch 37 ish
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // requirement 2: display google map
    val cameraPositionState = rememberCameraPositionState {
        // initially at BU, teehee
        position = CameraPosition.fromLatLngZoom(LatLng(42.3505, -71.1054), 15f)
    }

    // requirement 2: move camera to user when location found
    LaunchedEffect(userPos) {
        userPos?.let {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(it, 15f)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            onMapClick = { customMarkers.add(it) } // requirement 5: allow user to place custom markers by tapping the map
        ) {
            // requirement 3: marker at user location
            userPos?.let {
                Marker(
                    state = MarkerState(position = it),
                    title = "Current Location",
                    snippet = address
                )
            }

            // draw custom markers
            customMarkers.forEach { pos ->
                Marker(
                    state = MarkerState(position = pos),
                    // Use BitmapDescriptorFactory to change the hue
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                    title = "Saved Point",
                    snippet = "Tap to view"
                )
            }

        }

        // requirement 4: address display
        Card(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp, start = 20.dp, end = 20.dp).fillMaxWidth()
        ) {
            Text(
                text = address,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // button to trigger permission if needed

        Button(
            onClick = { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
        ) {
            Text("Grant Location Permission")
        }

    }


}

