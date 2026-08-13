package de.lambda9.ready2race.backend.app.eventExportBundle.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.EventExportBundleItemRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_EXPORT_BUNDLE_ITEM
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.selectOne
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.time.LocalDateTime
import java.util.UUID

object EventExportBundleItemRepo {

    /** Immer in Mappen-Reihenfolge - die Position ist Teil der Bedeutung, nicht der Darstellung. */
    fun allForEvent(eventId: UUID): JIO<List<EventExportBundleItemRecord>> = Jooq.query {
        selectFrom(EVENT_EXPORT_BUNDLE_ITEM)
            .where(EVENT_EXPORT_BUNDLE_ITEM.EVENT.eq(eventId))
            .orderBy(EVENT_EXPORT_BUNDLE_ITEM.POSITION, EVENT_EXPORT_BUNDLE_ITEM.ID)
            .fetch()
    }

    fun get(id: UUID): JIO<EventExportBundleItemRecord?> =
        EVENT_EXPORT_BUNDLE_ITEM.selectOne { ID.eq(id) }

    fun create(record: EventExportBundleItemRecord): JIO<UUID> =
        EVENT_EXPORT_BUNDLE_ITEM.insertReturning(record) { ID }

    fun delete(id: UUID): JIO<Int> = EVENT_EXPORT_BUNDLE_ITEM.delete { ID.eq(id) }

    fun nextPosition(eventId: UUID): JIO<Int> = Jooq.query {
        val highest = select(DSL.max(EVENT_EXPORT_BUNDLE_ITEM.POSITION))
            .from(EVENT_EXPORT_BUNDLE_ITEM)
            .where(EVENT_EXPORT_BUNDLE_ITEM.EVENT.eq(eventId))
            .fetchOne()
            ?.value1()

        (highest ?: 0) + 10
    }

    /**
     * Schreibt die Reihenfolge in einem Rutsch (Zehnerschritte) - dasselbe Muster wie
     * ClubNameRuleRepo.writeOrder: Der Aufrufer schickt ALLE Einträge, sonst gäbe es zwischendurch
     * eine Reihenfolge, die es nie geben sollte.
     */
    fun writeOrder(itemIds: List<UUID>, userId: UUID, now: LocalDateTime): JIO<Unit> = Jooq.query {
        itemIds.forEachIndexed { index, itemId ->
            update(EVENT_EXPORT_BUNDLE_ITEM)
                .set(EVENT_EXPORT_BUNDLE_ITEM.POSITION, (index + 1) * 10)
                .set(EVENT_EXPORT_BUNDLE_ITEM.UPDATED_AT, now)
                .set(EVENT_EXPORT_BUNDLE_ITEM.UPDATED_BY, userId)
                .where(EVENT_EXPORT_BUNDLE_ITEM.ID.eq(itemId))
                .execute()
        }
    }
}
