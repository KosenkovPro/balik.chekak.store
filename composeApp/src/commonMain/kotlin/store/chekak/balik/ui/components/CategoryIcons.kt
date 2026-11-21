package store.chekak.balik.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Соответствие названия иконки категории конкретной иконке Material. */
object CategoryIcons {
    fun of(iconName: String): ImageVector = when (iconName) {
        "ShoppingCart" -> Icons.Filled.ShoppingCart
        "LocalCafe" -> Icons.Filled.LocalCafe
        "DirectionsBus" -> Icons.Filled.DirectionsBus
        "Storefront" -> Icons.Filled.Storefront
        "Subscriptions" -> Icons.Filled.Subscriptions
        "Home" -> Icons.Filled.Home
        "MedicalServices" -> Icons.Filled.MedicalServices
        "Movie" -> Icons.Filled.Movie
        "SwapHoriz" -> Icons.Filled.SwapHoriz
        "AttachMoney" -> Icons.Filled.AttachMoney
        "Payments" -> Icons.Filled.Payments
        else -> Icons.Filled.Category
    }
}

/** Разбор цвета категории из HEX-строки. */
fun parseColor(hex: String): Color = runCatching {
    val cleaned = hex.removePrefix("#")
    val value = when (cleaned.length) {
        6 -> 0xFF000000L or cleaned.toLong(16)
        8 -> cleaned.toLong(16)
        else -> 0xFF9E9E9EL
    }
    Color(value.toInt())
}.getOrDefault(Color(0xFF9E9E9E))
