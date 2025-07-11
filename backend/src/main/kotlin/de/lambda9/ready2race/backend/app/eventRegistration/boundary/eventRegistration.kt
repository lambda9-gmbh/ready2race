package de.lambda9.ready2race.backend.app.eventRegistration.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.eventRegistration.entity.EventRegistrationUpsertDto
import de.lambda9.ready2race.backend.app.eventRegistration.entity.EventRegistrationViewSort
import de.lambda9.ready2race.backend.app.invoice.boundary.InvoiceService
import de.lambda9.ready2race.backend.app.invoice.entity.InvoiceForEventRegistrationSort
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.optionalQueryParam
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.boolean
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import de.lambda9.tailwind.core.extensions.kio.onNullDefault
import io.ktor.server.routing.*

fun Route.eventRegistration() {

    route("/eventRegistration") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadRegistrationGlobal)
                val eventId = !pathParam("eventId", uuid)
                val params = !pagination<EventRegistrationViewSort>()
                EventRegistrationService.pageForEvent(eventId, params)
            }
        }

        route("/{eventRegistrationId}") {
            get {
                call.respondComprehension {
                    val (user, scope) = !authenticate(Privilege.Action.READ, Privilege.Resource.REGISTRATION)
                    val id = !pathParam("eventRegistrationId", uuid)
                    EventRegistrationService.getRegistration(id, user, scope)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateRegistrationGlobal)
                    val id = !pathParam("eventRegistrationId", uuid)
                    EventRegistrationService.deleteRegistration(id)
                }
            }

            get("/invoices") {
                call.respondComprehension {
                    val (user, scope) = !authenticate(Privilege.Action.READ, Privilege.Resource.INVOICE)
                    val id = !pathParam("eventRegistrationId", uuid)
                    val params = !pagination<InvoiceForEventRegistrationSort>()
                    InvoiceService.pageForRegistration(id, params, user, scope)
                }
            }
        }
    }

    get("/registrationTemplate") {
        call.respondComprehension {
            val user = !authenticate()
            val eventId = !pathParam("eventId", uuid)
            EventRegistrationService.getEventRegistrationTemplate(eventId, user.club!!)
        }
    }

    post("/register") {
        call.respondComprehension {
            val (user, scope) = !authenticate(Privilege.Action.CREATE, Privilege.Resource.REGISTRATION)
            val eventId = !pathParam("eventId", uuid)
            val payload = !receiveKIO(EventRegistrationUpsertDto.example)
            EventRegistrationService.upsertRegistrationForEvent(eventId, payload, user, scope)
        }
    }

    post("/finalizeRegistrations") {
        call.respondComprehension {
            val user = !authenticate(Privilege.UpdateRegistrationGlobal)
            val eventId = !pathParam("eventId", uuid)
            val keepNumbers = !optionalQueryParam("keepNumbers", boolean).onNullDefault { true }
            EventRegistrationService.finalizeRegistrations(user.id!!, eventId, keepNumbers)
        }
    }

    get("/missingTeamNumbers"){
        call.respondComprehension {
            !authenticate(Privilege.ReadRegistrationGlobal)
            val eventId = !pathParam("eventId", uuid)
            EventRegistrationService.getRegistrationsWithoutTeamNumber(eventId)
        }
    }

    get("/registrationResult") {
        call.respondComprehension {
            !authenticate(Privilege.ReadRegistrationGlobal)
            val eventId = !pathParam("eventId", uuid)
            EventRegistrationService.downloadResult(eventId)
        }
    }
}