package com.fighteam.wannawear.data.remote

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** 위치 권한이 없거나 마지막 위치를 못 가져오면 서울시청 좌표로 대체한다. */
object LocationProvider {
    private const val DEFAULT_LAT = 37.5665
    private const val DEFAULT_LNG = 126.9780

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun getCurrentLatLng(context: Context): Pair<Double, Double> {
        if (!hasLocationPermission(context)) return DEFAULT_LAT to DEFAULT_LNG

        return try {
            suspendCancellableCoroutine { cont ->
                val client = LocationServices.getFusedLocationProviderClient(context)
                @Suppress("MissingPermission")
                client.lastLocation
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            cont.resume(location.latitude to location.longitude)
                        } else {
                            cont.resume(DEFAULT_LAT to DEFAULT_LNG)
                        }
                    }
                    .addOnFailureListener {
                        cont.resume(DEFAULT_LAT to DEFAULT_LNG)
                    }
            }
        } catch (e: Exception) {
            DEFAULT_LAT to DEFAULT_LNG
        }
    }
}
