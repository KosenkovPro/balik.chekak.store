package store.chekak.balik

/**
 * Файл, переданный системой «Поделиться» (или лежащий в общем контейнере на iOS).
 * Возвращает имя и содержимое, либо null, если импортировать нечего.
 */
expect fun pendingImportBytes(): Pair<String, ByteArray>?
