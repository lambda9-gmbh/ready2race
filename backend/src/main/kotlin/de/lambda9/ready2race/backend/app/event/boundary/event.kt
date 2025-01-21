package de.lambda9.ready2race.backend.app.event.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.event.entity.EventRequest
import de.lambda9.ready2race.backend.app.event.entity.EventSort
import de.lambda9.ready2race.backend.app.eventDay.boundary.eventDay
import de.lambda9.ready2race.backend.app.race.boundary.RaceService
import de.lambda9.ready2race.backend.app.race.boundary.race
import de.lambda9.ready2race.backend.plugins.*
import de.lambda9.tailwind.core.KIO
import io.ktor.server.request.*
import io.ktor.server.routing.*
import java.util.*

fun Route.event() {
    route("/event") {

        post {
            val params = call.receive<EventRequest>()
            call.respondKIO {
                KIO.comprehension {
                    val (user, _) = !authenticate(Privilege.EVENT_EDIT)
                    EventService.addEvent(params, user.id!!)
                }
            }
        }

        get {
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.EVENT_VIEW)
                    val params = !pagination<EventSort>()
                    EventService.page(params)
                }
            }
        }

        route("/{eventId}") {

            eventDay()
            race()

            get {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.EVENT_VIEW)
                        val id = !pathParam("eventId") { UUID.fromString(it) }
                        EventService.getEventById(id)
                    }
                }
            }

            put {
                val params = call.receive<EventRequest>()
                call.respondKIO {
                    KIO.comprehension {
                        val (user, _) = !authenticate(Privilege.EVENT_EDIT)
                        val id = !pathParam("eventId") { UUID.fromString(it) }
                        EventService.updateEvent(params, user.id!!, id)
                    }
                }
            }

            delete {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.EVENT_EDIT)
                        val id = !pathParam("eventId") { UUID.fromString(it) }
                        EventService.deleteEvent(id)
                    }
                }
            }
        }
    }
}