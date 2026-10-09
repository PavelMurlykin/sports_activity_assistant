package com.pamurlykin.sportsactivityassistant.ui

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.model.SaveSportsCenterInput
import com.pamurlykin.sportsactivityassistant.data.repo.AppRepository
import com.pamurlykin.sportsactivityassistant.ui.screen.MainViewModel
import kotlinx.coroutines.*
import org.junit.Assert.assertEquals
import org.junit.Test

class CenterWriteViewModelTest {
    @Test fun detachingDialogWaiterDoesNotCancelOrRepeatCenterWrite() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val database = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, AppDatabase::class.java).build()
        val repository = AppRepository(database)
        repository.localProfileId()
        val sports = database.referenceDao().getSports().map { it.id }.toSet()
        val input = SaveSportsCenterInput(name = "Центр с прерванным ожиданием", city = null, sportIds = sports,
            requestId = java.util.UUID.randomUUID().toString())
        val store = ViewModelStore()
        val work = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        lateinit var viewModel: MainViewModel
        try {
            instrumentation.runOnMainSync { viewModel = MainViewModel(repository); store.put("center", viewModel) }
            val lock = work.launch { database.withTransaction { entered.complete(Unit); release.await() } }
            withTimeout(5000) { entered.await() }
            lateinit var first: Job
            instrumentation.runOnMainSync {
                first = work.launch(Dispatchers.Main.immediate, start = CoroutineStart.UNDISPATCHED) { viewModel.saveSportsCenter(input) }
                first.cancel()
            }
            first.join()
            val second = work.async(Dispatchers.Main) { viewModel.saveSportsCenter(input) }
            release.complete(Unit)
            val id = withTimeout(5000) { second.await() }
            lock.join()
            assertEquals(id, repository.saveSportsCenter(input))
            assertEquals(1, database.referenceDao().getAllComplexes().count { it.publicId == input.requestId })
        } finally {
            release.complete(Unit)
            work.cancel()
            instrumentation.runOnMainSync { store.clear() }
            database.close()
        }
    }
}
