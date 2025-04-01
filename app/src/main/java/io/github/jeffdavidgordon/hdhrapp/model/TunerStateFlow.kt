package io.github.jeffdavidgordon.hdhrapp.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.jeffdavidgordon.hdhrlib.model.Channel
import io.github.jeffdavidgordon.hdhrlib.model.Tuner
import io.github.jeffdavidgordon.hdhrlib.model.TunerState
import io.github.jeffdavidgordon.hdhrlib.model.TunerStatus
import io.github.jeffdavidgordon.hdhrlib.service.TunerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TunerStateFlow(tuner: Tuner) : ViewModel() {
    private val tunerService = TunerService()
    private val _data: MutableStateFlow<TunerState> = MutableStateFlow(TunerState())
    val data: StateFlow<TunerState> = _data

    init {
        startFetchingData(tuner)
    }

    private fun startFetchingData(tuner: Tuner) {
        viewModelScope.launch {
            while (isActive) {
                try {
                    _data.value = tunerService.getTunerState(tuner)
                } catch (_: Exception) {
                    _data.value = TunerState(
                        Channel("n/a", "n/a"),
                        0,
                        TunerStatus("ch=none lock=none ss=0 snq=0 seq=0 bps=0 pps=0"),
                        emptyMap()
                    )
                }
                delay(1000)
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
        throw IllegalArgumentException("Unknown Tuner class")
    }
}
