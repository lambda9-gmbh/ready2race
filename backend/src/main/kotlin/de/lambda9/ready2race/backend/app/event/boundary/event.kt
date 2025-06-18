package de.lambda9.ready2race.backend.app.event.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competition.boundary.competition
import de.lambda9.ready2race.backend.app.event.entity.EventPublicViewSort
import de.lambda9.ready2race.backend.app.event.entity.EventRequest
import de.lambda9.ready2race.backend.app.event.entity.EventSort
import de.lambda9.ready2race.backend.app.eventDay.boundary.eventDay
import de.lambda9.ready2race.backend.app.eventDocument.boundary.eventDocument
import de.lambda9.ready2race.backend.app.eventRegistration.boundary.EventRegistrationService
import de.lambda9.ready2race.backend.app.eventRegistration.boundary.eventRegistration
import de.lambda9.ready2race.backend.app.eventRegistration.entity.EventRegistrationViewSort
import de.lambda9.ready2race.backend.app.invoice.boundary.InvoiceService
import de.lambda9.ready2race.backend.app.participant.boundary.participantForEvent
import de.lambda9.ready2race.backend.app.participantRequirement.boundary.participantRequirementForEvent
import de.lambda9.ready2race.backend.app.task.boundary.task
import de.lambda9.ready2race.backend.app.workShift.boundary.workShift
import de.lambda9.ready2race.backend.calls.requests.*
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
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
                val optionalUserAndScope = !optionalAuthenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                val params = !pagination<EventSort>()
                EventService.page(params, optionalUserAndScope?.second)
            }

        }

        route("/public") {
            get {
                call.respondComprehension {
                    val params = !pagination<EventPublicViewSort>()
                    EventService.pagePublicView(params)
                }
            }
        }

        route("/registration") {
            get {
                call.respondComprehension {
                    val params = !pagination<EventRegistrationViewSort>()
                    EventRegistrationService.pageView(params)
                }
            }
        }

        route("/{eventId}") {

            eventDay()
            competition()
            eventRegistration()
            eventDocument()
            participantRequirementForEvent()
            participantForEvent()
            task()
            workShift()

            get {
                call.respondComprehension {
                    val optionalUserAndScope = !optionalAuthenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                    val id = !pathParam("eventId", uuid)
                    EventService.getEvent(id, optionalUserAndScope?.second)
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

            post("/produceInvoices") {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val id = !pathParam("eventId", uuid)
                    InvoiceService.createRegistrationInvoicesForEventJobs(id, user.id!!)
                }
            }
        }
    }
}