package de.lambda9.ready2race.backend.app.eventDay.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.eventDay.entity.AssignCompetitionsToDayRequest
import de.lambda9.ready2race.backend.app.eventDay.entity.EventDayRequest
import de.lambda9.ready2race.backend.app.eventDay.entity.EventDaySort
import de.lambda9.ready2race.backend.requests.*
import de.lambda9.ready2race.backend.responses.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.routing.*
import java.util.*

fun Route.eventDay() {
    route("/eventDay") {

        post {
            val payload = call.receiveV(EventDayRequest.example)
            call.respondKIO {
                KIO.comprehension {
                    val (user, _) = !authenticate(Privilege.Action.CREATE, Privilege.Resource.EVENT)
                    val eventId = !pathParam("eventId") { UUID.fromString(it) }

                    EventDayService.addEventDay(!payload, user.id!!, eventId)
                }
            }
        }

        get {
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                    val eventId = !pathParam("eventId") { UUID.fromString(it) }
                    val params = !pagination<EventDaySort>()
                    val competitionId = !optionalQueryParam("competitionId") { UUID.fromString(it) }

                    EventDayService.pageByEvent(eventId, params, competitionId)
                }
            }
        }

        route("/{eventDayId}") {

            get {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                        val eventDayId = !pathParam("eventDayId") { UUID.fromString(it) }

                        EventDayService.getEventDay(eventDayId)
                    }
                }
            }

            put {
                val payload = call.receiveV(EventDayRequest.example)
                call.respondKIO {
                    KIO.comprehension {
                        val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT)
                        val eventDayId = !pathParam("eventDayId") { UUID.fromString(it) }

                        EventDayService.updateEvent(!payload, user.id!!, eventDayId)
                    }
                }
            }

            delete {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.Action.DELETE, Privilege.Resource.EVENT)
                        val eventDayId = !pathParam("eventDayId") { UUID.fromString(it) }

                        EventDayService.deleteEvent(eventDayId)
                    }
                }
            }

            route("/competitions"){

                put {
                    val payload = call.receiveV(AssignCompetitionsToDayRequest.example)
                    call.respondKIO {
                        KIO.comprehension {
                            val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT)
                            val eventDayId = !pathParam("eventDayId") { UUID.fromString(it) }
                            EventDayService.updateEventDayHasCompetition(!payload, user.id!!, eventDayId)
                        }
                    }
                }
            }
        }
    }
}