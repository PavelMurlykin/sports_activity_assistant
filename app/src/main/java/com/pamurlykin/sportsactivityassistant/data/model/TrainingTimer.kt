package com.pamurlykin.sportsactivityassistant.data.model

import java.util.Locale

/** Elapsed-realtime timestamps include sleep and do not depend on wall-clock changes. */
data class TrainingTimer(val accumulatedMillis: Long = 0L, val startedAtMillis: Long? = null) {
    val isRunning: Boolean get() = startedAtMillis != null

    fun elapsedMillis(nowMillis: Long): Long {
        val accumulated = accumulatedMillis.coerceIn(0L, MAX_MILLIS)
        val extra = startedAtMillis?.let { (nowMillis - it).coerceIn(0L, MAX_MILLIS - accumulated) } ?: 0L
        return accumulated + extra
    }

    fun start(nowMillis: Long): TrainingTimer = if (isRunning) this else copy(startedAtMillis = nowMillis)
    fun stop(nowMillis: Long): TrainingTimer = TrainingTimer(elapsedMillis(nowMillis))

    companion object {
        private const val MINUTE_MILLIS = 60_000L
        private const val MAX_MILLIS = Int.MAX_VALUE * MINUTE_MILLIS

        fun fromDuration(text: String): TrainingTimer = TrainingTimer(
            (text.trim().toIntOrNull()?.takeIf { it > 0 } ?: 0).toLong() * MINUTE_MILLIS,
        )

        /** Zero rounded minutes is an unset optional duration, never an invalid saved zero. */
        fun durationText(elapsedMillis: Long): String =
            ((elapsedMillis.coerceIn(0L, MAX_MILLIS) + MINUTE_MILLIS / 2) / MINUTE_MILLIS)
                .takeIf { it > 0L }?.toString().orEmpty()

        fun display(elapsedMillis: Long): String {
            val seconds = elapsedMillis.coerceAtLeast(0L) / 1_000L
            return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3_600L, seconds / 60L % 60L, seconds % 60L)
        }
    }
}
