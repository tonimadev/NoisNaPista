package digital.tonima.noisnapista.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import digital.tonima.noisnapista.core.model.LocationPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

class AndroidLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : LocationProvider {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    override fun getLocationUpdates(): Flow<LocationPoint> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(2000L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    val speed = if (location.hasSpeed()) location.speed else null
                    trySend(LocationPoint(location.latitude, location.longitude, location.accuracy, location.time, speed))
                }
            }
        }

        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            close(e)
        }

        awaitClose {
            client.removeLocationUpdates(callback)
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): LocationPoint? = suspendCancellableCoroutine { continuation ->
        val cancellationTokenSource = CancellationTokenSource()
        continuation.invokeOnCancellation { cancellationTokenSource.cancel() }
        try {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
                .addOnSuccessListener { location ->
                    val point = location?.let {
                        val speed = if (it.hasSpeed()) it.speed else null
                        LocationPoint(it.latitude, it.longitude, it.accuracy, it.time, speed)
                    }
                    if (continuation.isActive) continuation.resume(point)
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(null)
                }
        } catch (e: SecurityException) {
            if (continuation.isActive) continuation.resume(null)
        }
    }
}
