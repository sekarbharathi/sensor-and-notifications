package com.example.composetutorial

import SampleData
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.composetutorial.ui.theme.ComposeTutorialTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComposeTutorialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "mainScreen") {
                        composable("mainScreen") { MainScreen(navController) }
                        composable("settingsScreen") { SettingsScreen(navController) }
                    }
                }
            }
        }
    }
}

data class Message(val author: String, val body: String)

@Composable
fun MainScreen(navController: androidx.navigation.NavController) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(onSettingsClicked = {
            // Navigate to settings screen and clear the back stack
            navController.navigate("settingsScreen") {
                popUpTo("mainScreen") { inclusive = true }
                launchSingleTop = true
            }
        })
        Conversation(SampleData.conversationSample)
    }

    // Handle the back gesture to close the app from MainScreen
    BackHandler {
        // Exit the app or return to the home screen
        android.os.Process.killProcess(android.os.Process.myPid()) // This forces the app to close
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(onSettingsClicked: () -> Unit, isBackButton: Boolean = false) {
    var isPressed by remember { mutableStateOf(false) }

    TopAppBar(
        modifier = Modifier.fillMaxWidth(),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
        title = { Text(text = if (isBackButton) "Settings" else "Messages") },
        navigationIcon = {
            if (isBackButton) {
                IconButton(
                    onClick = {
                        isPressed = !isPressed
                        onSettingsClicked() },
                    modifier = Modifier
                        .background(
                            color = if (isPressed) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
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
                        contentDescription = "Settings"
                    )
                }
            }
        }
    )
}

@Composable
fun MessageCard(msg: Message) {
    Row(modifier = Modifier.padding(all = 8.dp)) {
        Image(
            painter = painterResource(R.drawable.profile_picture),
            contentDescription = null,
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .border(1.5.dp, MaterialTheme.colorScheme.secondary, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))

        var isExpanded by remember { mutableStateOf(false) }
        val surfaceColor by animateColorAsState(
            if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
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
                modifier = Modifier.animateContentSize().padding(1.dp)
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
fun Conversation(messages: List<Message>) {
    LazyColumn {
        items(messages) { message ->
            MessageCard(message)
        }
    }
}

@Composable
fun SettingsScreen(navController: androidx.navigation.NavController) {
    var userName by remember { mutableStateOf("Lexi") }

    // Handle back gesture to navigate to the Main Screen
    BackHandler {
        navController.navigate("mainScreen") {
            popUpTo("mainScreen") { inclusive = true }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // TopBar with Back Button
        TopBar(
            onSettingsClicked = {
                // When navigating back to MainScreen, prevent circular navigation
                navController.navigate("mainScreen") {
                    popUpTo("mainScreen") { inclusive = true }
                }
            },
            isBackButton = true
        )

        // Profile Section
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
                    painter = painterResource(R.drawable.profile_picture),
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
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
                        onValueChange = { userName = it },
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


@Preview
@Composable
fun PreviewMainScreen() {
    ComposeTutorialTheme {
        Surface {
            MainScreen(navController = rememberNavController())
        }
    }
}

@Preview
@Composable
fun PreviewSettingsScreen() {
    ComposeTutorialTheme {
        Surface {
            SettingsScreen(navController = rememberNavController())
        }
    }
}
