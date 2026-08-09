package de.lambda9.ready2race.backend.app.competitionExecution

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupPlacesOption
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchTeamRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionPropertiesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRoundRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_PARTICIPANT
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_ROUND
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_REGISTRATION
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.select
import de.lambda9.ready2race.backend.database.update
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import java.time.LocalDateTime
import java.util.UUID

/**
 * Die Zeitbasis der Vorrichtung - beliebig, aber fest, damit gleichartige Aufrufe vergleichbar
 * bleiben.
 */
val ROUND_PROGRESSION_SEED_TIME: LocalDateTime = LocalDateTime.of(2026, 8, 14, 10, 0)

/**
 * Das Ergebnis von [seedTwoRoundCompetition] - alle Ids, die die Automatik und ihre Tests
 * brauchen, um auf der aufgebauten Regatta weiterzuarbeiten.
 */
data class SeededRoundProgression(
    val eventId: UUID,
    val competitionId: UUID,
    val firstRoundId: UUID,
    val secondRoundId: UUID,
    val firstRoundMatchIds: List<UUID>,
    val secondRoundSetupMatchId: UUID,
    val registrationIds: List<UUID>,
    val userId: UUID,
)

/**
 * Eine Veranstaltung mit einem Wettkampf und einer zweistufigen Runden-Kette ("Vorlauf" ->
 * "Finale"): der Zustand, in dem die Automatik zur Erzeugung der Folgerunde greifen soll. Der
 * Vorlauf ist bereits mit vier gemeldeten Mannschaften auf zwei Läufe besetzt, aber noch nicht
 * gewertet - dafür gibt es [finishFirstRound].
 *
 * [eventAutoCreate] und [competitionAutoCreate] steuern die Einstellung, an der der Auslöser
 * (Task 5) unterscheidet, ob die Automatik greifen soll - hier nur als Parameter, weil dieselbe
 * Vorrichtung auch die Automatik-Tests bedient.
 */
fun TestComprehensionScope<JEnv>.seedTwoRoundCompetition(
    eventAutoCreate: Boolean = false,
    competitionAutoCreate: Boolean? = null,
): SeededRoundProgression {
    // CompetitionExecutionService.createNewRound schreibt userId als created_by/updated_by auf
    // die neu erzeugten Läufe - die Spalte ist zwar nullable, verlangt aber bei einem gesetzten
    // Wert einen tatsächlich existierenden app_user (Fremdschlüssel). SYSTEM_USER ist genau dafür
    // da: die Testinfrastruktur legt ihn in jeder Datenbank an (initializeDatabase), ohne dass die
    // Vorrichtung selbst einen Benutzer anlegen muss.
    val userId = SYSTEM_USER

    val eventId = UUID.randomUUID()
    val competitionId = UUID.randomUUID()
    val propertiesId = UUID.randomUUID()
    val firstRoundId = UUID.randomUUID()
    val secondRoundId = UUID.randomUUID()
    val firstRoundMatchAId = UUID.randomUUID()
    val firstRoundMatchBId = UUID.randomUUID()
    val secondRoundSetupMatchId = UUID.randomUUID()
    val clubId = UUID.randomUUID()
    val eventRegistrationId = UUID.randomUUID()

    !EVENT.insert(
        EventRecord(
            id = eventId,
            name = "Testregatta",
            createdAt = ROUND_PROGRESSION_SEED_TIME,
            updatedAt = ROUND_PROGRESSION_SEED_TIME,
            autoCreateFollowingRounds = eventAutoCreate,
        )
    )

    !COMPETITION.insert(
        CompetitionRecord(
            id = competitionId,
            event = eventId,
            createdAt = ROUND_PROGRESSION_SEED_TIME,
            updatedAt = ROUND_PROGRESSION_SEED_TIME,
            autoCreateFollowingRounds = competitionAutoCreate,
        )
    )
    !COMPETITION_PROPERTIES.insert(
        CompetitionPropertiesRecord(
            id = propertiesId,
            competition = competitionId,
            identifier = "1",
            name = "Vierer",
        )
    )
    !COMPETITION_SETUP.insert(
        CompetitionSetupRecord(
            competitionProperties = propertiesId,
            createdAt = ROUND_PROGRESSION_SEED_TIME,
            updatedAt = ROUND_PROGRESSION_SEED_TIME,
        )
    )

    // Runde B (Finale) zuerst - Runde A verweist mit next_round auf sie, die Fremdschlüssel-
    // Reihenfolge lässt keine andere Einfügereihenfolge zu.
    !COMPETITION_SETUP_ROUND.insert(
        CompetitionSetupRoundRecord(
            id = secondRoundId,
            competitionSetup = propertiesId,
            name = "Finale",
            required = true,
            useDefaultSeeding = true,
            placesOption = CompetitionSetupPlacesOption.EQUAL.name,
            nextRound = null,
        )
    )
    // Runde A: der Vorlauf, dessen Ergebnis über die Paarungen des Finales entscheidet.
    !COMPETITION_SETUP_ROUND.insert(
        CompetitionSetupRoundRecord(
            id = firstRoundId,
            competitionSetup = propertiesId,
            name = "Vorlauf",
            required = true,
            useDefaultSeeding = true,
            placesOption = CompetitionSetupPlacesOption.EQUAL.name,
            nextRound = secondRoundId,
        )
    )

    !COMPETITION_SETUP_MATCH.insert(
        CompetitionSetupMatchRecord(
            id = firstRoundMatchAId,
            competitionSetupRound = firstRoundId,
            weighting = 1,
            name = "Lauf 1",
            executionOrder = 1,
            teams = 2,
        )
    )
    !COMPETITION_SETUP_MATCH.insert(
        CompetitionSetupMatchRecord(
            id = firstRoundMatchBId,
            competitionSetupRound = firstRoundId,
            weighting = 2,
            name = "Lauf 2",
            executionOrder = 2,
            teams = 2,
        )
    )
    !COMPETITION_SETUP_MATCH.insert(
        CompetitionSetupMatchRecord(
            id = secondRoundSetupMatchId,
            competitionSetupRound = secondRoundId,
            weighting = 1,
            name = "Finale",
            executionOrder = 1,
            teams = 2,
        )
    )

    // Die Setup-Teilnehmer des Finales - wer aus dem Vorlauf hier landet, entscheidet die
    // Automatik anhand der Plätze, die [finishFirstRound] setzt.
    !COMPETITION_SETUP_PARTICIPANT.insert(
        CompetitionSetupParticipantRecord(
            id = UUID.randomUUID(),
            competitionSetupMatch = secondRoundSetupMatchId,
            seed = 1,
            ranking = 1,
        )
    )
    !COMPETITION_SETUP_PARTICIPANT.insert(
        CompetitionSetupParticipantRecord(
            id = UUID.randomUUID(),
            competitionSetupMatch = secondRoundSetupMatchId,
            seed = 2,
            ranking = 2,
        )
    )

    !CLUB.insert(
        ClubRecord(
            id = clubId,
            name = "Testverein",
            createdAt = ROUND_PROGRESSION_SEED_TIME,
            updatedAt = ROUND_PROGRESSION_SEED_TIME,
        )
    )
    !EVENT_REGISTRATION.insert(
        EventRegistrationRecord(
            id = eventRegistrationId,
            event = eventId,
            club = clubId,
            createdAt = ROUND_PROGRESSION_SEED_TIME,
            updatedAt = ROUND_PROGRESSION_SEED_TIME,
        )
    )

    val registrationIds = (1..4).map { teamNumber ->
        val registrationId = UUID.randomUUID()
        !COMPETITION_REGISTRATION.insert(
            CompetitionRegistrationRecord(
                id = registrationId,
                eventRegistration = eventRegistrationId,
                competition = competitionId,
                club = clubId,
                name = "Boot $teamNumber",
                createdAt = ROUND_PROGRESSION_SEED_TIME,
                updatedAt = ROUND_PROGRESSION_SEED_TIME,
                teamNumber = teamNumber,
            )
        )
        registrationId
    }

    // Nur der Vorlauf ist bereits als Lauf besetzt - das Finale entsteht erst durch die Automatik.
    val firstRoundMatches = listOf(
        firstRoundMatchAId to registrationIds.subList(0, 2),
        firstRoundMatchBId to registrationIds.subList(2, 4),
    )
    firstRoundMatches.forEach { (setupMatchId, matchRegistrationIds) ->
        !COMPETITION_MATCH.insert(
            CompetitionMatchRecord(
                competitionSetupMatch = setupMatchId,
                startTime = ROUND_PROGRESSION_SEED_TIME,
                createdAt = ROUND_PROGRESSION_SEED_TIME,
                updatedAt = ROUND_PROGRESSION_SEED_TIME,
                activatedAt = ROUND_PROGRESSION_SEED_TIME,
            )
        )
        matchRegistrationIds.forEachIndexed { index, registrationId ->
            !COMPETITION_MATCH_TEAM.insert(
                CompetitionMatchTeamRecord(
                    id = UUID.randomUUID(),
                    competitionMatch = setupMatchId,
                    competitionRegistration = registrationId,
                    startNumber = index + 1,
                    place = null,
                    createdAt = ROUND_PROGRESSION_SEED_TIME,
                    updatedAt = ROUND_PROGRESSION_SEED_TIME,
                )
            )
        }
    }

    return SeededRoundProgression(
        eventId = eventId,
        competitionId = competitionId,
        firstRoundId = firstRoundId,
        secondRoundId = secondRoundId,
        firstRoundMatchIds = listOf(firstRoundMatchAId, firstRoundMatchBId),
        secondRoundSetupMatchId = secondRoundSetupMatchId,
        registrationIds = registrationIds,
        userId = userId,
    )
}

/**
 * Trägt Plätze ein und beendet beide Läufe der ersten Runde - der Zustand, in dem die Automatik
 * greifen muss.
 */
fun TestComprehensionScope<JEnv>.finishFirstRound(seed: SeededRoundProgression, at: LocalDateTime) {
    seed.firstRoundMatchIds.forEach { matchId ->
        val teams = !COMPETITION_MATCH_TEAM.select { COMPETITION_MATCH.eq(matchId) }
        teams.sortedBy { it.startNumber }.forEachIndexed { index, team ->
            !COMPETITION_MATCH_TEAM.update(
                f = { place = index + 1 },
                condition = { ID.eq(team.id) },
            )
        }
        !COMPETITION_MATCH.update(
            f = { finishedAt = at },
            condition = { COMPETITION_SETUP_MATCH.eq(matchId) },
        )
    }
}
