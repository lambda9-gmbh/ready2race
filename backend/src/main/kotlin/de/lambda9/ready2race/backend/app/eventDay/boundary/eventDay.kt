package de.lambda9.ready2race.backend.app.eventDay.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.eventDay.entity.AssignCompetitionsToDayRequest
import de.lambda9.ready2race.backend.app.eventDay.entity.EventDayRequest
import de.lambda9.ready2race.backend.app.eventDay.entity.EventDaySort
import de.lambda9.ready2race.backend.app.eventDay.entity.TimeslotRequest
import de.lambda9.ready2race.backend.calls.requests.*
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.eventDay() {
    route("/eventDay") {

        post {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val eventId = !pathParam("eventId", uuid)

                val body = !receiveKIO(EventDayRequest.example)
                EventDayService.addEventDay(body, user.id!!, eventId)
            }
        }

        get {
            call.respondComprehension {
                val optionalUserAndScope = !optionalAuthenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                val eventId = !pathParam("eventId", uuid)
                val params = !pagination<EventDaySort>()
                val competitionId = !optionalQueryParam("competitionId", uuid)

                EventDayService.pageByEvent(eventId, params, competitionId, optionalUserAndScope?.second)
            }
        }

        route("/{eventDayId}") {

            get {
                call.respondComprehension {
                    val optionalUserAndScope = !optionalAuthenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                    val eventDayId = !pathParam("eventDayId", uuid)

                    EventDayService.getEventDay(eventDayId, optionalUserAndScope?.second)
                }
            }

            put {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val eventDayId = !pathParam("eventDayId", uuid)

                    val body = !receiveKIO(EventDayRequest.example)
                    EventDayService.updateEventDay(body, user.id!!, eventDayId)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val eventDayId = !pathParam("eventDayId", uuid)

                    EventDayService.deleteEvent(eventDayId)
                }
            }

            put("/competitions") {

                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val eventDayId = !pathParam("eventDayId", uuid)

                    val body = !receiveKIO(AssignCompetitionsToDayRequest.example)
                    EventDayService.updateEventDayHasCompetition(body, user.id!!, eventDayId)
                }
            }

            route("/timeslot") {
                get(){
                    call.respondComprehension {
                        val optionalUserAndScope = !optionalAuthenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                        val eventDayId = !pathParam("eventDayId", uuid)
                        TimeslotService.getTimeslotsByEventDay(eventDayId)
                    }
                }

                post(){
                    call.respondComprehension {
                        val user = !authenticate(Privilege.UpdateEventGlobal)
                        val eventDayId = !pathParam("eventDayId", uuid)

                        val body = !receiveKIO<TimeslotRequest>(TimeslotRequest.example)
                        TimeslotService.addTimeslotToEventDay(body, user.id!!, eventDayId)
                    }
                }

                put("/{timeslotId}") {
                    call.respondComprehension {
                        val user = !authenticate(Privilege.UpdateEventGlobal)
                        val timeslotId = !pathParam("timeslotId", uuid)

                        val body = !receiveKIO<TimeslotRequest>(TimeslotRequest.example)
                        TimeslotService.updateTimeslot(body, user.id!!, timeslotId)
                    }
                }

                delete("/{timeslotId}") {
                    call.respondComprehension {
                        !authenticate(Privilege.UpdateEventGlobal)
                        val timeslotId = !pathParam("timeslotId", uuid)

                        TimeslotService.deleteTimeslot(timeslotId)
                    }
                }
            }
        }
    }
}