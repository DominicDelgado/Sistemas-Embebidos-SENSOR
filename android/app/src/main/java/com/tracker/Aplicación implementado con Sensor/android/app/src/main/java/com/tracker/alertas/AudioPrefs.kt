package com.tracker.alertas

import android.content.Context

/**
 * Helper singleton que centraliza:
 *  - La lista de audios disponibles para la alarma.
 *  - La lectura y escritura de la elección del usuario en SharedPreferences.
 *
 * IMPORTANTE: cada audio debe existir como archivo MP3 en res/raw/ con el
 * mismo nombre exacto que se referencia en R.raw. Si agregas o quitas audios,
 * solo modifica la lista `audios` de abajo.
 */
object AudioPrefs {

    private const val PREFS = "tracker_alertas_prefs"
    private const val KEY_AUDIO = "audio_seleccionado"

    /**
     * Lista de audios disponibles. Cada par es:
     *   "nombre visible para el usuario" → resource ID en res/raw
     * El primer item (id = 0) es la alarma por defecto del sistema.
     *
     * Si quieres añadir más audios:
     *  1. Copia el .mp3 a res/raw/ (nombre en minúsculas y guiones bajos).
     *  2. Añade un Pair a esta lista.
     */
    val audios: List<Pair<String, Int>> = listOf(
        "🔔 Alarma del sistema (por defecto)" to 0,
        "Aiunii" to R.raw.aiunii,
        "Among Us" to R.raw.among_us,
        "Baile a la tumba" to R.raw.baile_a_la_tumba,
        "Charles Leclerc" to R.raw.charles_leclerc,
        "Despierta Perú" to R.raw.despierta_peru,
        "Dracarys Jace" to R.raw.dracarys_jace,
        "Hee hee" to R.raw.hee_hee,
        "I am stupid" to R.raw.i_am_stupid,
        "Just an inchident" to R.raw.just_an_inchident,
        "King Nasir" to R.raw.king_nasir,
        "Max Verstappen" to R.raw.max_verstappen,
        "Monte Everest" to R.raw.monte_everest,
        "Noo" to R.raw.noo,
        "Pibble" to R.raw.pibble,
        "Ritmo indio" to R.raw.ritmo_indio,
        "Se va caer" to R.raw.se_va_caer,
        "Señor Ayuwoki" to R.raw.senor_ayuwoki,
        "Spiderman" to R.raw.spiderman,
        "Todo está bien" to R.raw.todo_esta_bien,
        "Y que fue" to R.raw.y_que_fue,
        "Yo no quiero ser presidente" to R.raw.yo_no_quiero_ser_presidente
    )

    /** Devuelve el índice (0..audios.size-1) del audio elegido por el usuario. */
    fun getAudioSeleccionado(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_AUDIO, 0) // 0 = alarma del sistema por defecto
    }

    /** Persiste la elección del usuario en SharedPreferences. */
    fun setAudioSeleccionado(context: Context, indice: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_AUDIO, indice).apply()
    }

    /**
     * Devuelve el ID del recurso seleccionado.
     * Si el resultado es 0, el caller debe interpretar "usar alarma del sistema".
     * Si es != 0, es un R.raw.xxx válido para reproducir con MediaPlayer.create().
     */
    fun getResourceIdSeleccionado(context: Context): Int {
        val idx = getAudioSeleccionado(context)
        return audios[idx].second
    }
}