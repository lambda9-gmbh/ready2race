package de.lambda9.ready2race.backend.app.eventDay.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.eventDay.entity.AssignCompetitionsToDayRequest
import de.lambda9.ready2race.backend.app.eventDay.entity.EventDayRequest
import de.lambda9.ready2race.backend.app.eventDay.entity.EventDaySort
import de.lambda9.ready2race.backend.calls.requests.*
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import io.ktor.server.routing.*
import java.util.*

fun Route.eventDay() {
    route("/eventDay") {

        post {
            call.respondComprehension {
                val (user, _) = !authenticate(Privilege.Action.CREATE, Privilege.Resource.EVENT)
                val eventId = !pathParam("eventId") { UUID.fromString(it) }

                val body = !receiveKIO(EventDayRequest.example)
                EventDayService.addEventDay(body, user.id!!, eventId)
            }
        }

        get {
            call.respondComprehension {
                !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                val eventId = !pathParam("eventId") { UUID.fromString(it) }
                val params = !pagination<EventDaySort>()
                val competitionId = !optionalQueryParam("competitionId") { UUID.fromString(it) }

                EventDayService.pageByEvent(eventId, params, competitionId)
            }
        }

        route("/{eventDayId}") {

            get {
                call.respondComprehension {
                    !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                    val eventDayId = !pathParam("eventDayId") { UUID.fromString(it) }

                    EventDayService.getEventDay(eventDayId)
                }
            }

            put {
                call.respondComprehension {
                    val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT)
                    val eventDayId = !pathParam("eventDayId") { UUID.fromString(it) }

                    val body = !receiveKIO(EventDayRequest.example)
                    EventDayService.updateEvent(body, user.id!!, eventDayId)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.Action.DELETE, Privilege.Resource.EVENT)
                    val eventDayId = !pathParam("eventDayId") { UUID.fromString(it) }

                    EventDayService.deleteEvent(eventDayId)
                }
            }

            put("/competitions") {

                call.respondComprehension {
                    val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT)
                    val eventDayId = !pathParam("eventDayId") { UUID.fromString(it) }

                    val body = !receiveKIO(AssignCompetitionsToDayRequest.example)
                    EventDayService.updateEventDayHasCompetition(body, user.id!!, eventDayId)
                }
            }
        }
    }
}