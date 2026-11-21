package store.chekak.balik

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AppView()
        }
    }

    /**
     * Файл мог прийти из «Поделиться», пока приложение уже было открыто.
     * Пересоздаём Activity, чтобы Compose перезапустил предпросмотр импорта
     * (иначе он подхватился бы только при следующем запуске).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }
}
