package com.tracker.alertas

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Servicio que recibe los push notifications de Firebase Cloud Messaging.
 *
 * Cuando llega un mensaje (incluso con la app cerrada o el celular bloqueado):
 *  1. Crea el canal de notificación de alta prioridad si no existe.
 *  2. Lanza AlertaActivity directamente con startActivity() (más confiable en Android 14).
 *  3. Postea una notificación con FullScreenIntent como respaldo por si el sistema
 *     decide no permitir la primera estrategia.
 *
 * También maneja el evento onNewToken() para registrar el token FCM en el backend.
 */
class AlertaFirebaseService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCM_Service"
        const val CANAL_ID = "canal_alertas"
        const val NOTIF_ID = 1001
    }

    /** Se llama cuando Firebase genera o refresca el token del dispositivo. */
    override fun onNewToken(token: String) {
        Log.d(TAG, "Nuevo token FCM: $token")
        registrarTokenEnBackend(token)
    }

    /** Se llama cada vez que llega un mensaje desde el backend. */
    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "Mensaje recibido: ${message.data}")

        val data = message.data
        val tipoEvento = data["tipo_evento"] ?: "ALERTA"
        val intensidad = data["intensidad"] ?: "Desconocida"
        val acelX = data["acel_x"] ?: "0"
        val acelY = data["acel_y"] ?: "0"
        val acelZ = data["acel_z"] ?: "0"
        val timestamp = data["timestamp"] ?: ""

        crearCanalNotificacion()

        // Estrategia 1: lanzar la Activity directamente (más agresivo, funciona en Android 14)
        lanzarAlertaActivity(tipoEvento, intensidad, acelX, acelY, acelZ, timestamp)

        // Estrategia 2: notificación con FullScreenIntent como respaldo
        postearNotificacionRespaldo(tipoEvento, intensidad, acelX, acelY, acelZ, timestamp)
    }

    /**
     * Lanza AlertaActivity directamente desde el servicio.
     * Esto evita que el sistema decida si el FullScreenIntent se ejecuta o no.
     */
    private fun lanzarAlertaActivity(
        tipo: String, intensidad: String,
        x: String, y: String, z: String, timestamp: String
    ) {
        val intent = Intent(this, AlertaActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("tipo_evento", tipo)
            putExtra("intensidad", intensidad)
            putExtra("acel_x", x)
            putExtra("acel_y", y)
            putExtra("acel_z", z)
            putExtra("timestamp", timestamp)
        }
        try {
            startActivity(intent)
            Log.d(TAG, "AlertaActivity lanzada directamente")
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo lanzar la activity: ${e.message}")
        }
    }

    /**
     * Crea el canal de notificación (obligatorio desde Android 8).
     * Importancia HIGH + bypassDnd + sonido USAGE_ALARM.
     */
    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CANAL_ID) != null) return

            val canal = NotificationChannel(
                CANAL_ID,
                "Alertas críticas del sensor",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Golpes, sacudidas y caídas detectadas"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                enableLights(true)
                setBypassDnd(true) // atraviesa el modo No Molestar
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                val audioAttrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound(sound, audioAttrs)
            }
            nm.createNotificationChannel(canal)
        }
    }

    /**
     * Postea una notificación con FullScreenIntent como respaldo.
     * Si el sistema decide no usar FullScreenIntent, al menos quedará la notificación visible.
     */
    private fun postearNotificacionRespaldo(
        tipo: String, intensidad: String,
        x: String, y: String, z: String, timestamp: String
    ) {
        val fullScreenIntent = Intent(this, AlertaActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("tipo_evento", tipo)
            putExtra("intensidad", intensidad)
            putExtra("acel_x", x)
            putExtra("acel_y", y)
            putExtra("acel_z", z)
            putExtra("timestamp", timestamp)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CANAL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 ${emojiEvento(tipo)} $tipo detectado")
            .setContentText("Intensidad: $intensidad — toca para abrir")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, notif)
    }

    private fun emojiEvento(tipo: String): String = when (tipo.uppercase()) {
        "GOLPE" -> "💥"
        "SACUDIDA" -> "📳"
        "CAIDA" -> "⬇️"
        else -> "⚠️"
    }

    /** Envía el token recién generado al backend Flask. */
    private fun registrarTokenEnBackend(token: String) {
        Thread {
            try {
                val client = OkHttpClient()
                val json = JSONObject().apply { put("token", token) }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url("http://98.87.5.225/registrar-dispositivo")
                    .post(body).build()
                client.newCall(req).execute().use { resp ->
                    Log.d(TAG, "Token registrado: ${resp.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error registrando token", e)
            }
        }.start()
    }
}