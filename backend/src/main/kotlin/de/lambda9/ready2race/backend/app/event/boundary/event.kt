package de.lambda9.ready2race.backend.app.event.boundary

import de.lambda9.ready2race.backend.app.appUserWithQrCode.boundary.appUserWithQrCode
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.caterer.boundary.CatererService
import de.lambda9.ready2race.backend.app.caterer.entity.CatererTransactionViewSort
import de.lambda9.ready2race.backend.app.competition.boundary.competition
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.event.entity.EventPublicViewSort
import de.lambda9.ready2race.backend.app.event.entity.EventRequest
import de.lambda9.ready2race.backend.app.event.entity.EventViewSort
import de.lambda9.ready2race.backend.app.eventDay.boundary.eventDay
import de.lambda9.ready2race.backend.app.eventDocument.boundary.eventDocument
import de.lambda9.ready2race.backend.app.eventRegistration.boundary.EventRegistrationService
import de.lambda9.ready2race.backend.app.eventRegistration.boundary.eventRegistration
import de.lambda9.ready2race.backend.app.eventRegistration.entity.EventRegistrationViewSort
import de.lambda9.ready2race.backend.app.invoice.boundary.InvoiceService
import de.lambda9.ready2race.backend.app.invoice.entity.InvoiceForEventRegistrationSort
import de.lambda9.ready2race.backend.app.invoice.entity.ProduceInvoicesRequest
import de.lambda9.ready2race.backend.app.invoice.entity.RegistrationInvoiceType
import de.lambda9.ready2race.backend.app.participant.boundary.participantForEvent
import de.lambda9.ready2race.backend.app.participantRequirement.boundary.participantRequirementForEvent
import de.lambda9.ready2race.backend.app.task.boundary.task
import de.lambda9.ready2race.backend.app.participantTracking.boundary.ParticipantTrackingService
import de.lambda9.ready2race.backend.app.participantTracking.boundary.participantTracking
import de.lambda9.ready2race.backend.app.workShift.boundary.workShift
import de.lambda9.ready2race.backend.calls.requests.*
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.boolean
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.event() {
    route("/event") {

        post {
            call.respondComprehension {
                val user = !authenticate(Privilege.CreateEventGlobal)

                val body = !receiveKIO(EventRequest.example)
                EventService.addEvent(body, user.id!!)
            }
        }

        get {
            call.respondComprehension {
                val optionalUserAndScope = !optionalAuthenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                val params = !pagination<EventViewSort>()
                EventService.page(params, optionalUserAndScope?.second, optionalUserAndScope?.first)
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
            appUserWithQrCode()
            participantTracking()

            get("/matches") {
                call.respondComprehension {
                    !authenticate(Privilege.ReadEventGlobal)
                    val eventId = !pathParam("eventId", uuid)
                    val currentlyRunning = !optionalQueryParam("currentlyRunning", boolean)
                    val withoutPlaces = !optionalQueryParam("withoutPlaces", boolean)

                    CompetitionExecutionService.getMatchesByEvent(eventId, currentlyRunning, withoutPlaces)
                }
            }

            get("/invoices") {
                call.respondComprehension {
                    val (user, scope) = !authenticate(Privilege.Action.READ, Privilege.Resource.INVOICE)
                    val id = !pathParam("eventId", uuid)
                    val params = !pagination<InvoiceForEventRegistrationSort>()
                    InvoiceService.pageForEvent(id, params, user, scope)
                }
            }

            get("/caterer-transactions") {
                call.respondComprehension {
                    !authenticate(Privilege.Action.READ, Privilege.Resource.INVOICE)
                    val eventId = !pathParam("eventId", uuid)
                    val params = !pagination<CatererTransactionViewSort>()
                    CatererService.pageByEventId(eventId, params)
                }
            }

            get {
                call.respondComprehension {
                    val optionalUserAndScope = !optionalAuthenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                    val id = !pathParam("eventId", uuid)
                    EventService.getEvent(id, optionalUserAndScope?.second, optionalUserAndScope?.first)
                }
            }

            put {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val id = !pathParam("eventId", uuid)

                    val body = !receiveKIO(EventRequest.example)
                    EventService.updateEvent(body, user.id!!, id)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.DeleteEventGlobal)
                    val id = !pathParam("eventId", uuid)
                    EventService.deleteEvent(id)
                }
            }

            post("/produceInvoices") {
                call.respondComprehension {
                    val user = !authenticate(Privilege.CreateInvoiceGlobal)
                    val id = !pathParam("eventId", uuid)
                    val body = !receiveKIO(ProduceInvoicesRequest.example)
                    InvoiceService.createRegistrationInvoicesForEventJobs(id, body, user.id!!)
                }
            }
        }
    }
}