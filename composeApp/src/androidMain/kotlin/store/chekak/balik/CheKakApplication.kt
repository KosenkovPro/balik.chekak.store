package store.chekak.balik

import android.app.Application
import store.chekak.balik.data.db.DatabaseFile
import store.chekak.balik.data.db.DatabaseProvider
import store.chekak.balik.di.initKoin
import store.chekak.balik.parser.pdf.PdfOcrEngine
import store.chekak.balik.parser.pdf.PdfTextExtractor

class CheKakApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Контекст нужен и базе, и PDFBox (для разбора выписок).
        DatabaseProvider.init(this)
        DatabaseFile.init(this)
        PdfTextExtractor.init(this)
        // OCR-движку контекст нужен, чтобы один раз скопировать языковую модель
        // из assets в файловую систему приложения.
        PdfOcrEngine.init(this)
        initKoin()
    }
}
