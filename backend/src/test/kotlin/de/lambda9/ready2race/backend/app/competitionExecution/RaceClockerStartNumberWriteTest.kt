package de.lambda9.ready2race.backend.app.competitionExecution

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchTeamRepo
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.CompetitionSetupService
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupPlacesOption
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerFeedRow
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerMatchTarget
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerRaceRef
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionDeregistrationRecord
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
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_DEREGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_ROUND
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_REGISTRATION
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Das Schreiben der Bahnen (`writeStartNumbers`) gegen eine echte Datenbank, über den Weg, den der
 * RaceClocker-Abruf nimmt: `applyRaceClockerRows` → `applyLanesFromFeed` → `writeStartNumbers`.
 *
 * Warum überhaupt gegen Postgres: Die Funktion existiert *nur* wegen einer Datenbankeigenschaft.
 * Über `competition_match_team (competition_match, start_number)` liegt ein eindeutiger Index
 * (`starting_position_unique_in_match`), und ein Bahnentausch belegt zwischendurch zwangsläufig eine
 * Nummer doppelt. Deshalb negiert die Funktion erst alle Nummern und vergibt sie danach neu. Ob
 * dieser Kniff trägt, kann keine reine Funktion zeigen — nur eine Datenbank, die den Index
 * durchsetzt. Genau dieser Pfad läuft an einem Regattatag bei jedem einzelnen Abruf.
 *
 * `RaceClockerFeedTest` prüft die Gegenseite: dass aus den Listenpositionen des Feeds die richtigen
 * Bahnen *errechnet* werden. Hier geht es darum, dass sie auch ankommen.
 */
class RaceClockerStartNumberWriteTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 14, 10, 0)

    /** Ein Lauf ohne Wellenbezeichnung — die Zuordnung läuft über die IDs, nicht über die Welle. */
    private val target = RaceClockerMatchTarget(
        waveName = null,
        race = RaceClockerRaceRef(UUID.randomUUID(), "Kurzstrecke", "https://www.raceclocker.com/heats"),
    )

    private data class SeededTeam(
        val matchTeamId: UUID,
        val registrationId: UUID,
        val club: String,
    )

    private data class SeededMatch(
        val competitionId: UUID,
        val matchId: UUID,
        /** Mannschaften in der Reihenfolge der Startnummern 1..n, die sie beim Seeden bekommen haben. */
        val teams: List<SeededTeam>,
    ) {
        fun team(club: String): SeededTeam = teams.single { it.club == club }
    }

    /**
     * Eine Veranstaltung mit einem Wettkampf, einer Runde, einem Lauf und je einer Mannschaft pro
     * Eintrag in [clubs] — Startnummern 1..n in genau dieser Reihenfolge.
     *
     * Wer in [deregistered] steht, ist abgemeldet: solche Mannschaften bleiben am Lauf hängen, sind
     * aber im Feed nicht mehr enthalten. Sie sind der Grund, warum `writeStartNumbers` überhaupt
     * Ersatznummern vergeben muss.
     */
    private fun TestComprehensionScope<JEnv>.seedMatch(
        clubs: List<String>,
        deregistered: Set<String> = emptySet(),
    ): SeededMatch {
        val eventId = UUID.randomUUID()
        val competitionId = UUID.randomUUID()
        val propertiesId = UUID.randomUUID()
        val roundId = UUID.randomUUID()
        val matchId = UUID.randomUUID()
        val eventRegistrationId = UUID.randomUUID()
        val clubId = UUID.randomUUID()

        !EVENT.insert(
            EventRecord(
                id = eventId,
                name = "Testregatta",
                createdAt = now,
                updatedAt = now,
            )
        )
        !COMPETITION.insert(
            CompetitionRecord(
                id = competitionId,
                event = eventId,
                createdAt = now,
                updatedAt = now,
            )
        )
        !COMPETITION_PROPERTIES.insert(
            CompetitionPropertiesRecord(
                id = propertiesId,
                competition = competitionId,
                identifier = "1",
                name = "Coastal Quad",
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
                name = "Vorlauf",
                required = true,
                useDefaultSeeding = true,
                placesOption = CompetitionSetupPlacesOption.EQUAL.name,
            )
        )
        !COMPETITION_SETUP_MATCH.insert(
            CompetitionSetupMatchRecord(
                id = matchId,
                competitionSetupRound = roundId,
                weighting = 1,
                name = "Lauf 1",
                executionOrder = 1,
                teams = clubs.size,
            )
        )
        !COMPETITION_MATCH.insert(
            CompetitionMatchRecord(
                competitionSetupMatch = matchId,
                startTime = now,
                createdAt = now,
                updatedAt = now,
                activatedAt = now,
            )
        )

        !CLUB.insert(ClubRecord(id = clubId, name = "Meldender Verein", createdAt = now, updatedAt = now))
        !EVENT_REGISTRATION.insert(
            EventRegistrationRecord(
                id = eventRegistrationId,
                event = eventId,
                club = clubId,
                createdAt = now,
                updatedAt = now,
            )
        )

        val teams = clubs.mapIndexed { index, club ->
            val registrationId = UUID.randomUUID()
            val matchTeamId = UUID.randomUUID()

            !COMPETITION_REGISTRATION.insert(
                CompetitionRegistrationRecord(
                    id = registrationId,
                    eventRegistration = eventRegistrationId,
                    competition = competitionId,
                    club = clubId,
                    name = club,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            !COMPETITION_MATCH_TEAM.insert(
                CompetitionMatchTeamRecord(
                    id = matchTeamId,
                    competitionMatch = matchId,
                    competitionRegistration = registrationId,
                    startNumber = index + 1,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            if (club in deregistered) {
                !COMPETITION_DEREGISTRATION.insert(
                    CompetitionDeregistrationRecord(
                        competitionRegistration = registrationId,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }

            SeededTeam(matchTeamId = matchTeamId, registrationId = registrationId, club = club)
        }

        return SeededMatch(competitionId = competitionId, matchId = matchId, teams = teams)
    }

    /**
     * Eine Feed-Zeile, wie RaceClocker sie liefert. [rank] ist die Listenposition im Rennen — daraus
     * entstehen die Bahnen; die Mannschaft hängt an der Match-Team-ID in "Extra info".
     */
    private fun row(
        team: SeededTeam,
        rank: Int,
        result: String? = "00:20:00.0",
    ) = RaceClockerFeedRow(
        name = team.club,
        rank = rank,
        bib = null,
        wave = null,
        ids = listOf(team.matchTeamId),
        result = result,
        start = null,
        penaltySeconds = null,
        penaltyNote = null,
    )

    /** Die Startnummern des Laufs, nach Verein — so wie sie am Ende in der Datenbank stehen. */
    private fun TestComprehensionScope<JEnv>.startNumbersByClub(seeded: SeededMatch): Map<String, Int> {
        val records = !CompetitionMatchTeamRepo.getByMatch(seeded.matchId).orDie()
        return records.associate { record ->
            seeded.teams.single { it.registrationId == record.competitionRegistration }.club to record.startNumber
        }
    }

    private fun TestComprehensionScope<JEnv>.pull(seeded: SeededMatch, rows: List<RaceClockerFeedRow>) {
        val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(seeded.competitionId)
        val match = !CompetitionExecutionService.checkUpdateMatchResult(setupRounds, seeded.matchId)

        !CompetitionExecutionService.applyRaceClockerRows(match, seeded.matchId, target, rows, SYSTEM_USER)
    }

    /**
     * Der Fall, um den es geht: Der Zeitnehmer schiebt vor Ort das letzte Boot an die erste Position,
     * alle anderen rutschen eine Bahn nach hinten. **Jede** Bahn wechselt dabei den Besitzer, und
     * jede Zielnummer ist im Ausgangszustand bereits vergeben — ohne das Negieren vorweg stünde hier
     * eine Verletzung von `starting_position_unique_in_match`, und der ganze Abruf liefe auf einen
     * Fehler statt auf eine Ergebnisübernahme.
     */
    @Test
    fun aCyclicLaneSwapIsWrittenWithoutViolatingTheUniqueIndex() = testComprehension {
        val seeded = seedMatch(listOf("Kiel", "Husum", "Sonderburg", "Flensburg"))

        // Flensburg (bisher Bahn 4) wurde in RaceClocker nach vorn gezogen.
        pull(
            seeded,
            listOf(
                row(seeded.team("Flensburg"), rank = 1),
                row(seeded.team("Kiel"), rank = 2),
                row(seeded.team("Husum"), rank = 3),
                row(seeded.team("Sonderburg"), rank = 4),
            )
        )

        assertEquals(
            mapOf("Flensburg" to 1, "Kiel" to 2, "Husum" to 3, "Sonderburg" to 4),
            startNumbersByClub(seeded),
        )
    }

    /**
     * Zwei Boote tauschen die Bahnen, der Rest bleibt stehen — der häufigere Fall am Steg. Auch hier
     * ist die Zielnummer jedes der beiden im Moment des Schreibens noch beim anderen.
     */
    @Test
    fun twoBoatsTradingLanesKeepEveryOtherLaneWhereItWas() = testComprehension {
        val seeded = seedMatch(listOf("Kiel", "Husum", "Sonderburg"))

        pull(
            seeded,
            listOf(
                row(seeded.team("Sonderburg"), rank = 1),
                row(seeded.team("Husum"), rank = 2),
                row(seeded.team("Kiel"), rank = 3),
            )
        )

        assertEquals(
            mapOf("Sonderburg" to 1, "Husum" to 2, "Kiel" to 3),
            startNumbersByClub(seeded),
        )
    }

    /**
     * Ein abgemeldetes Boot steht nicht mehr im Feed, hängt aber weiter am Lauf. Es darf keine der
     * importierten Bahnen belegen — sonst kollidierte es mit einem fahrenden Boot — und bekommt
     * deshalb eine Nummer oberhalb der höchsten vergebenen.
     */
    @Test
    fun aBoatMissingFromTheFeedIsNumberedAfterTheHighestImportedLane() = testComprehension {
        val seeded = seedMatch(
            clubs = listOf("Kiel", "Husum", "Sonderburg"),
            deregistered = setOf("Husum"),
        )

        // Husum fährt nicht mit; Sonderburg rückt auf Bahn 1 vor.
        pull(
            seeded,
            listOf(
                row(seeded.team("Sonderburg"), rank = 1),
                row(seeded.team("Kiel"), rank = 2),
            )
        )

        val startNumbers = startNumbersByClub(seeded)
        assertEquals(1, startNumbers["Sonderburg"])
        assertEquals(2, startNumbers["Kiel"])
        assertEquals(3, startNumbers["Husum"], "Das abgemeldete Boot muss hinter die importierten Bahnen rücken")
    }

    /**
     * Der Lauf steht am Start und ist noch nicht gefahren: RaceClocker meldet für jede Zeile
     * `Not started`. Genau in diesem Moment legt der Zeitnehmer die Bahnen fest, und genau dann will
     * der Schiedsrichter sie auf dem Board sehen — die Bahnen hängen an der Startliste, nicht an
     * Ergebnissen.
     *
     * Beobachtet am 09.08.2026 an der CRF-Testregatta: Ein Tausch in RaceClocker kam nicht an,
     * der Abruf meldete stattdessen still "keine Ergebnisse" und ließ die Bahnen stehen.
     */
    @Test
    fun lanesArriveBeforeAnyBoatHasAResult() = testComprehension {
        val seeded = seedMatch(listOf("Kiel", "Husum", "Sonderburg"))

        // Niemand ist gefahren - der Zeitnehmer hat nur die Reihenfolge geändert.
        pull(
            seeded,
            listOf(
                row(seeded.team("Sonderburg"), rank = 1, result = "Not started"),
                row(seeded.team("Husum"), rank = 2, result = "Not started"),
                row(seeded.team("Kiel"), rank = 3, result = "Not started"),
            )
        )

        assertEquals(
            mapOf("Sonderburg" to 1, "Husum" to 2, "Kiel" to 3),
            startNumbersByClub(seeded),
        )
    }

    /**
     * Ein Lauf wird abgerufen, während noch Boote unterwegs sind: RaceClocker meldet für sie den
     * Verlaufszustand `In race...` statt einer Zeit. Diese Zeilen tragen kein Ergebnis, ihre Bahn
     * aber sehr wohl — würden nur die gestoppten Boote nummeriert, schöbe der erste Abruf eines
     * laufenden Rennens alle noch fahrenden aus ihren Bahnen.
     */
    @Test
    fun lanesAreTakenFromRowsWithoutATimeAsWell() = testComprehension {
        val seeded = seedMatch(listOf("Kiel", "Husum", "Sonderburg"))

        pull(
            seeded,
            listOf(
                row(seeded.team("Sonderburg"), rank = 1, result = "In race..."),
                row(seeded.team("Husum"), rank = 2, result = "00:20:00.0"),
                row(seeded.team("Kiel"), rank = 3, result = "In race..."),
            )
        )

        assertEquals(
            mapOf("Sonderburg" to 1, "Husum" to 2, "Kiel" to 3),
            startNumbersByClub(seeded),
        )
    }
}
