package store.chekak.balik.data.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import store.chekak.balik.domain.CategorizationEngine
import store.chekak.balik.parser.ParsedOperation

/** Категория без системного ключа — так выглядит пользовательская категория. */
private class FakeCategoryDao : CategoryDao {

    val items = mutableListOf<CategoryEntity>()
    private var nextId = 1L

    override fun observeAll(): Flow<List<CategoryEntity>> = flowOf(items.toList())

    override suspend fun getAll(): List<CategoryEntity> = items.toList()

    override suspend fun getById(id: Long): CategoryEntity? = items.firstOrNull { it.id == id }

    override suspend fun getByName(name: String): CategoryEntity? = items.firstOrNull { it.name == name }

    override suspend fun insert(category: CategoryEntity): Long {
        val id = nextId++
        items.add(category.copy(id = id))
        return id
    }

    override suspend fun insertAll(categories: List<CategoryEntity>) {
        categories.forEach { insert(it) }
    }

    override suspend fun update(category: CategoryEntity) {
        val index = items.indexOfFirst { it.id == category.id }
        if (index >= 0) items[index] = category
    }

    override suspend fun delete(category: CategoryEntity) {
        items.removeAll { it.id == category.id }
    }
}

class CategorySeederTest {

    @Test
    fun fillsEmptyDatabaseWithSystemCategories() = runTest {
        val dao = FakeCategoryDao()
        CategorySeeder(dao).ensureSeeded()

        assertEquals(SeedData.categories.size, dao.items.size)
        assertTrue(dao.items.all { it.isSystem })
        assertTrue(dao.items.any { it.key == SeedData.CategoryKeys.GROCERY })
        assertTrue(dao.items.any { it.key == SeedData.CategoryKeys.INTERNAL })
        assertTrue(dao.items.any { it.key == SeedData.CategoryKeys.INCOME })
    }

    @Test
    fun doesNotDuplicateCategoriesOnSecondCall() = runTest {
        val dao = FakeCategoryDao()
        val seeder = CategorySeeder(dao)

        seeder.ensureSeeded()
        val afterFirst = dao.items.size
        seeder.ensureSeeded()

        assertEquals(afterFirst, dao.items.size)
    }

    @Test
    fun keepsUserCategoriesAndDoesNotReseed() = runTest {
        val dao = FakeCategoryDao()
        dao.items.add(CategoryEntity(id = 1L, key = null, name = "Моя категория"))
        CategorySeeder(dao).ensureSeeded()

        // Системных категорий не добавили — в базе уже есть своя, пусть и без ключа.
        assertEquals(1, dao.items.size)
    }

    @Test
    fun categorizationWorksAfterSeeding() = runTest {
        val dao = FakeCategoryDao()
        CategorySeeder(dao).ensureSeeded()

        val engine = CategorizationEngine(
            rules = emptyList(),
            categories = dao.getAll(),
        )
        val purchase = engine.categorize(
            ParsedOperation(timestamp = 0L, amountMinor = -100_000L, rawDescription = "Пятёрочка 1234"),
        )
        val income = engine.categorize(
            ParsedOperation(timestamp = 0L, amountMinor = 100_000L, rawDescription = "Зарплата"),
        )

        val groceryId = dao.items.first { it.key == SeedData.CategoryKeys.OTHER }.id
        assertEquals(groceryId, purchase.categoryId)
        val incomeId = dao.items.first { it.key == SeedData.CategoryKeys.INCOME }.id
        assertEquals(incomeId, income.categoryId)
    }
}
