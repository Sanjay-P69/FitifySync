package com.fitifysync.app.ui.state

sealed interface UiEvent {
    data class ShowToast(val message: String, val isLong: Boolean = false) : UiEvent
    data class RequestHealthPermissions(val permissions: Set<String>) : UiEvent
}
