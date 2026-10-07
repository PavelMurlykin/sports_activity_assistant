package com.pamurlykin.sportsactivityassistant.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.pamurlykin.sportsactivityassistant.data.entity.SportEntity
import com.pamurlykin.sportsactivityassistant.data.entity.SportsComplexEntity
import com.pamurlykin.sportsactivityassistant.data.entity.SportsComplexSportEntity
import com.pamurlykin.sportsactivityassistant.data.entity.SportsComplexWithSports
import com.pamurlykin.sportsactivityassistant.data.entity.UserEntity
import com.pamurlykin.sportsactivityassistant.data.entity.UserFavoriteComplexEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReferenceDao {
    @Query("SELECT * FROM sports ORDER BY id")
    fun observeSports(): Flow<List<SportEntity>>

    @Query("SELECT * FROM sports ORDER BY id")
    suspend fun getSports(): List<SportEntity>

    @Query("SELECT * FROM sports WHERE id = :sportId LIMIT 1")
    suspend fun getSport(sportId: Int): SportEntity?

    @Query("SELECT * FROM sports WHERE slug = :slug LIMIT 1")
    suspend fun getSportBySlug(slug: String): SportEntity?

    @Query("SELECT * FROM sports_complexes ORDER BY name, city")
    suspend fun getAllComplexes(): List<SportsComplexEntity>

    @Transaction
    @Query("SELECT * FROM sports_complexes ORDER BY name, city")
    fun observeComplexesWithSports(): Flow<List<SportsComplexWithSports>>

    @Transaction
    @Query("SELECT * FROM sports_complexes ORDER BY name, city")
    suspend fun getComplexesWithSports(): List<SportsComplexWithSports>

    @Query("SELECT * FROM sports_complexes WHERE id = :complexId LIMIT 1")
    suspend fun getComplex(complexId: Long): SportsComplexEntity?

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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUsers(items: List<UserEntity>)

    @Query("SELECT * FROM users ORDER BY id")
    suspend fun getUsers(): List<UserEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSports(items: List<SportEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertComplexes(items: List<SportsComplexEntity>)

    @Insert
    suspend fun insertComplex(item: SportsComplexEntity): Long

    @Update
    suspend fun updateComplex(item: SportsComplexEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComplexSports(items: List<SportsComplexSportEntity>)

    @Query("SELECT * FROM sports_complex_sports ORDER BY id")
    suspend fun getComplexSports(): List<SportsComplexSportEntity>

    @Query("DELETE FROM sports_complex_sports WHERE sports_complex_id = :complexId")
    suspend fun deleteComplexSports(complexId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteComplexes(items: List<UserFavoriteComplexEntity>)

    @Query("SELECT * FROM user_favorite_complexes ORDER BY id")
    suspend fun getFavoriteComplexes(): List<UserFavoriteComplexEntity>
}
