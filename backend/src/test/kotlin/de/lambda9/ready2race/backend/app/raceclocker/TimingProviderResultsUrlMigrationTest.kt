package de.lambda9.ready2race.backend.app.raceclocker

import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Die Migration V202608211200 gegen echte Altdaten: Sie hebt die Nennung der Zeitnahme von der
 * Startseite des Anbieters auf die Ergebnisseite des Rennens, aus dem die Zeiten kamen.
 *
 * Der Bestand ist der eigentliche Punkt. V202608201200 hat für die schon gefahrene Regatta
 * `https://raceclocker.com` eingetragen; von dort aus müsste der Leser die Regatta selbst suchen.
 * Zugleich darf die Migration nicht zu weit greifen: Eine von Hand gepflegte Adresse aus einer
 * Import-Konfiguration bleibt stehen, auch wenn sie „RaceClocker" heißt, und ein Wettkampf ohne
 * angewähltes Rennen behält seinen Rückfall, statt ohne Verweis dazustehen.
 *
 * Wie [RaceClockerSingleRaceMigrationTest] mit eigenem Container: Der übliche Testcontainer
 * migriert immer bis zum Ende, und über einer leeren Datenbank gäbe es nichts nachzuziehen.
 */
class TimingProviderResultsUrlMigrationTest {

    private val eventId = UUID.randomUUID()
    private val raceId = UUID.randomUUID()

    private val raceResultsUrl = "https://raceclocker.com/0a77d582"
    private val providerHome = "https://raceclocker.com"
    private val ownUrl = "https://zeitnahme.example/regatta"

    /** Aus dem Abruf, mit angewähltem Rennen - die Nennung zeigt danach auf dessen Ergebnisseite. */
    private val pulledMatchId = UUID.randomUUID()

    /** Aus einer Import-Konfiguration mit eigener Adresse - sie bleibt unangetastet. */
    private val importedMatchId = UUID.randomUUID()

    /** Ohne angewähltes Rennen - es gibt keine Seite, der Rückfall bleibt. */
    private val raceLessMatchId = UUID.randomUUID()

    @Test
    fun `hebt die Nennung auf die Ergebnisseite des Rennens und laesst gepflegte Adressen stehen`() {
        val postgres = PostgreSQLContainer("postgres:17")
        postgres.start()
        try {
            // Bis einschließlich V202608201200 migrieren - der Stand, auf dem die Nennung existiert
            // und noch auf die Startseite zeigt. Ohne afterMigrate: Das Skript baut die Views des
            // ENDSTANDS; der zweite, vollständige Lauf unten zieht sie regulär hoch.
            flyway(postgres).target("202608201200").skipDefaultCallbacks(true).load().migrate()

            connect(postgres).use { seedLegacyState(it) }

            // Jetzt der Rest, insbesondere V202608211200.
            flyway(postgres).load().migrate()

            connect(postgres).use { conn ->
                assertEquals(raceResultsUrl, timingProviderUrl(conn, pulledMatchId))
                assertEquals(ownUrl, timingProviderUrl(conn, importedMatchId))
                assertEquals(providerHome, timingProviderUrl(conn, raceLessMatchId))

                // Der Name bleibt in allen Fällen, was er war - die Migration fasst nur die Adresse an.
                assertEquals("RaceClocker", timingProviderName(conn, pulledMatchId))
                assertEquals("RaceClocker", timingProviderName(conn, importedMatchId))
            }
        } finally {
            postgres.stop()
        }
    }

    private fun flyway(postgres: PostgreSQLContainer<*>) = Flyway.configure()
        .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        .defaultSchema("ready2race")

    private fun connect(postgres: PostgreSQLContainer<*>): Connection =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)

    private fun seedLegacyState(conn: Connection) {
        insertEvent(conn)
        insertRace(conn)

        val roundWithRace = insertCompetitionWithSetup(conn, race = raceId)
        val roundWithoutRace = insertCompetitionWithSetup(conn, race = null)

        insertMatch(conn, pulledMatchId, roundWithRace, weighting = 1, provider = "RaceClocker", url = providerHome)
        insertMatch(conn, importedMatchId, roundWithRace, weighting = 2, provider = "RaceClocker", url = ownUrl)
        insertMatch(conn, raceLessMatchId, roundWithoutRace, weighting = 1, provider = "RaceClocker", url = providerHome)
    }

    private fun insertEvent(conn: Connection) {
        conn.prepareStatement(
            "insert into ready2race.event (id, name, created_at, updated_at) values (?, 'Testregatta', now(), now())"
        ).use { stmt ->
            stmt.setObject(1, eventId)
            stmt.executeUpdate()
        }
    }

    private fun insertRace(conn: Connection) {
        conn.prepareStatement(
            "insert into ready2race.raceclocker_race (id, event, name, results_url, position, created_at, updated_at) " +
                "values (?, ?, 'Kurzstrecke', ?, 1, now(), now())"
        ).use { stmt ->
            stmt.setObject(1, raceId)
            stmt.setObject(2, eventId)
            stmt.setString(3, raceResultsUrl)
            stmt.executeUpdate()
        }
    }

    /** Wettkampf samt der Kette bis zur Runde, in der die Partien der Läufe hängen. */
    private fun insertCompetitionWithSetup(conn: Connection, race: UUID?): UUID {
        val competitionId = UUID.randomUUID()
        val propertiesId = UUID.randomUUID()
        val roundId = UUID.randomUUID()

        conn.prepareStatement(
            "insert into ready2race.competition (id, event, raceclocker_race, created_at, updated_at) " +
                "values (?, ?, ?, now(), now())"
        ).use { stmt ->
            stmt.setObject(1, competitionId)
            stmt.setObject(2, eventId)
            stmt.setObject(3, race)
            stmt.executeUpdate()
        }

        conn.prepareStatement(
            "insert into ready2race.competition_properties (id, competition, identifier, name) values (?, ?, '1', 'Testwettkampf')"
        ).use { stmt ->
            stmt.setObject(1, propertiesId)
            stmt.setObject(2, competitionId)
            stmt.executeUpdate()
        }

        conn.prepareStatement(
            "insert into ready2race.competition_setup (competition_properties, created_at, updated_at) values (?, now(), now())"
        ).use { stmt ->
            stmt.setObject(1, propertiesId)
            stmt.executeUpdate()
        }

        conn.prepareStatement(
            "insert into ready2race.competition_setup_round " +
                "(id, competition_setup, name, required, use_default_seeding, places_option) " +
                "values (?, ?, 'Hauptrunde', true, true, 'ASCENDING')"
        ).use { stmt ->
            stmt.setObject(1, roundId)
            stmt.setObject(2, propertiesId)
            stmt.executeUpdate()
        }

        return roundId
    }

    /**
     * Die Partie IST der Schlüssel des Laufs (`competition_match.competition_setup_match`), deshalb
     * legt jeder Lauf hier seine eigene Partie an.
     */
    private fun insertMatch(conn: Connection, matchId: UUID, roundId: UUID, weighting: Int, provider: String, url: String) {
        conn.prepareStatement(
            "insert into ready2race.competition_setup_match (id, competition_setup_round, weighting, execution_order) " +
                "values (?, ?, ?, ?)"
        ).use { stmt ->
            stmt.setObject(1, matchId)
            stmt.setObject(2, roundId)
            stmt.setInt(3, weighting)
            stmt.setInt(4, weighting)
            stmt.executeUpdate()
        }

        conn.prepareStatement(
            "insert into ready2race.competition_match (competition_setup_match, timing_provider_name, timing_provider_url, created_at, updated_at) " +
                "values (?, ?, ?, now(), now())"
        ).use { stmt ->
            stmt.setObject(1, matchId)
            stmt.setString(2, provider)
            stmt.setString(3, url)
            stmt.executeUpdate()
        }
    }

    private fun timingProviderUrl(conn: Connection, matchId: UUID) =
        queryText(conn, "select timing_provider_url from ready2race.competition_match where competition_setup_match = ?", matchId)

    private fun timingProviderName(conn: Connection, matchId: UUID) =
        queryText(conn, "select timing_provider_name from ready2race.competition_match where competition_setup_match = ?", matchId)

    private fun queryText(conn: Connection, sql: String, id: UUID): String? =
        conn.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, id)
            stmt.executeQuery().use { rs ->
                rs.next()
                rs.getString(1)
            }
        }
}
