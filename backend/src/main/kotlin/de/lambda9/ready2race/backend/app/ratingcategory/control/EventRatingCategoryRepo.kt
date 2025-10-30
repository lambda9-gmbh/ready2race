package de.lambda9.ready2race.backend.app.ratingcategory.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRatingCategoryRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_RATING_CATEGORY
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.select
import de.lambda9.ready2race.backend.database.selectOne
import java.util.*

object EventRatingCategoryRepo {

    fun insert(records: List<EventRatingCategoryRecord>) = EVENT_RATING_CATEGORY.insert(records)

    fun delete(eventId: UUID, ratingCategoryId: UUID) =
        EVENT_RATING_CATEGORY.delete { EVENT.eq(eventId).and(RATING_CATEGORY.eq(ratingCategoryId)) }

    fun getByEventAndRatingCategory(eventId: UUID, ratingCategoryId: UUID) =
        EVENT_RATING_CATEGORY.selectOne { EVENT.eq(eventId).and(RATING_CATEGORY.eq(ratingCategoryId)) }
}