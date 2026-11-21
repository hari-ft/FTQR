package com.hari.ftqr.history

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.hari.ftqr.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hari.ftqr.data.ScanEntity
import com.hari.ftqr.data.ScanRepository
import com.hari.ftqr.model.ContentKind
import com.hari.ftqr.ui.contentKindOf
import com.hari.ftqr.ui.icon
import com.hari.ftqr.ui.relativeTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)
@Composable
fun HistoryScreen(repository: ScanRepository, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var kindFilter by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val scansFlow = remember(repository) {
        snapshotFlow { query.trim() to kindFilter }
            .distinctUntilChanged()
            .flatMapLatest { (q, k) -> repository.observe(q, k) }
    }
    val scans by scansFlow.collectAsStateWithLifecycle<List<ScanEntity>?>(initialValue = null)
    val kinds by remember(repository) { repository.observeKinds() }
        .collectAsStateWithLifecycle<List<String>?>(initialValue = null)
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deleting = remember { mutableSetOf<Long>() }

    LaunchedEffect(kinds, kindFilter) {
        val available = kinds ?: return@LaunchedEffect
        if (kindFilter.isNotEmpty() && kindFilter !in available) kindFilter = ""
    }

    fun deleteWithUndo(scan: ScanEntity) {
        if (!deleting.add(scan.id)) return
        scope.launch {
            try {
                repository.delete(scan.id)
                val result = snackbar.showSnackbar("Deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) repository.restore(scan)
            } finally {
                deleting.remove(scan.id)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (!kinds.isNullOrEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear history")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search history") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Clear search") }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val sortedKinds = kinds.orEmpty().map(::contentKindOf).distinct().sortedBy { it.ordinal }
            if (sortedKinds.size > 1 || kindFilter.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(selected = kindFilter.isEmpty(), onClick = { kindFilter = "" }, label = { Text("All") })
                    }
                    items(sortedKinds) { kind ->
                        FilterChip(
                            selected = kindFilter == kind.name,
                            onClick = { kindFilter = if (kindFilter == kind.name) "" else kind.name },
                            label = { Text(kind.label) },
                            leadingIcon = { Icon(kind.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
            }
            val current = scans
            if (current == null) {
                Box(Modifier.fillMaxSize())
            } else if (current.isEmpty()) {
                EmptyHistory(searching = query.isNotBlank() || kindFilter.isNotEmpty())
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    items(current, key = { it.id }) { scan ->
                        HistoryRow(scan, onOpen = { onOpen(scan.id) }, onDelete = { deleteWithUndo(scan) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all history?") },
            text = { Text("This removes every saved scan. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { repository.deleteAll() }
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HistoryRow(scan: ScanEntity, onOpen: () -> Unit, onDelete: () -> Unit) {
    val kind: ContentKind = contentKindOf(scan.kind)
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        leadingContent = { Icon(kind.icon, contentDescription = kind.label) },
        headlineContent = { Text(scan.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                kind.label + " · " + relativeTime(scan.createdAt),
                maxLines = 1,
            )
        },
        trailingContent = {
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") }
        },
    )
}

@Composable
private fun EmptyHistory(searching: Boolean) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (searching) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.illus_empty_history),
                    contentDescription = null,
                    modifier = Modifier.size(200.dp),
                )
            }
            Text(
                if (searching) "No matching scans" else "Scans you make will appear here",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
