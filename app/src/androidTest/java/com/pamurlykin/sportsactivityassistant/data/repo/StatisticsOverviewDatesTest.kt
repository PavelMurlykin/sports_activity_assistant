package com.pamurlykin.sportsactivityassistant.data.repo

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.entity.PlannedTrainingEntity
import com.pamurlykin.sportsactivityassistant.data.entity.UserEntity
import com.pamurlykin.sportsactivityassistant.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class StatisticsOverviewDatesTest {
    private val today = LocalDate.of(2020, 10, 22)
    private suspend fun fixture(test: suspend (AppDatabase, AppRepository, Long, Int, Int, Long, Long) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        val repo = AppRepository(db)
        try {
            val user = repo.localProfileId()
            val f = db.referenceDao().getSportBySlug("football")!!.id
            val c = db.referenceDao().getSportBySlug("climbing")!!.id
            val a = repo.saveSportsCenter(SaveSportsCenterInput(name = "Первый", city = null, sportIds = setOf(f, c)))
            val b = repo.saveSportsCenter(SaveSportsCenterInput(name = "Второй", city = null, sportIds = setOf(f, c)))
            test(db, repo, user, f, c, a, b)
        } finally { db.close() }
    }
    private fun result(sport: Int, center: Long, day: LocalDate) = AddCompletedTrainingInput(sport, center, day,
        football = FootballTrainingInput(0, 0, 0, 0, null, null, null))
    private fun plan(sport: Int, center: Long, day: LocalDate, weekly: Boolean = false) = AddPlannedTrainingInput(sport, center, day, weekly, 1, null)

    @Test fun lastAndNextDatesRespectOwnerBothBoundsCentersStatusesAndArchivedHistory() = runBlocking {
        fixture { db, repo, user, f, c, a, b ->
            repo.addCompletedTraining(user, result(f, a, today.minusDays(12)))
            repo.addCompletedTraining(user, result(f, a, today.minusDays(2)))
            repo.addCompletedTraining(user, result(f, b, today.minusDays(1)))
            repo.addCompletedTraining(user, result(f, a, today.minusMonths(1)))
            db.referenceDao().insertUsers(listOf(UserEntity(id = 99, displayName = "Другой")))
            repo.addCompletedTraining(99, result(f, a, today))
            repo.addPlannedTraining(user, plan(f, a, today.minusDays(1)))
            repo.addPlannedTraining(user, plan(f, a, today))
            repo.addPlannedTraining(user, plan(c, a, today.plusDays(3)))
            repo.addPlannedTraining(user, plan(c, b, today.plusDays(1)))
            db.planningDao().insertPlannedTraining(PlannedTrainingEntity(userId = user, sportId = c, sportsComplexId = a,
                plannedDate = today, status = PlannedTrainingStatus.CANCELED))
            db.planningDao().insertPlannedTraining(PlannedTrainingEntity(userId = user, sportId = c, sportsComplexId = a,
                plannedDate = today.plusDays(1), status = PlannedTrainingStatus.COMPLETED))
            repo.setSportsCenterArchived(a, true)
            val filter = StatisticsFilter(today.withDayOfMonth(1), today.plusMonths(1), a)
            val rows = repo.observeStatisticsOverview(filter, today).first().sports.associateBy { it.id }
            assertEquals(2, rows.getValue(f).completedTrainings)
            assertEquals(today.minusDays(2), rows.getValue(f).lastTrainingDate)
            assertEquals(today, rows.getValue(f).nextPlannedDate)
            assertNull(rows.getValue(c).lastTrainingDate)
            assertEquals(today.plusDays(3), rows.getValue(c).nextPlannedDate)
            val old = repo.observeStatisticsOverview(filter.copy(endDate = today.minusDays(1)), today).first()
            assertTrue(old.sports.all { it.nextPlannedDate == null })
            val all = repo.observeStatisticsOverview(filter.copy(centerIds = emptySet()), today).first().sports
            assertEquals(today.minusDays(1), all.single { it.id == f }.lastTrainingDate)
            assertEquals(today.plusDays(1), all.single { it.id == c }.nextPlannedDate)
        }
    }

    @Test fun recurringDatesSkipCanceledCompletedAndMovedOccurrencesAcrossCenterFilters() = runBlocking {
        fixture { _, repo, user, f, _, a, b ->
            val rule = repo.addPlannedTraining(user, plan(f, a, today.minusWeeks(3), weekly = true))
            repo.cancelPlan(repo.loadPlan("rule-$rule-$today", PlanScope.EVENT), PlanScope.EVENT)
            val moved = repo.loadPlan("rule-$rule-${today.plusWeeks(1)}", PlanScope.EVENT)
            repo.updatePlan(moved, PlanScope.EVENT, plan(f, b, today.plusDays(2)))
            val completed = repo.loadPlan("rule-$rule-${today.plusWeeks(2)}", PlanScope.EVENT)
            repo.completePlan(completed, result(f, a, today), UUID.randomUUID().toString())
            val canceledRule = repo.addPlannedTraining(user, plan(f, a, today.plusDays(1), weekly = true))
            repo.cancelPlan(repo.loadPlan("rule-$canceledRule-${today.plusDays(1)}", PlanScope.SERIES), PlanScope.SERIES)
            suspend fun next(center: Long, end: LocalDate? = null) = repo.observeStatisticsOverview(
                StatisticsFilter(startDate = today, endDate = end, centerId = center), today).first().sports.single { it.id == f }.nextPlannedDate
            assertEquals(today.plusWeeks(3), next(a))
            assertEquals(today.plusDays(2), next(b))
            assertNull(next(a, today.plusWeeks(2)))
        }
    }

    @Test fun activeOverviewUpdatesAfterCreatingAndCancelingPlans() = runBlocking {
        fixture { _, repo, user, f, _, a, _ ->
            val emissions = Channel<StatisticsOverviewUiModel>(Channel.UNLIMITED)
            val job = launch { repo.observeStatisticsOverview(today = today).collect { emissions.send(it) } }
            suspend fun date() = withTimeout(5000) { emissions.receive().sports.single { it.id == f }.nextPlannedDate }
            try {
                assertNull(date())
                val id = repo.addPlannedTraining(user, plan(f, a, today.plusDays(1)))
                assertEquals(today.plusDays(1), date())
                repo.cancelPlan(repo.loadPlan("planned-$id", PlanScope.EVENT), PlanScope.EVENT)
                assertNull(date())
            } finally { job.cancelAndJoin(); emissions.close() }
        }
    }
}
