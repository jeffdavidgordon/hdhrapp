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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jeffdavidgordon.hdhrapp.model.TunerStateFlow
import io.github.jeffdavidgordon.hdhrapp.model.TunerStateFlowFactory
import io.github.jeffdavidgordon.hdhrapp.ui.theme.SignalStatisticsTheme
import io.github.jeffdavidgordon.hdhrapp.ui.theme.surfaceContainerHighestDarkMediumContrast
import io.github.jeffdavidgordon.hdhrlib.exception.HdhrException
import io.github.jeffdavidgordon.hdhrlib.model.Device
import io.github.jeffdavidgordon.hdhrlib.model.DeviceMap
import io.github.jeffdavidgordon.hdhrlib.model.Tuner
import io.github.jeffdavidgordon.hdhrlib.model.TunerState
import io.github.jeffdavidgordon.hdhrlib.service.DeviceService
import io.github.jeffdavidgordon.hdhrlib.service.TunerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.experimental.inv

class MainActivity : ComponentActivity() {
    val deviceService = DeviceService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        StrictMode.setThreadPolicy(ThreadPolicy.Builder().permitAll().build())

        setContent {
            LoadingScreen()
        }

        lifecycleScope.launch {
            val deviceMap = deviceService.getDeviceMap(getBroadcastAddress(this@MainActivity))
            deviceMap.addDevice(InetAddress.getByName("192.168.1.86"))
            delay(3000)
            setContent {
                AppContent(deviceMap)
            }
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
fun AppContent(deviceMap: DeviceMap?) {
    val tunerService = TunerService()

    SignalStatisticsTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Signal Statistics") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                )
            }
        ) { innerPadding ->
            Surface(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    deviceMap?.forEach { (_, device) ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Column {
                                DeviceRow(device)
                                HeaderRow()
                                Spacer(modifier = Modifier.height(8.dp))
                                device?.tuners?.map { tuner ->
                                    val tunerStateFlow: TunerStateFlow = viewModel(
                                        factory = TunerStateFlowFactory(tuner),
                                        key = "${device.id}_${tuner.id}"
                                    )
                                    val tunerStateFlowData by tunerStateFlow.data.collectAsState()

                                    DataRow(
                                        tunerService = tunerService,
                                        tuner = tuner,
                                        tunerStateFlowData = tunerStateFlowData,
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceRow(device: Device?) {
    var showDialog by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(0.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            device?.id.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            modifier = Modifier.weight(1f).padding(16.dp)
        )
        if (device != null) {
            Box(
                modifier = Modifier.padding(16.dp).clickable { showDialog = true }
            ) {
                Icon(imageVector = Icons.Outlined.Info, contentDescription = "Device Info", Modifier.size(28.dp))
            }
            if (showDialog) {
                DeviceDialog(onDismiss = { showDialog = false }, device)
            }
        }
    }
}

@Composable
fun HeaderRow() {
    Row(
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(0.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(
            modifier = Modifier.weight(0.05f).padding(top = 4.dp, bottom = 4.dp)
        )
        Text(
            "Tuner",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.2f).padding(top = 4.dp, bottom = 4.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Channel",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.2f).padding(top = 4.dp, bottom = 4.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Strength",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.16f).padding(top = 4.dp, bottom = 4.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Quality",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.15f).padding(top = 4.dp, bottom = 4.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Errors",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.14f).padding(top = 4.dp, bottom = 4.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(
            modifier = Modifier.weight(0.1f).padding(top = 4.dp, bottom = 4.dp)
        )
    }
}

@Composable
fun DataRow(
    tunerService: TunerService,
    tuner: Tuner,
    tunerStateFlowData: TunerState?,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = tuner.id.toString(),
            modifier = Modifier.weight(1f)
                .clickable { expanded = true },
            textAlign = TextAlign.Center,
            fontSize = 16.sp
        )
        if (tunerStateFlowData?.channelNumber != null) {
            Text(
                text = "Ch. " + tunerStateFlowData.channelNumber + "\n" + (tunerStateFlowData.channelInfo?.let { "${it.identifier} ${it.callsign}" }
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
        CircularProgressBar(progress = (tunerStateFlowData?.tunerStatus?.ss)?.toFloat(), modifier = Modifier.weight(1f).clickable { expanded = true })
        CircularProgressBar(progress = (tunerStateFlowData?.tunerStatus?.snq)?.toFloat(), modifier = Modifier.weight(1f).clickable { expanded = true })
        CircularProgressBar(progress = (tunerStateFlowData?.tunerStatus?.seq)?.toFloat(), modifier = Modifier.weight(1f).clickable { expanded = true })
        Column(
            modifier = Modifier.padding(16.dp).clickable { expanded = true }
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Change channel..."
            )
        }
    }
    DropdownMenu(
        modifier = Modifier.padding(bottom = 30.dp),
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        tunerStateFlowData?.lineup?.forEach { (channel, deviceChannel) ->
            if (deviceChannel?.deviceFrequency != null) {
                DropdownMenuItem(
                    text = { Text(text = "$channel - ${deviceChannel.deviceFrequency?.guideNumber} ${deviceChannel.deviceFrequency?.guideName}") },
                    onClick = {
                        expanded = false
                        setChannel(tunerService, tuner, channel.toLong())
                    }
                )
            } else {
                DropdownMenuItem(
                    text = { Text(text = "$channel - (no signal)", color = Color.Red) },
                    onClick = {
                        expanded = false
                        setChannel(tunerService, tuner, channel.toLong())
                    }
                )
            }
        }
    }
}

fun setChannel(tunerService: TunerService, tuner: Tuner, channel: Long) {
    try {
        tunerService.setChannel(tuner, channel)
    } catch (e: HdhrException) {
        println(e.message)
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

@Composable
fun DeviceDialog(onDismiss: () -> Unit, device: Device) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    text = "Device Information",
                    style = MaterialTheme.typography.headlineSmall, // Headline style
                    modifier = Modifier.padding(bottom = 8.dp) // Space below heading
                )
                Row(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(text = "ID: ", fontWeight = FontWeight.Bold)
                    Text(text = device.id)
                }
                Row(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(text = "IP: ", fontWeight = FontWeight.Bold)
                    Text(text = device.ip?.hostAddress ?: "(ip not available)")
                }
                Row(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(text = "Model: ", fontWeight = FontWeight.Bold)
                    Text(text = device.deviceDetails?.model ?: "not available")
                }
                Row(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(text = "Channel Maps: ", fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(text = device.deviceDetails?.features?.channelMap?.toString() ?: "not available")
                }
                Row {
                    Text(text = "Modulation: ", fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(text = device.deviceDetails?.features?.modulation?.toString() ?: "not available")
                }
                Row {
                    Text(text = "Auto Modulation: ", fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(text = device.deviceDetails?.features?.autoModulation?.toString() ?: "not available")
                }
                Row(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(text = "Version: ", fontWeight = FontWeight.Bold)
                    Text(device.deviceDetails?.version ?: "not available")
                }
                Row {
                    Text(text = "Copyright: ", fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(device.deviceDetails?.copyright ?: "not available")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(1f)) {
                    Text("OK")
                }
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    var isLogoVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(1000)
        isLogoVisible = true
    }

    SignalStatisticsTheme {
        Surface(
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedVisibility(
                        visible = isLogoVisible,
                        enter = fadeIn()
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.signalstatistics), // Replace with your logo
                            contentDescription = "Signal Statistics",
                            modifier = Modifier.size(150.dp),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}