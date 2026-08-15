package de.lambda9.ready2race.backend.app.competitionExecution

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.club.CHAIN_SEED_TIME
import de.lambda9.ready2race.backend.app.club.SeededClubChain
import de.lambda9.ready2race.backend.app.club.seedClubChain
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionExecution.entity.UpdateCompetitionMatchResultRequest
import de.lambda9.ready2race.backend.app.competitionExecution.entity.UpdateCompetitionMatchTeamResultRequest
import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerPollRepo
import de.lambda9.ready2race.backend.app.timingConfig.entity.TimingSystem
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchTeamRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceclockerRaceRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.RACECLOCKER_RACE
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.selectOne
import de.lambda9.ready2race.backend.database.update
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Die Reichweite der Abruf-Pause bei der manuellen Ergebniseingabe: Sie gilt je LAUF, nicht je
 * Runde (Bug-Meldung vom Regattatag 14.08.2026 - "das manuelle Eintragen in einen Lauf stoppt den
 * Polling-Automatismus für die ganze Runde").
 *
 * Zwei Läufe derselben Runde, beide pollbar (RaceClocker als Zeitnahmesystem, angewähltes Rennen,
 * Automatik an). Eine Handeingabe in Lauf 1 muss genau diesen Lauf pausieren; Lauf 2 bleibt
 * unpausiert und unmarkierter Abruf-Kandidat. Der pausierte Lauf bleibt dabei markierter Kandidat,
 * damit sein Zustand weiter für den Takt der Veranstaltung zählt (siehe
 * [RaceClockerPollRepo.getCandidates]).
 */
class ManualResultPauseScopeTest {

    @Test
    fun handeingabePausiertNurDenEinenLauf() = testComprehension {
        val seeded = seedClubChain()
        val secondMatchId = seedSecondMatchInSameRound(seeded)
        makePollable(seeded)

        // Gegenprobe: Vorher sind beide Läufe unpausierte Kandidaten.
        val before = !RaceClockerPollRepo.getCandidates(seeded.eventId)
        assertTrue(
            before.count { it.autoPausedAt == null } == 2 &&
                before.map { it.matchId }.toSet() == setOf(seeded.matchId, secondMatchId),
            "Gegenprobe schlägt fehl: Beide Läufe müssten unpausierte Abruf-Kandidaten sein: $before",
        )

        !CompetitionExecutionService.updateMatchResult(
            eventId = seeded.eventId,
            competitionId = seeded.competitionId,
            matchId = seeded.matchId,
            userId = SYSTEM_USER,
            request = UpdateCompetitionMatchResultRequest(
                teamResults = listOf(
                    UpdateCompetitionMatchTeamResultRequest(
                        registrationId = seeded.registrationId,
                        place = 1,
                        timeString = null,
                        failed = false,
                        failedReason = null,
                        penaltySeconds = null,
                        penaltyNote = null,
                    )
                )
            ),
        )

        assertNotNull(
            matchRecord(seeded.matchId).raceclockerAutoPausedAt,
            "Die Handeingabe muss den eingetragenen Lauf pausieren",
        )
        assertNull(
            matchRecord(secondMatchId).raceclockerAutoPausedAt,
            "Die Handeingabe in Lauf 1 hat den Nachbarlauf derselben Runde pausiert",
        )

        // Und aus Sicht des Abruf-Jobs: Lauf 1 markiert, Lauf 2 weiter frei.
        val after = !RaceClockerPollRepo.getCandidates(seeded.eventId)
        assertNotNull(
            after.single { it.matchId == seeded.matchId }.autoPausedAt,
            "Der eingetragene Lauf muss dem Job als pausiert gemeldet werden",
        )
        assertNull(
            after.single { it.matchId == secondMatchId }.autoPausedAt,
            "Der Nachbarlauf darf dem Job nicht als pausiert gemeldet werden",
        )
    }

    /** Auch der Weg zurück ist je Lauf: Das Fortsetzen von Lauf 1 rührt Lauf 2 nicht an. */
    @Test
    fun fortsetzenGiltNurFuerDenEinenLauf() = testComprehension {
        val seeded = seedClubChain()
        val secondMatchId = seedSecondMatchInSameRound(seeded)
        makePollable(seeded)

        // Beide pausiert, wie nach zwei getrennten Handeingaben.
        !COMPETITION_MATCH.update({ raceclockerAutoPausedAt = CHAIN_SEED_TIME }) {
            COMPETITION_SETUP_MATCH.eq(seeded.matchId)
        }
        !COMPETITION_MATCH.update({ raceclockerAutoPausedAt = CHAIN_SEED_TIME }) {
            COMPETITION_SETUP_MATCH.eq(secondMatchId)
        }

        !CompetitionExecutionService.resumeRaceClockerAutoPull(
            eventId = seeded.eventId,
            competitionId = seeded.competitionId,
            matchId = seeded.matchId,
            userId = SYSTEM_USER,
        )

        assertNull(
            matchRecord(seeded.matchId).raceclockerAutoPausedAt,
            "Das Fortsetzen muss die Pause dieses Laufs aufheben",
        )
        assertNotNull(
            matchRecord(secondMatchId).raceclockerAutoPausedAt,
            "Das Fortsetzen von Lauf 1 hat auch die Pause des Nachbarlaufs aufgehoben",
        )
    }

    /**
     * Ein zweiter Lauf in der Runde des Seeds ("Finale"), mit eigener Meldung und eigenem Boot -
     * so knapp, wie es die Prüfungen von `updateMatchResult` und `getCandidates` zulassen.
     */
    private fun TestComprehensionScope<JEnv>.seedSecondMatchInSameRound(seeded: SeededClubChain): UUID {
        val setupMatchId = UUID.randomUUID()
        val registrationId = UUID.randomUUID()

        !COMPETITION_SETUP_MATCH.insert(
            CompetitionSetupMatchRecord(
                id = setupMatchId,
                competitionSetupRound = seeded.roundId,
                weighting = 2,
                name = "Lauf 2",
                executionOrder = 2,
                teams = 1,
            )
        )
        !COMPETITION_MATCH.insert(
            CompetitionMatchRecord(
                competitionSetupMatch = setupMatchId,
                startTime = CHAIN_SEED_TIME.plusMinutes(15),
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
                activatedAt = CHAIN_SEED_TIME,
            )
        )

        val firstRegistration = assertNotNull(
            !COMPETITION_REGISTRATION.selectOne { ID.eq(seeded.registrationId) },
            "Seed-Meldung fehlt",
        )
        !COMPETITION_REGISTRATION.insert(
            CompetitionRegistrationRecord(
                id = registrationId,
                eventRegistration = firstRegistration.eventRegistration,
                competition = seeded.competitionId,
                club = seeded.registeringClubId,
                name = "Mix Süd",
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
            )
        )
        !COMPETITION_MATCH_TEAM.insert(
            CompetitionMatchTeamRecord(
                id = UUID.randomUUID(),
                competitionMatch = setupMatchId,
                competitionRegistration = registrationId,
                startNumber = 1,
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
            )
        )

        return setupMatchId
    }

    /**
     * Macht die Veranstaltung des Seeds pollbar: Zeitnahmesystem und Automatik an der
     * Veranstaltung, ein angewähltes Rennen am Wettkampf - dieselben Bedingungen, an denen
     * `isAutoPullConfigured` und `getCandidates` hängen.
     */
    private fun TestComprehensionScope<JEnv>.makePollable(seeded: SeededClubChain) {
        val raceId = UUID.randomUUID()
        !RACECLOCKER_RACE.insert(
            RaceclockerRaceRecord(
                id = raceId,
                event = seeded.eventId,
                name = "Kurzstrecke",
                resultsUrl = "https://raceclocker.com/pause-scope-test",
                capturesLaps = false,
                position = 1,
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
            )
        )
        !EVENT.update({
            timingSystem = TimingSystem.RACECLOCKER.name
            raceclockerAutoPull = true
        }) { ID.eq(seeded.eventId) }
        !COMPETITION.update({ raceclockerRace = raceId }) { ID.eq(seeded.competitionId) }
    }

    private fun TestComprehensionScope<JEnv>.matchRecord(matchId: UUID): CompetitionMatchRecord =
        assertNotNull(!COMPETITION_MATCH.selectOne { COMPETITION_SETUP_MATCH.eq(matchId) })
}
