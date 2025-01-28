package de.lambda9.ready2race.backend.app.eventDay.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.eventDay.entity.EventDayRequest
import de.lambda9.ready2race.backend.app.eventDay.entity.EventDaySort
import de.lambda9.ready2race.backend.plugins.*
import de.lambda9.tailwind.core.KIO
import io.ktor.server.request.*
import io.ktor.server.routing.*
import java.util.*

fun Route.eventDay() {
    route("/eventDay") {

        post {
            val params = call.receive<EventDayRequest>()
            call.respondKIO {
                KIO.comprehension {
                    val (user, _) = !authenticate(Privilege.Action.CREATE, Privilege.Resource.EVENT)
                    val eventId = !pathParam("eventId") { UUID.fromString(it) }

                    EventDayService.addEventDay(params, user.id!!, eventId)
                }
            }
        }

        get {
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                    val eventId = !pathParam("eventId") { UUID.fromString(it) }
                    val params = !pagination<EventDaySort>()
                    val raceId = !call.optionalQueryParam("raceId") { UUID.fromString(it) }

                    EventDayService.pageByEvent(eventId, params, raceId)
                }
            }
        }

        route("/{eventDayId}") {

            get {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                        val eventDayId = !pathParam("eventDayId") { UUID.fromString(it) }

                        EventDayService.getEventDayById(eventDayId)
                    }
                }
            }

            put {
                val params = call.receive<EventDayRequest>()
                call.respondKIO {
                    KIO.comprehension {
                        val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT)
                        val eventDayId = !pathParam("eventDayId") { UUID.fromString(it) }

                        EventDayService.updateEvent(params, user.id!!, eventDayId)
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
        }
    }
}