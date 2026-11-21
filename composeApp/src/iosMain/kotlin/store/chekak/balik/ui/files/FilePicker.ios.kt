package store.chekak.balik.ui.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerModeExport
import platform.UIKit.UIDocumentPickerModeImport
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

/**
 * iOS: UIDocumentPickerViewController. В режиме импорта системный диалог
 * отдаёт копию файла в контейнер приложения, в режиме экспорта — сохраняет копию.
 */
@Composable
actual fun FilePicker(
    request: FilePickerRequest?,
    onResult: (PickedFile?) -> Unit,
) {
    // Делегат в Objective-C хранится слабо, поэтому держим его в remember.
    var delegate by remember { mutableStateOf<PickerDelegate?>(null) }

    LaunchedEffect(request?.id) {
        val current = request ?: return@LaunchedEffect
        val mode = if (current.mode == FilePickerMode.Save) {
            UIDocumentPickerModeExport
        } else {
            UIDocumentPickerModeImport
        }
        val picker = UIDocumentPickerViewController(
            documentTypes = listOf("public.data"),
            inMode = mode,
        )
        val pickerDelegate = PickerDelegate { file -> onResult(file) }
        delegate = pickerDelegate
        picker.delegate = pickerDelegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

/** Делегат системного диалога: отдаёт содержимое выбранного файла. */
private class PickerDelegate(
    private val onResult: (PickedFile?) -> Unit,
) : platform.UIKit.UIDocumentPickerDelegateProtocol {

    override fun documentPicker(
        picker: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (url == null) {
            onResult(null)
            return
        }
        val name = url.lastPathComponent ?: "file"
        val bytes = url.data?.let { data ->
            val size = data.length.toInt()
            if (size <= 0) {
                ByteArray(0)
            } else {
                ByteArray(size).also { buffer ->
                    buffer.usePinned { pinned ->
                        data.getBytes(pinned.addressOf(0), length = data.length)
                    }
                }
            }
        } ?: ByteArray(0)
        onResult(PickedFile(name, bytes))
    }

    override fun documentPickerWasCancelled(picker: UIDocumentPickerViewController) {
        onResult(null)
    }
}

/** Верхний view controller окна — от него показываем системный диалог. */
private fun topViewController(): UIViewController? {
    val application = UIApplication.sharedApplication
    val window = application.windows.firstOrNull { it.isKeyWindow }
        ?: application.windows.firstOrNull()
    var controller = window?.rootViewController
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}
