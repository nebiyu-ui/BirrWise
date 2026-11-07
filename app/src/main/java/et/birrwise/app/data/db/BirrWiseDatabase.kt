package et.birrwise.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        PersonEntity::class,
        ImportBatchEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        CategoryRuleEntity::class,
        SavingsGoalEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class BirrWiseDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun personDao(): PersonDao
    abstract fun importBatchDao(): ImportBatchDao
    abstract fun budgetDao(): BudgetDao
    abstract fun categoryRuleDao(): CategoryRuleDao
    abstract fun savingsGoalDao(): SavingsGoalDao

    companion object {
        const val NAME = "birrwise.db"

        /**
         * Every schema change adds a migration here and a test for it. There is deliberately no
         * fallback to destructive migration: if the database cannot be upgraded or opened, the
         * app must stop with an error instead of wiping or replacing the person's data.
         */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun create(context: Context): BirrWiseDatabase =
            Room.databaseBuilder(context.applicationContext, BirrWiseDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
