@file:OptIn(ExperimentalMaterial3Api::class)

package store.chekak.balik.ui.deposits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import store.chekak.balik.core.parseMoneyToMinor
import store.chekak.balik.data.repository.SettingsRepository
import store.chekak.balik.domain.DepositTaxSettings
import store.chekak.balik.ui.LocalStrings
import store.chekak.balik.ui.navigation.LocalNavigator

/**
 * Настройки налога на проценты по вкладам.
 *
 * Значения по умолчанию — как предписывает НК РФ: 13% с превышения необлагаемого
 * минимума (160 000 ₽ за 2026 год) и 15% с превышения 2,4 млн ₽. Ставки и
 * лимиты меняются каждый год, поэтому их можно поправить вручную.
 */
@Composable
fun DepositTaxSettingsScreen() {
    val s = LocalStrings.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val settingsRepository = koinInject<SettingsRepository>()

    val loaded by produceState<DepositTaxSettings?>(initialValue = null) {
        value = settingsRepository.getDepositTaxSettings()
    }

    var rate by remember { mutableStateOf("") }
    var elevated by remember { mutableStateOf("") }
    var exempt by remember { mutableStateOf("") }
    var threshold by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }

    val current = loaded
    if (current != null && !initialized) {
        initialized = true
        rate = current.ratePercent.toString().replace('.', ',')
        elevated = current.elevatedRatePercent.toString().replace('.', ',')
        exempt = (current.exemptMinor / 100.0).toString().replace('.', ',')
        threshold = (current.thresholdMinor / 100.0).toString().replace('.', ',')
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.taxSettings) },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(s.taxNote, style = MaterialTheme.typography.bodyMedium)

            OutlinedTextField(
                value = rate,
                onValueChange = { rate = it },
                label = { Text(s.taxRate) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = elevated,
                onValueChange = { elevated = it },
                label = { Text(s.taxElevatedRate) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = exempt,
                onValueChange = { exempt = it },
                label = { Text(s.taxExempt) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = threshold,
                onValueChange = { threshold = it },
                label = { Text(s.taxThreshold) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            TextButton(
                onClick = {
                    val ratePercent = rate.replace(',', '.').toDoubleOrNull()
                    val elevatedPercent = elevated.replace(',', '.').toDoubleOrNull()
                    val exemptMinor = parseMoneyToMinor(exempt)
                    val thresholdMinor = parseMoneyToMinor(threshold)
                    if (ratePercent == null || ratePercent < 0.0) return@TextButton
                    if (elevatedPercent == null || elevatedPercent < 0.0) return@TextButton
                    if (exemptMinor == null || exemptMinor < 0L) return@TextButton
                    if (thresholdMinor == null || thresholdMinor < 0L) return@TextButton
                    scope.launch {
                        settingsRepository.setDepositTaxSettings(
                            DepositTaxSettings(
                                ratePercent = ratePercent,
                                elevatedRatePercent = elevatedPercent,
                                exemptMinor = exemptMinor,
                                thresholdMinor = thresholdMinor,
                            ),
                        )
                        navigator.pop()
                    }
                },
            ) {
                Text(s.save)
            }
        }
    }
}
