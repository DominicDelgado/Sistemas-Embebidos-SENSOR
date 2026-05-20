package com.tracker.alertas

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Pantalla principal de TrackerAlertas.
 *
 * Responsabilidades:
 *  - Solicitar permisos críticos (notificaciones, FullScreenIntent, overlay, batería).
 *  - Obtener el token FCM del dispositivo y registrarlo en el backend.
 *  - Permitir al usuario seleccionar el audio de alarma de una lista predefinida.
 *  - Disparar manualmente una alerta de prueba (botón "Test").
 */
class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private val BACKEND_URL = "http://98.87.5.225"  // IP pública del servidor EC2

    private lateinit var tvEstado: TextView
    private lateinit var tvToken: TextView
    private lateinit var spinnerAudio: Spinner
    private var previewPlayer: MediaPlayer? = null

    // Contrato moderno para pedir permisos en runtime
    private val permisoNotif = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) obtenerYRegistrarToken()
        else tvEstado.text = "⚠️ Permiso de notificaciones denegado"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvEstado = findViewById(R.id.tvEstado)
        tvToken = findViewById(R.id.tvToken)
        spinnerAudio = findViewById(R.id.spinnerAudio)

        configurarSpinnerAudio()

        findViewById<Button>(R.id.btnEscuchar).setOnClickListener { reproducirPreview() }
        findViewById<Button>(R.id.btnDetener).setOnClickListener { detenerPreview() }
        findViewById<Button>(R.id.btnTestPush).setOnClickListener { llamarTestPush() }

        pedirPermisoNotificaciones()
    }

    override fun onResume() {
        super.onResume()
        verificarPermisosCriticos()
    }

    override fun onPause() {
        super.onPause()
        detenerPreview()
    }

    // ───────────── AUDIO ─────────────

    /** Llena el spinner con todos los audios disponibles definidos en AudioPrefs. */
    private fun configurarSpinnerAudio() {
        val nombres = AudioPrefs.audios.map { it.first }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, nombres)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerAudio.adapter = adapter
        spinnerAudio.setSelection(AudioPrefs.getAudioSeleccionado(this))

        spinnerAudio.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                detenerPreview()
                AudioPrefs.setAudioSeleccionado(this@MainActivity, pos)
                Toast.makeText(this@MainActivity, "Audio: ${nombres[pos]}", Toast.LENGTH_SHORT).show()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /** Reproduce el audio elegido con USAGE_ALARM (no se silencia con música). */
    private fun reproducirPreview() {
        detenerPreview()
        val resId = AudioPrefs.getResourceIdSeleccionado(this)
        try {
            if (resId == 0) {
                val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                previewPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(this@MainActivity, uri)
                    prepare(); start()
                }
            } else {
                previewPlayer = MediaPlayer.create(this, resId)
                previewPlayer?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                previewPlayer?.start()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error reproduciendo: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun detenerPreview() {
        try { previewPlayer?.stop(); previewPlayer?.release() } catch (_: Exception) {}
        previewPlayer = null
    }

    // ───────────── PERMISOS ─────────────

    private fun pedirPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val tiene = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!tiene) permisoNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            else obtenerYRegistrarToken()
        } else obtenerYRegistrarToken()
    }

    /** Verifica los 3 permisos especiales que Android 14 exige para FullScreenIntent. */
    private fun verificarPermisosCriticos() {
        // 1. FullScreenIntent (Android 14+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.canUseFullScreenIntent()) {
                mostrarDialogoPermiso(
                    "Permitir pantalla completa",
                    "Activa 'Mostrar alertas a pantalla completa' en Ajustes.",
                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT
                )
                return
            }
        }
        // 2. Mostrar sobre otras apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            mostrarDialogoPermiso(
                "Permitir mostrar sobre otras apps",
                "Activa 'Mostrar sobre otras apps' para que la alerta interrumpa cualquier app.",
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION
            )
            return
        }
        // 3. Optimización de batería
        verificarBateria()
    }

    private fun verificarBateria() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                AlertDialog.Builder(this)
                    .setTitle("Desactivar optimización de batería")
                    .setMessage("Para recibir alertas con el celular bloqueado, desactiva la optimización de batería.")
                    .setPositiveButton("Ir a Ajustes") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            intent.data = Uri.parse("package:$packageName")
                            startActivity(intent)
                        } catch (e: Exception) {
                            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    }
                    .setNegativeButton("Después", null)
                    .show()
            }
        }
    }

    private fun mostrarDialogoPermiso(titulo: String, msg: String, accion: String) {
        AlertDialog.Builder(this)
            .setTitle(titulo).setMessage(msg).setCancelable(false)
            .setPositiveButton("Ir a Ajustes") { _, _ ->
                try {
                    val intent = Intent(accion)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(this, "Abre Ajustes manualmente", Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    // ───────────── TOKEN / BACKEND ─────────────

    /** Obtiene el token FCM y lo manda al backend. */
    private fun obtenerYRegistrarToken() {
        tvEstado.text = "Obteniendo token FCM..."
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                tvEstado.text = "❌ Error obteniendo token"
                return@addOnCompleteListener
            }
            val token = task.result
            tvToken.text = "Token: ${token.take(50)}..."
            enviarTokenAlBackend(token)
        }
    }

    private fun enviarTokenAlBackend(token: String) {
        Thread {
            try {
                val client = OkHttpClient()
                val json = JSONObject().apply { put("token", token) }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val req = Request.Builder().url("$BACKEND_URL/registrar-dispositivo").post(body).build()
                client.newCall(req).execute().use { resp ->
                    runOnUiThread {
                        tvEstado.text = if (resp.isSuccessful)
                            "✅ Conectado.\nEsperando alertas del sensor..."
                        else "❌ Backend respondió ${resp.code}"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { tvEstado.text = "❌ Error de conexión:\n${e.message}" }
            }
        }.start()
    }

    /** Llama al endpoint de prueba del backend para disparar un push de GOLPE. */
    private fun llamarTestPush() {
        Toast.makeText(this, "Enviando alerta de prueba...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val client = OkHttpClient()
                val req = Request.Builder().url("$BACKEND_URL/test-push").get().build()
                client.newCall(req).execute()
            } catch (e: Exception) {
                Log.e(TAG, "Error test-push", e)
            }
        }.start()
    }
}