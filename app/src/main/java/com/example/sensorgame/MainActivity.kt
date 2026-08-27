package com.example.sensorgame

import android.graphics.Paint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.modifier.modifierLocalConsumer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.nio.file.WatchEvent
import kotlin.math.roundToInt
// Class 1 SENSORS
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.Toast
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive


// fixed on screen the size of the ball
private const val BALL_SIZE = 60

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SensorGameScreen()
                }
            }
        }
    }
}

@Composable
fun SensorGameScreen() {
    // LocalDensity converts dp to pixels so we can drag with our cursor/sensor
    val density = LocalDensity.current
    val ballPx = with(density) { BALL_SIZE.dp.toPx() }

    // ballX/ballY = balls top-left corner, in pixels, inside the game area
    var ballX by remember { mutableFloatStateOf(0f) }
    var ballY by remember { mutableFloatStateOf(0f) }

    // area - sets the games area state, gets filled in once
    var areaWidthPx by remember { mutableFloatStateOf(0f) }
    var areaHeightPx by remember { mutableFloatStateOf(0f) }
    var score by remember { mutableIntStateOf(0) }
    var gpsText by remember { mutableStateOf("")}

    // Gyroscope
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val gyroscope = remember { sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }
    val hasGyroscope = gyroscope != null

    // Live gyroscope readings: updated by the event listener
    var gyroX by remember { mutableFloatStateOf(0f) }
    var gyroY by remember { mutableFloatStateOf(0f) }

    // pixels the ball moves per gyro unit (per frame)
    val baseSpeed = 5f


    LaunchedEffect(Unit) {
        if (!hasGyroscope) Toast.makeText(context, "No gyroscope — use touch controls", Toast.LENGTH_LONG).show()
    }

    fun moveBallWithGyro() {
        if(!hasGyroscope) return
        // ASSIGNMENT 1
        // 1. Update ballX based on the horizontal gyroscope reading and baseSpeed
        // 2. Update ballY based on the vertical gyroscope reading and baseSpeed
        // 3. Keep the ball on screen — see Hour 1, Topic 5 for the technique
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // ASSIGNMENT 1
                // 1. Check which sensor this event came from
                // 2. If it's the gyroscope, store its readings so moveBallWithGyro() can use them
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME ->
                    gyroscope?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
                Lifecycle.Event.ON_PAUSE ->
                    sensorManager.unregisterListener(listener)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            sensorManager.unregisterListener(listener)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(33L)
            moveBallWithGyro()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                if (areaWidthPx == 0f) { // runs once the Box has been measured
                    areaWidthPx = coords.size.width.toFloat()
                    areaHeightPx = coords.size.width.toFloat()
                    ballX = (areaWidthPx - ballPx) / 2f
                    ballY = (areaHeightPx - ballPx) / 2f
                }
            }
            .pointerInput(Unit) {// Touch-to-drag: lets emulator move ball with cursor
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    ballX = (ballX + dragAmount.x).coerceIn(0f, areaWidthPx - ballPx)
                    ballY = (ballY + dragAmount.y).coerceIn(0f, areaHeightPx - ballPx)
                }
            }
    ) {
        Text( // GPS text - at the top left corner (class 3)
            text = gpsText,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
        )

        Text( // Score count at top right corner
            text = "Score: $score",
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
        )

        Box( // the ball - a plain circle colored blue. offset{} takes a lambda so moving the ball only re-runs placement
            modifier = Modifier
                .offset { IntOffset(ballX.roundToInt(), ballY.roundToInt()) }
                .size(BALL_SIZE.dp)
                .background(Color.Blue, CircleShape)
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = {/* class 3 */}, enabled = false) {
                Text("Shake")
            }
            Button(onClick = {/*class 3 */}, enabled = false) {
                Text("GPS + 10")
            }
        }
    }
}







































































