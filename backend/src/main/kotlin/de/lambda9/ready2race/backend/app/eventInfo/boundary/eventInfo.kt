package de.lambda9.ready2race.backend.app.eventInfo.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.eventInfo.entity.*
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.optionalQueryParam
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.queryParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import de.lambda9.tailwind.core.KIO
import io.ktor.server.routing.*

fun Route.eventInfo() {
    route("/event/{eventId}/info") {


        // Get upcoming competition matches
        get("/upcoming-matches") {
            call.respondComprehension {
                val eventId = !pathParam("eventId", uuid)
                val limit = !queryParam("limit", { it.toIntOrNull() ?: 10 })

                EventInfoService.getUpcomingCompetitionMatches(eventId, limit)
            }
        }

        // Get latest match results
        get("/latest-match-results") {
            call.respondComprehension {
                val eventId = !pathParam("eventId", uuid)
                val limit = !queryParam("limit", { it.toIntOrNull() ?: 10 })
                val competitionId = !optionalQueryParam("competitionId", uuid)

                EventInfoService.getLatestMatchResults(eventId, limit, competitionId)
            }
        }

        // Get currently running matches
        get("/running-matches") {
            call.respondComprehension {
                val eventId = !pathParam("eventId", uuid)
                val limit = !queryParam("limit", { it.toIntOrNull() ?: 10 })

                EventInfoService.getRunningMatches(eventId, limit)
            }
        }
    }

    route("/event/{eventId}/info-views") {
        // Get all info views for an event
        get {
            call.respondComprehension {
                val user = !authenticate(Privilege.ReadEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val includeInactive = !call.optionalQueryParam("includeInactive") { it.toBoolean() }

                EventInfoService.getInfoViews(eventId, includeInactive ?: false)
            }
        }

        // Create new info view
        post {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val request = !receiveKIO(InfoViewConfigurationRequest.example)

                EventInfoService.createInfoView(eventId, request)
            }
        }

        // Update info view
        put("/{viewId}") {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val viewId = !pathParam("viewId", uuid)
                val request = !receiveKIO(InfoViewConfigurationRequest.example)

                EventInfoService.updateInfoView(viewId, request)
            }
        }

        // Delete info view
        delete("/{viewId}") {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val viewId = !pathParam("viewId", uuid)

                EventInfoService.deleteInfoView(viewId)
            }
        }
    }
}