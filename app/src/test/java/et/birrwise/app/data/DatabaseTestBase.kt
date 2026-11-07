package et.birrwise.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import et.birrwise.app.data.db.BirrWiseDatabase
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Runs database tests on the build machine with an in-memory database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
abstract class DatabaseTestBase {

    protected lateinit var db: BirrWiseDatabase

    protected val clock: Clock = Clock.fixed(Instant.parse("2026-03-01T09:00:00Z"), ZoneOffset.UTC)

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BirrWiseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        db.close()
    }
}
