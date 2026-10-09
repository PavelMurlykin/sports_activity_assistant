package com.pamurlykin.sportsactivityassistant.data.repo

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.entity.*
import com.pamurlykin.sportsactivityassistant.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test

class StatisticsQueriesTest {
    private suspend fun fixture(test: suspend (AppDatabase,AppRepository,ConcurrentLinkedQueue<String>) -> Unit) {
        val queries = ConcurrentLinkedQueue<String>()
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .setQueryCallback({ sql,_ -> queries.add(sql) }, Executor { it.run() }).build()
        val repo = AppRepository(db)
        try { repo.localProfileId(); test(db,repo,queries) } finally { db.close() }
    }
    private fun date(value: String) = LocalDate.parse(value)
    private suspend fun football(db: AppDatabase, user: Long, sport: Int, center: Long, day: String,
        distance: String? = null, minutes: Int? = null, scored: Int = 0, conceded: Int = 0, personal: Int = 0, assists: Int = 0): Long {
        val id = db.trainingDao().insertTraining(TrainingEntity(userId=user,sportId=sport,sportsComplexId=center,trainingDate=date(day)))
        db.trainingDao().insertFootballTraining(FootballTrainingEntity(id,scored,conceded,personal,assists,distance?.let(::BigDecimal),null,minutes))
        return id
    }
    private suspend fun metrics(repo: AppRepository, sport: Int, filter: StatisticsFilter = StatisticsFilter()) =
        repo.observeSportStatistics(sport,filter).first()!!.metrics.associate { it.label to it.value }

    @Test fun sameOwnerPeriodAndArchivedCenterSelectionIsUsedForEveryScreen() = runBlocking {
        fixture { db,repo,_ ->
            val user = repo.localProfileId()
            val sport = db.referenceDao().getSportBySlug("football")!!.id
            val center = db.referenceDao().getComplexesForSport(sport).first().id
            val otherCenter = repo.saveSportsCenter(SaveSportsCenterInput(name="Другой",city=null,sportIds=setOf(sport)))
            db.referenceDao().insertUsers(listOf(UserEntity(id=99,displayName="Другой профиль")))
            football(db,user,sport,center,"2020-02-01",null,null,2,1,1,1)
            football(db,user,sport,center,"2020-02-29","0",40)
            football(db,user,sport,center,"2020-02-15","0.3",41,0,2)
            football(db,user,sport,otherCenter,"2020-02-20","500",100)
            football(db,99,sport,center,"2020-02-20","600",120)
            football(db,user,sport,center,"2019-12-31","700",130)
            repo.addPlannedTraining(user,AddPlannedTrainingInput(sport,center,date("2020-02-20"),false,1,null))
            repo.setSportsCenterArchived(center,true)
            val filter = StatisticsFilter(date("2020-02-01"),date("2020-02-29"),center)
            val overview = repo.observeStatisticsOverview(filter).first()
            val details = metrics(repo,sport,filter)
            val page = repo.observeTrainingPage(sport,filter).first()
            assertEquals(3,overview.totalTrainings)
            assertEquals(3,overview.sports.single { it.id == sport }.completedTrainings)
            assertEquals("3",details["Игры"])
            assertEquals(3,page.total)
            assertEquals(listOf(29,15,1),page.items.map { it.date.dayOfMonth })
            assertEquals("1 / 1 / 1",details["Победы / ничьи / поражения"])
            assertEquals("2:3",details["Счёт команд"])
            assertEquals("0.3 км",details["Учтённая дистанция"])
            assertEquals("0.15 км",details["Средняя дистанция"])
            assertEquals("2 из 3",details["Игр с дистанцией"])
            assertEquals("81 мин",details["Учтённое время"])
            assertEquals("40.5 мин",details["Средняя длительность"])
            assertEquals("2 из 3",details["Игр со временем"])
            assertEquals(5,repo.observeStatisticsOverview().first().totalTrainings)
            val empty = repo.observeStatisticsOverview(StatisticsFilter(date("2017-12-15"),date("2018-03-01"),center)).first()
            assertEquals(listOf(0,0,0,0),empty.months.map { it.totalTrainings })
            assertEquals("Нет данных",metrics(repo,sport,StatisticsFilter(date("2018-01-01"),date("2018-12-31")))["Средняя дистанция"])
        }
    }

    @Test fun climbingCountsRowsSeparatelyFromRepeatsAndComparesOnlyConfirmedScales() = runBlocking {
        fixture { db,repo,_ ->
            val user = repo.localProfileId()
            val sport = db.referenceDao().getSportBySlug("climbing")!!.id
            val center = db.referenceDao().getComplexesForSport(sport).first().id
            val routes = listOf(
                ClimbingRouteInput(ClimbingWorkoutType.DIFFICULTY,"\u00a06B+\u3000",true,3),
                ClimbingRouteInput(ClimbingWorkoutType.DIFFICULTY,"9c",false,2),
                ClimbingRouteInput(ClimbingWorkoutType.BOULDERING,"7A",true),
                ClimbingRouteInput(ClimbingWorkoutType.SPEED,"",true,2,speedCourse="standard_15m"),
            )
            repo.addCompletedTraining(user,AddCompletedTrainingInput(sport,center,date("2020-01-01"),climbingRoutes=routes))
            val id = db.trainingDao().insertTraining(TrainingEntity(userId=user,sportId=sport,sportsComplexId=center,trainingDate=date("2020-03-01")))
            db.trainingDao().insertClimbingTraining(ClimbingTrainingEntity(id))
            db.trainingDao().insertClimbingRoutes(listOf(ClimbingRouteEntity(climbingTrainingId=id,workoutType=ClimbingWorkoutType.BOULDERING,
                routeDifficulty="9Z",isCompleted=true,repeatCount=4)))
            val filter = StatisticsFilter(date("2019-12-31"),date("2020-03-31"),center)
            val values = metrics(repo,sport,filter)
            assertEquals("2",values["Тренировки"])
            assertEquals("5",values["Записи трасс"])
            assertEquals("12",values["Попытки (с повторами)"])
            assertEquals("10 (83%)",values["Успешно пройдено"])
            assertEquals("3 / 5",values["Трудность · успешно / попытки"])
            assertEquals("5 / 5",values["Болдер · успешно / попытки"])
            assertEquals("6b+",values["Максимум · Трудность (Французская)"])
            assertEquals("7A",values["Максимум · Болдер (Fontainebleau)"])
            assertEquals("3 / 3",values["Трудность · 6b+ · успешно / попытки"])
            assertEquals("0 / 2",values["Трудность · 9c · успешно / попытки"])
            assertEquals("2 / 2",values["Скорость · Эталонная 15 м · успешно / попытки"])
            assertEquals("4",values["Исторические категории без сравнения"])
            assertEquals(listOf(1,0,1,0),repo.observeStatisticsOverview(filter).first().months.map { it.totalTrainings })
            assertEquals("10 из 12 попыток успешны",repo.observeStatisticsOverview(filter).first().sports.single { it.id == sport }.highlights.single())
        }
    }

    @Test fun stableTwentyRowPagesClampAfterDeletionAndRecomputeAfterLeafEdit() = runBlocking {
        fixture { db,repo,_ ->
            val user = repo.localProfileId()
            val sport = db.referenceDao().getSportBySlug("football")!!.id
            val center = db.referenceDao().getComplexesForSport(sport).first().id
            val ids = db.withTransaction { (1..61).map { football(db,user,sport,center,"2020-01-01") } }
            val pages = (0..3).map { repo.observeTrainingPage(sport,page=it).first() }
            assertEquals(listOf(20,20,20,1),pages.map { it.items.size })
            assertEquals(ids.reversed(),pages.flatMap { it.items }.map { it.id })
            assertEquals(3,repo.observeTrainingPage(sport,page=Int.MAX_VALUE).first().page)
            coroutineScope {
                val nextPage = async(start=CoroutineStart.UNDISPATCHED) { withTimeout(5000) { repo.observeTrainingPage(sport,page=3).first { it.total == 60 } } }
                db.trainingDao().deleteTraining(ids.first())
                assertEquals(2,nextPage.await().page)
            }
            coroutineScope {
                val nextStats = async(start=CoroutineStart.UNDISPATCHED) { withTimeout(5000) { repo.observeSportStatistics(sport).first { state ->
                    state?.metrics?.any { it.label == "Личные голы" && it.value == "1" } == true
                } } }
                db.trainingDao().insertFootballTraining(FootballTrainingEntity(ids.last(),1,0,1,0,null,null,null))
                assertNotNull(nextStats.await())
            }
        }
    }

    @Test fun largeHistoryUsesBoundedQueriesAndExactDecimalBatchesAndLongSums() = runBlocking {
        fixture { db,repo,queries ->
            val user = repo.localProfileId()
            val sport = db.referenceDao().getSportBySlug("football")!!.id
            val center = db.referenceDao().getComplexesForSport(sport).first().id
            db.withTransaction { repeat(5000) { index ->
                football(db,user,sport,center,"2020-01-01",if (index < 513) "1000000000000000000000" else null,
                    Int.MAX_VALUE,Int.MAX_VALUE,0,Int.MAX_VALUE,Int.MAX_VALUE)
            } }
            queries.clear()
            val overview = repo.observeStatisticsOverview().first()
            val values = metrics(repo,sport)
            val page = repo.observeTrainingPage(sport,page=249).first()
            assertEquals(5000,overview.totalTrainings)
            assertEquals(20,page.items.size)
            assertEquals(5000,page.total)
            assertEquals("10737418235000",values["Личные голы"])
            assertEquals("513000000000000000000000 км",values["Учтённая дистанция"])
            assertEquals("1000000000000000000000 км",values["Средняя дистанция"])
            assertEquals("513 из 5000",values["Игр с дистанцией"])
            assertEquals("10737418235000 мин",values["Учтённое время"])
            val reads = queries.filter { it.trimStart().startsWith("SELECT",ignoreCase=true) }
            assertTrue(reads.any { it.contains("LIMIT 512") })
            assertTrue(reads.none { it.contains("SELECT * FROM trainings") && !it.contains("LIMIT") })
            assertTrue(reads.none { it.contains("SELECT * FROM climbing_routes") && !it.contains("IN") })
            db.openHelper.readableDatabase.query("EXPLAIN QUERY PLAN SELECT * FROM trainings WHERE user_id = 1 AND training_date BETWEEN '2019-01-01' AND '2021-12-31' ORDER BY training_date DESC, id DESC LIMIT 20").use { cursor ->
                val descriptions = buildList { while(cursor.moveToNext()) add(cursor.getString(3)) }.joinToString()
                assertTrue(descriptions.contains("index_trainings_user_id_training_date"))
                assertFalse(descriptions.contains("TEMP B-TREE"))
            }
        }
    }

    @Test fun sqlMetricsAgreeWithControlFixtureAndFutureImportedResultIsNotAPlan() = runBlocking {
        fixture { db,repo,_ ->
            val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("control-backup-v2.json").use { it.readBytes() }
            repo.importData(bytes,repo.localProfileId())
            val bundles = db.trainingDao().getAllTrainingBundles()
            db.referenceDao().getSports().forEach { sport ->
                assertEquals(StatisticsMapper.sport(sport,bundles.filter { it.sport.id == sport.id }).metrics,
                    repo.observeSportStatistics(sport.id).first()!!.metrics)
            }
            val sport = db.referenceDao().getSportBySlug("football")!!.id
            val center = db.referenceDao().getComplexesForSport(sport).first().id
            football(db,repo.localProfileId(),sport,center,"9999-12-31")
            assertEquals(4,repo.observeStatisticsOverview().first().totalTrainings)
            assertEquals(1,repo.observeTrainingPage(sport,StatisticsFilter(date("9999-12-31"),date("9999-12-31"))).first().total)
        }
    }
    @Test fun legalScientificDistanceSurvivesStorageRetryAndBackupReimport() = runBlocking {
        fixture { db,repo,_ ->
            val user = repo.localProfileId()
            val sport = db.referenceDao().getSportBySlug("football")!!.id
            val center = db.referenceDao().getComplexesForSport(sport).first().id
            val input = AddCompletedTrainingInput(sport,center,date("2020-01-01"),
                FootballTrainingInput(0,0,0,0,BigDecimal("1000000000000000E+6"),null,null))
            val token = java.util.UUID.randomUUID().toString()
            val id = repo.addCompletedTraining(user,input,token)
            assertEquals(id,repo.addCompletedTraining(user,input,token))
            val backup = repo.createBackup().toByteArray()
            assertEquals(1,repo.importData(backup,user).skippedTrainings)
            assertEquals("1000000000000000000000 км",metrics(repo,sport)["Учтённая дистанция"])
            val plain = input.copy(football=input.football!!.copy(distanceKm=BigDecimal("1000000000000000000000")))
            assertEquals(id,repo.addCompletedTraining(user,plain,token))
            assertEquals(1,repo.observeTrainingPage(sport).first().total)
        }
    }
}
