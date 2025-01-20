package de.lambda9.ready2race.backend.app.eventDay.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.eventDay.entity.EventDayRequest
import de.lambda9.ready2race.backend.app.eventDay.entity.EventDaySort
import de.lambda9.ready2race.backend.plugins.authenticate
import de.lambda9.ready2race.backend.plugins.pagination
import de.lambda9.ready2race.backend.plugins.pathParam
import de.lambda9.ready2race.backend.plugins.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.request.*
import io.ktor.server.routing.*
import java.util.*

fun Route.eventDay() {
    route("/event-day") {

        post {
            val params = call.receive<EventDayRequest>()
            call.respondKIO {
                KIO.comprehension {
                    val user = !authenticate(Privilege.EVENT_EDIT)
                    val eventId = !call.pathParam("eventId") { UUID.fromString(it) }
                    EventDayService.addEventDay(params, user.first.id!!, eventId)
                }
            }
        }

        get {
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.EVENT_VIEW)
                    val params = !call.pagination<EventDaySort>()
                    EventDayService.page(params)
                }
            }
        }

        route("/{eventDayId}") {
            get {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.EVENT_VIEW)
                        val eventDayId = !call.pathParam("eventDayId") { UUID.fromString(it) }
                        EventDayService.getEventDayById(eventDayId)
                    }
                }
            }

            put {
                val params = call.receive<EventDayRequest>()
                call.respondKIO {
                    KIO.comprehension {
                        val user = !authenticate(Privilege.EVENT_EDIT)
                        val eventDayId = !call.pathParam("eventDayId") { UUID.fromString(it) }
                        val eventId = !call.pathParam("eventId") { UUID.fromString(it) }
                        EventDayService.updateEvent(params, eventDayId, eventId, user.first.id!!)
                    }
                }
            }

            delete {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.EVENT_EDIT)
                        val eventDayId = !call.pathParam("eventDayId") { UUID.fromString(it) }
                        EventDayService.deleteEvent(eventDayId)
                    }
                }
            }
        }
    }
}