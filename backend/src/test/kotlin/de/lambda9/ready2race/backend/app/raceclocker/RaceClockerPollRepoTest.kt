package de.lambda9.ready2race.backend.app.raceclocker

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupPlacesOption
import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerPollRepo
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerStartMode
import de.lambda9.ready2race.backend.app.timingConfig.entity.TimingSystem
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionPropertiesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRoundRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventScheduleSlotRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceclockerRaceRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_ROUND
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_SCHEDULE_SLOT
import de.lambda9.ready2race.backend.database.generated.tables.references.RACECLOCKER_RACE
import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.update
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [RaceClockerPollRepo.getCandidates] gegen eine echte Datenbank.
 *
 * Diese Abfrage ist die einzige Stelle des automatischen Abrufs, die sich nicht als reine Funktion
 * prüfen lässt - und genau dort ist der eine Fehler entstanden, der es in den Branch geschafft hat:
 * ein wiederverwendeter aliasierter Ausdruck, den jOOQ im WHERE als blanken Alias-Bezeichner
 * rendert, was Postgres ablehnt. Kein noch so aufmerksames Lesen fängt so etwas; ein Testlauf gegen
 * Postgres schon. Deshalb steht hier jeder Ausschluss der Abfrage einmal als eigener Fall.
 */
class RaceClockerPollRepoTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 14, 10, 0)

    private val eventHeatsUrl = "https://www.raceclocker.com/event-heats"
    private val eventTimeTrialUrl = "https://www.raceclocker.com/event-tt"

    private fun TestComprehensionScope<JEnv>.insertRace(
        eventId: UUID,
        name: String,
        url: String,
        startMode: RaceClockerStartMode,
        position: Int,
    ): UUID {
        val raceId = UUID.randomUUID()
        !RACECLOCKER_RACE.insert(
            RaceclockerRaceRecord(
                id = raceId,
                event = eventId,
                name = name,
                resultsUrl = url,
                startMode = startMode.name,
                capturesLaps = false,
                position = position,
                createdAt = now,
                updatedAt = now,
            )
        )
        return raceId
    }

    /**
     * Eine Veranstaltung mit genau einem Lauf, so knapp wie die Joins von `getCandidates` es
     * zulassen: Veranstaltung, Wettkampf, Eigenschaften, Ablauf, Runde, Setup-Lauf, Lauf.
     * Mannschaften braucht die Abfrage nicht - sie zählt keine Boote.
     */
    private fun TestComprehensionScope<JEnv>.seed(
        eventTimingSystem: String? = TimingSystem.RACECLOCKER.name,
        competitionTimingSystem: String? = null,
        competitionHeatsUrl: String? = null,
        competitionTimeTrialUrl: String? = null,
        eventHeatsResultsUrl: String? = eventHeatsUrl,
        eventTimeTrialResultsUrl: String? = eventTimeTrialUrl,
        isQualification: Boolean = false,
        activated: Boolean = true,
        startedAt: LocalDateTime? = null,
        finishedAt: LocalDateTime? = null,
        autoPausedAt: LocalDateTime? = null,
        slotSkippedAt: LocalDateTime? = null,
        withSlot: Boolean = false,
    ): Pair<UUID, UUID> {
        val eventId = UUID.randomUUID()
        val competitionId = UUID.randomUUID()
        val propertiesId = UUID.randomUUID()
        val roundId = UUID.randomUUID()
        val matchId = UUID.randomUUID()

        !EVENT.insert(
            EventRecord(
                id = eventId,
                name = "Testregatta",
                createdAt = now,
                updatedAt = now,
                timingSystem = eventTimingSystem,
                raceclockerAutoPull = true,
            )
        )

        // Die Rennen gehören der Veranstaltung; Veranstaltung und Wettkampf zeigen nur darauf.
        val eventTtRaceId = eventTimeTrialResultsUrl?.let {
            insertRace(eventId, "Zeitfahren", it, RaceClockerStartMode.INDIVIDUAL, 1)
        }
        val eventHeatsRaceId = eventHeatsResultsUrl?.let {
            insertRace(eventId, "Laeufe", it, RaceClockerStartMode.WAVE, 2)
        }
        val ownTtRaceId = competitionTimeTrialUrl?.let {
            insertRace(eventId, "Zeitfahren eigen", it, RaceClockerStartMode.INDIVIDUAL, 3)
        }
        val ownHeatsRaceId = competitionHeatsUrl?.let {
            insertRace(eventId, "Laeufe eigen", it, RaceClockerStartMode.WAVE, 4)
        }

        !EVENT.update({
            raceclockerRaceQualification = eventTtRaceId
            raceclockerRaceRounds = eventHeatsRaceId
        }) { ID.eq(eventId) }

        !COMPETITION.insert(
            CompetitionRecord(
                id = competitionId,
                event = eventId,
                createdAt = now,
                updatedAt = now,
                timingSystem = competitionTimingSystem,
                raceclockerRaceQualification = ownTtRaceId,
                raceclockerRaceRounds = ownHeatsRaceId,
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
                createdAt = now,
                updatedAt = now,
            )
        )

        !COMPETITION_SETUP_ROUND.insert(
            CompetitionSetupRoundRecord(
                id = roundId,
                competitionSetup = propertiesId,
                name = "Finale",
                required = true,
                useDefaultSeeding = true,
                placesOption = CompetitionSetupPlacesOption.EQUAL.name,
                isQualification = isQualification,
            )
        )

        !COMPETITION_SETUP_MATCH.insert(
            CompetitionSetupMatchRecord(
                id = matchId,
                competitionSetupRound = roundId,
                weighting = 1,
                name = "Lauf 1",
                executionOrder = 1,
            )
        )

        !COMPETITION_MATCH.insert(
            CompetitionMatchRecord(
                competitionSetupMatch = matchId,
                startTime = now,
                createdAt = now,
                updatedAt = now,
                activatedAt = if (activated) now else null,
                startedAt = startedAt,
                finishedAt = finishedAt,
                raceclockerAutoPausedAt = autoPausedAt,
            )
        )

        if (withSlot || slotSkippedAt != null) {
            !EVENT_SCHEDULE_SLOT.insert(
                EventScheduleSlotRecord(
                    id = UUID.randomUUID(),
                    event = eventId,
                    startTime = now,
                    competitionSetupMatch = matchId,
                    skippedAt = slotSkippedAt,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }

        return eventId to matchId
    }

    @Test
    fun aRunningMatchOfARaceClockerEventIsACandidate() = testComprehension {
        val (eventId, matchId) = seed()

        val candidates = !RaceClockerPollRepo.getCandidates(eventId)

        val candidate = candidates.singleOrNull()
        assertNotNull(candidate, "Der Lauf hätte als Kandidat zurückkommen müssen, kam aber nicht: $candidates")
        assertEquals(matchId, candidate.matchId)
        assertNotNull(candidate.activatedAt)
        assertEquals(now, candidate.startTime)
        // Die Wellenbezeichnung entsteht wie beim Startlisten-Export aus Startzeit und Laufname.
        assertEquals("10:00 Lauf 1", candidate.target.waveName)
        assertEquals(eventHeatsUrl, candidate.target.roundsRace?.resultsUrl)
        assertEquals(eventTimeTrialUrl, candidate.target.qualificationRace?.resultsUrl)
    }

    /**
     * Aktivierung und Ist-Start kommen getrennt zurück. Daran hängt der Zweig in
     * `RaceClockerPollService.pollMatch`, der den gemessenen Start eines bereits an den Start
     * gerufenen Laufs nachträgt: Er greift genau dann, wenn `startedAt` null ist. Käme die Spalte
     * nicht mit, bliebe ein von der Kette aktivierter Lauf für immer "in Vorbereitung".
     */
    @Test
    fun anActivatedMatchIsReportedWithoutARealStartUntilOneIsStamped() = testComprehension {
        val (preparingEventId, _) = seed()
        val preparing = (!RaceClockerPollRepo.getCandidates(preparingEventId)).single()
        assertNotNull(preparing.activatedAt)
        assertEquals(null, preparing.startedAt)

        val (runningEventId, _) = seed(startedAt = now.plusMinutes(2))
        val running = (!RaceClockerPollRepo.getCandidates(runningEventId)).single()
        assertEquals(now.plusMinutes(2), running.startedAt)
    }

    /** Der Job beendet nie einen Lauf und fasst einen beendeten auch nicht mehr an. */
    @Test
    fun aFinishedMatchIsExcluded() = testComprehension {
        val (eventId, _) = seed(finishedAt = now)

        assertEquals(emptyList(), !RaceClockerPollRepo.getCandidates(eventId))
    }

    /** Wer von Hand eingetragen hat, hat das letzte Wort - bis er den Lauf wieder freigibt. */
    @Test
    fun aPausedMatchIsExcluded() = testComprehension {
        val (eventId, _) = seed(autoPausedAt = now)

        assertEquals(emptyList(), !RaceClockerPollRepo.getCandidates(eventId))
    }

    /**
     * Ein Wettkampf, der die RaceClocker-Voreinstellung der Veranstaltung mit Webscorer
     * überschreibt, hat keinen Feed, den der Job abholen könnte. Der Fall prüft zugleich, dass die
     * Coalesce-Kette im WHERE überhaupt rendert - genau daran ist die Abfrage schon einmal
     * gescheitert.
     */
    @Test
    fun aCompetitionOverridingTheTimingSystemIsExcluded() = testComprehension {
        val (eventId, _) = seed(competitionTimingSystem = TimingSystem.WEBSCORER.name)

        assertEquals(emptyList(), !RaceClockerPollRepo.getCandidates(eventId))
    }

    /** Ohne Zeitnahmesystem - der Zustand jeder Bestandsveranstaltung - gibt es nichts abzurufen. */
    @Test
    fun anEventWithoutATimingSystemHasNoCandidates() = testComprehension {
        val (eventId, _) = seed(eventTimingSystem = null)

        assertEquals(emptyList(), !RaceClockerPollRepo.getCandidates(eventId))
    }

    /** Ohne ein angewähltes Rennen gibt es nichts, das man abfragen könnte. */
    @Test
    fun aMatchWithoutAnySelectedRaceIsExcluded() = testComprehension {
        val (eventId, _) = seed(eventHeatsResultsUrl = null, eventTimeTrialResultsUrl = null)

        assertEquals(emptyList(), !RaceClockerPollRepo.getCandidates(eventId))
    }

    /**
     * Ein abgesagter Slot bleibt abgesagt, auch wenn in RaceClocker jemand die Welle startet -
     * sonst aktivierte der Job einen Lauf, den das Regattabüro gestrichen hat.
     */
    @Test
    fun aMatchOnASkippedScheduleSlotIsExcluded() = testComprehension {
        val (eventId, _) = seed(slotSkippedAt = now)

        assertEquals(emptyList(), !RaceClockerPollRepo.getCandidates(eventId))
    }

    /** Ein vorhandener, aber nicht abgesagter Slot ändert nichts. */
    @Test
    fun aMatchOnALiveScheduleSlotStaysACandidate() = testComprehension {
        val (eventId, matchId) = seed(withSlot = true)

        assertEquals(listOf(matchId), (!RaceClockerPollRepo.getCandidates(eventId)).map { it.matchId })
    }

    /**
     * Die Coalesce-Kette, festgenagelt: Der eigene Wert des Wettkampfs schlägt die Voreinstellung
     * der Veranstaltung - dieselbe Regel wie beim Knopf
     * (`CompetitionMatchRepo.getForRaceClockerPull`). Liefe sie hier andersherum, zöge der Job
     * seine Ergebnisse aus dem falschen Rennen, und zwar lautlos.
     */
    @Test
    fun theCompetitionsOwnRaceWinsOverTheEventDefault() = testComprehension {
        val ownHeats = "https://www.raceclocker.com/competition-heats"
        val (eventId, _) = seed(competitionHeatsUrl = ownHeats)

        val candidate = (!RaceClockerPollRepo.getCandidates(eventId)).single()

        assertEquals(ownHeats, candidate.target.roundsRace?.resultsUrl)
        // Das andere Rennen hat der Wettkampf nicht angewählt - es erbt weiter.
        assertEquals(eventTimeTrialUrl, candidate.target.qualificationRace?.resultsUrl)
        // Und für eine Runde, die keine Qualifikation ist, ist die Läufe-Adresse die erste Wahl.
        assertEquals(listOf(ownHeats, eventTimeTrialUrl), candidate.target.candidateUrls)
    }

    /** Die Qualifikationsrunde dreht die Reihenfolge um: Zeitfahren zuerst, Läufe als Rückfall. */
    @Test
    fun aQualificationRoundTriesTheTimeTrialRaceFirst() = testComprehension {
        val (eventId, _) = seed(isQualification = true)

        val candidate = (!RaceClockerPollRepo.getCandidates(eventId)).single()

        assertTrue(candidate.target.isQualification)
        assertEquals(listOf(eventTimeTrialUrl, eventHeatsUrl), candidate.target.candidateUrls)
    }

    /** Ein anderer Veranstaltungs-Filter darf nichts durchlassen. */
    @Test
    fun candidatesAreScopedToTheirEvent() = testComprehension {
        seed()

        assertEquals(emptyList(), !RaceClockerPollRepo.getCandidates(UUID.randomUUID()))
    }

    /**
     * Ein gelöschtes Rennen entwertet die Anwahl (`on delete set null`), statt das Löschen zu
     * blockieren. Der Lauf fällt danach still aus der Kandidatenmenge - der Job überspringt ihn,
     * statt am fehlenden Rennen zu scheitern.
     */
    @Test
    fun aDeletedRaceLeavesTheMatchWithoutASelection() = testComprehension {
        val (eventId, _) = seed()

        !RACECLOCKER_RACE.delete { EVENT.eq(eventId) }

        assertEquals(emptyList(), !RaceClockerPollRepo.getCandidates(eventId))
    }
}
