package com.fastingshift.app.ui.partner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastingshift.domain.safety.FastPolicy
import com.fastingshift.domain.service.ScreeningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONException

@HiltViewModel
class PartnerWeekViewModel @Inject constructor(
    private val repository: PartnerWeekRepository,
    private val screening: ScreeningRepository,
) : ViewModel() {
    private val ui = MutableStateFlow(PartnerUiState())
    val state = ui.asStateFlow()
    private var updating: Job? = null
    private var previewJob: Job? = null
    private var acting = false

    init {
        viewModelScope.launch {
            repository.snapshot.collect { snapshot ->
                ui.update { it.copy(week = snapshot.week, pendingDays = snapshot.pendingDays, leaving = snapshot.leaving) }
            }
        }
        viewModelScope.launch {
            screening.observeLatest().collect {
                val allowed = FastPolicy.maxAllowed(it?.gateResult, it?.clinicianAcknowledged == true) != null
                ui.update { state -> state.copy(participantAllowed = allowed) }
            }
        }
        viewModelScope.launch { handled { repository.load() } }
    }

    fun refresh() {
        if (updating?.isActive == true) return
        updating = viewModelScope.launch {
            handled(silentOffline = true) {
                repository.refresh()
                ui.update { it.copy(offline = false) }
            }
        }
    }

    fun preview(code: String) {
        previewJob?.cancel()
        ui.update { it.copy(preview = null, message = null, busy = true) }
        previewJob = viewModelScope.launch {
            try {
                handled {
                    val result = repository.preview(code)
                    ui.update { it.copy(preview = result, offline = false) }
                }
            } finally {
                ui.update { it.copy(busy = acting) }
            }
        }
    }

    fun dismissPreview() {
        previewJob?.cancel()
        previewJob = null
        ui.update { it.copy(preview = null, message = null, busy = acting) }
    }

    fun create(alias: String) = action {
        if (participantPermitted()) repository.create(alias)
    }

    fun join(code: String, alias: String, supporter: Boolean) = action {
        if (supporter || participantPermitted()) {
            repository.join(code, alias, supporter)
            ui.update { it.copy(preview = null) }
        }
    }

    fun checkIn() = action {
        if (participantPermitted()) repository.checkIn()
    }

    fun cheer(kind: String = "glad") = action { repository.cheer(kind) }

    fun renew() = action {
        if (state.value.week?.self?.role == "supporter" || participantPermitted()) repository.renew()
    }

    fun leave() = action { repository.leave() }
    fun dismissMessage() = ui.update { it.copy(message = null) }

    private suspend fun participantPermitted(): Boolean {
        val latest = screening.observeLatest().first()
        val allowed = FastPolicy.maxAllowed(latest?.gateResult, latest?.clinicianAcknowledged == true) != null
        if (!allowed) ui.update { it.copy(message = "Your current screening allows a supporter role only.") }
        return allowed
    }

    private fun action(block: suspend () -> Unit) {
        if (acting) return
        acting = true
        ui.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                handled {
                    block()
                    ui.update { it.copy(offline = false) }
                }
            } finally {
                acting = false
                ui.update { it.copy(busy = previewJob?.isActive == true) }
            }
        }
    }

    private suspend fun handled(silentOffline: Boolean = false, block: suspend () -> Unit) {
        try {
            block()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: PartnerRequestException) {
            if (error.retryable) {
                unavailable(silentOffline)
            } else {
                val message = when (error.status) {
                    403, 404, 410 -> "This invitation or partner week is no longer available."
                    409 -> "This partner week has changed. Refresh to see the latest details."
                    422 -> "Check the invitation or display name and try again."
                    else -> "The request couldn’t be completed. Try again."
                }
                ui.update { it.copy(message = message, offline = false) }
            }
        } catch (_: IOException) {
            unavailable(silentOffline)
        } catch (_: JSONException) {
            ui.update { it.copy(message = "Couldn’t read the partner week. Try refreshing.") }
        } catch (validation: IllegalArgumentException) {
            ui.update { it.copy(message = validation.message ?: "Check the details and try again.") }
        }
    }

    private fun unavailable(silent: Boolean) {
        val local = repository.snapshot.value
        val message = when {
            local.leaving -> "Leaving is saved on this phone. Reconnect to finish removing the shared week."
            local.pendingDays.isNotEmpty() -> "Check-in saved on this phone. It will sync when a connection is available."
            else -> "The partner service is unavailable. Try again when you’re connected."
        }
        ui.update { it.copy(offline = true, message = if (silent) it.message else message) }
    }
}
