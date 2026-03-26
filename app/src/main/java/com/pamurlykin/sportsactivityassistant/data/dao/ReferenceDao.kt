package com.pamurlykin.sportsactivityassistant.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pamurlykin.sportsactivityassistant.data.entity.SportEntity
import com.pamurlykin.sportsactivityassistant.data.entity.SportsComplexEntity
import com.pamurlykin.sportsactivityassistant.data.entity.SportsComplexSportEntity
import com.pamurlykin.sportsactivityassistant.data.entity.UserEntity
import com.pamurlykin.sportsactivityassistant.data.entity.UserFavoriteComplexEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReferenceDao {
    @Query("SELECT * FROM sports ORDER BY id")
    fun observeSports(): Flow<List<SportEntity>>

    @Query("SELECT * FROM sports ORDER BY id")
    suspend fun getSports(): List<SportEntity>

    @Query("SELECT * FROM sports_complexes ORDER BY name, city")
    suspend fun getAllComplexes(): List<SportsComplexEntity>

    @Query(
        """
        SELECT sports_complexes.*
        FROM sports_complexes
        INNER JOIN sports_complex_sports
            ON sports_complex_sports.sports_complex_id = sports_complexes.id
        WHERE sports_complex_sports.sport_id = :sportId
        ORDER BY sports_complexes.name, sports_complexes.city
        """,
    )
    suspend fun getComplexesForSport(sportId: Int): List<SportsComplexEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(items: List<UserEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSports(items: List<SportEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComplexes(items: List<SportsComplexEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComplexSports(items: List<SportsComplexSportEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteComplexes(items: List<UserFavoriteComplexEntity>)
}
