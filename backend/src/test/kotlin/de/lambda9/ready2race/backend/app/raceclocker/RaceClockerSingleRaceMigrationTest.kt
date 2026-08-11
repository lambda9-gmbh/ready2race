package de.lambda9.ready2race.backend.app.raceclocker

import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Die Migration V202608111500 gegen echte Altdaten: Sie faltet die Spaltenpaare
 * (Zeitfahren-/Läufe-Rennen, Qualifikations-/Runden-Preset) auf je EINE Spalte. Die
 * Zusammenführungs-Regel — coalesce(rounds, qualification), das Läufe-Rennen gewinnt, das
 * Zeitfahren-Rennen ist nur der Rückfall — ist eine Entscheidung von Thomas und genau das, was
 * hier festgenagelt wird: Ein Fehler darin verlöre bei einer laufenden Regatta still die
 * Zuordnung, und einen Rollback ohne Dump gibt es nicht.
 *
 * Wie [RaceClockerUrlNormalizationMigrationTest] mit eigenem Container: Der übliche Testcontainer
 * migriert immer bis zum Ende, und über einer leeren Datenbank gäbe es nichts zu koaleszieren.
 */
class RaceClockerSingleRaceMigrationTest {

    private val eventId = UUID.randomUUID()

    /** Wettkampf mit BEIDEN Rennen: das Läufe-Rennen muss gewinnen. */
    private val bothRacesCompetitionId = UUID.randomUUID()

    /** Wettkampf mit nur einem Zeitfahren-Rennen: es bleibt als Rückfall erhalten. */
    private val qualiOnlyCompetitionId = UUID.randomUUID()

    private val timeTrialRaceId = UUID.randomUUID()
    private val heatsRaceId = UUID.randomUUID()

    private val qualiConfigId = UUID.randomUUID()
    private val roundsConfigId = UUID.randomUUID()

    @Test
    fun `faltet die Spaltenpaare per coalesce - das Laeufe-Rennen gewinnt, Qualifikation ist der Rueckfall`() {
        val postgres = PostgreSQLContainer("postgres:17")
        postgres.start()
        try {
            // Bis einschließlich V202608111200 migrieren — der letzte Stand, auf dem die alten
            // Spaltenpaare existieren. Ohne afterMigrate: Das Skript baut die Views des ENDSTANDS;
            // der zweite, vollständige Lauf unten zieht sie regulär hoch.
            flyway(postgres).target("202608111200").skipDefaultCallbacks(true).load().migrate()

            connect(postgres).use { seedLegacyState(it) }

            // Jetzt der Rest, insbesondere V202608111500.
            flyway(postgres).load().migrate()

            connect(postgres).use { conn ->
                // Beide Rennen angewählt → das Läufe-Rennen gewinnt.
                assertEquals(
                    heatsRaceId,
                    queryUuid(conn, "select raceclocker_race from ready2race.competition where id = ?", bothRacesCompetitionId),
                )
                // Nur das Zeitfahren-Rennen angewählt → es bleibt als Rückfall erhalten, der
                // Wettkampf steht nicht plötzlich ohne Rennen da.
                assertEquals(
                    timeTrialRaceId,
                    queryUuid(conn, "select raceclocker_race from ready2race.competition where id = ?", qualiOnlyCompetitionId),
                )

                // Die Startlisten-Presets folgen derselben Regel — auf beiden Ebenen.
                assertEquals(
                    roundsConfigId,
                    queryUuid(conn, "select startlist_config from ready2race.event where id = ?", eventId),
                )
                assertEquals(
                    roundsConfigId,
                    queryUuid(conn, "select startlist_config from ready2race.competition where id = ?", bothRacesCompetitionId),
                )
                assertEquals(
                    qualiConfigId,
                    queryUuid(conn, "select startlist_config from ready2race.competition where id = ?", qualiOnlyCompetitionId),
                )

                // Und die Startart ist wirklich weg — mitsamt ihrem Check-Constraint.
                assertFalse(columnExists(conn, "raceclocker_race", "start_mode"))
                assertFalse(columnExists(conn, "event", "raceclocker_race_qualification"))
                assertFalse(columnExists(conn, "competition", "raceclocker_race_rounds"))
                assertFalse(columnExists(conn, "event", "startlist_config_qualification"))
                assertFalse(columnExists(conn, "competition", "startlist_config_rounds"))
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
        // Die Presets zuerst - Veranstaltung und Wettkämpfe zeigen per Fremdschlüssel darauf.
        insertStartlistConfig(conn, qualiConfigId, "Zeitfahren-Preset")
        insertStartlistConfig(conn, roundsConfigId, "Läufe-Preset")

        conn.prepareStatement(
            "insert into ready2race.event (id, name, created_at, updated_at, " +
                "startlist_config_qualification, startlist_config_rounds) " +
                "values (?, 'Testregatta', now(), now(), ?, ?)"
        ).use { stmt ->
            stmt.setObject(1, eventId)
            stmt.setObject(2, qualiConfigId)
            stmt.setObject(3, roundsConfigId)
            stmt.executeUpdate()
        }
        insertRace(conn, timeTrialRaceId, "Zeitfahren", "https://raceclocker.com/tt", "INDIVIDUAL", 1)
        insertRace(conn, heatsRaceId, "Kurzstrecke", "https://raceclocker.com/kurz", "WAVE", 2)

        insertCompetition(
            conn,
            bothRacesCompetitionId,
            raceQualification = timeTrialRaceId,
            raceRounds = heatsRaceId,
            startlistQualification = qualiConfigId,
            startlistRounds = roundsConfigId,
        )
        insertCompetition(
            conn,
            qualiOnlyCompetitionId,
            raceQualification = timeTrialRaceId,
            raceRounds = null,
            startlistQualification = qualiConfigId,
            startlistRounds = null,
        )
    }

    private fun insertCompetition(
        conn: Connection,
        id: UUID,
        raceQualification: UUID?,
        raceRounds: UUID?,
        startlistQualification: UUID?,
        startlistRounds: UUID?,
    ) {
        conn.prepareStatement(
            "insert into ready2race.competition (id, event, created_at, updated_at, " +
                "raceclocker_race_qualification, raceclocker_race_rounds, " +
                "startlist_config_qualification, startlist_config_rounds) " +
                "values (?, ?, now(), now(), ?, ?, ?, ?)"
        ).use { stmt ->
            stmt.setObject(1, id)
            stmt.setObject(2, eventId)
            stmt.setObject(3, raceQualification)
            stmt.setObject(4, raceRounds)
            stmt.setObject(5, startlistQualification)
            stmt.setObject(6, startlistRounds)
            stmt.executeUpdate()
        }
    }

    private fun insertStartlistConfig(conn: Connection, id: UUID, name: String) {
        conn.prepareStatement(
            "insert into ready2race.startlist_export_config (id, name, created_at, updated_at) " +
                "values (?, ?, now(), now())"
        ).use { stmt ->
            stmt.setObject(1, id)
            stmt.setString(2, name)
            stmt.executeUpdate()
        }
    }

    private fun insertRace(conn: Connection, id: UUID, name: String, url: String, startMode: String, position: Int) {
        conn.prepareStatement(
            "insert into ready2race.raceclocker_race " +
                "(id, event, name, results_url, start_mode, captures_laps, position, created_at, updated_at) " +
                "values (?, ?, ?, ?, ?, false, ?, now(), now())"
        ).use { stmt ->
            stmt.setObject(1, id)
            stmt.setObject(2, eventId)
            stmt.setString(3, name)
            stmt.setString(4, url)
            stmt.setString(5, startMode)
            stmt.setInt(6, position)
            stmt.executeUpdate()
        }
    }

    private fun queryUuid(conn: Connection, sql: String, id: UUID): UUID? =
        conn.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, id)
            stmt.executeQuery().use { rs ->
                rs.next()
                rs.getObject(1, UUID::class.java)
            }
        }

    private fun columnExists(conn: Connection, table: String, column: String): Boolean =
        conn.prepareStatement(
            "select exists (select 1 from information_schema.columns " +
                "where table_schema = 'ready2race' and table_name = ? and column_name = ?)"
        ).use { stmt ->
            stmt.setString(1, table)
            stmt.setString(2, column)
            stmt.executeQuery().use { rs ->
                rs.next()
                rs.getBoolean(1)
            }
        }
}
