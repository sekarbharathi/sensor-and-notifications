package com.example.composetutorial


import SampleData
import android.app.Application
import android.content.Context
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
import androidx.compose.runtime.LaunchedEffect
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

    private val _userName = MutableStateFlow<String>("") // Start with an empty state.
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

class MainActivity : ComponentActivity() {
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

// SettingsScreen

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

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(onSettingsClicked = { navController.popBackStack() }, isBackButton = true)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
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