package de.lambda9.ready2race.backend.app.raceclocker

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.club.SeededClubChain
import de.lambda9.ready2race.backend.app.club.seedClubChain
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchTeamRepo
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionMatchTeamWithRegistration
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.CompetitionSetupService
import de.lambda9.ready2race.backend.app.raceclocker.boundary.RaceClockerPollService
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerFeedRow
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.selectOne
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Der Rückzug des Ist-Starts über den Schreibweg des Abruf-Jobs
 * ([RaceClockerPollService.retractStartIfWithdrawn]), gegen eine echte Datenbank.
 *
 * Der Fall dahinter (beobachtet am 11.08.2026): Fehlstart, der Zeitnehmer zieht in RaceClocker
 * alle Zeiten zurück, jedes Boot steht wieder auf „Not started" — aber der Lauf blieb in
 * ready2race auf „Läuft", weil der Abruf `started_at` zwar setzt, den Rückzug aber nur über den
 * Reset-Pfad kannte, der hinter der „unverändert"-Abkürzung liegt. Hier steht der direkte Weg:
 * Feed kennt den Lauf, keine Zeile trägt Start oder Ergebnis → `started_at` geht zurück,
 * `activated_at` bleibt (der Lauf steht weiter „In Vorbereitung" am Start).
 */
class RaceClockerStartRetractionTest {

    private val startedAtSeed: LocalDateTime = LocalDateTime.of(2026, 8, 16, 8, 50, 3)
    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 16, 8, 52, 0)

    @Test
    fun einZurueckgezogenerFeedNimmtDenIstStartZurueck() = testComprehension {
        val seeded = seedStartedWithoutResults()
        val teams = teamsOf(seeded)

        val retracted = !RaceClockerPollService.retractStartIfWithdrawn(
            matchId = seeded.matchId,
            startedAt = startedAtSeed,
            assigned = listOf(retractedRow(teams)),
            teams = teams,
            now = now,
        )

        assertEquals(true, retracted, "Der Rückzug hätte greifen müssen")
        val match = matchRecord(seeded.matchId)
        assertNull(match.startedAt, "Der Ist-Start steht noch")
        // Der Lauf bleibt an den Start gerufen: Die Aktivierung ist die Entscheidung eines
        // Schiedsrichters (oder der Kette), und die Zeitnahme nimmt sie ihm nicht ab.
        assertNotNull(match.activatedAt, "Der Lauf wurde stillschweigend deaktiviert")
    }

    /**
     * Der Feed kennt den Lauf (noch) nicht — keine zugeordneten Zeilen. Ein von Hand markierter
     * Start (markMatchStarted) muss das überleben; sonst nähme der Job jede manuelle
     * Startmeldung im nächsten Takt wieder zurück, solange die Welle in RaceClocker leer ist.
     */
    @Test
    fun einLeererFeedLaesstEinenManuellMarkiertenStartStehen() = testComprehension {
        val seeded = seedStartedWithoutResults()

        val retracted = !RaceClockerPollService.retractStartIfWithdrawn(
            matchId = seeded.matchId,
            startedAt = startedAtSeed,
            assigned = emptyList(),
            teams = teamsOf(seeded),
            now = now,
        )

        assertEquals(false, retracted)
        assertEquals(startedAtSeed, matchRecord(seeded.matchId).startedAt)
    }

    /**
     * Handstände blockieren den Rückzug: Steht in ready2race ein Platz (hier aus der Fixture),
     * während der Feed nichts davon kennt, kam er nicht aus dem Feed — und was der Abruf nicht
     * geschrieben hat, nimmt er auch nicht zurück. (Feed-Stände räumt beim Rückzug der Reset-Pfad
     * in `applyRaceClockerRows` ab — mitsamt Ist-Start, wie
     * [RaceClockerRestartResetTest.einNeugestartetesRennenLoeschtZeitPlatzUndIstStart] belegt.)
     */
    @Test
    fun handErgebnisseBlockierenDenRueckzug() = testComprehension {
        val seeded = seedClubChain()
        !CompetitionMatchRepo.update(seeded.matchId) { startedAt = startedAtSeed }
        val teams = teamsOf(seeded)

        val retracted = !RaceClockerPollService.retractStartIfWithdrawn(
            matchId = seeded.matchId,
            startedAt = startedAtSeed,
            assigned = listOf(retractedRow(teams)),
            teams = teams,
            now = now,
        )

        assertEquals(false, retracted)
        assertEquals(startedAtSeed, matchRecord(seeded.matchId).startedAt)
    }

    /** Gestartet, aber ohne jedes Ergebnis — der Zustand direkt nach einem Fehlstart. */
    private fun TestComprehensionScope<JEnv>.seedStartedWithoutResults(): SeededClubChain {
        val seeded = seedClubChain()
        // Die Fixture wertet den Lauf; für den Fehlstart-Fall darf kein Stand existieren.
        val team = (!CompetitionMatchTeamRepo.getByMatch(seeded.matchId)).single()
        !CompetitionMatchTeamRepo.update(team) {
            place = null
            placesCalculated = false
        }
        !CompetitionMatchRepo.update(seeded.matchId) { startedAt = startedAtSeed }
        return seeded
    }

    private fun TestComprehensionScope<JEnv>.teamsOf(
        seeded: SeededClubChain,
    ): List<CompetitionMatchTeamWithRegistration> {
        val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(seeded.competitionId)
        val match = !CompetitionExecutionService.checkUpdateMatchResult(setupRounds, seeded.matchId)
        return match.teams.filter { !it.deregistered }
    }

    /** Eine zurückgezogene Zeile: RaceClocker liefert `00:00:00.0`, der Parser macht daraus null. */
    private fun retractedRow(teams: List<CompetitionMatchTeamWithRegistration>) = RaceClockerFeedRow(
        name = "Test Mix Nord",
        rank = 1,
        bib = 1,
        wave = "08:50 Vorlauf 2 DM",
        ids = listOf(teams.single().id),
        result = "Not started",
        start = null,
        penaltySeconds = null,
        penaltyNote = null,
    )

    private fun TestComprehensionScope<JEnv>.matchRecord(matchId: UUID): CompetitionMatchRecord =
        assertNotNull(!COMPETITION_MATCH.selectOne { COMPETITION_SETUP_MATCH.eq(matchId) })
}
