package com.pamurlykin.sportsactivityassistant.data.seed

import androidx.room.withTransaction
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.entity.SportEntity
import com.pamurlykin.sportsactivityassistant.data.entity.SportsComplexEntity
import com.pamurlykin.sportsactivityassistant.data.entity.SportsComplexSportEntity
import com.pamurlykin.sportsactivityassistant.data.entity.UserEntity
import com.pamurlykin.sportsactivityassistant.data.sport.SportModules

/** Idempotent reference initialization. Never creates results/plans or overwrites existing records. */
object LocalSeed {
    suspend fun initialize(database: AppDatabase): Long = database.withTransaction {
        val references = database.referenceDao()
        if (references.getUsers().isEmpty()) references.insertUsers(listOf(UserEntity()))
        SportModules.all.forEach { module ->
            if (references.getSportBySlug(module.slug) == null) {
                references.insertSports(listOf(SportEntity(slug = module.slug, title = module.title)))
            }
        }
        if (references.getAllComplexes().isEmpty()) {
            val climbingId = requireNotNull(references.getSportBySlug("climbing")).id
            val footballId = requireNotNull(references.getSportBySlug("football")).id
            listOf(
                Triple("Энергия Высоты", "Санкт-Петербург", climbingId),
                Triple("Фабрика Футбола", "Санкт-Петербург", footballId),
                Triple("Арена на горе", "Анталья", footballId),
            ).forEach { (name, city, sportId) ->
                val id = references.insertComplex(SportsComplexEntity(name = name, city = city, isInitial = true))
                references.insertComplexSports(listOf(SportsComplexSportEntity(sportsComplexId = id, sportId = sportId)))
            }
        }
        references.getUsers().first().id
    }
}
