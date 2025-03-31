package io.github.jeffdavidgordon.hdhrapp.model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.jeffdavidgordon.hdhrlib.model.Channel
import io.github.jeffdavidgordon.hdhrlib.model.DeviceChannel
import io.github.jeffdavidgordon.hdhrlib.model.DeviceMap
import io.github.jeffdavidgordon.hdhrlib.model.Features
import io.github.jeffdavidgordon.hdhrlib.model.TunerStatus
import io.github.jeffdavidgordon.hdhrlib.service.DeviceService
import io.github.jeffdavidgordon.hdhrlib.service.SysService
import io.github.jeffdavidgordon.hdhrlib.service.TunerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetAddress

class TunerDataViewModel(deviceMap: DeviceMap) : ViewModel() {
    private val deviceService = DeviceService()
    private val _data: MutableStateFlow<DeviceMap> = MutableStateFlow(DeviceMap())
    val data: StateFlow<DeviceMap?> = _data

    init {
        startFetchingData(deviceMap)
    }

    private fun startFetchingData(deviceMap: DeviceMap) {
        viewModelScope.launch {
            while (isActive) { // Ensures cancellation when ViewModel is cleared
                Log.i("FETCHING","fetching data...")


                val newDeviceMapData = DeviceMap()
                deviceMap.forEach { (_, device) ->
                    val device = deviceService.updateDevice(device)
                }
                _data.value = newDeviceMapData
                delay(1000) // Ping every second
            }
        }
    }
}

class TunerDataViewModelFactory(private val deviceMap: DeviceMap) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TunerDataViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TunerDataViewModel(deviceMap) as T
        }
        throw IllegalArgumentException("Unknown TunerDataViewModel class")
    }
}

class DeviceMapData : HashMap<String, DeviceData>()

data class DeviceData(
    val id: String,
    val ip: InetAddress,
    val model: String,
    val features: Features,
    val version: String,
    val copyright: String,
    val tuners: List<TunerData>
)

data class TunerData(
    var id: Int,
    var channelInfo: Channel?,
    val channelNumber: Int?,
    val status: TunerStatus,
    val lineup: MutableMap<Int, DeviceChannel?>
)