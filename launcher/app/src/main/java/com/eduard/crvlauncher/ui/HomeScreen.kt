package com.eduard.crvlauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.eduard.crvlauncher.Config
import com.eduard.crvlauncher.R
import com.eduard.crvlauncher.data.GeoFix
import com.eduard.crvlauncher.data.ObdState
import java.io.File
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Acciones de la pantalla de inicio; MainActivity las conecta a las apps reales. */
class HomeActions(
    val openNavigation: () -> Unit,
    val openAndroidAuto: () -> Unit,
    val openCarPlay: () -> Unit,
    val openEqualizer: () -> Unit,
    val openBluetooth: () -> Unit,
    /** Toque en el logo de Chevrolet. */
    val openApps: () -> Unit,
    /** Toque largo en el logo de Chevrolet. */
    val openSettings: () -> Unit,
)

data class HomeState(
    /** RPM, temperatura y voltaje del adaptador OBD2 (null = sin dato, se muestra "--"). */
    val obd: ObdState = ObdState(),
    val bluetoothOn: Boolean = false,
    /** Posición GPS en vivo; null hasta que el estéreo tenga una. */
    val fix: GeoFix? = null,
    /** Mapa offline del estéreo; null si todavía no se copió. */
    val mapFile: File? = null,
)

// Medidas tomadas de la imagen de referencia (1672 x 941); los pesos mantienen las proporciones.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(state: HomeState, actions: HomeActions) {
    val obd = state.obd
    val speed = state.fix?.speedKmh?.toInt() ?: 0

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // Auto al centro
        Image(
            painterResource(R.drawable.corsa), null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 100.u)
                .fillMaxWidth(0.5f)
                .height(470.u),
        )

        // Columna izquierda: RPM, temperatura, voltaje
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 18.u, top = 95.u)
                .fillMaxWidth(0.25f)
                .fillMaxHeight(0.63f),
            verticalArrangement = Arrangement.spacedBy(40.u),
        ) {
            RpmCard(obd.rpm, Modifier.weight(217f).fillMaxWidth())
            StatCard(
                Icons.Rounded.Thermostat, "TEMP. MOTOR",
                obd.coolantC?.toString() ?: "--", "°C",
                Modifier.weight(153f).fillMaxWidth(0.88f),
            )
            StatCard(
                Icons.Rounded.BatteryFull, "VOLTAJE",
                obd.voltage?.let { String.format(Locale.US, "%.1f", it) } ?: "--", "V",
                Modifier.weight(140f).fillMaxWidth(0.82f),
            )
        }

        // Columna derecha: CarPlay, Android Auto, Navegación
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = 22.u, top = 95.u)
                .fillMaxWidth(0.25f)
                .fillMaxHeight(0.6f),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(36.u),
        ) {
            AppTile("CarPlay", actions.openCarPlay, Modifier.weight(143f).fillMaxWidth()) {
                CarPlayMark(88.u)
            }
            AppTile("Android Auto", actions.openAndroidAuto, Modifier.weight(132f).fillMaxWidth(0.91f)) {
                AutoMark(84.u)
            }
            NavTile(state, actions.openNavigation, Modifier.weight(222f).fillMaxWidth(0.89f))
        }

        // Velocidad (GPS) al centro, sobre la fila de abajo
        TechCard(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 196.u)
                .fillMaxWidth(0.44f)
                .height(155.u),
        ) {
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.Bottom) {
                Label("$speed", 128, weight = FontWeight.Bold)
                Spacer(Modifier.width(24.u))
                Label("km/h", 56, color = Palette.TextSecondary, modifier = Modifier.padding(bottom = 14.u))
            }
        }

        // Fila de abajo: Ecualizador, logo (apps), Bluetooth
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 20.u, end = 22.u, bottom = 46.u)
                .height(130.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTile("Ecualizador", actions.openEqualizer, Modifier.weight(610f).fillMaxHeight(), 44) {
                Icon(
                    Icons.Rounded.Tune, null, tint = Color.White,
                    modifier = Modifier.size(72.u).rotate(90f),
                )
            }
            Box(
                Modifier
                    .weight(413f)
                    .fillMaxHeight()
                    .combinedClickable(onClick = actions.openApps, onLongClick = actions.openSettings),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painterResource(R.drawable.logo_chevrolet), null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(80.u),
                )
            }
            AppTile("Bluetooth", actions.openBluetooth, Modifier.weight(607f).fillMaxHeight(), 44) {
                Icon(
                    if (state.bluetoothOn) Icons.Rounded.Bluetooth else Icons.Rounded.BluetoothDisabled,
                    null,
                    tint = if (state.bluetoothOn) Color(0xFF2F6BFF) else Palette.TextSecondary,
                    modifier = Modifier.size(80.u),
                )
            }
        }
    }
}

/** Tarjeta negra con borde blanco, como en la imagen de referencia. */
@Composable
private fun TechCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(topStart = 36.u, topEnd = 50.u, bottomEnd = 36.u, bottomStart = 36.u)
    val borderBrush = Brush.verticalGradient(
        listOf(Color(0xFFFFFFFF), Color(0x66FFFFFF), Color(0xFFE6E6E6)),
    )
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF14151A), Color(0xFF050506))))
            .border(2.u, borderBrush, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

@Composable
private fun RpmCard(rpm: Int?, modifier: Modifier) {
    TechCard(modifier) {
        Row(
            Modifier.fillMaxSize().padding(start = 26.u, end = 28.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RpmGauge(rpm, Modifier.size(150.u))
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Label("RPM", 32)
                Label(
                    rpm?.let { String.format(Locale.US, "%.1f", it / 1000f) } ?: "--",
                    84, weight = FontWeight.Bold,
                )
                Label("x1000", 28)
            }
        }
    }
}

/** Reloj de 270° con aguja; escala hasta 8000 rpm. */
@Composable
private fun RpmGauge(rpm: Int?, modifier: Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.07f
        val arcSize = Size(size.width - stroke * 2, size.height - stroke * 2)
        val topLeft = Offset(stroke, stroke)
        val start = 135f
        val sweep = 270f
        val fraction = ((rpm ?: 0) / 8000f).coerceIn(0f, 1f)

        drawArc(
            Color(0x55FFFFFF), start, sweep, false, topLeft, arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        if (fraction > 0f) {
            drawArc(
                Color.White, start, sweep * fraction, false, topLeft, arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }

        val angle = Math.toRadians((start + sweep * fraction).toDouble())
        val center = Offset(size.width / 2, size.height / 2)
        val length = size.minDimension * 0.34f
        drawLine(
            Color.White, center,
            Offset(center.x + (cos(angle) * length).toFloat(), center.y + (sin(angle) * length).toFloat()),
            strokeWidth = stroke * 0.6f, cap = StrokeCap.Round,
        )
        drawCircle(Color.White, radius = stroke * 0.9f, center = center)
    }
}

@Composable
private fun StatCard(icon: ImageVector, title: String, value: String, unit: String, modifier: Modifier) {
    TechCard(modifier) {
        Row(
            Modifier.fillMaxSize().padding(start = 26.u, end = 22.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(72.u))
            Spacer(Modifier.width(22.u))
            Column {
                Label(title, 26)
                Row(verticalAlignment = Alignment.Bottom) {
                    Label(value, 62, weight = FontWeight.Bold)
                    Spacer(Modifier.width(10.u))
                    Label(unit, 38, modifier = Modifier.padding(bottom = 8.u))
                }
            }
        }
    }
}

@Composable
private fun AppTile(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier,
    titleSize: Int = 34,
    icon: @Composable () -> Unit,
) {
    TechCard(modifier, onClick) {
        Row(
            Modifier.fillMaxSize().padding(start = 26.u, end = 22.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(96.u), contentAlignment = Alignment.Center) { icon() }
            Spacer(Modifier.width(22.u))
            Label(title, titleSize, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, null, tint = Color.White, modifier = Modifier.size(44.u))
        }
    }
}

@Composable
private fun NavTile(state: HomeState, onClick: () -> Unit, modifier: Modifier) {
    // Sigue el GPS; sin señal, muestra la primera ciudad de Config.CITIES.
    val fix = state.fix
    val city = Config.CITIES.first()
    TechCard(modifier) {
        val mapFile = state.mapFile
        if (mapFile != null) {
            LiveMap(
                mapFile = mapFile,
                lat = fix?.lat ?: city.lat,
                lon = fix?.lon ?: city.lon,
                bearing = fix?.bearing,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(Modifier.align(Alignment.Center)) {
                Label("Mapa offline no instalado", 20, color = Palette.TextSecondary)
                Label("Copia $MAP_FILE_NAME al radio", 17, color = Palette.TextSecondary.copy(alpha = 0.7f))
            }
        }
        // La tarjeta es solo una vista previa: esta capa recibe el toque y abre la app de navegación.
        Box(Modifier.fillMaxSize().clickable(onClick = onClick))
        Box(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(70.u)
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))),
        )
        Label(
            "Navegación", 32,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 22.u, top = 14.u),
        )
    }
}
