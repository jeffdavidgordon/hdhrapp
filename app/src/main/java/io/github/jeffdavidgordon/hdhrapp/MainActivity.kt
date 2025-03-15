package io.github.jeffdavidgordon.hdhrapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jeffdavidgordon.hdhrapp.model.DeviceData
import io.github.jeffdavidgordon.hdhrapp.model.TunerData
import io.github.jeffdavidgordon.hdhrapp.model.TunerDataViewModel
import io.github.jeffdavidgordon.hdhrapp.model.TunerDataViewModelFactory
import io.github.jeffdavidgordon.hdhrlib.model.DeviceMap
import io.github.jeffdavidgordon.hdhrlib.model.Tuner
import io.github.jeffdavidgordon.hdhrlib.service.DiscoverService
import io.github.jeffdavidgordon.hdhrlib.service.TunerService
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.experimental.inv

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gfgPolicy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(gfgPolicy)

        val deviceMap = DiscoverService.getDeviceMap(getBroadcastAddress(this))
        println(InetAddress.getByName("192.168.1.86").hostAddress)
        deviceMap.addDevice(InetAddress.getByName("192.168.1.86"))
        println(deviceMap)
        setContent {
            AppContent(deviceMap)
        }
    }

    private fun getBroadcastAddress(context: Context): InetAddress? {
        val connectivityManager = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return null
        val linkProperties: LinkProperties = connectivityManager.getLinkProperties(network) ?: return null

        val ipv4Address = linkProperties.linkAddresses
            .map(LinkAddress::getAddress)
            .firstOrNull { it is java.net.Inet4Address } ?: return null

        val prefixLength = linkProperties.linkAddresses
            .firstOrNull { it.address == ipv4Address }
            ?.prefixLength ?: return null

        val ipBytes = ipv4Address.address
        val subnetMask = prefixLengthToSubnetMask(prefixLength)

        val broadcastBytes = ByteArray(4)
        for (i in 0..3) {
            broadcastBytes[i] = (ipBytes[i].toInt() or subnetMask[i].inv().toInt()).toByte()
        }

        return try {
            InetAddress.getByAddress(broadcastBytes)
        } catch (_: UnknownHostException) {
            null
        }
    }

    /**
     * Converts a prefix length (CIDR notation) to a subnet mask in byte array form.
     */
    private fun prefixLengthToSubnetMask(prefixLength: Int): ByteArray {
        val mask = ByteArray(4)
        for (i in 0 until prefixLength) {
            mask[i / 8] = (mask[i / 8].toInt() or (1 shl (7 - (i % 8)))).toByte()
        }
        return mask
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent(deviceMap: DeviceMap) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color.Black,
            onBackground = Color.White
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("HDHomeRun Signal Statistics") }
                )
            }
        ) { innerPadding ->
            Surface(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    val tunerDataViewModel: TunerDataViewModel =
                        viewModel(factory = TunerDataViewModelFactory(deviceMap))
                    val data by tunerDataViewModel.data.collectAsState()

                    deviceMap.forEach { (deviceId, device) ->
                        val deviceData: DeviceData? = data?.get(deviceId)
                        DeviceRow(deviceData)
                        HeaderRow()
                        device.tuners.map { tuner ->
                            val tunerData: TunerData? = data?.get(deviceId)?.tuners?.get(tuner.id)
                            DataRow(
                                tuner = tuner,
                                tunerData = tunerData,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceRow(deviceData: DeviceData?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Device: " + deviceData?.id, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), color = Color.White)
    }
}

@Composable
fun HeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.DarkGray)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "Tuner",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.2f)
        )
        Text(
            "Channel",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.2f)
        )
        Text(
            "Strength",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.16f)
        )
        Text(
            "Quality",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.15f)
        )
        Text(
            "Errors",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.14f)
        )
        Spacer(
            modifier = Modifier.weight(0.1f)
        )
    }
}

@Composable
fun DataRow(
    tuner: Tuner,
    tunerData: TunerData?,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = tunerData?.id.toString(),
            modifier = Modifier.weight(1f)
                .clickable { expanded = true },
            textAlign = TextAlign.Center,
            fontSize = 16.sp
        )

        if (tunerData?.channelNumber != null) {
            Text(
                text = "Ch. " + tunerData.channelNumber + "\n" + (tunerData.channelInfo?.let { "${it.identifier} ${it.callsign}" }
                    ?: "No Signal"),
                modifier = Modifier.weight(2f)
                    .clickable { expanded = true }
                    .padding(16.dp),
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )
        } else {
            Text(
                text = "No Channel",
                modifier = Modifier.weight(2f)
                    .clickable { expanded = true }
                    .padding(16.dp),
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            tunerData?.lineup?.forEach { (channel, deviceChannel) ->
                if (deviceChannel?.deviceFrequency != null) {
                    DropdownMenuItem(
                        text = { Text(text = "$channel - ${deviceChannel.deviceFrequency.guideNumber} ${deviceChannel.deviceFrequency.guideName}") },
                        onClick = {
                            expanded = false
                            TunerService.setChannel(tuner, channel.toLong())
                        }
                    )
                }
            }
        }
        CircularProgressBar(progress = (tunerData?.status?.ss)?.toFloat(), modifier = Modifier.weight(1f).clickable { expanded = true })
        CircularProgressBar(progress = (tunerData?.status?.snq)?.toFloat(), modifier = Modifier.weight(1f).clickable { expanded = true })
        CircularProgressBar(progress = (tunerData?.status?.seq)?.toFloat(), modifier = Modifier.weight(1f).clickable { expanded = true })
        Column(
            modifier = Modifier.padding(16.dp).clickable { expanded = true }
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Change channel..."
            )
        }
    }
}

@Composable
fun CircularProgressBar(progress: Float?, modifier: Modifier = Modifier) {
    val red = Color.Red
    val green = Color.Green
    Box(
        modifier = modifier.size(50.dp),
        contentAlignment = Alignment.Center
    ) {
        val fraction = (progress?.coerceIn(0F, 100F) ?: 0F) / 100F
        CircularProgressIndicator(
            progress = { progress?.div(100) ?: 0F },
            modifier = Modifier.fillMaxSize(),
            color = lerp(red, green, fraction),
            strokeWidth = 6.dp,
        )
        Text(
            text = "${progress?.toInt()}%",
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}