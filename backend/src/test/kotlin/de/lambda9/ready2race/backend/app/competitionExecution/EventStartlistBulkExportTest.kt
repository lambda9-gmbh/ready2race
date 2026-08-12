package de.lambda9.ready2race.backend.app.competitionExecution

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchTeamRepo
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionExecutionError
import de.lambda9.ready2race.backend.app.competitionExecution.entity.EventStartlistFileType
import de.lambda9.ready2race.backend.app.competitionExecution.entity.UpdateMatchByeMustRaceRequest
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupPlacesOption
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerFeedRow
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchTeamRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionPropertiesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRoundRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceclockerRaceRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.StartlistExportConfigRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_ROUND
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.RACECLOCKER_RACE
import de.lambda9.ready2race.backend.database.generated.tables.references.STARTLIST_EXPORT_CONFIG
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Der Startlisten-Sammelexport (Zeitplan-Tab) gegen eine echte Datenbank: Erstrunden-Auswahl,
 * Freilos-Regeln samt "muss gefahren werden", ZIP-Aufteilung und der Delta-Abgleich gegen einen
 * Fixture-Feed. Der HTTP-Abruf selbst ist hier bewusst außen vor - [buildEventStartlists] nimmt
 * die Feed-Zeilen entgegen, geholt wird in `downloadEventStartlists` (dieselbe Trennung wie
 * applyRaceClockerRows gegenüber updateMatchResultFromRaceClocker, der Abruf ist in
 * RaceClockerFeedFetchTest belegt).
 */
class EventStartlistBulkExportTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 14, 10, 0)

    private data class SeededMatch(
        val setupMatchId: UUID,
        /** Die Lauf-Mannschafts-Kennungen - der Schlüssel des Delta-Abgleichs. */
        val matchTeamIds: List<UUID>,
    )

    private data class SeededCompetition(
        val competitionId: UUID,
        val firstRoundId: UUID,
        val matches: List<SeededMatch>,
    )

    private fun TestComprehensionScope<JEnv>.insertStartlistConfig(): UUID {
        val configId = UUID.randomUUID()
        !STARTLIST_EXPORT_CONFIG.insert(
            StartlistExportConfigRecord(
                id = configId,
                name = "Testpreset",
                colTeamStartNumber = "Bib",
                colMatchStartTime = "Start",
                colCompetitionIdentifier = "Comp",
                createdAt = now,
                updatedAt = now,
            )
        )
        return configId
    }

    private fun TestComprehensionScope<JEnv>.insertEvent(startlistConfig: UUID): UUID {
        val eventId = UUID.randomUUID()
        !EVENT.insert(
            EventRecord(
                id = eventId,
                name = "Testregatta",
                createdAt = now,
                updatedAt = now,
                startlistConfig = startlistConfig,
            )
        )
        return eventId
    }

    private fun TestComprehensionScope<JEnv>.insertRace(
        eventId: UUID,
        url: String,
        // Name und Position parametrierbar: je Veranstaltung sind beide eindeutig, und der
        // Rennen-Filter-Fall braucht ZWEI Rennen in derselben Veranstaltung.
        name: String = "Kurzstrecke",
        position: Int = 1,
    ): UUID {
        val raceId = UUID.randomUUID()
        !RACECLOCKER_RACE.insert(
            RaceclockerRaceRecord(
                id = raceId,
                event = eventId,
                name = name,
                resultsUrl = url,
                capturesLaps = false,
                position = position,
                createdAt = now,
                updatedAt = now,
            )
        )
        return raceId
    }

    private fun TestComprehensionScope<JEnv>.insertTeam(
        eventId: UUID,
        competitionId: UUID,
        matchId: UUID,
        startNumber: Int,
        place: Int? = null,
        out: Boolean = false,
    ): UUID {
        val clubId = UUID.randomUUID()
        val eventRegistrationId = UUID.randomUUID()
        val registrationId = UUID.randomUUID()
        val matchTeamId = UUID.randomUUID()

        // Der Vereinsname ist datenbankweit eindeutig (club_name_unique), und die Test-DB wird
        // zwischen den Fällen nicht zurückgesetzt - deshalb die Id im Namen.
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
                teamNumber = startNumber,
                createdAt = now,
                updatedAt = now,
            )
        )
        !COMPETITION_MATCH_TEAM.insert(
            CompetitionMatchTeamRecord(
                id = matchTeamId,
                competitionMatch = matchId,
                competitionRegistration = registrationId,
                startNumber = startNumber,
                place = place,
                out = out,
                createdAt = now,
                updatedAt = now,
            )
        )
        return matchTeamId
    }

    /**
     * Ein Wettkampf mit einer gesetzten Runde ([roundName], [required]) und je Eintrag in
     * [matchTeamCounts] einem Lauf mit so vielen Mannschaften; die Startzeiten liegen
     * [startOffsetsMinutes] Minuten nach 10:00. Mit [withEmptyFollowingRound] hängt eine noch
     * nicht gesetzte Folgerunde dahinter - der Beleg, dass der Export die ERSTE gesetzte Runde
     * nimmt und keine leere.
     */
    private fun TestComprehensionScope<JEnv>.insertCompetition(
        eventId: UUID,
        identifier: String,
        roundName: String = "Vorlauf",
        required: Boolean = true,
        matchTeamCounts: List<Int>,
        // null = keine geplante Startzeit - der Fall, der den Export blockieren muss.
        startOffsetsMinutes: List<Long?>,
        raceId: UUID? = null,
        withEmptyFollowingRound: Boolean = false,
    ): SeededCompetition {
        val competitionId = UUID.randomUUID()
        val propertiesId = UUID.randomUUID()
        val roundId = UUID.randomUUID()

        !COMPETITION.insert(
            CompetitionRecord(
                id = competitionId,
                event = eventId,
                createdAt = now,
                updatedAt = now,
                raceclockerRace = raceId,
            )
        )
        !COMPETITION_PROPERTIES.insert(
            CompetitionPropertiesRecord(
                id = propertiesId,
                competition = competitionId,
                identifier = identifier,
                name = "Wettkampf $identifier",
            )
        )
        !COMPETITION_SETUP.insert(
            CompetitionSetupRecord(
                competitionProperties = propertiesId,
                createdAt = now,
                updatedAt = now,
            )
        )

        var followingRoundId: UUID? = null
        if (withEmptyFollowingRound) {
            followingRoundId = UUID.randomUUID()
            !COMPETITION_SETUP_ROUND.insert(
                CompetitionSetupRoundRecord(
                    id = followingRoundId,
                    competitionSetup = propertiesId,
                    name = "Finale",
                    required = true,
                    useDefaultSeeding = true,
                    placesOption = CompetitionSetupPlacesOption.EQUAL.name,
                )
            )
            // Die Folgerunde braucht einen Setup-Lauf, sonst wäre sie auch strukturell leer -
            // materialisiert (competition_match) wird sie absichtlich nicht.
            !COMPETITION_SETUP_MATCH.insert(
                CompetitionSetupMatchRecord(
                    id = UUID.randomUUID(),
                    competitionSetupRound = followingRoundId,
                    weighting = 1,
                    name = "Finale 1",
                    executionOrder = 1,
                )
            )
        }

        !COMPETITION_SETUP_ROUND.insert(
            CompetitionSetupRoundRecord(
                id = roundId,
                competitionSetup = propertiesId,
                nextRound = followingRoundId,
                name = roundName,
                required = required,
                useDefaultSeeding = true,
                placesOption = CompetitionSetupPlacesOption.EQUAL.name,
            )
        )

        val matches = matchTeamCounts.mapIndexed { index, teamCount ->
            val matchId = UUID.randomUUID()
            !COMPETITION_SETUP_MATCH.insert(
                CompetitionSetupMatchRecord(
                    id = matchId,
                    competitionSetupRound = roundId,
                    weighting = index + 1,
                    name = "Lauf ${index + 1}",
                    executionOrder = index + 1,
                )
            )
            !COMPETITION_MATCH.insert(
                CompetitionMatchRecord(
                    competitionSetupMatch = matchId,
                    startTime = startOffsetsMinutes[index]?.let { now.plusMinutes(it) },
                    createdAt = now,
                    updatedAt = now,
                )
            )
            val teamIds = (1..teamCount).map { n ->
                insertTeam(eventId, competitionId, matchId, startNumber = n)
            }
            SeededMatch(matchId, teamIds)
        }

        return SeededCompetition(competitionId, roundId, matches)
    }

    /**
     * Die Werte einer Spalte, Zeile für Zeile - zum Prüfen von Reihenfolge und Kopfzeile.
     * OpenCSV schreibt jede Zelle in Anführungszeichen, die werden hier wieder abgestreift.
     */
    private fun csvColumn(csv: String, header: String): List<String> {
        val lines = csv.trim().lines().map { line ->
            line.trim().split(",").map { it.removeSurrounding("\"") }
        }
        val index = lines.first().indexOf(header)
        assertTrue(index >= 0, "Spalte $header fehlt in: ${lines.first()}")
        return lines.drop(1).map { it[index] }
    }

    @Test
    fun bigCsvContainsFirstRoundsOfAllCompetitionsSortedByStartTime() = testComprehension {
        val configId = insertStartlistConfig()
        val eventId = insertEvent(configId)
        // Wettkampf 1: zwei Läufe (10:00, 10:10) plus eine noch nicht gesetzte Folgerunde.
        insertCompetition(
            eventId, "1",
            matchTeamCounts = listOf(2, 2),
            startOffsetsMinutes = listOf(0, 10),
            withEmptyFollowingRound = true,
        )
        // Wettkampf 2: ein Lauf dazwischen (10:05).
        insertCompetition(
            eventId, "2",
            matchTeamCounts = listOf(2),
            startOffsetsMinutes = listOf(5),
        )

        val plan = !CompetitionExecutionService.eventStartlistPlan(eventId, allRounds = false, skipByes = true)
        val file = !CompetitionExecutionService.buildEventStartlists(plan, EventStartlistFileType.CSV)

        val csv = String((file as ApiResponse.File).bytes)
        // Eine Kopfzeile, dann je Mannschaft eine Zeile, über alles nach Startzeit sortiert:
        // 10:00 (Wettkampf 1, Lauf 1), 10:05 (Wettkampf 2), 10:10 (Wettkampf 1, Lauf 2).
        assertEquals(
            listOf("1", "1", "2", "2", "1", "1"),
            csvColumn(csv, "Comp"),
        )
        assertEquals(
            listOf("10:00:00", "10:00:00", "10:05:00", "10:05:00", "10:10:00", "10:10:00"),
            csvColumn(csv, "Start"),
        )
    }

    @Test
    fun zipContainsOneCsvPerCompetitionNamedLikeTheRoundExport() = testComprehension {
        val configId = insertStartlistConfig()
        val eventId = insertEvent(configId)
        insertCompetition(eventId, "1", matchTeamCounts = listOf(2), startOffsetsMinutes = listOf(0))
        insertCompetition(eventId, "2", matchTeamCounts = listOf(2), startOffsetsMinutes = listOf(5))

        val plan = !CompetitionExecutionService.eventStartlistPlan(eventId, allRounds = false, skipByes = true)
        val file = !CompetitionExecutionService.buildEventStartlists(plan, EventStartlistFileType.ZIP)

        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream((file as ApiResponse.File).bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = String(zip.readBytes())
                entry = zip.nextEntry
            }
        }

        assertEquals(
            setOf("startList-1-Vorlauf.csv", "startList-2-Vorlauf.csv"),
            entries.keys,
        )
        // Jede Datei trägt ihre eigene Kopfzeile - anders als die große CSV, die nur eine hat.
        entries.values.forEach { csv ->
            assertTrue(csv.trim().lines().first().contains("Bib"))
        }
    }

    @Test
    fun byesAreSkippedUnlessTheyMustRace() = testComprehension {
        val configId = insertStartlistConfig()
        val eventId = insertEvent(configId)
        // Nicht verpflichtende Runde: Lauf 1 mit zwei Booten, Lauf 2 mit einem - ein Freilos.
        val competition = insertCompetition(
            eventId, "1",
            required = false,
            matchTeamCounts = listOf(2, 1),
            startOffsetsMinutes = listOf(0, 10),
        )
        val byeMatch = competition.matches[1]

        // Vorbelegung "Freilose überspringen": das Freilos fehlt.
        val skipped = !CompetitionExecutionService.eventStartlistPlan(eventId, allRounds = false, skipByes = true)
        assertEquals(
            listOf(competition.matches[0].setupMatchId),
            skipped.single().matches.map { it.setupMatchId },
        )

        // Häkchen raus: das Freilos kommt mit.
        val unskipped = !CompetitionExecutionService.eventStartlistPlan(eventId, allRounds = false, skipByes = false)
        assertEquals(2, unskipped.single().matches.size)

        // "Muss gefahren werden" übersteuert das Häkchen: das Freilos wird IMMER exportiert.
        !CompetitionExecutionService.updateByeMustRace(
            eventId = eventId,
            competitionId = competition.competitionId,
            matchId = byeMatch.setupMatchId,
            userId = SYSTEM_USER,
            request = UpdateMatchByeMustRaceRequest(mustRace = true),
        )
        val mustRace = !CompetitionExecutionService.eventStartlistPlan(eventId, allRounds = false, skipByes = true)
        assertEquals(
            listOf(competition.matches[0].setupMatchId, byeMatch.setupMatchId),
            mustRace.single().matches.map { it.setupMatchId },
        )
    }

    @Test
    fun deltaExportsOnlyMatchesMissingInTheFeed() = testComprehension {
        val configId = insertStartlistConfig()
        val eventId = insertEvent(configId)
        val raceUrl = "https://raceclocker.com/7c854955"
        val raceId = insertRace(eventId, raceUrl)

        val withRace = insertCompetition(
            eventId, "1",
            matchTeamCounts = listOf(2, 2),
            startOffsetsMinutes = listOf(0, 10),
            raceId = raceId,
        )
        // Ohne angewähltes Rennen gibt es keine Vergleichsbasis - der Wettkampf fällt im Delta raus.
        insertCompetition(eventId, "2", matchTeamCounts = listOf(2), startOffsetsMinutes = listOf(5))

        val feedRow = { ids: List<UUID> ->
            RaceClockerFeedRow(
                name = "Boot",
                rank = null,
                bib = null,
                wave = null,
                ids = ids,
                result = null,
                start = null,
                penaltySeconds = null,
                penaltyNote = null,
            )
        }
        // Lauf 1 steht schon im Rennen (eine seiner Lauf-Mannschafts-Kennungen kommt zurück),
        // dazu eine Zeile ohne Kennungen - die belegt nichts.
        val feeds = mapOf(
            raceUrl to listOf(
                feedRow(listOf(withRace.matches[0].matchTeamIds.first())),
                feedRow(emptyList()),
            )
        )

        val plan = !CompetitionExecutionService.eventStartlistPlan(eventId, allRounds = true, skipByes = true)
        val file = !CompetitionExecutionService.buildEventStartlists(plan, EventStartlistFileType.ZIP, feeds)

        val entries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream((file as ApiResponse.File).bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries += entry.name
                entry = zip.nextEntry
            }
        }

        // Nur Wettkampf 1 und dort nur Lauf 2 - Lauf 1 ist im Feed, Wettkampf 2 hat kein Rennen.
        assertEquals(listOf("startList-1-Vorlauf.csv"), entries)

        val csvPlanned = !CompetitionExecutionService.buildEventStartlists(plan, EventStartlistFileType.CSV, feeds)
        val csv = String((csvPlanned as ApiResponse.File).bytes)
        assertEquals(listOf("10:10:00", "10:10:00"), csvColumn(csv, "Start"))
    }

    /**
     * Der Rennen-Filter (Import Rennen für Rennen): Zwei Wettkämpfe auf zwei Rennen, dazu einer
     * ganz ohne Rennen - gefiltert kommen nur die Läufe des Wettkampfs am gewählten Rennen,
     * ungefiltert weiterhin alle.
     */
    @Test
    fun raceFilterPlansOnlyCompetitionsOfTheChosenRace() = testComprehension {
        val configId = insertStartlistConfig()
        val eventId = insertEvent(configId)
        val raceA = insertRace(eventId, "https://raceclocker.com/aaa111", name = "Kurzstrecke", position = 1)
        val raceB = insertRace(eventId, "https://raceclocker.com/bbb222", name = "Langstrecke", position = 2)

        val onRaceA = insertCompetition(
            eventId, "1",
            matchTeamCounts = listOf(2, 2),
            startOffsetsMinutes = listOf(0, 10),
            raceId = raceA,
        )
        insertCompetition(
            eventId, "2",
            matchTeamCounts = listOf(2),
            startOffsetsMinutes = listOf(5),
            raceId = raceB,
        )
        insertCompetition(eventId, "3", matchTeamCounts = listOf(2), startOffsetsMinutes = listOf(15))

        val filtered = !CompetitionExecutionService.eventStartlistPlan(
            eventId, allRounds = false, skipByes = true, raceclockerRaceId = raceA,
        )
        // Nur der Wettkampf am gewählten Rennen - und dort ALLE seine Läufe.
        assertEquals(listOf(onRaceA.competitionId), filtered.map { it.competitionId })
        assertEquals(
            onRaceA.matches.map { it.setupMatchId },
            filtered.single().matches.map { it.setupMatchId },
        )

        // Auch die gebaute Datei trägt nur die Läufe des gewählten Rennens (10:00 und 10:10).
        val file = !CompetitionExecutionService.buildEventStartlists(filtered, EventStartlistFileType.CSV)
        val csv = String((file as ApiResponse.File).bytes)
        assertEquals(
            listOf("10:00:00", "10:00:00", "10:10:00", "10:10:00"),
            csvColumn(csv, "Start"),
        )

        // Ohne Filter bleibt alles beim Alten: alle drei Wettkämpfe.
        val unfiltered = !CompetitionExecutionService.eventStartlistPlan(
            eventId, allRounds = false, skipByes = true,
        )
        assertEquals(3, unfiltered.size)
    }

    /**
     * Die Vorschau ist keine zweite Wahrheit: Mit demselben Plan und denselben Fixture-Feeds
     * listet sie im Delta-Modus GENAU die Läufe, die der Export in die Datei schreibt.
     */
    @Test
    fun previewListsExactlyWhatTheDeltaExportContains() = testComprehension {
        val configId = insertStartlistConfig()
        val eventId = insertEvent(configId)
        val raceUrl = "https://raceclocker.com/preview1"
        val raceId = insertRace(eventId, raceUrl)

        val withRace = insertCompetition(
            eventId, "1",
            matchTeamCounts = listOf(2, 2),
            startOffsetsMinutes = listOf(0, 10),
            raceId = raceId,
        )
        // Ohne Rennen: fällt im Delta aus Export UND Vorschau - dieselbe Regel, dieselbe Funktion.
        insertCompetition(eventId, "2", matchTeamCounts = listOf(2), startOffsetsMinutes = listOf(5))

        // Lauf 1 steht schon im Rennen, Lauf 2 fehlt.
        val feeds = mapOf(
            raceUrl to listOf(
                RaceClockerFeedRow(
                    name = "Boot", rank = null, bib = null, wave = null,
                    ids = listOf(withRace.matches[0].matchTeamIds.first()),
                    result = null, start = null, penaltySeconds = null, penaltyNote = null,
                )
            )
        )

        val plan = !CompetitionExecutionService.eventStartlistPlan(eventId, allRounds = true, skipByes = true)

        val preview = CompetitionExecutionService.buildEventStartlistPreview(
            plan, feeds, onlyMissingInRaceClocker = true,
        )
        assertEquals(listOf(withRace.matches[1].setupMatchId), preview.map { it.matchId })
        // Im Delta trivial überall true - die Information steckt im Nicht-Delta-Fall (Test unten).
        assertEquals(listOf(true), preview.map { it.missingInRaceClocker })

        // Der Export mit denselben Eingaben schreibt genau diesen einen Lauf (10:10).
        val file = !CompetitionExecutionService.buildEventStartlists(plan, EventStartlistFileType.CSV, feeds)
        val csv = String((file as ApiResponse.File).bytes)
        assertEquals(listOf("10:10:00", "10:10:00"), csvColumn(csv, "Start"))
    }

    /**
     * Ohne Delta listet die Vorschau alle Läufe des Plans - und das Flag trägt die Information:
     * true = fehlt im Feed, false = schon drüben, null = kein Rennen angewählt.
     */
    @Test
    fun previewFlagsMissingCorrectlyWithoutDelta() = testComprehension {
        val configId = insertStartlistConfig()
        val eventId = insertEvent(configId)
        val raceUrl = "https://raceclocker.com/preview2"
        val raceId = insertRace(eventId, raceUrl)

        val withRace = insertCompetition(
            eventId, "1",
            matchTeamCounts = listOf(2, 2),
            startOffsetsMinutes = listOf(0, 10),
            raceId = raceId,
        )
        val withoutRace = insertCompetition(
            eventId, "2",
            matchTeamCounts = listOf(2),
            startOffsetsMinutes = listOf(5),
        )

        val feeds = mapOf(
            raceUrl to listOf(
                RaceClockerFeedRow(
                    name = "Boot", rank = null, bib = null, wave = null,
                    ids = listOf(withRace.matches[0].matchTeamIds.first()),
                    result = null, start = null, penaltySeconds = null, penaltyNote = null,
                )
            )
        )

        val plan = !CompetitionExecutionService.eventStartlistPlan(eventId, allRounds = false, skipByes = true)
        val preview = CompetitionExecutionService.buildEventStartlistPreview(
            plan, feeds, onlyMissingInRaceClocker = false,
        )

        // Nach Startzeit sortiert: 10:00 (im Feed), 10:05 (kein Rennen), 10:10 (fehlt).
        assertEquals(
            listOf(
                withRace.matches[0].setupMatchId to false,
                withoutRace.matches[0].setupMatchId to null,
                withRace.matches[1].setupMatchId to true,
            ),
            preview.map { it.matchId to it.missingInRaceClocker },
        )
        // Und die Beschriftung reicht für die Anzeige: Kennung, Runde, Laufname.
        assertEquals("1", preview.first().competitionIdentifier)
        assertEquals("Vorlauf", preview.first().roundName)
        assertEquals("Lauf 1", preview.first().matchName)
    }

    /**
     * Die Schnittmengen-Regel von matchIds: Der Parameter kann den Plan nur verkleinern - eine
     * fremde Id (anderer Wettkampf, andere Veranstaltung, ausgefiltert) wird ignoriert, nie
     * exportiert.
     */
    @Test
    fun matchIdsOnlyEverIntersectThePlan() = testComprehension {
        val configId = insertStartlistConfig()
        val eventId = insertEvent(configId)
        val competition = insertCompetition(
            eventId, "1",
            matchTeamCounts = listOf(2, 2),
            startOffsetsMinutes = listOf(0, 10),
        )

        val plan = !CompetitionExecutionService.eventStartlistPlan(eventId, allRounds = false, skipByes = true)

        // Ein echter Lauf plus eine untergeschobene fremde Id: nur der echte bleibt.
        val restricted = CompetitionExecutionService.restrictPlanToMatches(
            plan,
            setOf(competition.matches[0].setupMatchId, UUID.randomUUID()),
        )
        assertEquals(
            listOf(competition.matches[0].setupMatchId),
            restricted.flatMap { it.matches }.map { it.setupMatchId },
        )

        // Nur fremde Ids: leerer Plan, kein Fehler - der Parameter kann nichts hinzufügen.
        val foreignOnly = CompetitionExecutionService.restrictPlanToMatches(plan, setOf(UUID.randomUUID()))
        assertTrue(foreignOnly.flatMap { it.matches }.isEmpty())

        // null = keine Einschränkung.
        assertEquals(plan, CompetitionExecutionService.restrictPlanToMatches(plan, null))
    }

    /**
     * Läufe ohne geplante Startzeit blockieren den Export weiterhin (eine still unvollständige
     * Startliste wäre gefährlicher als ein lauter Fehler) - aber der Fehler nennt jetzt ALLE
     * betroffenen Läufe mit Wettkampf, Runde und Laufname statt eines nackten "StartTime not
     * set". Nach der Abwahl über matchIds exportiert der Rest.
     */
    @Test
    fun exportFailsNamingAllMatchesWithoutStartTimeAndExportsTheRestAfterDeselection() = testComprehension {
        val configId = insertStartlistConfig()
        val eventId = insertEvent(configId)
        // Lauf 1 hat eine Startzeit, Lauf 2 und 3 nicht.
        val competition = insertCompetition(
            eventId, "1",
            matchTeamCounts = listOf(2, 2, 2),
            startOffsetsMinutes = listOf(0, null, null),
        )

        val plan = !CompetitionExecutionService.eventStartlistPlan(eventId, allRounds = false, skipByes = true)

        val blocked = { index: Int ->
            CompetitionExecutionError.StartlistMatchWithoutStartTime(
                matchId = competition.matches[index].setupMatchId,
                competitionIdentifier = "1",
                competitionShortName = null,
                competitionName = "Wettkampf 1",
                roundName = "Vorlauf",
                matchName = "Lauf ${index + 1}",
            )
        }
        assertKIOFails(
            CompetitionExecutionError.StartlistMatchesWithoutStartTime(listOf(blocked(1), blocked(2))),
        ) {
            CompetitionExecutionService.buildEventStartlists(plan, EventStartlistFileType.CSV)
        }

        // Abwahl der beiden über matchIds (die Schnittmenge): der Rest exportiert anstandslos.
        val restricted = CompetitionExecutionService.restrictPlanToMatches(
            plan,
            setOf(competition.matches[0].setupMatchId),
        )
        val file = !CompetitionExecutionService.buildEventStartlists(restricted, EventStartlistFileType.CSV)
        val csv = String((file as ApiResponse.File).bytes)
        assertEquals(listOf("10:00:00", "10:00:00"), csvColumn(csv, "Start"))
    }

    /** Der Text je Seite - Reihenfolge und Seitenzahl des Sammel-PDFs prüfbar machen. */
    private fun pdfPageTexts(bytes: ByteArray): List<String> =
        Loader.loadPDF(bytes).use { doc ->
            (1..doc.numberOfPages).map { page ->
                PDFTextStripper().apply {
                    startPage = page
                    endPage = page
                }.getText(doc)
            }
        }

    /**
     * Der PDF-Sammelexport (Wunsch Regattabüro, 12.08.2026): je Lauf der Auswahl die bekannte
     * Einzel-Startlisten-PDF als Seite, in Startzeit-Reihenfolge über alle Wettkämpfe - derselbe
     * Plan wie bei ZIP/CSV. OHNE zugewiesene START_LIST-Vorlage rendert der Export die
     * vorlagenlose Standard-Startliste (kein Fehler, kein 500er) - exakt das Verhalten des
     * Einzel-Lauf-Exports; genau das belegt dieser Fall, denn hier ist keine Vorlage zugewiesen.
     */
    @Test
    fun pdfConcatenatesStartlistsSortedByStartTimeAlsoWithoutAssignedTemplate() = testComprehension {
        val configId = insertStartlistConfig()
        val eventId = insertEvent(configId)
        insertCompetition(
            eventId, "1",
            matchTeamCounts = listOf(2, 2),
            startOffsetsMinutes = listOf(0, 10),
        )
        insertCompetition(eventId, "2", matchTeamCounts = listOf(2), startOffsetsMinutes = listOf(5))

        val plan = !CompetitionExecutionService.eventStartlistPlan(eventId, allRounds = false, skipByes = true)
        val file = !CompetitionExecutionService.buildEventStartlists(plan, EventStartlistFileType.PDF)

        val pages = pdfPageTexts((file as ApiResponse.File).bytes)
        // Eine Seite je Lauf, nach Startzeit über alle Wettkämpfe: 10:00 (W1), 10:05 (W2), 10:10 (W1).
        assertEquals(3, pages.size, "Erwartet eine Seite je Lauf")
        assertTrue(pages[0].contains("10:00:00") && pages[0].contains("Wettkampf 1"), pages[0])
        assertTrue(pages[1].contains("10:05:00") && pages[1].contains("Wettkampf 2"), pages[1])
        assertTrue(pages[2].contains("10:10:00") && pages[2].contains("Wettkampf 1"), pages[2])
        assertTrue(file.name.endsWith(".pdf"), file.name)
    }

    /** Dieselben Regeln wie bei ZIP/CSV wirken auch im PDF: matchIds-Schnittmenge und Startzeit-Wächter. */
    @Test
    fun pdfHonorsMatchIdsIntersectionAndStartTimeGuard() = testComprehension {
        val configId = insertStartlistConfig()
        val eventId = insertEvent(configId)
        val competition = insertCompetition(
            eventId, "1",
            matchTeamCounts = listOf(2, 2),
            startOffsetsMinutes = listOf(0, 10),
        )

        val plan = !CompetitionExecutionService.eventStartlistPlan(eventId, allRounds = false, skipByes = true)

        // Schnittmenge: nur Lauf 2 gewählt, dazu eine fremde Id - exportiert wird genau Lauf 2.
        val restricted = CompetitionExecutionService.restrictPlanToMatches(
            plan,
            setOf(competition.matches[1].setupMatchId, UUID.randomUUID()),
        )
        val file = !CompetitionExecutionService.buildEventStartlists(restricted, EventStartlistFileType.PDF)
        val pages = pdfPageTexts((file as ApiResponse.File).bytes)
        assertEquals(1, pages.size)
        assertTrue(pages[0].contains("10:10:00"), pages[0])

        // Startzeit-Wächter: derselbe strukturierte Fehler wie bei CSV/ZIP, weil derselbe Plan
        // durch denselben Bau läuft.
        val second = insertCompetition(
            eventId, "2",
            matchTeamCounts = listOf(2),
            startOffsetsMinutes = listOf(null),
        )
        val planWithNull = !CompetitionExecutionService.eventStartlistPlan(eventId, allRounds = false, skipByes = true)
        assertKIOFails(
            CompetitionExecutionError.StartlistMatchesWithoutStartTime(
                listOf(
                    CompetitionExecutionError.StartlistMatchWithoutStartTime(
                        matchId = second.matches[0].setupMatchId,
                        competitionIdentifier = "2",
                        competitionShortName = null,
                        competitionName = "Wettkampf 2",
                        roundName = "Vorlauf",
                        matchName = "Lauf 1",
                    )
                )
            ),
        ) {
            CompetitionExecutionService.buildEventStartlists(planWithNull, EventStartlistFileType.PDF)
        }
    }

    /**
     * Der Schalter selbst: Einschalten nimmt den automatischen ersten Platz zurück (sonst wäre
     * der Lauf für Kette und Automatik schon "durch", bevor er gefahren ist), Ausschalten vergibt
     * ihn wieder. Ein gemessenes Ergebnis würde nie angefasst - siehe Service-KDoc.
     */
    @Test
    fun mustRaceToggleClearsAndRestoresTheAutomaticFirstPlace() = testComprehension {
        val configId = insertStartlistConfig()
        val eventId = insertEvent(configId)
        val competition = insertCompetition(
            eventId, "1",
            required = false,
            matchTeamCounts = listOf(0),
            startOffsetsMinutes = listOf(0),
        )
        val matchId = competition.matches[0].setupMatchId
        // Das Freilos-Boot mit automatischem ersten Platz, wie createNewRound es anlegt.
        insertTeam(eventId, competition.competitionId, matchId, startNumber = 1, place = 1)

        !CompetitionExecutionService.updateByeMustRace(
            eventId = eventId,
            competitionId = competition.competitionId,
            matchId = matchId,
            userId = SYSTEM_USER,
            request = UpdateMatchByeMustRaceRequest(mustRace = true),
        )
        val cleared = (!CompetitionMatchTeamRepo.getByMatch(matchId)).single()
        assertNull(cleared.place)

        !CompetitionExecutionService.updateByeMustRace(
            eventId = eventId,
            competitionId = competition.competitionId,
            matchId = matchId,
            userId = SYSTEM_USER,
            request = UpdateMatchByeMustRaceRequest(mustRace = false),
        )
        val restored = (!CompetitionMatchTeamRepo.getByMatch(matchId)).single()
        assertEquals(1, restored.place)
    }
}
