package de.lambda9.ready2race.backend.app.ratingcategory.control

import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_RATING_CATEGORY_VIEW
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRatingCategoryViewRecord
import java.util.UUID

object EventRatingCategoryViewRepo {

    /**
     * In der konfigurierten Abschnittsreihenfolge, mit dem Namen als Tiebreak - dieselbe
     * Sortierung, nach der auch [de.lambda9.ready2race.backend.app.ratingcategory.boundary.RatingCategoryRanking]
     * die Ergebnisabschnitte anordnet. Ohne sie stünde die Konfigurationsseite anders da als die
     * Ergebnisliste, die sie steuert.
     */
    fun get(eventId: UUID): JIO<List<EventRatingCategoryViewRecord>> = Jooq.query {
        with(EVENT_RATING_CATEGORY_VIEW) {
            selectFrom(this)
                .where(EVENT.eq(eventId))
                .orderBy(SORT_ORDER.asc(), RATING_CATEGORY_NAME.asc())
                .fetch()
        }
    }

}