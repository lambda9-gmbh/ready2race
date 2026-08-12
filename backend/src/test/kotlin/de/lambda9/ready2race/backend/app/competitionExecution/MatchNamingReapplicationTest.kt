package de.lambda9.ready2race.backend.app.competitionExecution

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupMatchRepo
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupPlacesOption
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionDeregistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionPropertiesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchNamingRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRoundRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_DEREGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH_NAMING
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_ROUND
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_REGISTRATION
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Die Wieder-Anwendung der Lauf-Benennungen bei der Rundenerzeugung - der Nutzer-Fall vom
 * 12.08.2026: Runde mit n=8 erzeugt (VF1..VF4), gelöscht, eine Abmeldung, mit n=7 neu erzeugt.
 * Vor dem Fix behielt der Lauf, dessen weighting im Satz für das NEUE n nicht vorkam, den Namen
 * der ALTEN Anwendung - zweimal "VF1" auf Zeitplan und Schiedsrichter-Dashboard, kein "VF2".
 *
 * Der Ausgangszustand (der im Setup konfigurierte Name samt Reihenfolge) wird seit V202608121200
 * beim ersten Überschreiben gesichert und beim Zurücksetzen GENAU wiederhergestellt - kein
 * erfundener Name (siehe CompetitionSetupMatchRepo.applyNaming).
 */
class MatchNamingReapplicationTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 12, 10, 0)

    private data class Seeded(
        val eventId: UUID,
        val competitionId: UUID,
        val roundId: UUID,
        /** Meldungs-Ids in teamNumber-Reihenfolge 1..8. */
        val registrationIds: List<UUID>,
    )

    /**
     * Ein Wettkampf mit einer einzigen (Viertelfinal-)Runde: vier Setup-Läufe à zwei Plätze
     * (Kapazität 8) mit den Ausgangsnamen "Lauf 1".."Lauf 4", acht Meldungen und zwei
     * Benennungs-Sätzen - n=8 benennt alle vier Läufe (VF1..VF4), n=7 nur die weightings 1..3
     * (VF1..VF3): genau die Lücke, in der vorher der Alt-Name stehen blieb.
     */
    private fun TestComprehensionScope<JEnv>.seed(): Seeded {
        val eventId = UUID.randomUUID()
        val competitionId = UUID.randomUUID()
        val propertiesId = UUID.randomUUID()
        val roundId = UUID.randomUUID()

        !EVENT.insert(EventRecord(id = eventId, name = "Testregatta", createdAt = now, updatedAt = now))
        !COMPETITION.insert(
            CompetitionRecord(id = competitionId, event = eventId, createdAt = now, updatedAt = now)
        )
        !COMPETITION_PROPERTIES.insert(
            CompetitionPropertiesRecord(
                id = propertiesId,
                competition = competitionId,
                identifier = "1",
                name = "Coastal Einer",
            )
        )
        !COMPETITION_SETUP.insert(
            CompetitionSetupRecord(competitionProperties = propertiesId, createdAt = now, updatedAt = now)
        )
        !COMPETITION_SETUP_ROUND.insert(
            CompetitionSetupRoundRecord(
                id = roundId,
                competitionSetup = propertiesId,
                name = "Viertelfinale",
                required = true,
                useDefaultSeeding = true,
                placesOption = CompetitionSetupPlacesOption.EQUAL.name,
            )
        )
        (1..4).forEach { weighting ->
            !COMPETITION_SETUP_MATCH.insert(
                CompetitionSetupMatchRecord(
                    id = UUID.randomUUID(),
                    competitionSetupRound = roundId,
                    weighting = weighting,
                    teams = 2,
                    name = "Lauf $weighting",
                    executionOrder = weighting,
                )
            )
        }

        // Benennungs-Sätze: Abweichungen vom Ausgangszustand je Bracket-Größe.
        (1..4).forEach { weighting ->
            !COMPETITION_SETUP_MATCH_NAMING.insert(
                CompetitionSetupMatchNamingRecord(
                    competitionSetupRound = roundId,
                    participantCount = 8,
                    matchWeighting = weighting,
                    name = "VF$weighting",
                    executionOrder = null,
                )
            )
        }
        (1..3).forEach { weighting ->
            !COMPETITION_SETUP_MATCH_NAMING.insert(
                CompetitionSetupMatchNamingRecord(
                    competitionSetupRound = roundId,
                    participantCount = 7,
                    matchWeighting = weighting,
                    name = "VF$weighting",
                    executionOrder = null,
                )
            )
        }

        val registrationIds = (1..8).map { teamNumber ->
            val clubId = UUID.randomUUID()
            val eventRegistrationId = UUID.randomUUID()
            val registrationId = UUID.randomUUID()
            !CLUB.insert(ClubRecord(id = clubId, name = "RV Test $clubId", createdAt = now, updatedAt = now))
            !EVENT_REGISTRATION.insert(
                EventRegistrationRecord(
                    id = eventRegistrationId,
                    event = eventId,
                    club = clubId,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            !COMPETITION_REGISTRATION.insert(
                CompetitionRegistrationRecord(
                    id = registrationId,
                    eventRegistration = eventRegistrationId,
                    competition = competitionId,
                    club = clubId,
                    teamNumber = teamNumber,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            registrationId
        }

        return Seeded(eventId, competitionId, roundId, registrationIds)
    }

    private fun TestComprehensionScope<JEnv>.deregister(registrationId: UUID, roundId: UUID) {
        !COMPETITION_DEREGISTRATION.insert(
            CompetitionDeregistrationRecord(
                competitionRegistration = registrationId,
                competitionSetupRound = roundId,
                reason = "Krankheit",
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    /** Name und Reihenfolge der Setup-Läufe der Runde, nach weighting sortiert. */
    private fun TestComprehensionScope<JEnv>.namesByWeighting(roundId: UUID): List<Pair<String?, Int>> =
        (!CompetitionSetupMatchRepo.get(listOf(roundId)))
            .sortedBy { it.weighting }
            // execution_order ist NOT NULL - das `!!` deckt nur die generische Nullbarkeit des
            // jOOQ-Generators ab.
            .map { it.name to it.executionOrder!! }

    @Test
    fun reapplyingASmallerNamingSetResetsUncoveredMatchesToTheirSetupNames() = testComprehension {
        val seeded = seed()

        // Erste Erzeugung: n=8, alle vier Läufe bekommen ihre VF-Namen.
        !CompetitionExecutionService.createNewRound(seeded.eventId, seeded.competitionId, SYSTEM_USER)
        assertEquals(
            listOf<Pair<String?, Int>>("VF1" to 1, "VF2" to 2, "VF3" to 3, "VF4" to 4),
            namesByWeighting(seeded.roundId),
        )

        // Der Nutzer-Fall: Runde löschen, eine Abmeldung, mit n=7 neu erzeugen. Der Satz für 7
        // deckt nur die weightings 1..3 - weighting 4 muss auf seinen Setup-Namen zurück, statt
        // als zweites "VF..."-Duplikat aus der n=8-Anwendung stehen zu bleiben.
        !CompetitionExecutionService.deleteCurrentRound(seeded.competitionId, seeded.eventId)
        deregister(seeded.registrationIds[7], seeded.roundId)
        !CompetitionExecutionService.createNewRound(seeded.eventId, seeded.competitionId, SYSTEM_USER)

        val names = namesByWeighting(seeded.roundId)
        assertEquals(
            listOf<Pair<String?, Int>>("VF1" to 1, "VF2" to 2, "VF3" to 3, "Lauf 4" to 4),
            names,
        )
        // Keine Duplikate mehr - genau die Verwirrung aus den Screenshots.
        assertEquals(names.map { it.first }.distinct().size, names.size)
    }

    @Test
    fun aMissingNamingSetResetsTheWholeRoundToItsSetupNames() = testComprehension {
        val seeded = seed()

        !CompetitionExecutionService.createNewRound(seeded.eventId, seeded.competitionId, SYSTEM_USER)
        assertEquals(
            listOf<Pair<String?, Int>>("VF1" to 1, "VF2" to 2, "VF3" to 3, "VF4" to 4),
            namesByWeighting(seeded.roundId),
        )

        // Zwei Abmeldungen: n=6 hat gar keinen Benennungs-Satz - dann müssen ALLE Läufe zu ihren
        // Setup-Namen zurück, nicht nur die vom letzten Satz nicht abgedeckten.
        !CompetitionExecutionService.deleteCurrentRound(seeded.competitionId, seeded.eventId)
        deregister(seeded.registrationIds[7], seeded.roundId)
        deregister(seeded.registrationIds[6], seeded.roundId)
        !CompetitionExecutionService.createNewRound(seeded.eventId, seeded.competitionId, SYSTEM_USER)

        assertEquals(
            listOf<Pair<String?, Int>>("Lauf 1" to 1, "Lauf 2" to 2, "Lauf 3" to 3, "Lauf 4" to 4),
            namesByWeighting(seeded.roundId),
        )
    }
}
