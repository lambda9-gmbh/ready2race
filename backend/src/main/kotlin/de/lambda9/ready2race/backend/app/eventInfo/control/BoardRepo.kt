package de.lambda9.ready2race.backend.app.eventInfo.control

import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.records.BoardRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.BOARD
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object BoardRepo {

    /**
     * Aufsteigend nach Anlagedatum: eine stabile Reihenfolge, deren erstes Element die
     * Umleitung der alten Athleten-Board-URL trägt.
     */
    fun findByEvent(eventId: UUID): JIO<List<BoardRecord>> =
        Jooq.query {
            selectFrom(BOARD)
                .where(BOARD.EVENT_ID.eq(eventId))
                .orderBy(BOARD.CREATED_AT.asc(), BOARD.ID.asc())
                .fetch()
        }

    fun findById(id: UUID) = BOARD.selectOne { ID.eq(id) }

    fun create(record: BoardRecord) = BOARD.insertReturning(record) { ID }

    fun update(id: UUID, f: BoardRecord.() -> Unit) = BOARD.update(f) { ID.eq(id) }

    fun delete(id: UUID) = BOARD.delete { ID.eq(id) }

    fun getAsJson(eventId: UUID) = BOARD.selectAsJson { EVENT_ID.eq(eventId) }

    fun insertJsonData(data: String) = BOARD.insertJsonData(data)
}
