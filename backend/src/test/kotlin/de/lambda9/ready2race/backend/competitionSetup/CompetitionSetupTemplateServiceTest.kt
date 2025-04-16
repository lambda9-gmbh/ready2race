package de.lambda9.ready2race.backend.competitionSetup

import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesRepo
import de.lambda9.ready2race.backend.app.competitionProperties.entity.CompetitionPropertiesRequestDto
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.CompetitionSetupService
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupDto
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupMatchDto
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupRoundDto
import de.lambda9.ready2race.backend.app.competitionTemplate.boundary.CompetitionTemplateService
import de.lambda9.ready2race.backend.app.competitionTemplate.control.CompetitionTemplateRepo
import de.lambda9.ready2race.backend.app.competitionTemplate.entity.CompetitionTemplateRequest
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionPropertiesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionTemplateRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_OUTCOME
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_ROUND
import de.lambda9.ready2race.testing.testComprehension
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.jooq.Jooq
import org.junit.Test
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

        val setupId = !CompetitionSetupService.createCompetitionSetup(SYSTEM_USER, competitionPropertiesId)

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
        val template = !CompetitionTemplateService.addCompetitionTemplate(
            CompetitionTemplateRequest(
                properties = CompetitionPropertiesRequestDto(
                    identifier = "001",
                    name = "Name",
                    shortName = null,
                    description = null,
                    competitionCategory = null,
                    namedParticipants = emptyList(),
                    fees = emptyList()
                )
            ),
            userId = SYSTEM_USER
        )

        !CompetitionSetupService.updateCompetitionSetup(
            CompetitionSetupDto(
                rounds = listOf(
                    CompetitionSetupRoundDto(
                        name = "Name2",
                        required = false,
                        matches = listOf(
                            CompetitionSetupMatchDto(
                                duplicatable = false,
                                weighting = 1,
                                teams = 4,
                                name = "MatchName1",
                                outcomes = listOf(1, 4, 5, 8)
                            ),
                            CompetitionSetupMatchDto(
                                duplicatable = false,
                                weighting = 2,
                                teams = 4,
                                name = "MatchName2",
                                outcomes = listOf(2, 3, 6, 7)
                            )
                        ),
                        groups = null,
                        statisticEvaluations = null
                    ),
                    CompetitionSetupRoundDto(
                        name = "Name1",
                        required = true,
                        matches = listOf(
                            CompetitionSetupMatchDto(
                                duplicatable = false,
                                weighting = 1,
                                teams = 2,
                                name = "MatchName",
                                outcomes = listOf(1, 2),
                            )
                        ),
                        groups = null,
                        statisticEvaluations = null
                    ),

                    )
            ),
            userId = SYSTEM_USER,
            key = template.id
        )

        val roundsCount = !Jooq.query {
            with(COMPETITION_SETUP_ROUND) {
                fetchCount(this)
            }
        }
        assertEquals(roundsCount, 2)

        val matchesCount = !Jooq.query {
            with(COMPETITION_SETUP_MATCH) {
                fetchCount(this)
            }
        }
        assertEquals(matchesCount, 3)

        val outcomesResult = !Jooq.query {
            with(COMPETITION_SETUP_OUTCOME) {
                fetch(this)
            }
        }
        val outcomesList = outcomesResult.toList()
        assertEquals(outcomesList.size, 10)
        // todo: more asserts
    }

    // todo: test for groups
}