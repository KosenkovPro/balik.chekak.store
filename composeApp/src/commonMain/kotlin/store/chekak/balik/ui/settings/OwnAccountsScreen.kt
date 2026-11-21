@file:OptIn(ExperimentalMaterial3Api::class)

package store.chekak.balik.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import store.chekak.balik.data.db.OwnAccountEntity
import store.chekak.balik.ui.navigation.LocalNavigator
import store.chekak.balik.ui.strings

/**
 * Мои счета и карты: номера нужны, чтобы отличать переводы самому себе
 * от переводов друзьям.
 */
@Composable
fun OwnAccountsScreen(vm: SettingsViewModel) {
    val s = strings
    val navigator = LocalNavigator.current
    val accounts by vm.ownAccounts.collectAsState()
    var label by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }

    Scaffold(
        topBar = { SettingsTopBar(title = s.myAccounts, onBack = { navigator.pop() }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text(
                        text = s.myAccountsHint,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = label,
                            onValueChange = { label = it },
                            label = { Text(text = s.accountLabel) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = number,
                            onValueChange = { input -> number = input.filter { it.isDigit() } },
                            label = { Text(text = s.accountNumber) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                val digits = number.filter { it.isDigit() }
                                if (digits.length >= 4) {
                                    vm.addOwnAccount(label.trim(), digits)
                                    label = ""
                                    number = ""
                                }
                            },
                            enabled = number.filter { it.isDigit() }.length >= 4,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = s.addAccount)
                        }
                    }
                }
            }

            items(items = accounts, key = { it.id }) { account ->
                OwnAccountCard(
                    account = account,
                    onDelete = { vm.removeOwnAccount(account) },
                )
            }
        }
    }
}

/** Экран своих счетов со своим ViewModel. */
@Composable
fun OwnAccountsScreen() {
    OwnAccountsScreen(rememberSettingsViewModel())
}

/** Карточка добавленного счёта: название и маска номера. */
@Composable
private fun OwnAccountCard(
    account: OwnAccountEntity,
    onDelete: () -> Unit,
) {
    val s = strings
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(text = account.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = maskedNumber(account.normalized),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = s.delete,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Маска номера: видны только последние четыре цифры. */
private fun maskedNumber(normalized: String): String {
    val visible = normalized.takeLast(4)
    return "•••• $visible"
}
