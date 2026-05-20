package com.tracker.alertas

import android.animation.ObjectAnimator
import android.app.KeyguardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout

/**
 * Pantalla de alerta a pantalla completa que se muestra cuando llega un push.
 *
 * Características clave:
 *  - Aparece sobre la pantalla bloqueada y sobre cualquier app en primer plano.
 *  - Cambia su tema visual (color y emoji) según el tipo de evento recibido.
 *  - Reproduce el audio seleccionado por el usuario en bucle con USAGE_ALARM.
 *  - Vibra con un patrón largo repetitivo hasta que se descarta.
 *  - Animaciones de ondas pulsantes y latido del ícono central.
 */
class AlertaActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Forzar que la pantalla se encienda y se muestre sobre el bloqueo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        setContentView(R.layout.activity_alerta)

        // Recuperar datos del Intent enviados desde el FirebaseService
        val tipo = (intent.getStringExtra("tipo_evento") ?: "ALERTA").uppercase()
        val intensidad = intent.getStringExtra("intensidad") ?: "Desconocida"
        val x = intent.getStringExtra("acel_x") ?: "0"
        val y = intent.getStringExtra("acel_y") ?: "0"
        val z = intent.getStringExtra("acel_z") ?: "0"
        val ts = intent.getStringExtra("timestamp") ?: "--:--:--"

        aplicarTemaPorTipo(tipo)

        findViewById<TextView>(R.id.tvTipoEvento).text = tipo
        findViewById<TextView>(R.id.tvSubtitulo).text = subtituloPorTipo(tipo)
        findViewById<TextView>(R.id.tvIntensidad).text = intensidad
        findViewById<TextView>(R.id.tvTimestamp).text = ts
        findViewById<TextView>(R.id.tvX).text = formatNumero(x)
        findViewById<TextView>(R.id.tvY).text = formatNumero(y)
        findViewById<TextView>(R.id.tvZ).text = formatNumero(z)

        animarOndas()
        animarIcono()

        findViewById<Button>(R.id.btnDescartar).setOnClickListener {
            detenerAlarma()
            finish()
        }

        iniciarAlarma()
    }

    /** Aplica el gradiente y emoji correspondiente al tipo de evento. */
    private fun aplicarTemaPorTipo(tipo: String) {
        val root = findViewById<ConstraintLayout>(R.id.rootLayout)
        val emoji = findViewById<TextView>(R.id.tvEmoji)
        val bg = when (tipo) {
            "GOLPE" -> R.drawable.bg_gradient_golpe
            "CAIDA" -> R.drawable.bg_gradient_caida
            "SACUDIDA" -> R.drawable.bg_gradient_sacudida
            else -> R.drawable.bg_gradient_golpe
        }
        root.setBackgroundResource(bg)
        emoji.text = when (tipo) {
            "GOLPE" -> "💥"
            "CAIDA" -> "⬇️"
            "SACUDIDA" -> "📳"
            else -> "🚨"
        }
    }

    private fun subtituloPorTipo(tipo: String): String = when (tipo) {
        "GOLPE" -> "Impacto fuerte detectado"
        "CAIDA" -> "Posible caída del dispositivo"
        "SACUDIDA" -> "Movimiento brusco detectado"
        else -> "Evento detectado"
    }

    private fun formatNumero(s: String): String =
        try { "%.2f".format(s.toFloat()) } catch (e: Exception) { s }

    /** Dos círculos blancos crecen y se desvanecen como ondas de radar. */
    private fun animarOndas() {
        val wave1 = findViewById<android.view.View>(R.id.wave1)
        val wave2 = findViewById<android.view.View>(R.id.wave2)

        listOf(wave1 to 0L, wave2 to 600L).forEach { (view, delay) ->
            ObjectAnimator.ofFloat(view, "scaleX", 0.8f, 1.4f).apply {
                duration = 1800
                repeatCount = ObjectAnimator.INFINITE
                interpolator = LinearInterpolator()
                startDelay = delay
                start()
            }
            ObjectAnimator.ofFloat(view, "scaleY", 0.8f, 1.4f).apply {
                duration = 1800
                repeatCount = ObjectAnimator.INFINITE
                interpolator = LinearInterpolator()
                startDelay = delay
                start()
            }
            ObjectAnimator.ofFloat(view, "alpha", 0.3f, 0f).apply {
                duration = 1800
                repeatCount = ObjectAnimator.INFINITE
                interpolator = LinearInterpolator()
                startDelay = delay
                start()
            }
        }
    }

    /** Latido del círculo central con el emoji. */
    private fun animarIcono() {
        val icon = findViewById<android.widget.FrameLayout>(R.id.iconCircle)
        ObjectAnimator.ofFloat(icon, "scaleX", 1f, 1.08f, 1f).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        ObjectAnimator.ofFloat(icon, "scaleY", 1f, 1.08f, 1f).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /** Reproduce el audio elegido y arranca la vibración. */
    private fun iniciarAlarma() {
        try {
            val resId = AudioPrefs.getResourceIdSeleccionado(this)
            if (resId == 0) {
                // Alarma del sistema
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(this@AlertaActivity, uri)
                    isLooping = true
                    prepare()
                    start()
                }
            } else {
                // Audio personalizado de res/raw
                mediaPlayer = MediaPlayer.create(this, resId)?.apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    start()
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        // Vibrador (API distinta según versión de Android)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val patron = longArrayOf(0, 800, 300, 800, 300, 800, 300)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // El 0 final en createWaveform indica "repetir desde el índice 0"
            vibrator?.vibrate(VibrationEffect.createWaveform(patron, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(patron, 0)
        }
    }

    private fun detenerAlarma() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) { e.printStackTrace() }
        vibrator?.cancel()
    }

    override fun onDestroy() {
        detenerAlarma()
        super.onDestroy()
    }
}