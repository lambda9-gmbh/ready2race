package de.lambda9.ready2race.backend.app.raceclocker

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerRaceRepo
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerStartMode
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceclockerRaceRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.RACECLOCKER_RACE
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Die Rennen einer Veranstaltung gegen eine echte Datenbank. Reine Funktionen decken die
 * Anwahl-Logik ab; was hier geprüft wird, ist alles, was nur Postgres beantworten kann —
 * Reihenfolge, Abgrenzung zwischen Veranstaltungen, und dass ein gelöschtes Rennen die Anwahl
 * entwertet statt das Löschen zu blockieren.
 */
class RaceClockerRaceRepoTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 14, 10, 0)

    private fun TestComprehensionScope<JEnv>.seedEvent(): UUID {
        val eventId = UUID.randomUUID()
        !EVENT.insert(
            EventRecord(id = eventId, name = "Testregatta", createdAt = now, updatedAt = now)
        )
        return eventId
    }

    private fun TestComprehensionScope<JEnv>.seedRace(
        eventId: UUID,
        name: String,
        url: String,
        startMode: RaceClockerStartMode = RaceClockerStartMode.WAVE,
        position: Int = 1,
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

    @Test
    fun `liefert die Rennen einer Veranstaltung nach Position sortiert`() = testComprehension {
        val eventId = seedEvent()
        seedRace(eventId, "Kurzstrecke", "https://www.raceclocker.com/kurz", position = 2)
        seedRace(eventId, "Timetrials", "https://www.raceclocker.com/tt", RaceClockerStartMode.INDIVIDUAL, position = 1)

        val races = !RaceClockerRaceRepo.getForEvent(eventId)

        assertEquals(listOf("Timetrials", "Kurzstrecke"), races.map { it.name })
        assertEquals(RaceClockerStartMode.INDIVIDUAL, races.first().startMode)
    }

    @Test
    fun `liefert keine Rennen einer fremden Veranstaltung`() = testComprehension {
        val eventId = seedEvent()
        val otherEventId = seedEvent()
        seedRace(otherEventId, "Kurzstrecke", "https://www.raceclocker.com/kurz")

        assertEquals(emptyList(), !RaceClockerRaceRepo.getForEvent(eventId))
    }

    @Test
    fun `nextPosition zählt hinter dem letzten Rennen weiter`() = testComprehension {
        val eventId = seedEvent()
        assertEquals(1, !RaceClockerRaceRepo.nextPosition(eventId))

        seedRace(eventId, "Kurzstrecke", "https://www.raceclocker.com/kurz", position = 7)
        assertEquals(8, !RaceClockerRaceRepo.nextPosition(eventId))
    }

    @Test
    fun `belongsToEvent trennt die Veranstaltungen`() = testComprehension {
        val eventId = seedEvent()
        val otherEventId = seedEvent()
        val raceId = seedRace(eventId, "Kurzstrecke", "https://www.raceclocker.com/kurz")

        assertEquals(true, !RaceClockerRaceRepo.belongsToEvent(raceId, eventId))
        assertEquals(false, !RaceClockerRaceRepo.belongsToEvent(raceId, otherEventId))
    }
}
