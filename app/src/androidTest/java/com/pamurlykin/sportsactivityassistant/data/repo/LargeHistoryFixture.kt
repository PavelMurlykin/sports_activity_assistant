package com.pamurlykin.sportsactivityassistant.data.repo

import com.pamurlykin.sportsactivityassistant.data.backup.*
import java.time.LocalDate
import java.util.UUID

/** Deterministic, anonymous ten-year history. Never seeds a user's application database. */
object LargeHistoryFixture {
    private fun id(key: String) = UUID.nameUUIDFromBytes("sports-acceptance:$key".toByteArray()).toString()
    fun document(): BackupDocument {
        val profile = id("profile")
        val created = "2016-01-01T00:00:00Z"
        val centers = (0..3).map { CenterBackup(name="Тестовый центр $it", city="Тестовый город",
            sportSlugs=listOf("football", "climbing"), publicId=id("center:$it"), createdAt=created) }
        val trainings = (0 until 2000).map { index ->
            val center = centers[(index / 2) % centers.size]
            val football = index % 2 == 0
            TrainingBackup(publicId=id("training:$index"), createdAt=created,
                date=LocalDate.of(2016,1,1).plusDays(index * 3652L / 2000).toString(),
                sportSlug=if (football) "football" else "climbing", centerName=center.name, centerCity=center.city,
                centerPublicId=center.publicId, profilePublicId=profile,
                football=if (football) FootballBackup(3,1,1,1,if (index % 4 == 0) "6.5" else null,5,60) else null,
                climbingRoutes=if (football) emptyList() else (0..9).map { route ->
                    val kind = route % 3
                    ClimbingRouteBackup(publicId=id("route:$index:$route"),
                        workoutType=listOf("difficulty","bouldering","speed")[kind],
                        routeDifficulty=listOf("6b+","6A","")[kind],
                        gradingSystem=listOf("french","fontainebleau","none")[kind],
                        gradeCode=listOf("6b+","6a",null)[kind], speedCourse=if (kind == 2) "standard_15m" else null,
                        completed=route % 2 == 0, repeatCount=if (route == 9) 2 else 1)
                })
        }
        val center = centers.first()
        val rule = RecurrenceRuleBackup(publicId=id("rule"),createdAt=created,startDate="2016-01-01",endDate="2026-12-31",
            sportSlug="football",centerName=center.name,centerCity=center.city,centerPublicId=center.publicId,profilePublicId=profile)
        fun plan(key: String, date: String, original: String, status: String, result: String? = null) =
            PlannedTrainingBackup(publicId=id(key),createdAt=created,date=date,occurrenceDate=original,status=status,
                sportSlug="football",centerName=center.name,centerCity=center.city,centerPublicId=center.publicId,
                profilePublicId=profile,recurrenceRulePublicId=rule.publicId,completedTrainingPublicId=result)
        return BackupDocument(exportedAt="2026-10-09T00:00:00Z",
            sports=listOf(SportBackup("football","Футбол"),SportBackup("climbing","Скалолазание")),
            centers=centers,trainings=trainings,profiles=listOf(ProfileBackup(profile,"Тестовая история",created)),
            primaryProfilePublicId=profile,recurrenceRules=listOf(rule),plannedTrainings=listOf(
                plan("complete","2016-01-01","2016-01-01","completed",trainings.first().publicId),
                plan("cancel","2016-01-08","2016-01-08","canceled"),
                plan("move","2016-01-17","2016-01-15","planned")))
    }
}
