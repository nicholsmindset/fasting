package com.fastingshift.app.ui.partner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.R
import com.fastingshift.domain.service.ScreeningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
                val allowed = it.partnerEligibility()
                ui.update { state ->
                    state.copy(adultAllowed = allowed.adult, participantAllowed = allowed.participant,
                        preview = state.preview.takeIf { allowed.adult })
                }
            }
        }
        viewModelScope.launch { handled { repository.load() } }
    }

    fun refresh() {
        if (updating?.isActive == true) return
        updating = viewModelScope.launch {
            handled(silentOffline = true) {
                val allowed = screening.getLatest().partnerEligibility()
                if (allowed.adult) repository.refresh(allowed.participant)
                else repository.syncPending(participationAllowed = false)
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
                    if (!permitted(participating = false)) return@handled
                    val result = repository.preview(code)
                    if (permitted(participating = false)) ui.update { it.copy(preview = result, offline = false) }
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
        if (permitted(participating = true)) repository.create(alias)
    }

    fun join(code: String, alias: String, supporter: Boolean) = action {
        if (permitted(participating = !supporter)) {
            repository.join(code, alias, supporter)
            ui.update { it.copy(preview = null) }
        }
    }

    fun checkIn() = action {
        if (permitted(participating = true)) repository.checkIn()
    }

    fun cheer(kind: String = "glad") = action { if (permitted(participating = false)) repository.cheer(kind) }

    fun renew() = action {
        if (permitted(participating = state.value.week?.self?.role != "supporter")) repository.renew()
    }

    fun leave() = action { repository.leave() }
    fun dismissMessage() = ui.update { it.copy(message = null) }

    private suspend fun permitted(participating: Boolean): Boolean {
        val allowed = screening.getLatest().partnerEligibility()
        if (!allowed.adult) {
            ui.update { it.copy(message = R.string.partner_error_adults_only) }
            return false
        }
        if (participating && !allowed.participant) {
            ui.update { it.copy(message = R.string.partner_error_supporter_only) }
            return false
        }
        return true
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
                    403, 404, 410 -> R.string.partner_error_unavailable
                    409 -> R.string.partner_error_changed
                    422 -> R.string.partner_error_details
                    else -> R.string.partner_error_retry
                }
                ui.update { it.copy(message = message, offline = false) }
            }
        } catch (_: IOException) {
            unavailable(silentOffline)
        } catch (_: JSONException) {
            ui.update { it.copy(message = R.string.partner_error_read) }
        } catch (validation: IllegalArgumentException) {
            val message = if (validation.message == PartnerWeekRepository.INVALID_ALIAS) {
                R.string.partner_error_display_name
            } else R.string.partner_error_details
            ui.update { it.copy(message = message) }
        }
    }

    private fun unavailable(silent: Boolean) {
        val local = repository.snapshot.value
        val message = when {
            local.leaving -> R.string.partner_leaving_offline
            local.pendingDays.isNotEmpty() -> R.string.partner_queued
            else -> R.string.partner_service_offline
        }
        ui.update { it.copy(offline = true, message = if (silent) it.message else message) }
    }
}
