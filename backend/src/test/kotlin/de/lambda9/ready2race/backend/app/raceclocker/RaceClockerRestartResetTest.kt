package de.lambda9.ready2race.backend.app.raceclocker

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.club.SeededClubChain
import de.lambda9.ready2race.backend.app.club.seedClubChain
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchTeamRepo
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.CompetitionSetupService
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerError
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerFeedRow
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerMatchTarget
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerRaceRef
import de.lambda9.ready2race.backend.app.timecode.control.TimecodeRepo
import de.lambda9.ready2race.backend.app.timecode.control.toRecord
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.data.Timecode
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchTeamRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.selectOne
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Was passiert, wenn in RaceClocker ein Rennen neu gestartet wird.
 *
 * Der Zeitnehmer setzt eine Welle zurück, wenn ein Start ungültig war: In der Antwort steht danach
 * `00:00:00.0` als Startzeit und `Not started` als Ergebnis - der Feed behauptet also, dieser Lauf
 * sei nie gefahren. ready2race muss diese Aussage übernehmen, sonst bleiben Zeiten und Plätze des
 * ungültigen Laufs stehen, während RaceClocker längst neu misst. Am 09.08.2026 an
 * `raceclocker.com/7aa7e86d` beobachtet.
 *
 * Der Unterschied zum Normalfall ist die eine Zeile, auf die es ankommt: Solange ein einziges Boot
 * eine gemessene Startzeit ODER ein Ergebnis trägt, wird nichts gelöscht. "Kein Ergebnis in keiner
 * Zeile" ist nämlich auch der Zustand fast jedes Laufs über fast seine ganze Dauer - alle noch auf
 * dem Wasser -, und ein Reset an dieser Stelle nähme einem laufenden Rennen die bereits
 * eingelaufenen Boote weg. Beide Fälle stehen hier nebeneinander, weil nur ihr Gegensatz die Regel
 * festnagelt.
 */
class RaceClockerRestartResetTest {

    private val startedAtSeed: LocalDateTime = LocalDateTime.of(2026, 8, 16, 8, 50, 3)

    private val target = RaceClockerMatchTarget(
        waveName = "08:50 Vorlauf 2 DM",
        isQualification = false,
        qualificationRace = null,
        roundsRace = RaceClockerRaceRef(
            UUID.randomUUID(),
            "Kurzstrecke",
            "https://www.raceclocker.com/7aa7e86d",
        ),
    )

    @Test
    fun einNeugestartetesRennenLoeschtZeitPlatzUndIstStart() = testComprehension {
        val seeded = seedClubChain()
        val teamId = seedFinishedResult(seeded)

        !applyRows(seeded, listOf(resetRow(teamId)))

        val team = matchTeam(seeded.matchId)
        assertNull(team.place, "Platz steht noch")
        assertNull(team.timecode, "Zeit steht noch")
        assertEquals(false, team.failed, "Ausscheidung steht noch")
        assertNull(team.failedReason)
        assertNull(team.penaltySeconds, "Strafzeit steht noch")
        assertNull(team.penaltyNote)

        val match = matchRecord(seeded.matchId)
        assertNull(match.startedAt, "Ist-Start steht noch")

        // Der Lauf bleibt aktiv: Ein Schiedsrichter hat ihn an den Start gerufen, und dass
        // RaceClocker die Zeitnahme neu aufsetzt, nimmt ihm diese Entscheidung nicht ab.
        assertNotNull(match.activatedAt, "Lauf wurde stillschweigend deaktiviert")
    }

    /**
     * Die Gegenprobe: Boote auf dem Wasser sehen im Feed fast genauso aus - kein Ergebnis in keiner
     * Zeile - und unterscheiden sich allein in der gemessenen Startzeit. Hier darf nichts gelöscht
     * werden.
     *
     * Der Aufruf endet dabei mit Erfolg, nicht mehr mit [RaceClockerError.NoResults]: Seit die
     * Bahnvergabe vor den Riegeln steht, übernimmt auch ein Lauf ohne Ergebnisse etwas aus dem Feed,
     * und ein Fehler würde diese Bahnen der Transaktion des Hintergrund-Jobs zum Opfer fallen
     * lassen. Was dieser Fall wirklich zusichert, steht ohnehin in den Prüfungen darunter: dass
     * Zeit, Platz und Ist-Start stehen bleiben.
     */
    @Test
    fun booteAufDemWasserLoeschenNichts() = testComprehension {
        val seeded = seedClubChain()
        val teamId = seedFinishedResult(seeded)

        assertKIOSucceeds<ApiResponse.NoData> {
            applyRows(seeded, listOf(inRaceRow(teamId)))
        }

        val team = matchTeam(seeded.matchId)
        assertEquals(1, team.place, "Platz wurde gelöscht, obwohl der Lauf läuft")
        assertNotNull(team.timecode, "Zeit wurde gelöscht, obwohl der Lauf läuft")
        assertNotNull(matchRecord(seeded.matchId).startedAt, "Ist-Start wurde gelöscht")
    }

    /** Eine zurückgesetzte Zeile: RaceClocker liefert `00:00:00.0`, der Parser macht daraus null. */
    private fun resetRow(teamId: UUID) = feedRow(teamId, start = null, result = "Not started")

    /** Ein Boot, das gestartet, aber noch nicht im Ziel ist. */
    private fun inRaceRow(teamId: UUID) = feedRow(teamId, start = LocalTime.of(8, 50, 3), result = "In race...")

    private fun feedRow(teamId: UUID, start: LocalTime?, result: String) = RaceClockerFeedRow(
        name = "Test Mix Nord",
        rank = 1,
        bib = 1,
        wave = target.waveName,
        ids = listOf(teamId),
        result = result,
        start = start,
        penaltySeconds = null,
        penaltyNote = null,
    )

    private fun applyRows(
        seeded: SeededClubChain,
        rows: List<RaceClockerFeedRow>,
    ): KIO<JEnv, ServiceError, ApiResponse.NoData> = KIO.comprehension {
        val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(seeded.competitionId)
        val match = !CompetitionExecutionService.checkUpdateMatchResult(setupRounds, seeded.matchId)

        CompetitionExecutionService.applyRaceClockerRows(match, seeded.matchId, target, rows, SYSTEM_USER)
    }

    /**
     * Der Zustand nach einem gefahrenen Lauf: Platz, Zeit, Ausscheidung, Strafzeit und ein Ist-Start.
     * Genau das, was ein Neustart in RaceClocker hinfällig macht.
     */
    private fun TestComprehensionScope<JEnv>.seedFinishedResult(seeded: SeededClubChain): UUID {
        val team = matchTeam(seeded.matchId)

        !TimecodeRepo.create(
            Timecode(
                millis = 247_600,
                baseUnit = Timecode.BaseUnit.SECONDS,
                millisecondPrecision = Timecode.MillisecondPrecision.ONE,
            ).toRecord(team.id)
        )
        !CompetitionMatchTeamRepo.update(team) {
            place = 1
            placesCalculated = true
            failed = true
            failedReason = "DNF"
            penaltySeconds = 30
            penaltyNote = "Bojenfehler"
            timecode = team.id
        }
        !CompetitionMatchRepo.update(seeded.matchId) { startedAt = startedAtSeed }

        return team.id
    }

    private fun TestComprehensionScope<JEnv>.matchTeam(matchId: UUID): CompetitionMatchTeamRecord =
        (!CompetitionMatchTeamRepo.getByMatch(matchId)).single()

    private fun TestComprehensionScope<JEnv>.matchRecord(matchId: UUID): CompetitionMatchRecord =
        assertNotNull(!COMPETITION_MATCH.selectOne { COMPETITION_SETUP_MATCH.eq(matchId) })
}
