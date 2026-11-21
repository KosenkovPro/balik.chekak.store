package store.chekak.balik.data.db

/**
 * Создание экземпляра БД — различается только расположение файла и контекст,
 * поэтому API одинаков для обеих платформ.
 */
expect object DatabaseProvider {
    fun create(): AppDatabase
}
