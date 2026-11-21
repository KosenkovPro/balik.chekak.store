package store.chekak.balik.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import store.chekak.balik.ui.LocalStrings

/**
 * Онбординг из трёх шагов: импорт выписок, категории и лимиты, приватность.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val s = LocalStrings.current
    var step by remember { mutableIntStateOf(0) }

    val steps = listOf(
        Triple(Icons.Filled.CreditCard, s.onboardingTitle1, s.onboardingText1),
        Triple(Icons.Filled.PieChart, s.onboardingTitle2, s.onboardingText2),
        Triple(Icons.Filled.Lock, s.onboardingTitle3, s.onboardingText3),
    )
    val (icon, title, text) = steps[step]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp),
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            steps.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == step) 10.dp else 8.dp)
                        .background(
                            color = if (index == step) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = CircleShape,
                        ),
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = {
                if (step < steps.lastIndex) {
                    step += 1
                } else {
                    onFinish()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (step < steps.lastIndex) s.onboardingNext else s.onboardingStart)
        }

        Spacer(Modifier.height(8.dp))

        TextButton(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(s.onboardingSkip)
        }
    }
}
