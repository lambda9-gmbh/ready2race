package de.lambda9.ready2race.backend.competitionSetup

import de.lambda9.ready2race.backend.app.competition.boundary.CompetitionService
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesRepo
import de.lambda9.ready2race.backend.app.competitionProperties.entity.CompetitionPropertiesRequest
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.CompetitionSetupService
import de.lambda9.ready2race.backend.app.competitionSetup.entity.*
import de.lambda9.ready2race.backend.app.competitionTemplate.control.CompetitionTemplateRepo
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.event.entity.CreateEventRequest
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionPropertiesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionTemplateRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.ready2race.testing.testComprehension
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.jooq.Jooq
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompetitionSetupTemplateServiceTest {

    @Test
    fun testCreateCompetitionSetup() = testComprehension {
        val templateId = !CompetitionTemplateRepo.create(
            LocalDateTime.now().let { now ->
                CompetitionTemplateRecord(
                    id = UUID.randomUUID(),
                    createdAt = now,
                    createdBy = SYSTEM_USER,
                    updatedAt = now,
                    updatedBy = SYSTEM_USER
                )
            }
        ).orDie()

        val competitionPropertiesId =
            !CompetitionPropertiesRepo.create(
                CompetitionPropertiesRecord(
                    id = UUID.randomUUID(),
                    competitionTemplate = templateId,
                    identifier = "001",
                    name = "Name"
                ),
            ).orDie()

        val setupId = !CompetitionSetupService.createCompetitionSetup(SYSTEM_USER, competitionPropertiesId, null, false)

        assertTrue {
            !Jooq.query {
                with(COMPETITION_SETUP) {
                    fetchExists(this, COMPETITION_PROPERTIES.eq(setupId))
                }
            }
        }
    }

    @Test
    fun testUpdateCompetitionSetup() = testComprehension {
        val event = !EventService.addEvent(
            CreateEventRequest(
                name = "Event",
                description = "descr",
                location = "Loc",
                registrationAvailableFrom = LocalDateTime.now(),
                registrationAvailableTo = LocalDateTime.now(),
                invoicePrefix = "A2",
                published = false,
                paymentDueBy = LocalDate.now(),
                latePaymentDueBy = null,
                lateRegistrationAvailableTo = null,
                mixedTeamTerm = null,
                challengeEvent = false,
                challengeResultType = null,
                allowSelfSubmission = false,
                submissionNeedsVerification = false,
            ),
            userId = SYSTEM_USER
        )
        val competition = !CompetitionService.addCompetition(
            CompetitionPropertiesRequest(
                identifier = "001",
                name = "Name",
                shortName = null,
                description = null,
                competitionCategory = null,
                namedParticipants = emptyList(),
                fees = emptyList(),
                lateRegistrationAllowed = false,
                setupTemplate = null,
                challengeConfig = null,
                ratingCategoryRequired = false,
            ),
            userId = SYSTEM_USER,
            eventId = event.id
        )

        !CompetitionSetupService.updateCompetitionSetup(
            CompetitionSetupDto(
                rounds = listOf(
                    CompetitionSetupRoundDto(
                        name = "Name2",
                        required = false,
                        matches = listOf(
                            CompetitionSetupMatchDto(
                                weighting = 1,
                                teams = 4,
                                name = "MatchName1",
                                participants = listOf(1, 4, 5, 8),
                                startTimeOffset = null,
                                executionOrder = 1,
                            ),
                            CompetitionSetupMatchDto(
                                weighting = 2,
                                teams = 4,
                                name = "MatchName2",
                                participants = listOf(2, 3, 6, 7),
                                startTimeOffset = null,
                                executionOrder = 2,
                            )
                        ),
                        groups = null,
                        statisticEvaluations = null,
                        useDefaultSeeding = true,
                        places = listOf(
                            CompetitionSetupPlaceDto(roundOutcome = 5, place = 5),
                            CompetitionSetupPlaceDto(roundOutcome = 6, place = 5),
                            CompetitionSetupPlaceDto(roundOutcome = 7, place = 5),
                            CompetitionSetupPlaceDto(roundOutcome = 8, place = 5)
                        ),
                        placesOption = CompetitionSetupPlacesOption.CUSTOM
                    ),
                    CompetitionSetupRoundDto(
                        name = "Name1",
                        required = true,
                        matches = listOf(
                            CompetitionSetupMatchDto(
                                weighting = 1,
                                teams = 2,
                                name = "MatchName",
                                participants = listOf(1, 2),
                                startTimeOffset = null,
                                executionOrder = 1,
                            )
                        ),
                        groups = null,
                        statisticEvaluations = null,
                        useDefaultSeeding = true,
                        places = listOf(
                            CompetitionSetupPlaceDto(roundOutcome = 1, place = 1),
                            CompetitionSetupPlaceDto(roundOutcome = 2, place = 2)
                        ),
                        placesOption = CompetitionSetupPlacesOption.CUSTOM
                    ),

                    )
            ),
            userId = SYSTEM_USER,
            key = competition.id
        )

        val rounds = !Jooq.query {
            with(COMPETITION_SETUP_ROUND) {
                fetch(this.where(COMPETITION_SETUP_ROUND.COMPETITION_SETUP_TEMPLATE.isNull))
            }
        }
        assertEquals(2, rounds.size)

        val matches = !Jooq.query {
            with(COMPETITION_SETUP_MATCH) {
                fetch(this.where(COMPETITION_SETUP_ROUND.`in`(rounds.map { it.id })))
            }
        }
        assertEquals(3, matches.size)

        val outcomesResult = !Jooq.query {
            with(COMPETITION_SETUP_PARTICIPANT) {
                fetch(this.where(COMPETITION_SETUP_MATCH.`in`(matches.map { it.id })))
            }
        }

        assertEquals(10, outcomesResult.size)
        // todo: more asserts
    }

    // todo: test for groups
}