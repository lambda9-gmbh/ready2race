package de.lambda9.ready2race.backend.app.raceclocker

import de.lambda9.ready2race.backend.app.raceclocker.boundary.RaceClockerRaceService
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerRaceError
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceclockerRaceRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.RACECLOCKER_RACE
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.update
import de.lambda9.ready2race.testing.testComprehension
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.recover
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Die Löschsperre für RaceClocker-Rennen gegen ein echtes Postgres.
 *
 * Warum diese Ebene: Der Fremdschlüssel `competition.raceclocker_race` steht auf ON DELETE
 * SET NULL — ohne die Sperre im Service würde das Löschen einen noch zugewiesenen Wettkampf
 * stillschweigend von seiner Zeitnahme trennen, und der Abruf bliebe kommentarlos stehen.
 * Genau dieses Zusammenspiel aus Constraint und Service-Prüfung sieht nur die Datenbank.
 */
class RaceClockerRaceDeleteTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 13, 12, 0)

    @Test
    fun `blockt solange ein Wettkampf zugewiesen ist und loescht nach dem Abhaken`() =
        testComprehension {
            val eventId = UUID.randomUUID()
            val raceId = UUID.randomUUID()
            val competitionId = UUID.randomUUID()

            !EVENT.insert(EventRecord(id = eventId, name = "Testregatta", createdAt = now, updatedAt = now))
            !RACECLOCKER_RACE.insert(
                RaceclockerRaceRecord(
                    id = raceId,
                    event = eventId,
                    name = "Timetrails",
                    resultsUrl = "https://raceclocker.com/track/testrace",
                    capturesLaps = false,
                    position = 1,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            !COMPETITION.insert(
                CompetitionRecord(
                    id = competitionId,
                    event = eventId,
                    raceclockerRace = raceId,
                    createdAt = now,
                    updatedAt = now,
                )
            )

            // Noch zugewiesen: der Service muss blocken, nicht stillschweigend abhängen.
            val blocked = !RaceClockerRaceService.deleteRace(eventId, raceId)
                .map { null as RaceClockerRaceError? }
                .recover { error -> KIO.ok(error as? RaceClockerRaceError) }
            assertEquals(RaceClockerRaceError.StillAssigned, blocked)

            // Zuordnung abhaken - danach geht das Löschen durch.
            !COMPETITION.update({ raceclockerRace = null }) { ID.eq(competitionId) }
            val deleted = !RaceClockerRaceService.deleteRace(eventId, raceId)
                .map { true }
                .recover { KIO.ok(false) }
            assertNotNull(deleted)
            assertEquals(true, deleted)

            // Und das Rennen ist wirklich weg.
            val remaining = !de.lambda9.tailwind.jooq.Jooq.query {
                selectFrom(RACECLOCKER_RACE).where(RACECLOCKER_RACE.ID.eq(raceId)).fetchOne()
            }
            assertNull(remaining)
        }
}
