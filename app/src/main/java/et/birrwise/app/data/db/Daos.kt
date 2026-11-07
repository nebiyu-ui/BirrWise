package et.birrwise.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Insert
    suspend fun insert(account: AccountEntity)

    @Update
    suspend fun update(account: AccountEntity): Int

    @Delete
    suspend fun delete(account: AccountEntity): Int

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY archived, name COLLATE NOCASE")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId")
    suspend fun transactionCount(accountId: String): Int
}

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: TransactionEntity)

    @Update
    suspend fun update(transaction: TransactionEntity): Int

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC, createdAt DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY occurredAt DESC, createdAt DESC")
    fun observeForAccount(accountId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE occurredAt >= :startMillis AND occurredAt < :endMillis ORDER BY occurredAt DESC")
    suspend fun getBetween(startMillis: Long, endMillis: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE transferGroupId = :groupId")
    suspend fun getByTransferGroup(groupId: String): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int
}

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnoringExisting(categories: List<CategoryEntity>): List<Long>

    @Update
    suspend fun update(category: CategoryEntity): Int

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Query("SELECT * FROM categories ORDER BY isSystemCategory DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :id")
    suspend fun transactionCount(id: String): Int

    @Query("SELECT COUNT(*) FROM categories WHERE parentCategoryId = :id")
    suspend fun childCount(id: String): Int
}

@Dao
interface PersonDao {
    @Insert
    suspend fun insert(person: PersonEntity)

    @Update
    suspend fun update(person: PersonEntity): Int

    @Delete
    suspend fun delete(person: PersonEntity): Int

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun getById(id: String): PersonEntity?

    @Query("SELECT * FROM people WHERE isHidden = 0 ORDER BY displayName COLLATE NOCASE")
    fun observeVisible(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people ORDER BY displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<PersonEntity>>
}

@Dao
interface ImportBatchDao {
    @Insert
    suspend fun insert(batch: ImportBatchEntity)

    @Update
    suspend fun update(batch: ImportBatchEntity): Int

    @Delete
    suspend fun delete(batch: ImportBatchEntity): Int

    @Query("SELECT * FROM import_batches WHERE id = :id")
    suspend fun getById(id: String): ImportBatchEntity?

    @Query("SELECT * FROM import_batches ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<ImportBatchEntity>>
}

@Dao
interface BudgetDao {
    @Insert
    suspend fun insert(budget: BudgetEntity)

    @Update
    suspend fun update(budget: BudgetEntity): Int

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: String): BudgetEntity?

    @Query("SELECT * FROM budgets ORDER BY periodStart DESC")
    fun observeAll(): Flow<List<BudgetEntity>>
}

@Dao
interface CategoryRuleDao {
    @Insert
    suspend fun insert(rule: CategoryRuleEntity)

    @Update
    suspend fun update(rule: CategoryRuleEntity): Int

    @Query("DELETE FROM category_rules WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT * FROM category_rules WHERE id = :id")
    suspend fun getById(id: String): CategoryRuleEntity?

    @Query("SELECT * FROM category_rules ORDER BY priority DESC, createdAt")
    fun observeAll(): Flow<List<CategoryRuleEntity>>
}

@Dao
interface SavingsGoalDao {
    @Insert
    suspend fun insert(goal: SavingsGoalEntity)

    @Update
    suspend fun update(goal: SavingsGoalEntity): Int

    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun getById(id: String): SavingsGoalEntity?

    @Query("SELECT * FROM savings_goals ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SavingsGoalEntity>>
}
