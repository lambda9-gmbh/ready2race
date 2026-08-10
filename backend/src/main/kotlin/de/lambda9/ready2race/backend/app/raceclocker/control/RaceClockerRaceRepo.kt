package de.lambda9.ready2race.backend.app.raceclocker.control

import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerRaceDto
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerStartMode
import de.lambda9.ready2race.backend.database.generated.tables.references.RACECLOCKER_RACE
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.UUID

object RaceClockerRaceRepo {

    fun getForEvent(eventId: UUID) = Jooq.query {
        select(
            RACECLOCKER_RACE.ID,
            RACECLOCKER_RACE.NAME,
            RACECLOCKER_RACE.RESULTS_URL,
            RACECLOCKER_RACE.START_MODE,
            RACECLOCKER_RACE.CAPTURES_LAPS,
            RACECLOCKER_RACE.POSITION,
        )
            .from(RACECLOCKER_RACE)
            .where(RACECLOCKER_RACE.EVENT.eq(eventId))
            .orderBy(RACECLOCKER_RACE.POSITION, RACECLOCKER_RACE.NAME)
            .fetch {
                RaceClockerRaceDto(
                    // Im Schema not null; die Projektion verliert nur die Garantie.
                    id = it[RACECLOCKER_RACE.ID]!!,
                    name = it[RACECLOCKER_RACE.NAME]!!,
                    resultsUrl = it[RACECLOCKER_RACE.RESULTS_URL]!!,
                    startMode = RaceClockerStartMode.valueOf(it[RACECLOCKER_RACE.START_MODE]!!),
                    capturesLaps = it[RACECLOCKER_RACE.CAPTURES_LAPS]!!,
                    position = it[RACECLOCKER_RACE.POSITION]!!,
                )
            }
    }

    /** Ein neues Rennen landet hinten. Reihenfolge ändern ist ein eigener Vorgang, kein Nebeneffekt. */
    fun nextPosition(eventId: UUID) = Jooq.query {
        (select(DSL.max(RACECLOCKER_RACE.POSITION))
            .from(RACECLOCKER_RACE)
            .where(RACECLOCKER_RACE.EVENT.eq(eventId))
            .fetchOne()
            ?.value1() ?: 0) + 1
    }

    /**
     * Ob dieses Rennen zu dieser Veranstaltung gehört.
     *
     * Der Fremdschlüssel allein verhindert nicht, dass ein Wettkampf ein Rennen einer ANDEREN
     * Veranstaltung anwählt — dafür bräuchte es einen zusammengesetzten Schlüssel, der den übrigen
     * Tabellen dieses Projekts fremd wäre. Also prüft der Service, und das ist seine Frage.
     */
    fun belongsToEvent(raceId: UUID, eventId: UUID) = Jooq.query {
        fetchExists(
            selectOne()
                .from(RACECLOCKER_RACE)
                .where(RACECLOCKER_RACE.ID.eq(raceId))
                .and(RACECLOCKER_RACE.EVENT.eq(eventId))
        )
    }
}
