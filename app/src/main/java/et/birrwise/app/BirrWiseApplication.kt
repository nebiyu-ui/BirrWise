package et.birrwise.app

import android.app.Application
import android.content.Context
import et.birrwise.app.data.RoomAccountRepository
import et.birrwise.app.data.RoomCategoryRepository
import et.birrwise.app.data.RoomPersonRepository
import et.birrwise.app.data.RoomTransactionRepository
import et.birrwise.app.data.db.BirrWiseDatabase
import et.birrwise.app.domain.TransactionFilter
import et.birrwise.app.domain.repository.AccountRepository
import et.birrwise.app.domain.repository.CategoryRepository
import et.birrwise.app.domain.repository.PersonRepository
import et.birrwise.app.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow

class BirrWiseApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/** Creates the database and repositories the first time a screen asks for them. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: BirrWiseDatabase by lazy { BirrWiseDatabase.create(appContext) }
    val accountRepository: AccountRepository by lazy { RoomAccountRepository(database) }
    val transactionRepository: TransactionRepository by lazy { RoomTransactionRepository(database) }
    val categoryRepository: CategoryRepository by lazy { RoomCategoryRepository(database) }
    val personRepository: PersonRepository by lazy { RoomPersonRepository(database) }

    /**
     * The search and filters of the Transactions list. They live here so that other screens, such as
     * Home, can open the list with a filter already set, and so they survive switching tabs.
     */
    val transactionFilter: MutableStateFlow<TransactionFilter> = MutableStateFlow(TransactionFilter())
}
