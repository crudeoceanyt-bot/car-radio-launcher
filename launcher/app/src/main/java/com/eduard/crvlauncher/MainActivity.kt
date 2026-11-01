package com.eduard.crvlauncher

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduard.crvlauncher.data.AppEntry
import com.eduard.crvlauncher.data.Apps
import com.eduard.crvlauncher.data.LocationRepository
import com.eduard.crvlauncher.data.ObdRepository
import com.eduard.crvlauncher.ui.AppBackground
import com.eduard.crvlauncher.ui.AppDrawer
import com.eduard.crvlauncher.ui.DESIGN_H
import com.eduard.crvlauncher.ui.DESIGN_W
import com.eduard.crvlauncher.ui.HomeActions
import com.eduard.crvlauncher.ui.HomeScreen
import com.eduard.crvlauncher.ui.HomeState
import com.eduard.crvlauncher.ui.LocalUnit
import com.eduard.crvlauncher.ui.findMapFile
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var location: LocationRepository
    private lateinit var obd: ObdRepository
    private var mapFile by mutableStateOf<File?>(null)
    private var bluetoothOn by mutableStateOf(false)
    private var drawerApps by mutableStateOf<List<AppEntry>?>(null)

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshBluetooth()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        location = LocationRepository(applicationContext)
        obd = ObdRepository(applicationContext)

        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        )
        // Android 12+ pide BLUETOOTH_CONNECT en tiempo de ejecución (lo usa el adaptador OBD2).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }
        requestPermissions(permissions.toTypedArray(), 1)

        val actions = HomeActions(
            openNavigation = { open(Config.NAVIGATION, "Navegación") },
            openAndroidAuto = { open(Config.ANDROID_AUTO, "Android Auto") },
            openCarPlay = { open(Config.CARPLAY, "CarPlay") },
            openEqualizer = { openEqualizer() },
            openBluetooth = { openBluetooth() },
            // En el diseño no hay botón de apps: ábrelo con el logo de Chevrolet.
            openApps = { drawerApps = Apps.installedLaunchables(this) },
            openSettings = { Apps.openSettings(this) },
        )

        setContent {
            val obdState by obd.state.collectAsStateWithLifecycle()
            val fix by location.fix.collectAsStateWithLifecycle()

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val unit = minOf(maxWidth / DESIGN_W, maxHeight / DESIGN_H)
                CompositionLocalProvider(LocalUnit provides unit) {
                    AppBackground()
                    val apps = drawerApps
                    if (apps != null) {
                        BackHandler { drawerApps = null }
                        AppDrawer(
                            apps,
                            onLaunch = { Apps.launch(this@MainActivity, it.packageName); drawerApps = null },
                            onClose = { drawerApps = null },
                        )
                    } else {
                        BackHandler { /* pantalla de inicio: ignorar "atrás" */ }
                        HomeScreen(
                            HomeState(
                                obd = obdState,
                                bluetoothOn = bluetoothOn,
                                fix = fix,
                                mapFile = mapFile,
                            ),
                            actions,
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        location.start()
        obd.start()
        mapFile = findMapFile(this)
        registerReceiver(btReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        refreshBluetooth()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        location.start()
        obd.start()
        mapFile = findMapFile(this)
    }

    override fun onStop() {
        location.stop()
        obd.stop()
        unregisterReceiver(btReceiver)
        super.onStop()
    }

    /** Pulsar Home estando ya en Home cierra el cajón de apps. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        drawerApps = null
    }

    private fun refreshBluetooth() {
        @Suppress("DEPRECATION")
        bluetoothOn = runCatching { BluetoothAdapter.getDefaultAdapter()?.isEnabled == true }.getOrDefault(false)
    }

    private fun open(candidates: List<String>, what: String) {
        if (!Apps.launchAny(this, candidates)) Apps.notInstalled(this, what)
    }

    /** Primero la app de ecualizador del estéreo; si no existe, el panel de audio de Android. */
    private fun openEqualizer() {
        if (Apps.launchAny(this, Config.EQUALIZER)) return
        val panel = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0)
            .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        runCatching { startActivity(panel) }
            .onFailure { Apps.notInstalled(this, "Ecualizador") }
    }

    /** Primero la app de Bluetooth/teléfono del estéreo; si no existe, los ajustes de Bluetooth. */
    private fun openBluetooth() {
        if (Apps.launchAny(this, Config.BLUETOOTH)) return
        runCatching {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Apps.openSettings(this) }
    }
}
