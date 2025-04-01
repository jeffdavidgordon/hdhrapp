package io.github.jeffdavidgordon.hdhrapp.model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.jeffdavidgordon.hdhrlib.model.Tuner
import io.github.jeffdavidgordon.hdhrlib.model.TunerState
import io.github.jeffdavidgordon.hdhrlib.service.TunerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TunerStateFlow(tuner: Tuner) : ViewModel() {
    private val tunerService = TunerService()
    private val _data: MutableStateFlow<TunerState> = MutableStateFlow(TunerState())
    val data: StateFlow<TunerState?> = _data

    init {
        startFetchingData(tuner)
    }

    private fun startFetchingData(tuner: Tuner) {
        viewModelScope.launch {
            while (isActive) { // Ensures cancellation when ViewModel is cleared
                val myTunerState = tunerService.getTunerState(tuner)
                _data.value = myTunerState
                delay(1000) // Ping every second
            }
        }
    }
}

class TunerStateFlowFactory(private val tuner: Tuner) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TunerStateFlow::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TunerStateFlow(tuner) as T
        }
        throw IllegalArgumentException("Unknown DeviceMapState class")
    }
}
