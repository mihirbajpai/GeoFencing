package com.example.geofencing.shared.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.geofencing.shared.AppConfig
import com.example.geofencing.shared.data.FirestoreRepository
import com.example.geofencing.shared.location.LocationProvider
import com.example.geofencing.shared.model.GeoFence
import com.example.geofencing.shared.model.MemberStatus
import com.example.geofencing.shared.storage.LocalStore
import com.example.geofencing.shared.util.runCatchingCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MemberUi(val name: String, val status: MemberStatus, val isMe: Boolean)

data class FenceDraft(
    val lat: Double? = null,
    val lng: Double? = null,
    val radiusMeters: Double = AppConfig.DEFAULT_RADIUS_M,
)

data class MapUiState(
    val fence: GeoFence? = null,
    val members: List<MemberUi> = emptyList(),
    val intervalSec: Int = AppConfig.DEFAULT_INTERVAL_SEC,
    val needsName: Boolean = false,
    val isAdmin: Boolean = false,
    val draft: FenceDraft? = null,
    val message: String? = null,
)

/** State and actions for the map screen, including the admin fence editor. */
class MapViewModel(private val store: LocalStore, private val locations: LocationProvider) :
    ViewModel() {
    private val repo = FirestoreRepository
    val radiusRange = AppConfig.MIN_RADIUS_M..AppConfig.MAX_RADIUS_M

    private val local = MutableStateFlow(
        MapUiState(
            needsName = store.name.isNullOrBlank(),
            isAdmin = store.isAdmin
        )
    )

    val uiState: StateFlow<MapUiState> =
        combine(
            local,
            repo.fence,
            repo.memberList,
            repo.intervalSec
        ) { local, fence, members, sec ->
            local.copy(
                fence = fence,
                members = members.map {
                    MemberUi(
                        it.name,
                        it.statusFor(fence),
                        it.id == store.deviceId
                    )
                },
                intervalSec = sec,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        store.name?.let(::register)
    }

    fun saveName(name: String) {
        val trimmed = name.trim().ifEmpty { return }
        store.name = trimmed
        local.update { it.copy(needsName = false) }
        register(trimmed)
    }

    fun unlockAdmin(pin: String): Boolean {
        if (pin != AppConfig.ADMIN_PIN) return false
        setAdmin(true)
        return true
    }

    fun exitAdmin() = setAdmin(false)

    fun startDraft() {
        val fence = uiState.value.fence
        local.update {
            it.copy(draft = fence?.let { f -> FenceDraft(f.lat, f.lng, f.radiusMeters) }
                ?: FenceDraft())
        }
    }

    fun cancelDraft() = local.update { it.copy(draft = null) }

    fun setDraftCenter(lat: Double, lng: Double) = updateDraft { it.copy(lat = lat, lng = lng) }

    fun useMyLocation() {
        viewModelScope.launch { locations.current()?.let { setDraftCenter(it.lat, it.lng) } }
    }

    suspend fun myLocation() = locations.current()

    fun setDraftRadius(meters: Double) =
        updateDraft { it.copy(radiusMeters = meters.coerceIn(radiusRange)) }

    fun saveDraft() {
        val draft = local.value.draft ?: return
        val lat = draft.lat ?: return
        val lng = draft.lng ?: return
        runAction("Fence saved") {
            repo.saveFence(lat, lng, draft.radiusMeters)
            local.update { it.copy(draft = null) }
        }
    }

    fun deleteFence() = runAction("Fence deleted") { repo.deleteFence() }

    fun setInterval(input: String) {
        val sec = input.toIntOrNull()
        if (sec == null || sec !in 1..600) return showMessage("Interval must be between 1 and 600 seconds")
        runAction("Interval set to ${sec}s") { repo.setIntervalSec(sec) }
    }

    fun messageShown() = local.update { it.copy(message = null) }

    private fun register(name: String) = runAction { repo.registerMember(store.deviceId, name) }

    private fun setAdmin(admin: Boolean) {
        store.isAdmin = admin
        local.update { it.copy(isAdmin = admin, draft = null) }
    }

    private fun updateDraft(change: (FenceDraft) -> FenceDraft) =
        local.update { s -> s.draft?.let { s.copy(draft = change(it)) } ?: s }

    private fun showMessage(msg: String) = local.update { it.copy(message = msg) }

    private fun runAction(successMsg: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatchingCancellable { block() }
                .onSuccess { successMsg?.let(::showMessage) }
                .onFailure { showMessage(it.message ?: "Something went wrong") }
        }
    }
}
