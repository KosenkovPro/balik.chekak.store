package store.chekak.balik.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import store.chekak.balik.MainActivity
import store.chekak.balik.R
import store.chekak.balik.core.DateUtils
import store.chekak.balik.core.formatMoney
import store.chekak.balik.data.db.AppDatabase
import store.chekak.balik.data.db.DatabaseProvider
import store.chekak.balik.domain.Analytics

/**
 * Виджет «Итоги месяца»: расходы и доходы текущего месяца.
 * Данные берутся из локальной базы, сеть не используется.
 */
class MonthSummaryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int,
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_month_summary)
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))

        CoroutineScope(Dispatchers.IO).launch {
            // Тот же экземпляр базы, что и у приложения: два соединения к одному
            // файлу SQLite могли бы столкнуться на блокировке.
            val database = GlobalContext.getOrNull()?.get<AppDatabase>()
                ?: DatabaseProvider.create()
            val categories = database.categoryDao().getAll()
            val transactions = database.transactionDao().getAll()
            val now = System.currentTimeMillis()
            val from = DateUtils.startOfMonth(now)
            val to = DateUtils.endOfMonth(now)
            val summary = Analytics.summarize(transactions, categories, from, to)

            val currency = summary.currencies.firstOrNull()?.currency ?: "RUB"
            val expenses = summary.currencies.firstOrNull()?.expensesMinor ?: 0L
            val incomes = summary.currencies.firstOrNull()?.incomesMinor ?: 0L

            views.setTextViewText(R.id.widget_expenses, formatMoney(expenses, currency))
            views.setTextViewText(R.id.widget_incomes, formatMoney(incomes, currency))
            views.setTextViewText(
                R.id.widget_month,
                context.getString(R.string.widget_month, DateUtils.toLocalDate(now).monthNumber),
            )
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }
}
