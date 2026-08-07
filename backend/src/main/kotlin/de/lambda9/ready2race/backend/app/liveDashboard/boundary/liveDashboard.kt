package de.lambda9.ready2race.backend.app.liveDashboard.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardScope
import de.lambda9.ready2race.backend.app.liveDashboard.entity.OpenResultHandling
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.optionalQueryParam
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.queryParam
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.enum
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.liveDashboard() {
    route("/event/{eventId}/liveDashboard") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadLiveDashboardGlobal)
                val eventId = !pathParam("eventId", uuid)
                val scope = !optionalQueryParam("scope", enum<LiveDashboardScope>())

                LiveDashboardService.getLiveDashboard(eventId, scope ?: LiveDashboardScope.ALL)
            }
        }

        // Personendaten einer Mannschaft; bewusst nicht Teil des Polls.
        get("/match/{matchId}/team/{teamId}") {
            call.respondComprehension {
                !authenticate(Privilege.ReadLiveDashboardGlobal)
                val eventId = !pathParam("eventId", uuid)
                val matchId = !pathParam("matchId", uuid)
                val teamId = !pathParam("teamId", uuid)

                LiveDashboardService.getTeamDetail(eventId, matchId, teamId)
            }
        }

        // Lauf offiziell beenden; die Läufe der nächsten Startzeit werden dabei aktiv.
        // Optional trägt `openResults` bei allen Booten ohne Ergebnis denselben Grund ein.
        put("/match/{matchId}/finish") {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateLiveDashboardGlobal)
                val eventId = !pathParam("eventId", uuid)
                val matchId = !pathParam("matchId", uuid)
                val openResults = !optionalQueryParam("openResults", enum<OpenResultHandling>())

                LiveDashboardService.finishMatch(eventId, matchId, user.id!!, openResults)
            }
        }

        // Markiert den echten Start des Laufs; die geplante Startzeit bleibt unangetastet.
        put("/match/{matchId}/start") {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateLiveDashboardGlobal)
                val eventId = !pathParam("eventId", uuid)
                val matchId = !pathParam("matchId", uuid)

                LiveDashboardService.markMatchStarted(eventId, matchId, user.id!!)
            }
        }

        put("/match/{matchId}/running-state") {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateLiveDashboardGlobal)
                val eventId = !pathParam("eventId", uuid)
                val matchId = !pathParam("matchId", uuid)
                val running = !queryParam("running") { it.toBoolean() }

                LiveDashboardService.setMatchRunning(eventId, matchId, running, user.id!!)
            }
        }
    }
}
