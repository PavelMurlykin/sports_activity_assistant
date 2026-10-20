package com.pamurlykin.sportsactivityassistant.data.seed

import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.model.SaveSportsCenterInput
import com.pamurlykin.sportsactivityassistant.data.repo.AppRepository

/** Explicit fixture creation; production initialization deliberately leaves the directory empty. */
suspend fun AppRepository.testCenterForSport(sportId: Int) =
    getComplexOptionsForSport(sportId).firstOrNull() ?: run {
        val id = saveSportsCenter(SaveSportsCenterInput(name = "Тестовый центр $sportId ${java.util.UUID.randomUUID()}",
            city = null, sportIds = setOf(sportId)))
        getComplexOptionsForSport(sportId).single { it.id == id }
    }

suspend fun AppDatabase.testCenterForSport(sportId: Int) =
    requireNotNull(referenceDao().getComplex(AppRepository(this).testCenterForSport(sportId).id))
