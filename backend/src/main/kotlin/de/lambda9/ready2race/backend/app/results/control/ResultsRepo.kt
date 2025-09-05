package de.lambda9.ready2race.backend.app.results.control

import de.lambda9.ready2race.backend.app.results.entity.CompetitionHavingResultsSort
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.generated.tables.CompetitionHavingResults
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionHavingResultsRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_HAVING_RESULTS
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.database.select
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object ResultsRepo {

    private fun CompetitionHavingResults.searchFields() = listOf(IDENTIFIER, NAME, SHORT_NAME, CATEGORY)

    fun pageCompetitionsHavingResults(
        eventId: UUID,
        params: PaginationParameters<CompetitionHavingResultsSort>,
    ) = COMPETITION_HAVING_RESULTS.page(params, { searchFields() }) { EVENT.eq(eventId) }

    fun getEventsHavingResultsByEventIds(
        eventIds: List<UUID>,
    ) = COMPETITION_HAVING_RESULTS.select({EVENT}) { EVENT.`in`(eventIds) }
}