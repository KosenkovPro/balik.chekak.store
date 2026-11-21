@file:OptIn(ExperimentalMaterial3Api::class)

package store.chekak.balik.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import store.chekak.balik.data.db.DatabaseFile
import store.chekak.balik.ui.files.FilePicker
import store.chekak.balik.ui.files.FilePickerMode
import store.chekak.balik.ui.files.FilePickerRequest
import store.chekak.balik.ui.navigation.LocalNavigator
import store.chekak.balik.ui.strings

/** Информация о файле базы, который нужно скопировать вручную. */
private data class DbExport(val path: String, val sizeBytes: Int)

/**
 * Резервная копия: экспорт базы в JSON или SQLite и восстановление из JSON-файла.
 */
@Composable
fun BackupScreen(vm: SettingsViewModel) {
    val s = strings
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var saveRequest by remember { mutableStateOf<FilePickerRequest?>(null) }
    var openRequest by remember { mutableStateOf<FilePickerRequest?>(null) }
    var exportJson by remember { mutableStateOf<String?>(null) }
    var exportFileName by remember { mutableStateOf<String?>(null) }
    var dbExport by remember { mutableStateOf<DbExport?>(null) }
    var busy by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { SettingsTopBar(title = s.backup, onBack = { navigator.pop() }) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (busy) {
                LinearProgressIndicator(
                    progress = { 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }

            BackupAction(
                title = s.exportJson,
                icon = Icons.Filled.Save,
                onClick = {
                    scope.launch {
                        busy = true
                        val json = runCatching { vm.exportJson() }.getOrNull()
                        busy = false
                        if (json != null) {
                            exportJson = json
                            exportFileName = null
                            saveRequest = FilePickerRequest(
                                id = System.currentTimeMillis(),
                                mode = FilePickerMode.Save,
                                suggestedName = "chekak-backup.json",
                            )
                        } else {
                            snackbarHostState.showSnackbar(s.importFailed)
                        }
                    }
                },
            )

            BackupAction(
                title = s.exportSqlite,
                icon = Icons.Filled.FileDownload,
                onClick = {
                    scope.launch {
                        busy = true
                        val bytes = runCatching { vm.exportSqliteBytes() }.getOrNull()
                        busy = false
                        if (bytes != null) {
                            dbExport = DbExport(path = DatabaseFile.path(), sizeBytes = bytes.size)
                        } else {
                            snackbarHostState.showSnackbar(s.importFailed)
                        }
                    }
                },
            )

            BackupAction(
                title = s.importBackup,
                icon = Icons.Filled.Folder,
                onClick = {
                    openRequest = FilePickerRequest(
                        id = System.currentTimeMillis(),
                        mode = FilePickerMode.Open,
                        mimeTypes = listOf("application/json", "text/plain", "*/*"),
                    )
                },
            )
        }
    }

    // Системные диалоги выбора файла: один на сохранение, один на открытие.
    FilePicker(
        request = saveRequest,
        onResult = { picked ->
            saveRequest = null
            exportFileName = picked?.name
        },
    )
    FilePicker(
        request = openRequest,
        onResult = { picked ->
            openRequest = null
            if (picked == null) return@FilePicker
            scope.launch {
                busy = true
                val content = String(picked.bytes, Charsets.UTF_8)
                val message = runCatching { vm.restoreJson(content) }
                    .fold(
                        onSuccess = { s.backupRestored },
                        onFailure = { s.importFailed },
                    )
                busy = false
                snackbarHostState.showSnackbar(message)
            }
        },
    )

    val json = exportJson
    if (json != null) {
        AlertDialog(
            onDismissRequest = { exportJson = null },
            title = { Text(text = s.exportJson) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val fileName = exportFileName
                    if (fileName != null) {
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedTextField(
                        value = json,
                        onValueChange = {},
                        readOnly = true,
                        minLines = 4,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { exportJson = null }) {
                    Text(text = s.done)
                }
            },
        )
    }

    val export = dbExport
    if (export != null) {
        AlertDialog(
            onDismissRequest = { dbExport = null },
            title = { Text(text = s.exportSqlite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = export.path)
                    Text(
                        text = "${export.sizeBytes}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { dbExport = null }) {
                    Text(text = s.done)
                }
            },
        )
    }
}

/** Экран резервной копии со своим ViewModel. */
@Composable
fun BackupScreen() {
    BackupScreen(rememberSettingsViewModel())
}

/** Карточка-действие раздела резервной копии. */
@Composable
private fun BackupAction(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Text(text = title, style = MaterialTheme.typography.titleMedium)
        }
    }
}
