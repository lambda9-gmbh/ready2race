package de.lambda9.ready2race.backend.app.ratingcategory.control

import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_RATING_CATEGORY_VIEW
import de.lambda9.ready2race.backend.database.select
import java.util.UUID

object EventRatingCategoryViewRepo {

    fun get(eventId: UUID) = EVENT_RATING_CATEGORY_VIEW.select { EVENT.eq(eventId) }

}