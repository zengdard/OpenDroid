package com.raissummit.orchestrator.tools

import android.content.Context
import android.location.Geocoder
import com.google.android.gms.location.LocationServices
import java.util.Locale

class GetDeviceLocationTool : Tool(
    name = "get_device_location",
    description = "Obtenir la localisation de l'appareil"
) {
    override suspend fun execute(
        context: Context,
        parameters: Map<String, Any?>
    ): ToolResult {
        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val task = fusedClient.lastLocation
            val location = task.await()

            if (location != null) {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                val address = if (!addresses.isNullOrEmpty()) {
                    addresses[0].getAddressLine(0)
                } else {
                    "Inconnue"
                }
                ToolResult.SuccessWithData(mapOf(
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "address" to address
                ))
            } else {
                ToolResult.Error("Localisation non disponible")
            }
        } catch (e: SecurityException) {
            ToolResult.Error("Permission ACCESS_FINE_LOCATION non accordée")
        } catch (e: Exception) {
            ToolResult.Error("Erreur de localisation : ${e.message}")
        }
    }

    private fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
        Tasks.await(this)
        return this.result
    }

    private object Tasks {
        fun <T> await(task: com.google.android.gms.tasks.Task<T>) {
            while (!task.isComplete) {
                Thread.sleep(50)
            }
            if (task.isSuccessful) return
            throw task.exception ?: Exception("Task failed")
        }
    }
}
