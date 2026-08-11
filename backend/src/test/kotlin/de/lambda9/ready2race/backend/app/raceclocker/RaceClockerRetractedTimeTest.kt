package de.lambda9.ready2race.backend.app.raceclocker

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.club.SeededClubChain
import de.lambda9.ready2race.backend.app.club.seedClubChain
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchTeamRepo
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.CompetitionSetupService
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerFeedRow
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerMatchTarget
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerRaceRef
import de.lambda9.ready2race.backend.app.timecode.control.TimecodeRepo
import de.lambda9.ready2race.backend.app.timecode.control.toRecord
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.data.Timecode
import de.lambda9.ready2race.backend.database.SYSTEM_USER
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
 * Was passiert, wenn in RaceClocker EINE Zeit wieder entfernt wird, während die Welle weiterläuft.
 *
 * Der Zeitnehmer nimmt eine versehentlich gedrückte Zielzeit zurück (beobachtet am 10.08.2026):
 * Die Zeile trägt danach wieder `In race...`, die übrigen Boote behalten ihre Zeiten. Der
 * Komplett-Reset ([RaceClockerRestartResetTest]) greift hier nicht - die Welle hat weiterhin
 * gemessene Starts. Ohne eigene Regel stünde die zurückgenommene Zeit dauerhaft in ready2race.
 *
 * Die Grenze der Regel: Ausgeschiedene Boote ([CompetitionMatchTeamRecord.failed]) bleiben stehen.
 * Eine von Hand eingetragene Ausscheidung hat im Feed nie ein Ergebnis und wäre sonst bei jedem
 * Abruf wieder weg.
 */
class RaceClockerRetractedTimeTest {

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
    fun eineInRaceClockerEntfernteZeitWirdZurueckgenommen() = testComprehension {
        val seeded = seedClubChain()
        val teamId = seedTimedResult(seeded, failed = false)

        assertKIOSucceeds<ApiResponse.NoData> {
            applyRows(seeded, listOf(inRaceRow(teamId)))
        }

        val team = matchTeam(seeded.matchId)
        assertNull(team.place, "Platz steht noch, obwohl RaceClocker die Zeit entfernt hat")
        assertNull(team.timecode, "Zeit steht noch, obwohl RaceClocker sie entfernt hat")
        assertNull(team.penaltySeconds, "Strafzeit steht noch")
        assertNull(team.penaltyNote)
        assertEquals(false, team.failed, "Die Rücknahme darf keine Ausscheidung erfinden")

        // Die Welle läuft weiter - der Ist-Start des Laufs bleibt.
        val match = assertNotNull(!COMPETITION_MATCH.selectOne { COMPETITION_SETUP_MATCH.eq(seeded.matchId) })
        assertNotNull(match.startedAt, "Ist-Start wurde mitgelöscht")
    }

    @Test
    fun eineHandeingetrageneAusscheidungUeberlebtDenAbruf() = testComprehension {
        val seeded = seedClubChain()
        val teamId = seedTimedResult(seeded, failed = true)

        assertKIOSucceeds<ApiResponse.NoData> {
            applyRows(seeded, listOf(inRaceRow(teamId)))
        }

        val team = matchTeam(seeded.matchId)
        assertEquals(true, team.failed, "Die Ausscheidung wurde vom Abruf gelöscht")
        assertEquals("DNF", team.failedReason)
    }

    /** Ein Boot, das laut Feed gestartet, aber (wieder) ohne Ergebnis ist. */
    private fun inRaceRow(teamId: UUID) = RaceClockerFeedRow(
        name = "Test Mix Nord",
        rank = 1,
        bib = 1,
        wave = target.waveName,
        ids = listOf(teamId),
        result = "In race...",
        start = LocalTime.of(8, 50, 3),
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

    /** Ein Boot mit gespeicherter Zeit, Platz und Strafzeit - wahlweise zusätzlich ausgeschieden. */
    private fun TestComprehensionScope<JEnv>.seedTimedResult(
        seeded: SeededClubChain,
        failed: Boolean,
    ): UUID {
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
            this.failed = failed
            failedReason = if (failed) "DNF" else null
            penaltySeconds = 30
            penaltyNote = "Bojenfehler"
            timecode = team.id
        }
        !CompetitionMatchRepo.update(seeded.matchId) {
            startedAt = LocalDateTime.of(2026, 8, 16, 8, 50, 3)
        }

        return team.id
    }

    private fun TestComprehensionScope<JEnv>.matchTeam(matchId: UUID): CompetitionMatchTeamRecord =
        (!CompetitionMatchTeamRepo.getByMatch(matchId)).single()
}
