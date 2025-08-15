package de.lambda9.ready2race.backend.app.results.boundary

import de.lambda9.ready2race.backend.app.results.entity.CompetitionHavingResultsSort
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.results() {

    route("/results") {

        get("/event/{eventId}/competition") {
            call.respondComprehension {
                val id = !pathParam("eventId", uuid)
                val params = !pagination<CompetitionHavingResultsSort>()
                ResultsService.pageCompetitionsHavingResults(id, params)
            }
        }

    }

}