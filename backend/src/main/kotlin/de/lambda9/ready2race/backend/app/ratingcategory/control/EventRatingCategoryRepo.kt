package de.lambda9.ready2race.backend.app.ratingcategory.control

import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRatingCategoryRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_RATING_CATEGORY
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.time.LocalDateTime
import java.util.*

object EventRatingCategoryRepo {

    fun insert(records: List<EventRatingCategoryRecord>) = EVENT_RATING_CATEGORY.insert(records)

    fun getByEvent(eventId: UUID) = EVENT_RATING_CATEGORY.select { EVENT.eq(eventId) }

    /**
     * Die nächste freie Stelle in der Abschnittsreihenfolge der Veranstaltung. Eine neu
     * zugeordnete Kategorie hängt sich damit hinten an, statt sich zwischen bereits sortierte
     * Kategorien zu drängen.
     */
    fun nextSortOrder(eventId: UUID): JIO<Int> = Jooq.query {
        select(DSL.max(EVENT_RATING_CATEGORY.SORT_ORDER))
            .from(EVENT_RATING_CATEGORY)
            .where(EVENT_RATING_CATEGORY.EVENT.eq(eventId))
            .fetchOne()
            ?.value1()
            ?.plus(1)
            ?: 0
    }

    fun updateSortOrder(eventId: UUID, ratingCategoryId: UUID, position: Int, userId: UUID) =
        EVENT_RATING_CATEGORY.update({
            sortOrder = position
            updatedAt = LocalDateTime.now()
            updatedBy = userId
        }) { EVENT.eq(eventId).and(RATING_CATEGORY.eq(ratingCategoryId)) }

    fun delete(eventId: UUID, ratingCategoryId: UUID) =
        EVENT_RATING_CATEGORY.delete { EVENT.eq(eventId).and(RATING_CATEGORY.eq(ratingCategoryId)) }

    fun getByEventAndRatingCategory(eventId: UUID, ratingCategoryId: UUID) =
        EVENT_RATING_CATEGORY.selectOne { EVENT.eq(eventId).and(RATING_CATEGORY.eq(ratingCategoryId)) }

    fun existsByEvent(eventId: UUID) = EVENT_RATING_CATEGORY.exists { EVENT.eq(eventId) }
}