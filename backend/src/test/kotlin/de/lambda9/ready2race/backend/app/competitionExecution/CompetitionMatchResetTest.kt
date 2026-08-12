package de.lambda9.ready2race.backend.app.competitionExecution

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.club.CHAIN_SEED_TIME
import de.lambda9.ready2race.backend.app.club.SeededClubChain
import de.lambda9.ready2race.backend.app.club.seedClubChain
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchTeamLapRepo
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchTeamRepo
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionExecutionError
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupPlacesOption
import de.lambda9.ready2race.backend.app.raceclocker.boundary.RaceClockerPollService
import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerPollRepo
import de.lambda9.ready2race.backend.app.timecode.control.TimecodeRepo
import de.lambda9.ready2race.backend.app.timingConfig.entity.TimingSystem
import de.lambda9.ready2race.backend.app.timecode.control.toRecord
import de.lambda9.ready2race.backend.data.Timecode
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchTeamLapRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchTeamRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRoundRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceclockerRaceRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_ROUND
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.RACECLOCKER_RACE
import de.lambda9.ready2race.backend.database.generated.tables.references.TIMECODE
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.selectOne
import de.lambda9.ready2race.backend.database.update
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Der Lauf-Reset ([CompetitionExecutionService.resetMatch]): ein einzelner Lauf kehrt in den
 * Zustand „nie gefahren" zurück, ohne dass seine Zeilen gelöscht werden.
 *
 * Der Kern ist der Erhalt der `competition_match_team`-Kennungen: Sie stecken in
 * RaceClocker-Extra-infos und exportierten Startlisten, und das bisherige Werkzeug -
 * Runde löschen und neu erstellen - vergibt beim Neuerstellen neue. Deshalb prüft der erste Fall
 * ausdrücklich VORHER == NACHHER auf der Team-Id, nicht nur das Leeren der Felder.
 */
class CompetitionMatchResetTest {

    private val startedAtSeed: LocalDateTime = LocalDateTime.of(2026, 8, 16, 8, 50, 3)

    @Test
    fun resetLeertDenAusfuehrungszustandUndBehaeltDieKennungen() = testComprehension {
        val seeded = seedClubChain()
        val teamIdBefore = seedRacedState(seeded)

        !CompetitionExecutionService.resetMatch(
            eventId = seeded.eventId,
            competitionId = seeded.competitionId,
            matchId = seeded.matchId,
            userId = SYSTEM_USER,
        )

        val team = matchTeam(seeded.matchId)
        // Die eine Zusage, um die es geht: dieselbe Zeile, dieselbe Kennung.
        assertEquals(teamIdBefore, team.id, "Die Team-Kennung hat sich geändert - RaceClocker-Zuordnung wäre kaputt")

        // Ergebnis dieses Laufs: alles weg.
        assertNull(team.place, "Platz steht noch")
        assertEquals(false, team.placesCalculated, "placesCalculated steht noch")
        assertEquals(false, team.failed, "Ausscheidung steht noch")
        assertNull(team.failedReason)
        assertNull(team.timecode, "Zeit steht noch")
        assertNull(team.penaltySeconds, "Strafzeit steht noch")
        assertNull(team.penaltyNote)
        assertNull(team.startedAt, "Boot-Start steht noch")
        assertNull(!TIMECODE.selectOne { ID.eq(teamIdBefore) }, "Timecode-Zeile wurde nicht gelöscht")
        assertTrue((!CompetitionMatchTeamLapRepo.getByTeams(listOf(teamIdBefore))).isEmpty(), "Rundenzeiten stehen noch")

        // Struktur bleibt: Bahn und Vorrunden-Aus sind kein Ergebnis dieses Laufs.
        assertEquals(1, team.startNumber, "Die Bahn wurde verändert")
        assertEquals(false, team.out, "Das Vorrunden-Aus wurde verändert")

        val match = matchRecord(seeded.matchId)
        assertNull(match.activatedAt, "Aktivierung steht noch")
        assertNull(match.startedAt, "Ist-Start steht noch")
        assertNull(match.finishedAt, "Beenden-Stempel steht noch")
        // GESETZT, nicht geleert: Der Reset pausiert den automatischen Abruf, sonst spielte der
        // nächste Takt die gelöschten Ergebnisse sofort wieder ein (siehe eigener Testfall unten).
        assertNotNull(match.raceclockerAutoPausedAt, "Der Reset muss den automatischen Abruf pausieren")
        assertNull(match.raceclockerPollError, "Abruf-Fehler steht noch")
        // Die geplante Startzeit ist Struktur, kein Ausführungszustand.
        assertNotNull(match.startTime, "Die geplante Startzeit wurde gelöscht")
    }

    /**
     * Sobald die Folgerunde erzeugte Läufe hat, ist der Reset gesperrt - die Ergebnisse haben die
     * nächste Runde dann schon gesät, dieselbe Stromrichtung wie bei `deleteCurrentRound`.
     */
    @Test
    fun resetScheitertWennDieFolgerundeSchonLaeufeHat() = testComprehension {
        val seeded = seedClubChain()
        val previousMatchId = seedPreviousRoundWithMatch(seeded)

        assertKIOFails(CompetitionExecutionError.ResetBlockedByNextRound) {
            CompetitionExecutionService.resetMatch(
                eventId = seeded.eventId,
                competitionId = seeded.competitionId,
                matchId = previousMatchId,
                userId = SYSTEM_USER,
            )
        }

        // Und der Lauf steht unangetastet da.
        val team = (!CompetitionMatchTeamRepo.getByMatch(previousMatchId)).single()
        assertEquals(2, team.place, "Der gesperrte Reset hat trotzdem geschrieben")
    }

    /**
     * Die Pause selbst, gegen den echten Kandidaten-Filter: Ein pollbarer Lauf (RaceClocker als
     * Zeitnahmesystem, angewähltes Rennen, weder beendet noch pausiert) ist VOR dem Reset
     * Kandidat des Abruf-Jobs - danach nicht mehr. Ohne die Pause importierte der nächste Takt
     * die soeben gelöschten Ergebnisse sofort wieder aus dem Feed, solange RaceClocker den alten
     * Stand noch führt: Der Reset höbe sich selbst auf (Nutzer-Beobachtung 12.08.2026).
     * Fortgesetzt wird bewusst über [CompetitionExecutionService.resumeRaceClockerAutoPull],
     * nachdem der Lauf in RaceClocker aufgeräumt ist.
     */
    @Test
    fun resetPausiertDenAutomatischenAbruf() = testComprehension {
        val seeded = seedClubChain()
        seedRacedState(seeded)

        // Den Lauf pollbar machen: Zeitnahmesystem an der Veranstaltung, angewähltes Rennen am
        // Wettkampf - und Beenden/Pause aus dem Seed zurücknehmen, damit die Gegenprobe VOR dem
        // Reset überhaupt greifen kann.
        val raceId = UUID.randomUUID()
        !RACECLOCKER_RACE.insert(
            RaceclockerRaceRecord(
                id = raceId,
                event = seeded.eventId,
                name = "Kurzstrecke",
                resultsUrl = "https://raceclocker.com/reset-test",
                capturesLaps = false,
                position = 1,
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
            )
        )
        !EVENT.update({ timingSystem = TimingSystem.RACECLOCKER.name }) { ID.eq(seeded.eventId) }
        !COMPETITION.update({ raceclockerRace = raceId }) { ID.eq(seeded.competitionId) }
        !CompetitionMatchRepo.update(seeded.matchId) {
            finishedAt = null
            raceclockerAutoPausedAt = null
        }

        val before = !RaceClockerPollRepo.getCandidates(seeded.eventId)
        assertTrue(
            before.any { it.matchId == seeded.matchId },
            "Gegenprobe schlägt fehl: Der Lauf müsste vor dem Reset Abruf-Kandidat sein",
        )

        !CompetitionExecutionService.resetMatch(
            eventId = seeded.eventId,
            competitionId = seeded.competitionId,
            matchId = seeded.matchId,
            userId = SYSTEM_USER,
        )

        assertNotNull(
            matchRecord(seeded.matchId).raceclockerAutoPausedAt,
            "Der Reset muss raceclocker_auto_paused_at setzen",
        )
        val after = !RaceClockerPollRepo.getCandidates(seeded.eventId)
        assertTrue(
            after.none { it.matchId == seeded.matchId },
            "Der zurückgesetzte Lauf ist noch Abruf-Kandidat - der nächste Takt spielte die gelöschten Ergebnisse wieder ein",
        )
    }

    /**
     * Der Abruf-Job merkt sich je Lauf den zuletzt geschriebenen Feed-Stand und überspringt
     * unveränderte Läufe. Bliebe der Merkposten nach dem Reset stehen, käme das zurückgesetzte
     * Ergebnis nie wieder herein, solange sich in RaceClocker keine Zeile ändert - deshalb muss
     * [CompetitionExecutionService.resetMatch] ihn vergessen.
     *
     * Der Merkposten ist bewusst privat ([RaceClockerPollService]); der Test greift per Reflection
     * hinein, statt dem Service dafür eine öffentliche Schreib-Schnittstelle zu geben, die im
     * Betrieb niemand braucht.
     */
    @Test
    fun resetVergisstDenRaceClockerFingerabdruck() = testComprehension {
        val seeded = seedClubChain()
        seedRacedState(seeded)

        val fingerprints = pollServiceFingerprints()
        fingerprints[seeded.matchId] = "stale"

        !CompetitionExecutionService.resetMatch(
            eventId = seeded.eventId,
            competitionId = seeded.competitionId,
            matchId = seeded.matchId,
            userId = SYSTEM_USER,
        )

        assertFalse(
            fingerprints.containsKey(seeded.matchId),
            "Der Fingerabdruck steht noch - der nächste Takt würde den Lauf als unverändert überspringen",
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun pollServiceFingerprints(): MutableMap<UUID, String> {
        val field = RaceClockerPollService::class.java.getDeclaredField("fingerprints")
        field.isAccessible = true
        return field.get(RaceClockerPollService) as MutableMap<UUID, String>
    }

    /**
     * Der Zustand nach einem gefahrenen Lauf: Aktivierung (aus dem Seed), Ist-Start, Beenden,
     * Abruf-Pause samt Fehlercode am Lauf; Platz, Zeit, Ausscheidung, Strafzeit, Boot-Start und
     * eine Rundenzeit am Boot. Alles, was der Reset leeren soll.
     */
    private fun TestComprehensionScope<JEnv>.seedRacedState(seeded: SeededClubChain): UUID {
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
            startedAt = startedAtSeed
        }
        !CompetitionMatchTeamLapRepo.create(
            listOf(
                CompetitionMatchTeamLapRecord(
                    id = UUID.randomUUID(),
                    competitionMatchTeam = team.id,
                    position = 1,
                    name = "Runde 1",
                    lapMillis = 123_400,
                    createdAt = CHAIN_SEED_TIME,
                    createdBy = null,
                )
            )
        )
        !CompetitionMatchRepo.update(seeded.matchId) {
            startedAt = startedAtSeed
            finishedAt = startedAtSeed.plusMinutes(10)
            raceclockerAutoPausedAt = startedAtSeed.plusMinutes(5)
            raceclockerPollError = "RACECLOCKER_UNREACHABLE"
        }

        return team.id
    }

    /**
     * Eine bereits gefahrene Vorrunde vor dem Finale des Seeds: eigene Setup-Runde mit
     * `nextRound` auf das Finale, ein erzeugter Lauf mit gewerteter Mannschaft. Weil das Finale
     * (aus [seedClubChain]) schon einen Lauf hat, ist die Vorrunde nicht mehr die aktuelle Runde.
     */
    private fun TestComprehensionScope<JEnv>.seedPreviousRoundWithMatch(seeded: SeededClubChain): UUID {
        val finalRound = assertNotNull(!COMPETITION_SETUP_ROUND.selectOne { ID.eq(seeded.roundId) })

        val previousRoundId = UUID.randomUUID()
        val previousMatchId = UUID.randomUUID()

        !COMPETITION_SETUP_ROUND.insert(
            CompetitionSetupRoundRecord(
                id = previousRoundId,
                competitionSetup = finalRound.competitionSetup,
                nextRound = seeded.roundId,
                name = "Vorlauf",
                required = true,
                useDefaultSeeding = true,
                placesOption = CompetitionSetupPlacesOption.EQUAL.name,
            )
        )
        !COMPETITION_SETUP_MATCH.insert(
            CompetitionSetupMatchRecord(
                id = previousMatchId,
                competitionSetupRound = previousRoundId,
                weighting = 1,
                name = "Vorlauf 1",
                executionOrder = 1,
                teams = 1,
            )
        )
        !COMPETITION_MATCH.insert(
            CompetitionMatchRecord(
                competitionSetupMatch = previousMatchId,
                startTime = CHAIN_SEED_TIME.minusDays(1),
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
            )
        )
        !COMPETITION_MATCH_TEAM.insert(
            CompetitionMatchTeamRecord(
                id = UUID.randomUUID(),
                competitionMatch = previousMatchId,
                competitionRegistration = seeded.registrationId,
                startNumber = 1,
                place = 2,
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
            )
        )

        return previousMatchId
    }

    private fun TestComprehensionScope<JEnv>.matchTeam(matchId: UUID): CompetitionMatchTeamRecord =
        (!CompetitionMatchTeamRepo.getByMatch(matchId)).single()

    private fun TestComprehensionScope<JEnv>.matchRecord(matchId: UUID): CompetitionMatchRecord =
        assertNotNull(!COMPETITION_MATCH.selectOne { COMPETITION_SETUP_MATCH.eq(matchId) })
}
