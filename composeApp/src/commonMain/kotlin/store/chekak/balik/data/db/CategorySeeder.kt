package store.chekak.balik.data.db

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Первичное наполнение базы системными категориями.
 *
 * Без категорий в базе не работает ни категоризация (движок ищет категории по
 * стабильным ключам из [SeedData]), ни аналитика, ни лимиты — поэтому засев
 * обязателен до первого отображения экранов.
 *
 * Засев выполняется один раз: категории добавляются, только если таблица категорий
 * пуста. Если пользователь что-то удалил или добавил своё — база не трогается.
 * Повторные и параллельные вызовы безопасны.
 */
class CategorySeeder(private val categoryDao: CategoryDao) {

    private val mutex = Mutex()

    suspend fun ensureSeeded() = mutex.withLock {
        if (categoryDao.getAll().isEmpty()) {
            categoryDao.insertAll(SeedData.categories)
        }
    }
}
