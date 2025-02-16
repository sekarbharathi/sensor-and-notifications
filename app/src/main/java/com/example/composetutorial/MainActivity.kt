package com.example.composetutorial


import SampleData
import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberAsyncImagePainter
import com.example.composetutorial.ui.theme.ComposeTutorialTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlin.math.abs

const val CHANNEL_ID = "notification_channel"
const val NOTIFICATION_PERMISSION_REQUEST_CODE = 101

//DataStore Setup

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

class UserPreferences(private val context: Context) {
    companion object {
        val USERNAME_KEY = stringPreferencesKey("username")
        val PROFILE_IMAGE_KEY = stringPreferencesKey("profile_image")
    }

    val userNameFlow = context.dataStore.data.map { preferences: Preferences ->
        preferences[USERNAME_KEY] ?: "Lexi"
    }
    val profileImageFlow = context.dataStore.data.map { preferences: Preferences ->
        preferences[PROFILE_IMAGE_KEY]
    }

    suspend fun saveUserName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[USERNAME_KEY] = name
        }
    }

    suspend fun saveProfileImage(uri: String) {
        context.dataStore.edit { preferences ->
            preferences[PROFILE_IMAGE_KEY] = uri
        }
    }
}

//View model
class UserViewModel(application: Application) : AndroidViewModel(application) {
    private val userPreferences = UserPreferences(application)

    private val _userName = MutableStateFlow("") // Start with an empty state.
    val userName: StateFlow<String> = _userName

    private val _profileImageUri = MutableStateFlow<String?>(null)
    val profileImageUri: StateFlow<String?> = _profileImageUri

    init {
        viewModelScope.launch {
            // Load stored username and profile image when the ViewModel starts.
            userPreferences.userNameFlow.collect { storedName ->
                // Only update if the value is not empty
                if (_userName.value.isEmpty()) {
                    _userName.value = storedName
                }
            }
        }
        viewModelScope.launch {
            userPreferences.profileImageFlow.collect { storedUri ->
                _profileImageUri.value = storedUri
            }
        }
    }

    fun updateUsername(newName: String) {
        viewModelScope.launch {
            // Save the new name to DataStore
            userPreferences.saveUserName(newName)
            // Force immediate update of the StateFlow with the new name
            _userName.value = newName
        }
    }

    fun updateProfileImage(uri: String) {
        viewModelScope.launch {
            // Save the new profile image URI to DataStore
            userPreferences.saveProfileImage(uri)
            // Update the profile image URI in the state
            _profileImageUri.value = uri
        }
    }
}


// MainActivity & Navigation
class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var isFirstLaunch = true // Flag to track the first launch
    private var isNotificationTriggered = false // Track if the rotation notification has been triggered
    private var isFirstRotationReading = true // Flag to track if first rotation sensor reading should be ignored
    private val threshold = 10.0 // Rotation threshold to trigger notification
    private var lastRotation: FloatArray? = null // Track the last known rotation to detect manual change
    private var isFirstRotationNotificationSent = false // Flag to track if first rotation alert has been sent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComposeTutorialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val userViewModel: UserViewModel = viewModel()
                    NavHost(navController = navController, startDestination = "mainScreen") {
                        composable("mainScreen") {
                            MainScreen(navController, userViewModel)
                        }
                        composable("settingsScreen") {
                            SettingsScreen(navController, userViewModel)
                        }
                    }
                }
            }
        }

        // Initialize sensor manager for rotation vector
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        }

        // Trigger test notification only once on app launch
        if (isFirstLaunch) {
            triggerTestNotification(this)
            isFirstLaunch = false // Ensure the test notification is only triggered once
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                val rotationMatrix = FloatArray(9)
                val orientationAngles = FloatArray(3)

                // Get the rotation matrix from the sensor event
                SensorManager.getRotationMatrixFromVector(rotationMatrix, it.values)

                // Compute pitch, roll, and azimuth from the rotation matrix
                val pitch = Math.toDegrees(Math.asin(rotationMatrix[6].toDouble())).toFloat()
                val roll = Math.toDegrees(Math.atan2(rotationMatrix[3].toDouble(), rotationMatrix[0].toDouble())).toFloat()
                val azimuth = Math.toDegrees(Math.atan2(rotationMatrix[7].toDouble(), rotationMatrix[8].toDouble())).toFloat()

                // Log values for debugging purposes
                Log.d("RotationSensor", "Azimuth: $azimuth, Pitch: $pitch, Roll: $roll")

                // Skip processing the first reading after launch to avoid false triggering
                if (isFirstRotationReading) {
                    isFirstRotationReading = false
                    return // Skip processing the first sensor reading
                }

                // Avoid triggering the rotation notification immediately after app launch
                if (isFirstRotationNotificationSent) {
                    // Only trigger rotation notification if the device rotation exceeds the threshold
                    val rotationDifference = lastRotation?.let {
                        val diffPitch = Math.abs(pitch - it[0])
                        val diffRoll = Math.abs(roll - it[1])
                        diffPitch > threshold || diffRoll > threshold
                    } ?: true

                    if (rotationDifference && !isNotificationTriggered) {
                        triggerNotification(this, pitch, roll)
                        isNotificationTriggered = true // Set to true after triggering the first notification
                    }
                } else {
                    // Set the flag to true after the first rotation reading
                    isFirstRotationNotificationSent = true
                }

                // Update last rotation for the next comparison
                lastRotation = floatArrayOf(pitch, roll)

                // Reset the notification trigger when the device is back to neutral position
                if (abs(pitch) < threshold && abs(roll) < threshold && isNotificationTriggered) {
                    isNotificationTriggered = false // Allow triggering again if the device is neutral
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    // Function to trigger the test notification on app launch
    fun triggerTestNotification(context: Context) {
        val channelId = "test_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Test Notifications", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Test App Launch ")
            .setContentText("Automatic notification on app launch!")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(0, notification)
    }

    // Function to trigger rotation notification when rotation exceeds threshold
    fun triggerNotification(context: Context, pitch: Float, roll: Float) {
        // Ensure the notification channel exists
        createNotificationChannel(context)

        // Check notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission not granted, show a message and exit
            Toast.makeText(context, "Please grant notification permission", Toast.LENGTH_SHORT).show()
            return
        }

        // Create an explicit intent to launch the MainActivity
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        // Create a PendingIntent with the proper flag
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE // or FLAG_MUTABLE if needed for your case
        )

        // Build the notification, including pitch and roll data
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Rotation Detected")
            .setContentText("Pitch: $pitch, Roll: $roll")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)  // Action when tapped
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setLights(0xFF0000, 500, 500)
            .build()

        // Show the notification
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(0, notification)
    }

}


data class Message(val author: String, val body: String, val profileImageUri: String? = null)

// MainScreen displays the conversation.
@Composable
fun MainScreen(navController: NavController, userViewModel: UserViewModel) {
    val userName by userViewModel.userName.collectAsState()
    val profileImageUri by userViewModel.profileImageUri.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(onSettingsClicked = {
                navController.navigate("settingsScreen") {
                    popUpTo("mainScreen") { inclusive = false }
                    launchSingleTop = true
                }
            })

            // Conditionally render UI once user data is available
            if (userName.isNotEmpty()) {
                // Wait until data is loaded before displaying the name
                Conversation(SampleData.conversationSample, userName, profileImageUri)
            } else {
                // Show CircularProgressIndicator while loading user data
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}


// Top bar with Settings or Back button.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(onSettingsClicked: () -> Unit, isBackButton: Boolean = false) {
    var isPressed by remember { mutableStateOf(false) }

    TopAppBar(
        modifier = Modifier.fillMaxWidth(),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
        title = { },
        navigationIcon = {
            if (isBackButton) {
                IconButton(
                    onClick = {
                        isPressed = !isPressed
                        onSettingsClicked()
                    },
                    modifier = Modifier.background(
                        color = if (isPressed) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back Icon"
                    )
                }
            }
        },
        actions = {
            if (!isBackButton) {
                IconButton(
                    onClick = {
                        isPressed = !isPressed
                        onSettingsClicked()
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = if (isPressed) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings Icon"
                    )
                }
            }
        }
    )
}


@Composable
fun MessageCard(msg: Message) {
    Row(modifier = Modifier.padding(all = 8.dp)) {
        val profileImagePainter: Painter = if (msg.profileImageUri != null) {
            rememberAsyncImagePainter(msg.profileImageUri)
        } else {
            painterResource(R.drawable.baseline_circle_24)
        }

        Image(
            painter = profileImagePainter,
            contentDescription = null,
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .border(1.5.dp, MaterialTheme.colorScheme.secondary, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))

        var isExpanded by remember { mutableStateOf(false) }
        val surfaceColor by animateColorAsState(
            targetValue = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            label = ""
        )

        Column(modifier = Modifier.clickable { isExpanded = !isExpanded }) {
            Text(
                text = msg.author,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 1.dp,
                color = surfaceColor,
                modifier = Modifier
                    .animateContentSize()
                    .padding(1.dp)
            ) {
                Text(
                    text = msg.body,
                    modifier = Modifier.padding(all = 4.dp),
                    maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun Conversation(messages: List<Message>, userName: String, profileImageUri: String?) {
    LazyColumn {
        items(messages) { message ->
            MessageCard(message.copy(author = userName, profileImageUri = profileImageUri))
        }
    }
}

@Composable
fun SettingsScreen(navController: NavController, userViewModel: UserViewModel) {
    val userName by userViewModel.userName.collectAsState()
    val profileImageUri by userViewModel.profileImageUri.collectAsState()
    val context = LocalContext.current

    val getImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                val imagePath = saveImageToInternalStorage(context, it)
                userViewModel.updateProfileImage(imagePath)
            }
        }
    )

    var notificationPermissionGranted by remember { mutableStateOf(false) }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                notificationPermissionGranted = true
                triggerNotification(context)
            } else {
                notificationPermissionGranted = false
            }
        }

    // Check for notification permission on screen load
    LaunchedEffect(Unit) {
        notificationPermissionGranted = checkNotificationPermission(context)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(onSettingsClicked = { navController.popBackStack() }, isBackButton = true)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Profile Image and Username Section (Unchanged)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = if (profileImageUri != null) {
                        rememberAsyncImagePainter(profileImageUri)
                    } else {
                        painterResource(R.drawable.baseline_circle_24)
                    },
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable {
                            getImage.launch("image/*")
                        }
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "User Name",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = userName,
                        onValueChange = { newName ->
                            userViewModel.updateUsername(newName)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            Button(
                onClick = {
                    // Request notification permission directly when the button is clicked
                    if (!notificationPermissionGranted) {
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            ) {
                Text(text = if (notificationPermissionGranted) "Notifications Enabled" else "Enable Notifications")
            }
        }
    }
}


fun checkNotificationPermission(context: Context): Boolean {
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // For Android 13 and above
        notificationManager.areNotificationsEnabled()
    } else {
        // For older versions, notifications are always enabled
        true
    }
}

fun requestNotificationPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1
            )
        } else {
            // Permission already granted, trigger notification
            triggerNotification(context)
        }
    }
}



fun saveImageToInternalStorage(context: Context, uri: Uri): String {
    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
    val file = File(context.filesDir, "profile_image.jpg")
    val outputStream = FileOutputStream(file)
    inputStream?.copyTo(outputStream)
    inputStream?.close()
    outputStream.close()
    return file.absolutePath
}

fun triggerNotification(context: Context) {
    // Ensure the notification channel exists
    createNotificationChannel(context)

    // Check notification permission
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        // Permission not granted, show a message and exit
        Toast.makeText(context, "Please grant notification permission", Toast.LENGTH_SHORT).show()
        return
    }

    // Create an explicit intent to launch the MainActivity
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }

    // Create a PendingIntent with the proper flag
    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_IMMUTABLE // or FLAG_MUTABLE if needed for your case
    )

    // Build the notification
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Notification Enabled")
        .setContentText("You will be notified when device rotates!")
        .setPriority(NotificationCompat.PRIORITY_HIGH) // High priority for banners
        .setCategory(NotificationCompat.CATEGORY_MESSAGE) // Categorize as a message
        .setContentIntent(pendingIntent)  // Action when tapped
        .setAutoCancel(true)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Make it visible to everyone
        .setDefaults(NotificationCompat.DEFAULT_ALL)  // Play sound, vibrate, etc.
        .setLights(0xFF0000, 500, 500)  // Optional: Add light if you want a visual cue
        .build()

    // Show the notification
    val notificationManager = NotificationManagerCompat.from(context)
    notificationManager.notify(0, notification)
}



fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Default Channel",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Channel for default notifications"
            enableLights(true)
            lightColor = Color.RED
            enableVibration(true)
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewMainScreen() {
    ComposeTutorialTheme {
        Surface {
            val userViewModel: UserViewModel = viewModel()
            MainScreen(navController = rememberNavController(), userViewModel = userViewModel)
        }
    }
}
