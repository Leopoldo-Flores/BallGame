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
// CLASS 2
import androidx.compose.foundation.Image
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import kotlin.math.sqrt
import kotlin.random.Random
// CLASS 3
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch


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

    // Accelerometer
    val accelerometer = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    var lastShakeTime by remember { mutableStateOf(0L) }
    val shakeThreshold = 12f // m/s2 above gravity
    val shakeCooldownMs = 1500L // minimum ms between shakes

    // Ball colour cycle
    var colorIndex by remember { mutableIntStateOf(0) }
    val ballColors = remember {
        listOf(Color.Blue, Color.Red, Color.Green, Color.Magenta, Color.Yellow)
    }

    // Stars
    val starPx = with(density) { 32.dp.toPx() }
    val starCount = 5
    val stars = remember { mutableStateListOf<Offset>() }

    // FusedLocationProviderClient - google's recommended location API
    val fusedLocation = remember { LocationServices.getFusedLocationProviderClient(context) }
    var lastLocation by remember { mutableStateOf<Location?>(null) }
    val rewardDistanceM = 10f //meters walked before reward triggers
    val scope = rememberCoroutineScope()
    var gpsButtonEnable by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (!hasGyroscope) Toast.makeText(context, "No gyroscope — use touch controls", Toast.LENGTH_LONG).show()
    }

    fun moveBallWithGyro() {
        if(!hasGyroscope) return
        ballX = (ballX + gyroY * baseSpeed).coerceIn(0f, areaWidthPx - ballPx)
        ballY = (ballY + gyroX * baseSpeed).coerceIn(0f, areaHeightPx - ballPx)
        // ASSIGNMENT 1
        // 1. Update ballX based on the horizontal gyroscope reading and baseSpeed
        // 2. Update ballY based on the vertical gyroscope reading and baseSpeed
        // 3. Keep the ball on screen — see Hour 1, Topic 5 for the technique
    }

    fun spawnStars() {
        stars.clear()
        val bottomMarginPX = with(density) { 120.dp.toPx() }
        val safeHeight = (areaHeightPx - bottomMarginPX).coerceAtLeast(starPx * 2)
        repeat(starCount) {
            val x = Random.nextFloat() * (areaWidthPx - starPx)
            val y = Random.nextFloat() * (areaHeightPx - starPx)
            stars.add(Offset(x, y))
        }
    }

    fun checkStarCollisions() {
        val ballRight = ballX + ballPx
        val ballBottom = ballY + ballPx
        val collected = mutableListOf<Offset>()
        for (star in stars) {
            if (ballX < star.x + starPx && ballRight > star.x &&
                ballY < star.y + starPx && ballBottom > star.y) {
                collected.add(star)
            }
        }
        for (star in collected) {
            stars.remove(star)
            score += 10
        }
        if (stars.isEmpty()) spawnStars()
    }

    fun onNewLocation(location: Location) { // requirement: display current location. rounds to 5 decimal
        gpsText = "Lat: ${"%.5f".format(location.latitude)}, " +
                "Lon: ${"%.5f".format(location.longitude)}, "
        // ASSIGNMENT 3
        // 1. If we've seen a location before, compare it against this new one
        // 2. Figure out how far the player has moved since that last fix
        // 3. If they've gone far enough, reward them and update your baseline
        // 4. Handle the very first fix, when there's nothing to compare against yet
    }

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) { // REMOVE THE S ON Result(s)
                result.lastLocation?.let { onNewLocation(it) }
            }
        }
    }


    fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()
        try {
            fusedLocation.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            gpsText = "Location Unavailable"
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startLocationUpdates() else gpsText = "Location Permission Denied"
    }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) startLocationUpdates() else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun onShakeDetected() {
        //ASSIGNMENT 3
        //1. Move to the next colour in the list, wrapping back to the start after the last one
        //2. Give the player some feedback that a shake was detected

    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_GYROSCOPE -> {
                        gyroX = event.values[0]
                        gyroY = event.values[1]
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        val ax = event.values[0]
                        val ay = event.values[1]
                        val az = event.values[2]
                        // val net= work out how much force is being applied beyond gravity
                        val now = System.currentTimeMillis()
                        //if (net > shakeThreshold && now - lastShakeTime > shakeCooldownMs) {
                            //lastShakeTime = now
                            //onShakeDetected()
                        //}
                    }
                }
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
                Lifecycle.Event.ON_PAUSE -> {
                    sensorManager.unregisterListener(listener)
                    fusedLocation.removeLocationUpdates(locationCallback)  // SET OF {} after ON_PAUSE -> {... }
                }                                                               //  CLOSE THE SET HERE }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            sensorManager.unregisterListener(listener)
            fusedLocation.removeLocationUpdates(locationCallback)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(33L)
            moveBallWithGyro()
            checkStarCollisions()
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
                    spawnStars()
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
                .background(ballColors[colorIndex], CircleShape)
        )

        stars.forEach { starPos ->
            Image(
                painter = painterResource(R.drawable.star_shape),
                contentDescription = null,
                modifier = Modifier
                    .offset{ IntOffset(starPos.x.roundToInt(), starPos.y.roundToInt()) }
                    .size(32.dp)
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = { onShakeDetected() }, enabled = true) {
                Text("Shake")
            }
            Button(onClick = {gpsButtonEnable = false  // before position: fixed location in LA
                val base = Location("test").apply { latitude = 34.0522; longitude = -118.2437 }
                                                       // after position: 0.0001 degrees north of "base": 11 meters
                val current = Location("test").apply { latitude = 34.0523; longitude = -118.2437  }
                lastLocation = base
                onNewLocation(current)
                scope.launch { delay(5000L); gpsButtonEnable = true }
            },
                enabled = gpsButtonEnable
            ) {
                Text("GPS + 10")
            }
        }
    }
}







































































