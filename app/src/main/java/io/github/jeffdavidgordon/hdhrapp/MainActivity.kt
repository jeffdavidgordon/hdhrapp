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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jeffdavidgordon.hdhrlib.model.Channel
import io.github.jeffdavidgordon.hdhrlib.model.Device
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
            AppContent(deviceMap.values.iterator().next())
        }
    }

    private fun getBroadcastAddress(context: Context): InetAddress? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
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
        } catch (e: UnknownHostException) {
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

@Composable
fun AppContent(device: Device) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        device.tuners.map { tuner ->
            val channels = TunerService.getStreamInfo(tuner)?.channels?.values

            var channelInfo: Channel? = null
            if (!channels?.isEmpty()!!) {
                channelInfo = channels.iterator().next()
            }

            val channel = TunerService.getChannel(tuner)?.channel
            val status = TunerService.getStatus(tuner)

            if (channel == null) {
                NoDataRow(tuner.id)
            } else {
                DataRow(
                    tunerNumber = tuner.id,
                    channel = channel,
                    description = channelInfo?.let { "${it.identifier} ${it.callsign}" } ?: "No Signal",
                    progress1 = (status.ss).toFloat(),
                    progress2 = (status.snq).toFloat(),
                    progress3 = (status.seq).toFloat()
                )
            }
        }
    }
}

@Composable
fun DataRow(
    tunerNumber: Int,
    channel: Int?,
    description: String,
    progress1: Float,
    progress2: Float,
    progress3: Float
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$tunerNumber",
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontSize = 16.sp
        )
        Text(
            text = "Ch. $channel\n$description",
            modifier = Modifier.weight(2f),
            textAlign = TextAlign.Center,
            fontSize = 14.sp
        )
        CircularProgressBar(progress = progress1, modifier = Modifier.weight(1f))
        CircularProgressBar(progress = progress2, modifier = Modifier.weight(1f))
        CircularProgressBar(progress = progress3, modifier = Modifier.weight(1f))
    }
}

@Composable
fun NoDataRow(
    tunerNumber: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$tunerNumber",
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontSize = 16.sp
        )
        Text(
            text = "No Channel",
            modifier = Modifier.weight(2f),
            textAlign = TextAlign.Center,
            fontSize = 14.sp
        )
        CircularProgressBar(progress = 0.0F, modifier = Modifier.weight(1f))
        CircularProgressBar(progress = 0.0F, modifier = Modifier.weight(1f))
        CircularProgressBar(progress = 0.0F, modifier = Modifier.weight(1f))
    }
}


@Composable
fun CircularProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val red = Color.Red
    val green = Color.Green
    val fraction = progress.coerceIn(0F, 100F) / 100F
    Box(
        modifier = modifier.size(50.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { progress / 100 },
            modifier = Modifier.fillMaxSize(),
            color = lerp(red, green, fraction),
            strokeWidth = 6.dp,
        )
        Text(
            text = "${progress.toInt()}%",
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}
/*
@Preview(showBackground = true)
@Composable
fun PreviewAppContent() {
    AppContent()
}*/