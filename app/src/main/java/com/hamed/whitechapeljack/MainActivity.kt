package com.hamed.whitechapeljack

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        setContent { WhitechapelApp() }
    }
}

enum class Screen { SETUP, JACK, DETECTIVES, UNLOCK }
enum class MoveType { NORMAL, COACH, ALLEY }

data class JackMove(
    val night: Int,
    val turn: Int,
    val location: Int,
    val type: MoveType
)

class GameState {
    var pin by mutableStateOf("")
    var hideout by mutableIntStateOf(0)
    var night by mutableIntStateOf(1)
    var screen by mutableStateOf(Screen.SETUP)
    val moves = mutableStateListOf<JackMove>()

    fun addMove(location: Int, type: MoveType) {
        moves += JackMove(night, moves.count { it.night == night } + 1, location, type)
    }

    // A searched location is a clue if Jack has visited it during the current night.
    fun clueAt(location: Int): Boolean =
        moves.any { it.night == night && it.location == location }

    // Arrest tests Jack's CURRENT location only.
    fun arrestAt(location: Int): Boolean =
        moves.lastOrNull { it.night == night }?.location == location

    fun nextNight() {
        if (night < 4) night++
    }
}

@Composable
fun WhitechapelApp() {
    val game = remember { GameState() }
    MaterialTheme(
        colorScheme = darkColorScheme()
    ) {
        Surface(Modifier.fillMaxSize()) {
            when (game.screen) {
                Screen.SETUP -> SetupScreen(game)
                Screen.JACK -> JackScreen(game)
                Screen.DETECTIVES -> DetectiveScreen(game)
                Screen.UNLOCK -> UnlockScreen(game)
            }
        }
    }
}

@Composable
private fun Page(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        content()
    }
}

@Composable
fun SetupScreen(game: GameState) {
    var pin by remember { mutableStateOf("") }
    var hideout by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Page("Jack's Secret Ledger") {
        Text("New game • London, 1888")
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(6) },
            label = { Text("Jack PIN (4–6 digits)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = hideout,
            onValueChange = { hideout = it.filter(Char::isDigit).take(3) },
            label = { Text("Hideout location") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Button(
            onClick = {
                val h = hideout.toIntOrNull()
                if (pin.length !in 4..6 || h == null || h !in 1..199) {
                    error = "Enter a 4–6 digit PIN and a location from 1 to 199."
                } else {
                    game.pin = pin
                    game.hideout = h
                    game.screen = Screen.JACK
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Start game") }
    }
}

@Composable
fun JackScreen(game: GameState) {
    var location by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(MoveType.NORMAL) }
    var error by remember { mutableStateOf("") }

    Page("Jack • Night ${game.night}") {
        Text("Hideout: ${game.hideout}  •  Moves: ${game.moves.count { it.night == game.night }}")
        OutlinedTextField(
            value = location,
            onValueChange = { location = it.filter(Char::isDigit).take(3) },
            label = { Text("Destination location") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MoveType.entries.forEach {
                FilterChip(
                    selected = type == it,
                    onClick = { type = it },
                    label = { Text(it.name) }
                )
            }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Button(onClick = {
            val n = location.toIntOrNull()
            if (n == null || n !in 1..199) error = "Location must be 1–199."
            else {
                game.addMove(n, type)
                location = ""
                error = ""
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Record secret move") }

        HorizontalDivider()
        Text("Secret move log")
        LazyColumn(Modifier.weight(1f)) {
            items(game.moves.filter { it.night == game.night }.reversed()) {
                Text("Turn ${it.turn}: ${it.location} • ${it.type}")
            }
        }
        Button(
            onClick = { game.screen = Screen.DETECTIVES },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Lock & pass to detectives") }
    }
}

@Composable
fun DetectiveScreen(game: GameState) {
    var location by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("No secret information is visible.") }

    Page("Detective Desk • Night ${game.night}") {
        Text("Enter the numbered location being searched or arrested.")
        OutlinedTextField(
            value = location,
            onValueChange = { location = it.filter(Char::isDigit).take(3) },
            label = { Text("Location 1–199") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                val n = location.toIntOrNull()
                result = when {
                    n == null || n !in 1..199 -> "Invalid location."
                    game.clueAt(n) -> "CLUE FOUND at location $n."
                    else -> "No clue at location $n."
                }
            }) { Text("Search clue") }

            Button(onClick = {
                val n = location.toIntOrNull()
                result = when {
                    n == null || n !in 1..199 -> "Invalid location."
                    game.arrestAt(n) -> "ARREST SUCCESSFUL — Jack is here."
                    else -> "Arrest failed."
                }
            }) { Text("Arrest") }
        }
        Card(Modifier.fillMaxWidth()) {
            Text(result, Modifier.padding(18.dp), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(
            onClick = { game.screen = Screen.UNLOCK },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Jack access") }
    }
}

@Composable
fun UnlockScreen(game: GameState) {
    var attempt by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Page("Restricted") {
        Text("Jack only")
        OutlinedTextField(
            value = attempt,
            onValueChange = { attempt = it.filter(Char::isDigit).take(6) },
            label = { Text("PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Button(onClick = {
            if (attempt == game.pin) game.screen = Screen.JACK
            else error = "Wrong PIN."
        }, modifier = Modifier.fillMaxWidth()) { Text("Unlock ledger") }

        OutlinedButton(onClick = { game.screen = Screen.DETECTIVES }) {
            Text("Back to detectives")
        }
    }
}
