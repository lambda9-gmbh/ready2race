package de.lambda9.ready2race.backend.app.eventInfo.control

import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.references.INFO_VIEW_CONFIGURATION
import de.lambda9.ready2race.backend.database.generated.tables.records.InfoViewConfigurationRecord
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object InfoViewConfigurationRepo {

    fun findByEvent(eventId: UUID, includeInactive: Boolean = false): JIO<List<InfoViewConfigurationRecord>> = Jooq.query {
        selectFrom(INFO_VIEW_CONFIGURATION)
            .where(INFO_VIEW_CONFIGURATION.EVENT_ID.eq(eventId))
            .apply {
                if (!includeInactive) {
                    and(INFO_VIEW_CONFIGURATION.IS_ACTIVE.eq(true))
                }
            }
            .orderBy(INFO_VIEW_CONFIGURATION.SORT_ORDER.asc())
            .fetch()
    }

    fun findById(id: UUID) = INFO_VIEW_CONFIGURATION.selectOne { ID.eq(id) }

    fun create(record: InfoViewConfigurationRecord) = INFO_VIEW_CONFIGURATION.insertReturning(record) {ID}

    fun update(id: UUID, f: InfoViewConfigurationRecord.() -> Unit) =
        INFO_VIEW_CONFIGURATION.update(f) { ID.eq(id) }

    fun delete(id: UUID) = INFO_VIEW_CONFIGURATION.delete { ID.eq(id) }

    fun exists(id: UUID) = INFO_VIEW_CONFIGURATION.exists { ID.eq(id) }
}