package de.lambda9.ready2race.backend.app.raceclocker

import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Die Migration V202608101120 gegen den Zustand, den der Backfill aus V202608101100 wirklich
 * hinterlassen hat: Altadressen wörtlich übernommen, also mit `www.` — während `normalizeUrl` neue
 * Eingaben auf den Apex faltet. Der Dubletten-Schutz vergleicht (normalisierte) Zeichenketten, und
 * der Abruf entdoppelt über die gespeicherte Adresse: Eine www-Zeile neben einer Apex-Zeile wäre
 * derselbe Feed zweimal je Takt.
 *
 * Der übliche Testcontainer aus [de.lambda9.ready2race.testing.testComprehension] hilft hier nicht:
 * Er migriert immer bis zum Ende, der Backfill über eine leere Datenbank erzeugt keine Zeilen. Der
 * Test baut deshalb einen eigenen Container, hält Flyway vor der neuen Migration an, legt den
 * Altzustand per Hand hinein und lässt dann den Rest laufen — nur so ist die Migrationslogik selbst
 * unter Beweis, nicht bloß ihr Sollzustand.
 */
class RaceClockerUrlNormalizationMigrationTest {

    private val eventId = UUID.randomUUID()
    private val competitionId = UUID.randomUUID()

    /** Die www-Zeile des Backfills — kleinste position, also die Überlebende des Duplikatpaars. */
    private val wwwTwinId = UUID.randomUUID()

    /** Dieselbe Adresse in Apex-Form, wie sie vor dem normalisierten Vergleich anlegbar war. */
    private val apexTwinId = UUID.randomUUID()

    /** Eine www-Zeile ohne Zwilling: wird nur normalisiert, nicht entdoppelt. */
    private val soloId = UUID.randomUUID()

    @Test
    fun `normalisiert Altadressen auf den Apex und haengt Zeiger von Duplikaten um`() {
        val postgres = PostgreSQLContainer("postgres:17")
        postgres.start()
        try {
            // Bis einschließlich V202608101110 migrieren — der Stand, auf dem die alten
            // Datenbanken vor der Normalisierung wirklich stehen.
            flyway(postgres).target("202608101110").load().migrate()

            connect(postgres).use { seedLegacyState(it) }

            // Jetzt der Rest, insbesondere V202608101120.
            flyway(postgres).load().migrate()

            connect(postgres).use { conn ->
                // Das Duplikatpaar ist auf EINE Zeile zusammengefallen — die mit der kleinsten
                // position — und trägt die Apex-Form.
                assertEquals(
                    listOf(wwwTwinId to "https://raceclocker.com/2a8c59a6"),
                    racesWithUrl(conn, "2a8c59a6"),
                )

                // Die Zeile ohne Zwilling bleibt bestehen und ist nur normalisiert.
                assertEquals(
                    listOf(soloId to "https://raceclocker.com/7c854955"),
                    racesWithUrl(conn, "7c854955"),
                )

                // Zeiger auf das gelöschte Duplikat sind auf die Überlebende umgehängt; Zeiger auf
                // unbeteiligte Rennen stehen unverändert.
                assertEquals(
                    soloId to wwwTwinId,
                    queryPair(
                        conn,
                        "select raceclocker_race_qualification, raceclocker_race_rounds " +
                            "from ready2race.event where id = ?",
                        eventId,
                    ),
                )
                assertEquals(
                    wwwTwinId,
                    querySingleUuid(
                        conn,
                        "select raceclocker_race_rounds from ready2race.competition where id = ?",
                        competitionId,
                    ),
                )

                // Und die harte Zusicherung, an der alles hängt: keine www-Form mehr im Bestand.
                conn.prepareStatement(
                    "select count(*) from ready2race.raceclocker_race where results_url like '%www.%'"
                ).use { stmt ->
                    stmt.executeQuery().use { rs ->
                        rs.next()
                        assertEquals(0, rs.getInt(1), "Nach der Migration darf keine www-Adresse mehr existieren")
                    }
                }
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
        conn.prepareStatement(
            "insert into ready2race.event (id, name, created_at, updated_at) values (?, 'Testregatta', now(), now())"
        ).use { stmt ->
            stmt.setObject(1, eventId)
            stmt.executeUpdate()
        }
        conn.prepareStatement(
            "insert into ready2race.competition (id, event, created_at, updated_at) values (?, ?, now(), now())"
        ).use { stmt ->
            stmt.setObject(1, competitionId)
            stmt.setObject(2, eventId)
            stmt.executeUpdate()
        }

        insertRace(conn, wwwTwinId, "Läufe", "https://www.raceclocker.com/2a8c59a6", position = 1)
        insertRace(conn, apexTwinId, "Kurzstrecke", "https://raceclocker.com/2a8c59a6", position = 2)
        insertRace(conn, soloId, "Zeitfahren", "https://www.raceclocker.com/7c854955", position = 3)

        // Die Anwahl zeigt auf das Duplikat, das die Migration löschen wird — genau der Fall, in
        // dem stures Löschen die Konfiguration einer laufenden Regatta entwerten würde.
        conn.prepareStatement(
            "update ready2race.event set raceclocker_race_qualification = ?, raceclocker_race_rounds = ? where id = ?"
        ).use { stmt ->
            stmt.setObject(1, soloId)
            stmt.setObject(2, apexTwinId)
            stmt.setObject(3, eventId)
            stmt.executeUpdate()
        }
        conn.prepareStatement(
            "update ready2race.competition set raceclocker_race_rounds = ? where id = ?"
        ).use { stmt ->
            stmt.setObject(1, apexTwinId)
            stmt.setObject(2, competitionId)
            stmt.executeUpdate()
        }
    }

    private fun insertRace(conn: Connection, id: UUID, name: String, url: String, position: Int) {
        conn.prepareStatement(
            "insert into ready2race.raceclocker_race " +
                "(id, event, name, results_url, start_mode, captures_laps, position, created_at, updated_at) " +
                "values (?, ?, ?, ?, 'WAVE', false, ?, now(), now())"
        ).use { stmt ->
            stmt.setObject(1, id)
            stmt.setObject(2, eventId)
            stmt.setString(3, name)
            stmt.setString(4, url)
            stmt.setInt(5, position)
            stmt.executeUpdate()
        }
    }

    private fun racesWithUrl(conn: Connection, code: String): List<Pair<UUID, String>> =
        conn.prepareStatement(
            "select id, results_url from ready2race.raceclocker_race where results_url like ? order by position"
        ).use { stmt ->
            stmt.setString(1, "%$code%")
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(rs.getObject(1, UUID::class.java) to rs.getString(2))
                    }
                }
            }
        }

    private fun queryPair(conn: Connection, sql: String, id: UUID): Pair<UUID?, UUID?> =
        conn.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, id)
            stmt.executeQuery().use { rs ->
                rs.next()
                rs.getObject(1, UUID::class.java) to rs.getObject(2, UUID::class.java)
            }
        }

    private fun querySingleUuid(conn: Connection, sql: String, id: UUID): UUID? =
        conn.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, id)
            stmt.executeQuery().use { rs ->
                rs.next()
                rs.getObject(1, UUID::class.java)
            }
        }
}
