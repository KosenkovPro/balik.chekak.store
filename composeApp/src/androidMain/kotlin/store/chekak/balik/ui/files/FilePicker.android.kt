package store.chekak.balik.ui.files

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android: Storage Access Framework. Открытие — ACTION_OPEN_DOCUMENT,
 * сохранение — ACTION_CREATE_DOCUMENT.
 */
@Composable
actual fun FilePicker(
    request: FilePickerRequest?,
    onResult: (PickedFile?) -> Unit,
) {
    val context = LocalContext.current

    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) {
            onResult(null)
            return@rememberLauncherForActivityResult
        }
        val name = queryDisplayName(context, uri) ?: "statement"
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: ByteArray(0)
        onResult(PickedFile(name, bytes))
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? ->
        if (uri == null) {
            onResult(null)
            return@rememberLauncherForActivityResult
        }
        onResult(PickedFile(request?.suggestedName ?: "backup", ByteArray(0)))
    }

    LaunchedEffect(request?.id) {
        val current = request ?: return@LaunchedEffect
        when (current.mode) {
            FilePickerMode.Open -> openLauncher.launch(
                current.mimeTypes.ifEmpty { listOf("*/*") }.toTypedArray(),
            )
            FilePickerMode.Save -> saveLauncher.launch(current.suggestedName ?: "backup")
        }
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()
