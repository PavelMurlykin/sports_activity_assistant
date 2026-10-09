package com.pamurlykin.sportsactivityassistant.data.repo

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.backup.*
import com.pamurlykin.sportsactivityassistant.data.entity.*
import com.pamurlykin.sportsactivityassistant.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class PlanningLifecycleTest {
    private val date = LocalDate.parse("2020-02-29")
    private suspend fun fixture(test: suspend (AppDatabase, AppRepository, Long, Int, Long) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            val repo = AppRepository(db); val user = repo.localProfileId()
            val sport = db.referenceDao().getSports().first { it.slug == "football" }.id
            test(db, repo, user, sport, repo.getComplexOptionsForSport(sport).first().id)
        } finally { db.close() }
    }
    private fun plan(s: Int, c: Long, weekly: Boolean = false) = AddPlannedTrainingInput(s, c, date, weekly, 1, null)
    private fun result(s: Int, c: Long) = AddCompletedTrainingInput(s,c,date, FootballTrainingInput(3,1,1,1,null,null,null))
    private suspend fun events(repo: AppRepository, user: Long, day: LocalDate = date) =
        repo.getScheduleMonth(user, YearMonth.from(day), day, LocalDate.now()).selectedDayEvents

    @Test fun resultFromPlanIsAtomicIdempotentLinkedAndReopenedOnDeletion() = runBlocking {
        fixture { db, repo, user, sport, center ->
            val id = repo.addPlannedTraining(user, plan(sport,center))
            val snapshot = repo.loadPlan("planned-$id")
            val request = UUID.randomUUID().toString()
            val input = result(sport,center).copy(date = date.plusDays(1))
            val ids = coroutineScope { (1..2).map { async { repo.completePlan(snapshot,input,request) } }.awaitAll() }
            assertEquals(ids.first(), ids.last())
            assertTrue(events(repo,user).isEmpty())
            val event = events(repo,user,input.date).single()
            assertEquals(ScheduleEventState.COMPLETED,event.state); assertTrue(event.linkedPlan)
            assertEquals(ids.first(),db.planningDao().getPlan(id)!!.completedTrainingId)
            assertTrue(runCatching { repo.completePlan(snapshot,input,UUID.randomUUID().toString()) }.isFailure)
            repo.deleteCompletedTraining(repo.loadTrainingForEdit(ids.first()))
            assertNull(db.planningDao().getPlan(id)!!.completedTrainingId)
            assertEquals(ScheduleEventState.PLANNED,events(repo,user).single().state)
        }
    }

    @Test fun movingAndCancelingOneOccurrenceNeverDuplicatesOrChangesOtherDates() = runBlocking {
        fixture { db,repo,user,s,c ->
            val rule = repo.addPlannedTraining(user,plan(s,c,true))
            val key = "rule-$rule-$date"
            val original = repo.loadPlan(key)
            repo.updatePlan(original,PlanScope.EVENT,original.input().copy(date = date.plusDays(1)))
            assertTrue(events(repo,user).isEmpty())
            val moved = events(repo,user,date.plusDays(1)).single()
            assertTrue(moved.isRecurring); assertEquals("Перенесена с $date",moved.details.single())
            assertEquals(ScheduleEventState.PLANNED,events(repo,user,date.plusWeeks(1)).single().state)
            repo.cancelPlan(repo.loadPlan(moved.planKey!!),PlanScope.EVENT)
            assertEquals(ScheduleEventState.CANCELED,events(repo,user,date.plusDays(1)).single().state)
            assertEquals(1,db.planningDao().getAllPlannedTrainings().size)
        }
    }

    @Test fun wholeSeriesChangesOnlyVirtualEventsAndCancelKeepsResultsAndExceptions() = runBlocking {
        fixture { db,repo,user,s,c ->
            val rule = repo.addPlannedTraining(user,plan(s,c,true))
            val key = "rule-$rule-$date"
            val completed = repo.completePlan(repo.loadPlan(key),result(s,c),UUID.randomUUID().toString())
            val next = date.plusWeeks(1)
            repo.cancelPlan(repo.loadPlan("rule-$rule-$next"),PlanScope.EVENT)
            val third = date.plusWeeks(2)
            val snapshot = repo.loadPlan("rule-$rule-$third",PlanScope.SERIES)
            repo.updatePlan(snapshot,PlanScope.SERIES,snapshot.input().copy(intervalWeeks = 2))
            assertEquals(ScheduleEventState.CANCELED,events(repo,user,next).single().state)
            val whole = repo.loadPlan("rule-$rule-$third",PlanScope.SERIES)
            repo.cancelPlan(whole,PlanScope.SERIES)
            assertEquals(ScheduleEventState.CANCELED,events(repo,user,third).single().state)
            assertEquals(ScheduleEventState.COMPLETED,events(repo,user).single().state)
            repo.deleteCompletedTraining(repo.loadTrainingForEdit(completed))
            assertEquals(ScheduleEventState.CANCELED,events(repo,user).single().state)
            assertTrue(db.planningDao().getRule(rule)!!.isCanceled)
        }
    }

    @Test fun staleForeignCanceledAndFutureResultsRejectedWithoutWrites() = runBlocking {
        fixture { db,repo,user,s,c ->
            val id = repo.addPlannedTraining(user,plan(s,c))
            val old = repo.loadPlan("planned-$id")
            repo.updatePlan(old,PlanScope.EVENT,old.input().copy(date = date.plusDays(1)))
            assertTrue(runCatching { repo.cancelPlan(old,PlanScope.EVENT) }.isFailure)
            assertTrue(runCatching { repo.completePlan(old,result(s,c),UUID.randomUUID().toString()) }.isFailure)
            val fresh = repo.loadPlan("planned-$id")
            assertTrue(runCatching { repo.completePlan(fresh,result(s,c).copy(date = LocalDate.now().plusDays(1)),UUID.randomUUID().toString()) }.isFailure)
            repo.cancelPlan(fresh,PlanScope.EVENT)
            assertTrue(runCatching { repo.completePlan(repo.loadPlan("planned-$id"),result(s,c),UUID.randomUUID().toString()) }.isFailure)
            val other = db.referenceDao().insertUser(UserEntity())
            val foreign = repo.addPlannedTraining(other,plan(s,c))
            assertTrue(runCatching { repo.loadPlan("planned-$foreign") }.isFailure)
            assertTrue(db.trainingDao().getAllTrainingBundles().isEmpty())
        }
    }

    @Test fun failuresDuringPlanCompletionAndResultDeletionRollBackBothSides() = runBlocking {
        fixture { db,repo,user,s,c ->
            val rule = repo.addPlannedTraining(user,plan(s,c,true))
            val key = "rule-$rule-$date"
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_plan BEFORE INSERT ON planned_trainings BEGIN SELECT RAISE(ABORT,'test failure'); END")
            assertTrue(runCatching { repo.completePlan(repo.loadPlan(key),result(s,c),UUID.randomUUID().toString()) }.isFailure)
            assertTrue(db.trainingDao().getAllTrainingBundles().isEmpty()); assertTrue(db.planningDao().getAllPlannedTrainings().isEmpty())
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_plan")
            val training = repo.completePlan(repo.loadPlan(key),result(s,c),UUID.randomUUID().toString())
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_delete BEFORE DELETE ON trainings BEGIN SELECT RAISE(ABORT,'test failure'); END")
            assertTrue(runCatching { repo.deleteCompletedTraining(repo.loadTrainingForEdit(training)) }.isFailure)
            assertNotNull(db.trainingDao().getTrainingBundle(training))
            assertEquals(training,db.planningDao().getAllPlannedTrainings().single().completedTrainingId)
            assertEquals(ScheduleEventState.COMPLETED,events(repo,user).single().state)
        }
    }

    @Test fun backupRestoresMovedCanceledCompletedSeriesAndLinksExactly() = runBlocking {
        fixture { db,repo,user,s,c ->
            val rule = repo.addPlannedTraining(user,plan(s,c,true))
            val first = repo.loadPlan("rule-$rule-$date")
            repo.updatePlan(first,PlanScope.EVENT,first.input().copy(date = date.plusDays(1)))
            val next = date.plusWeeks(1)
            repo.cancelPlan(repo.loadPlan("rule-$rule-$next"),PlanScope.EVENT)
            val third = date.plusWeeks(2)
            repo.completePlan(repo.loadPlan("rule-$rule-$third"),result(s,c).copy(date = third),UUID.randomUUID().toString())
            val whole = repo.loadPlan("rule-$rule-${date.plusWeeks(3)}",PlanScope.SERIES)
            repo.cancelPlan(whole,PlanScope.SERIES)
            val bytes = repo.createBackup().toByteArray()
            val source = repo.prepareImport(bytes)
            assertEquals(6,source.sourceVersion); assertTrue(BackupValidation.errors(source).isEmpty())
            val before = source.document
            fixture { restoredDb,restored,restoredUser,_,_ ->
                restored.importData(bytes,restoredUser)
                val after = BackupCodec.decode(restored.createBackup())
                assertEquals(before.plannedTrainings,after.plannedTrainings)
                assertEquals(before.recurrenceRules,after.recurrenceRules)
                assertEquals(ScheduleEventState.CANCELED,events(restored,restoredUser,date.plusDays(1)).single().state)
                assertEquals(ScheduleEventState.COMPLETED,events(restored,restoredUser,third).single().state)
                assertEquals(0,restored.importData(bytes,restoredUser).importedPlans)
                assertEquals(3,restoredDb.planningDao().getAllPlannedTrainings().size)
                restoredDb.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            }
            assertEquals(3,db.planningDao().getAllPlannedTrainings().size)
        }
    }

    @Test fun skippingReferencedResultBlocksImportBeforeAnyWrite() = runBlocking {
        fixture { _,repo,user,s,c ->
            val planId = repo.addPlannedTraining(user,plan(s,c))
            repo.completePlan(repo.loadPlan("planned-$planId"),result(s,c),UUID.randomUUID().toString())
            val source = repo.prepareImport(repo.createBackup().toByteArray())
            fixture { db,target,targetUser,_,_ ->
                val preview = target.previewImport(source)
                val choices = preview.choices.copy(skipTrainingIds = setOf(source.document.trainings.single().publicId!!))
                assertFalse(target.previewImport(source,choices).canApply)
                assertTrue(runCatching { target.applyImport(source,choices) }.isFailure)
                assertTrue(db.planningDao().getAllPlannedTrainings().isEmpty())
                assertTrue(db.trainingDao().getAllTrainingBundles().isEmpty())
                assertEquals(targetUser,target.localProfileId())
            }
        }
    }

    @Test fun incomingExceptionCannotReuseAnOccupiedLegacyOccurrenceWithoutExplicitKeepLocal() = runBlocking {
        fixture { db,repo,user,s,c ->
            val rule = repo.addPlannedTraining(user,plan(s,c,true))
            val legacy = PlannedTrainingEntity(userId = user,sportId = s,sportsComplexId = c,plannedDate = date,recurrenceRuleId = rule)
            val id = db.planningDao().insertPlannedTraining(legacy)
            val original = BackupCodec.decode(repo.createBackup())
            val incoming = original.copy(plannedTrainings = listOf(original.plannedTrainings.single().copy(
                publicId = UUID.randomUUID().toString(),occurrenceDate = date.toString())))
            val source = repo.prepareImport(BackupCodec.encode(incoming).toByteArray())
            val preview = repo.previewImport(source)
            assertFalse(preview.canApply)
            val conflict = preview.records.single { it.kind == "plan" }
            assertTrue(conflict.conflict)
            val choices = preview.choices.copy(keepLocalIds = setOf(incoming.plannedTrainings.single().publicId!!))
            assertTrue(repo.previewImport(source,choices).canApply)
            assertEquals(0,repo.applyImport(source,choices).importedPlans)
            assertEquals(id,db.planningDao().getAllPlannedTrainings().single().id)
            assertNull(db.planningDao().getPlan(id)!!.occurrenceDate)
        }
    }

    @Test fun creationRetryAndArchivedCenterPolicyPreserveHistoricalPlan() = runBlocking {
        fixture { db,repo,user,s,c ->
            val request = UUID.randomUUID().toString()
            val id = repo.addPlannedTraining(user,plan(s,c),request)
            val snapshot = repo.loadPlan("planned-$id")
            repo.setSportsCenterArchived(c,true)
            assertEquals(id,repo.addPlannedTraining(user,plan(s,c),request))
            repo.updatePlan(snapshot,PlanScope.EVENT,snapshot.input().copy(date = date.plusDays(1)))
            assertTrue(runCatching { repo.completePlan(repo.loadPlan("planned-$id"),result(s,c),UUID.randomUUID().toString()) }.isFailure)
            assertTrue(runCatching { repo.addPlannedTraining(user,plan(s,c)) }.isFailure)
            assertEquals(1,db.planningDao().getAllPlannedTrainings().size)
        }
    }

    @Test fun seriesEditRetrySurvivesChangingAnchorAwayFromSelectedOccurrence() = runBlocking {
        fixture { db,repo,user,s,c ->
            val rule = repo.addPlannedTraining(user,plan(s,c,true))
            val old = repo.loadPlan("rule-$rule-$date",PlanScope.SERIES)
            val changed = old.input().copy(date = date.plusDays(1),intervalWeeks = 3,endDate = date.plusMonths(1))
            repo.updatePlan(old,PlanScope.SERIES,changed)
            repo.updatePlan(old,PlanScope.SERIES,changed)
            assertEquals(changed.date,db.planningDao().getRule(rule)!!.startDate)
            assertEquals(1,db.planningDao().getAllRecurrenceRules().size)
            assertTrue(events(repo,user).isEmpty())
            assertEquals(1,events(repo,user,changed.date).size)
        }
    }

    @Test fun databaseObservationSeesExternalPlanCancellationAndCenterRename() = runBlocking {
        fixture { db,repo,user,s,c ->
            val id = repo.addPlannedTraining(user,plan(s,c))
            coroutineScope {
                val changed = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5000) {
                    repo.observeScheduleMonth(user,YearMonth.from(date),date,date).first {
                        it.selectedDayEvents.singleOrNull()?.state == ScheduleEventState.CANCELED
                    }
                } }
                AppRepository(db).cancelPlan(repo.loadPlan("planned-$id"),PlanScope.EVENT)
                assertEquals(ScheduleEventState.CANCELED,changed.await().selectedDayEvents.single().state)
                val renamed = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5000) {
                    repo.observeScheduleMonth(user,YearMonth.from(date),date,date).first {
                        it.selectedDayEvents.singleOrNull()?.complexName == "Новая арена"
                    }
                } }
                repo.saveSportsCenter(SaveSportsCenterInput(c,"Новая арена",null,setOf(s)))
                assertEquals("Новая арена",renamed.await().selectedDayEvents.single().complexName)
            }
        }
    }
}
