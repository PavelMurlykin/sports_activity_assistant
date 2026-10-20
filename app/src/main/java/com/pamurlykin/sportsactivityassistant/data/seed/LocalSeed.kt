package com.pamurlykin.sportsactivityassistant.data.seed

import androidx.room.withTransaction
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.entity.SportEntity
import com.pamurlykin.sportsactivityassistant.data.entity.UserEntity
import com.pamurlykin.sportsactivityassistant.data.sport.SportModules

/** Idempotent profile/sport initialization. Centers and history are added only by the user or import. */
object LocalSeed {
    suspend fun initialize(database: AppDatabase): Long = database.withTransaction {
        val references = database.referenceDao()
        if (references.getUsers().isEmpty()) references.insertUsers(listOf(UserEntity()))
        SportModules.all.forEach { module ->
            if (references.getSportBySlug(module.slug) == null) {
                references.insertSports(listOf(SportEntity(slug = module.slug, title = module.title)))
            }
        }
        references.getUsers().first().id
    }
}
