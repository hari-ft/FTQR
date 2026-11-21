package com.hari.ftqr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalContext
import com.hari.ftqr.data.ScanRepository
import com.hari.ftqr.generator.GeneratorScreen
import com.hari.ftqr.history.HistoryScreen
import com.hari.ftqr.result.ResultScreen
import com.hari.ftqr.scanner.ScannerScreen
import com.hari.ftqr.ui.FtqrTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FtqrTheme {
                Surface { FtqrApp() }
            }
        }
    }
}

private sealed interface Route {
    data object Scanner : Route
    data object History : Route
    data object Generator : Route
    data class Result(val scanId: Long) : Route
}

private fun Route.encode(): String = when (this) {
    Route.Scanner -> "scanner"
    Route.History -> "history"
    Route.Generator -> "generator"
    is Route.Result -> "result:$scanId"
}

private fun decodeRoute(value: String): Route = when {
    value == "history" -> Route.History
    value == "generator" -> Route.Generator
    value.startsWith("result:") -> value.removePrefix("result:").toLongOrNull()?.let { Route.Result(it) } ?: Route.Scanner
    else -> Route.Scanner
}

private val BackStackSaver = listSaver<SnapshotStateList<Route>, String>(
    save = { stack -> stack.map { it.encode() } },
    restore = { saved -> mutableStateListOf<Route>().apply { addAll(saved.map(::decodeRoute)) } },
)

@Composable
private fun FtqrApp() {
    val context = LocalContext.current
    val repository = remember { ScanRepository.from(context) }
    val scope = rememberCoroutineScope()
    val holder = rememberSaveableStateHolder()
    val backStack = rememberSaveable(saver = BackStackSaver) { mutableStateListOf<Route>(Route.Scanner) }

    fun push(route: Route) {
        if (backStack.last() != route) backStack.add(route)
    }

    fun pop(expected: Route): Boolean {
        if (backStack.size <= 1 || backStack.last() != expected) return false
        backStack.removeAt(backStack.lastIndex)
        if (expected !in backStack) holder.removeState(expected.encode())
        return true
    }

    BackHandler(enabled = backStack.size > 1) { pop(backStack.last()) }

    val route = backStack.last()
    holder.SaveableStateProvider(route.encode()) {
        when (route) {
            Route.Scanner -> ScannerScreen(
                onScanned = { raw, format ->
                    scope.launch {
                        val id = repository.record(raw, format)
                        if (backStack.last() == Route.Scanner) push(Route.Result(id))
                    }
                },
                onOpenHistory = { if (backStack.last() == Route.Scanner) push(Route.History) },
                onOpenGenerator = { if (backStack.last() == Route.Scanner) push(Route.Generator) },
            )
            Route.History -> HistoryScreen(
                repository = repository,
                onBack = { pop(Route.History) },
                onOpen = { id -> if (backStack.last() == Route.History) push(Route.Result(id)) },
            )
            Route.Generator -> GeneratorScreen(onBack = { pop(Route.Generator) })
            is Route.Result -> ResultScreen(
                scanId = route.scanId,
                repository = repository,
                onBack = { pop(route) },
                onDelete = { scan ->
                    if (pop(route)) scope.launch { repository.delete(scan.id) }
                },
            )
        }
    }
}
