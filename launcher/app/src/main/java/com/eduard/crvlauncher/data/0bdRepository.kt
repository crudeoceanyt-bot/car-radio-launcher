package com.eduard.crvlauncher.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** null = sin dato (el diseño muestra "--"). */
data class ObdState(
    val connected: Boolean = false,
    val rpm: Int? = null,
    val coolantC: Int? = null,
    val voltage: Float? = null,
)

/**
 * Lee RPM, temperatura del motor y voltaje desde un adaptador ELM327 Bluetooth (clásico, no BLE).
 * El adaptador debe estar VINCULADO antes, desde los ajustes de Bluetooth del estéreo.
 */
class ObdRepository(private val context: Context) {

    private val _state = MutableStateFlow(ObdState())
    val state: StateFlow<ObdState> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                try {
                    session()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Sin permiso, sin adaptador o conexión caída: se reintenta abajo.
                }
                _state.value = ObdState()
                delay(RETRY_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.value = ObdState()
    }

    @SuppressLint("MissingPermission")
    private suspend fun session() {
        if (!hasBluetoothPermission()) return
        @Suppress("DEPRECATION")
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        if (!adapter.isEnabled) return
        val device = adapter.bondedDevices.firstOrNull { looksLikeObd(it) } ?: return

        adapter.cancelDiscovery()
        val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        try {
            socket.connect()
            val input = socket.inputStream
            val output = socket.outputStream

            // Inicialización del ELM327: reset, sin eco, sin saltos de línea, sin espacios, protocolo automático.
            query(input, output, "ATZ", timeoutMs = 3_000)
            for (cmd in listOf("ATE0", "ATL0", "ATS0", "ATSP0")) query(input, output, cmd)
            _state.value = ObdState(connected = true)

            var tick = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val rpm = parseRpm(query(input, output, "010C"))
                var current = _state.value.copy(connected = true, rpm = rpm)
                // Temperatura y voltaje cambian lento: se leen cada ~2 s.
                if (tick % 10 == 0) {
                    current = current.copy(
                        coolantC = parseCoolant(query(input, output, "0105")),
                        voltage = parseVoltage(query(input, output, "ATRV")),
                    )
                }
                _state.value = current
                tick++
                delay(100)
            }
        } finally {
            runCatching { socket.close() }
        }
    }

    /** Envía un comando y espera hasta el prompt '>' del ELM327. */
    private suspend fun query(
        input: InputStream,
        output: OutputStream,
        command: String,
        timeoutMs: Int = 2_000,
    ): String {
        output.write((command + "\r").toByteArray())
        output.flush()
        val sb = StringBuilder()
        var waited = 0
        while (waited < timeoutMs) {
            while (input.available() > 0) {
                val c = input.read().toChar()
                if (c == '>') return sb.toString()
                sb.append(c)
            }
            delay(20)
            waited += 20
        }
        return sb.toString()
    }

    /** Bytes de datos que siguen a la cabecera de respuesta (ej. "410C"). */
    private fun dataBytes(response: String, header: String): List<Int> {
        val clean = response.filter { !it.isWhitespace() }.uppercase()
        val i = clean.indexOf(header)
        if (i < 0) return emptyList()
        return clean.substring(i + header.length)
            .chunked(2)
            .filter { it.length == 2 }
            .mapNotNull { it.toIntOrNull(16) }
    }

    private fun parseRpm(response: String): Int? {
        val b = dataBytes(response, "410C")
        return if (b.size >= 2) (b[0] * 256 + b[1]) / 4 else null
    }

    private fun parseCoolant(response: String): Int? {
        val b = dataBytes(response, "4105")
        return if (b.isNotEmpty()) b[0] - 40 else null
    }

    /** ATRV responde algo como "12.2V" y funciona aunque la ECU no soporte OBD estándar. */
    private fun parseVoltage(response: String): Float? =
        Regex("""(\d+(?:\.\d+)?)\s*V""", RegexOption.IGNORE_CASE)
            .find(response)?.groupValues?.get(1)?.toFloatOrNull()

    @SuppressLint("MissingPermission")
    private fun looksLikeObd(device: BluetoothDevice): Boolean {
        val name = device.name?.lowercase() ?: return false
        return NAME_HINTS.any { name.contains(it) }
    }

    private fun hasBluetoothPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            Manifest.permission.BLUETOOTH
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        /** Si el nombre de tu adaptador no contiene ninguna de estas palabras, agrégala aquí. */
        val NAME_HINTS = listOf("obd", "elm", "vlink", "v-link", "veepeak", "viecar", "konnwei", "icar")

        const val RETRY_MS = 5_000L
    }
}
