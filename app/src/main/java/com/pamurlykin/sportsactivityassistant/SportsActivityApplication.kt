package com.pamurlykin.sportsactivityassistant

import android.app.Application
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.repo.AppRepository
import com.pamurlykin.sportsactivityassistant.data.seed.DemoSeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SportsActivityApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this).also { db ->
            applicationScope.launch {
                DemoSeed.seed(db)
            }
        }
    }

    val repository: AppRepository by lazy {
        AppRepository(database)
    }
}
