package com.pamurlykin.sportsactivityassistant

import android.app.Application
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.repo.AppRepository

class SportsActivityApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val repository: AppRepository by lazy {
        AppRepository(database)
    }
}
