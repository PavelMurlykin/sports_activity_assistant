package com.pamurlykin.sportsactivityassistant.ui.screen

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** A failed read is not an empty database and must never become a file-operation message. */
data class ReadState<out T>(val data: T? = null, val failed: Boolean = false) {
    val loading: Boolean get() = data == null && !failed
}

internal fun <T> Flow<T>.readStates(): Flow<ReadState<T>> =
    map<T, ReadState<T>> { ReadState(data = it) }
        .onStart { emit(ReadState()) }
        .catch { error ->
            if (error is CancellationException) throw error
            // Do not expose SQLite paths, provider details or stack traces to the user.
            emit(ReadState(failed = true))
        }
