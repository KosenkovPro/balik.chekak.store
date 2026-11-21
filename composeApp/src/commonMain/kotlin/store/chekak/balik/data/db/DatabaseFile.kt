package store.chekak.balik.data.db

/** Доступ к файлу базы для экспорта в .sqlite. */
expect object DatabaseFile {
    /** Абсолютный путь к файлу БД. */
    fun path(): String

    /** Читает файл БД целиком (или null, если файла нет). */
    suspend fun readBytes(): ByteArray?
}
