package com.pamurlykin.sportsactivityassistant.data.sport

import com.pamurlykin.sportsactivityassistant.ResourceTextTest
import com.pamurlykin.sportsactivityassistant.data.backup.TrainingBackup
import com.pamurlykin.sportsactivityassistant.data.dao.TrainingDao
import com.pamurlykin.sportsactivityassistant.data.entity.TrainingBundle
import com.pamurlykin.sportsactivityassistant.data.model.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class SportModuleExtensionContractTest : ResourceTextTest() {
    /** Compile-time proof of the extension contract, not an implementation of a real third sport. */
    private class Probe(override val slug: String = "contract_probe") : SportModule {
        override val title = "Contract probe"
        override fun validate(input: AddCompletedTrainingInput, allowHistorical: Boolean) {
            require(input.football == null && input.climbingRoutes.isEmpty())
        }
        override fun decodeDetails(backup: TrainingBackup, sportId: Int, complexId: Long) =
            AddCompletedTrainingInput(sportId,complexId,LocalDate.parse(backup.date)).also { validate(it,false) }
        override fun encodeDetails(bundle: TrainingBundle, common: TrainingBackup) = common.copy(sportSlug=slug)
        override fun metrics(items: List<TrainingBundle>) = listOf(MetricUiModel("Probe sessions",items.size.toString()))
        override fun highlights(items: List<TrainingBundle>) = listOf("Probe summary ${items.size}")
        override fun details(bundle: TrainingBundle) = listOf("Probe details")
        // A real sport must supply its own table, migration, backup payload, queries and editor.
        override suspend fun insertDetails(dao: TrainingDao, trainingId: Long, input: AddCompletedTrainingInput): Unit =
            error("Test-only probe has no persistence")
        override suspend fun updateDetails(dao: TrainingDao, bundle: TrainingBundle, input: AddCompletedTrainingInput): Unit =
            error("Test-only probe has no persistence")
        override suspend fun aggregate(dao: TrainingDao, selection: StatisticsSelection, includeMetrics: Boolean): SportAggregate =
            error("Test-only probe has no SQL queries")
    }

    @Test fun aThirdContractDispatchesWithoutChangingTheShippedRegistryOrSharedScreens() {
        val probe = Probe()
        val registry = SportModuleRegistry(SportModules.all + probe)
        assertSame(probe,registry.require("contract_probe"))
        assertSame(FootballModule,registry.require("football"))
        val input = registry.require(probe.slug).decodeDetails(
            TrainingBackup(date="2025-12-14",sportSlug=probe.slug,centerName="Test center"),3,42)
        registry.require(probe.slug).validate(input)
        assertEquals(LocalDate.of(2025,12,14),input.date)
        assertEquals(42L,input.complexId)
        assertEquals(listOf(MetricUiModel("Probe sessions","0")),registry.require(probe.slug).metrics(emptyList()))
        assertNull(SportModules.find(probe.slug))
        assertEquals(listOf("football","climbing"),SportModules.all.map { it.slug })
    }

    @Test fun duplicateOrBlankSlugCannotSilentlyReplaceAnotherSport() {
        assertTrue(runCatching { SportModuleRegistry(SportModules.all + Probe("football")) }.isFailure)
        assertTrue(runCatching { SportModuleRegistry(listOf(Probe(" "))) }.isFailure)
    }

    @Test fun registryTakesItsOwnSnapshotAndUnsupportedSportStillFailsExplicitly() {
        val modules = SportModules.all.toMutableList()
        val registry = SportModuleRegistry(modules)
        modules.clear()
        assertEquals(2,registry.all.size)
        assertTrue(runCatching { registry.require("unknown") }.exceptionOrNull()!!.message!!.contains("не поддерживается"))
    }
}
