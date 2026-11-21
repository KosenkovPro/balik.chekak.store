package store.chekak.balik.data.repository

import kotlinx.coroutines.flow.Flow
import store.chekak.balik.data.db.DepositDao
import store.chekak.balik.data.db.DepositEntity

/**
 * Вклады (депозиты): хранение и выдача списка.
 *
 * Расчёт процентов и налога живёт в домене (`DepositCalculator`) — репозиторий
 * только хранит.
 */
class DepositRepository(
    private val depositDao: DepositDao,
) {

    fun observeAll(): Flow<List<DepositEntity>> = depositDao.observeAll()

    suspend fun getAll(): List<DepositEntity> = depositDao.getAll()

    suspend fun getById(id: Long): DepositEntity? = depositDao.getById(id)

    suspend fun upsert(deposit: DepositEntity): Long =
        if (deposit.id == 0L) {
            depositDao.insert(deposit)
        } else {
            depositDao.update(deposit)
            deposit.id
        }

    suspend fun delete(deposit: DepositEntity) = depositDao.delete(deposit)
}
