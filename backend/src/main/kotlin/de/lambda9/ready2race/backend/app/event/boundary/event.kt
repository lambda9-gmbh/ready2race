package de.lambda9.ready2race.backend.app.event.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competition.boundary.competition
import de.lambda9.ready2race.backend.app.event.entity.EventRequest
import de.lambda9.ready2race.backend.app.event.entity.EventSort
import de.lambda9.ready2race.backend.app.eventDay.boundary.eventDay
import de.lambda9.ready2race.backend.app.eventDocument.boundary.eventDocument
import de.lambda9.ready2race.backend.app.eventRegistration.boundary.eventRegistration
import de.lambda9.ready2race.backend.app.participant.boundary.participantForEvent
import de.lambda9.ready2race.backend.app.participantRequirement.boundary.participantRequirementForEvent
import de.lambda9.ready2race.backend.calls.requests.ParamParser.Companion.uuid
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import io.ktor.server.routing.*

fun Route.event() {
    route("/event") {

        post {
            call.respondComprehension {
                val (user, _) = !authenticate(Privilege.Action.CREATE, Privilege.Resource.EVENT)

                val body = !receiveKIO(EventRequest.example)
                EventService.addEvent(body, user.id!!)
            }
        }

        get {
            call.respondComprehension {
                val (_, scope) = !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                val params = !pagination<EventSort>()
                EventService.page(params, scope)
            }
        }

        route("/{eventId}") {

            eventDay()
            competition()
            eventRegistration()
            eventDocument()
            participantRequirementForEvent()
            participantForEvent()

            get {
                call.respondComprehension {
                    val (_, scope) = !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                    val id = !pathParam("eventId", uuid)
                    EventService.getEvent(id, scope)
                }
            }

            put {
                call.respondComprehension {
                    val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT)
                    val id = !pathParam("eventId", uuid)

                    val body = !receiveKIO(EventRequest.example)
                    EventService.updateEvent(body, user.id!!, id)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.Action.DELETE, Privilege.Resource.EVENT)
                    val id = !pathParam("eventId", uuid)
                    EventService.deleteEvent(id)
                }
            }
        }
    }
}