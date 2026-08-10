package de.lambda9.ready2race.backend.app.raceclocker

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.club.SeededClubChain
import de.lambda9.ready2race.backend.app.club.seedClubChain
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchTeamLapRepo
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchTeamRepo
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.CompetitionSetupService
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerFeedRow
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerLapMark
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerMatchTarget
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerRaceRef
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import de.lambda9.tailwind.core.KIO
import java.time.LocalTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Wie Zwischenzeiten aus dem Feed in der Datenbank landen.
 *
 * Der Feed liefert je Marke einen Tageszeit-Stempel; gespeichert wird die kumulierte Fahrzeit
 * seit dem gemessenen Start der Zeile. Je Takt werden die Runden eines Boots vollständig ersetzt -
 * eine in RaceClocker korrigierte oder entfernte Marke muss beim nächsten Abruf ankommen.
 */
class RaceClockerLapWriteTest {

    private val target = RaceClockerMatchTarget(
        waveName = "08:50 Vorlauf 2 DM",
        isQualification = false,
        qualificationRace = null,
        roundsRace = RaceClockerRaceRef(
            UUID.randomUUID(),
            "Langstrecke",
            "https://www.raceclocker.com/7aa7e86d",
        ),
    )

    @Test
    fun lapMarksAreStoredAsCumulativeRaceTime() = testComprehension {
        val seeded = seedClubChain()
        val teamId = teamRegistrationId(seeded)

        assertKIOSucceeds<ApiResponse.NoData> {
            applyRows(
                seeded,
                listOf(
                    row(
                        teamId,
                        start = LocalTime.of(22, 32, 9, 600_000_000),
                        laps = listOf(
                            RaceClockerLapMark("Runde 1", LocalTime.of(22, 32, 47, 700_000_000)),
                            RaceClockerLapMark("Runde 2", LocalTime.of(22, 33, 15, 100_000_000)),
                        ),
                    )
                ),
            )
        }

        val laps = !CompetitionMatchTeamLapRepo.getByTeams(listOf(matchTeamRecordId(seeded)))
        assertEquals(
            listOf("Runde 1" to 38_100L, "Runde 2" to 65_500L),
            laps.sortedBy { it.position }.map { it.name to it.lapMillis },
        )
    }

    @Test
    fun lapsAreReplacedOnEveryPull() = testComprehension {
        val seeded = seedClubChain()
        val teamId = teamRegistrationId(seeded)

        assertKIOSucceeds<ApiResponse.NoData> {
            applyRows(
                seeded,
                listOf(
                    row(
                        teamId,
                        start = LocalTime.of(10, 0),
                        laps = listOf(
                            RaceClockerLapMark("Runde 1", LocalTime.of(10, 1)),
                            RaceClockerLapMark("Runde 2", LocalTime.of(10, 2)),
                        ),
                    )
                ),
            )
        }
        // Der Zeitnehmer nimmt die zweite Marke zurück und korrigiert die erste.
        assertKIOSucceeds<ApiResponse.NoData> {
            applyRows(
                seeded,
                listOf(
                    row(
                        teamId,
                        start = LocalTime.of(10, 0),
                        laps = listOf(RaceClockerLapMark("Runde 1", LocalTime.of(10, 1, 30))),
                    )
                ),
            )
        }

        val laps = !CompetitionMatchTeamLapRepo.getByTeams(listOf(matchTeamRecordId(seeded)))
        assertEquals(listOf("Runde 1" to 90_000L), laps.map { it.name to it.lapMillis })
    }

    private fun row(teamId: UUID, start: LocalTime, laps: List<RaceClockerLapMark>) = RaceClockerFeedRow(
        name = "Test Mix Nord",
        rank = 1,
        bib = 1,
        wave = target.waveName,
        ids = listOf(teamId),
        result = "In race...",
        start = start,
        penaltySeconds = null,
        penaltyNote = null,
        laps = laps,
    )

    private fun applyRows(
        seeded: SeededClubChain,
        rows: List<RaceClockerFeedRow>,
    ): KIO<JEnv, ServiceError, ApiResponse.NoData> = KIO.comprehension {
        val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(seeded.competitionId)
        val match = !CompetitionExecutionService.checkUpdateMatchResult(setupRounds, seeded.matchId)

        CompetitionExecutionService.applyRaceClockerRows(match, seeded.matchId, target, rows, SYSTEM_USER)
    }

    private fun TestComprehensionScope<JEnv>.teamRegistrationId(
        seeded: SeededClubChain,
    ): UUID = (!CompetitionMatchTeamRepo.getByMatch(seeded.matchId)).single().competitionRegistration!!

    private fun TestComprehensionScope<JEnv>.matchTeamRecordId(
        seeded: SeededClubChain,
    ): UUID = (!CompetitionMatchTeamRepo.getByMatch(seeded.matchId)).single().id!!
}
