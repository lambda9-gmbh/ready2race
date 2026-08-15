package de.lambda9.ready2race.backend.app.eventRegistration.boundary

import com.fasterxml.jackson.module.kotlin.readValue
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.email.entity.EmailAttachment
import de.lambda9.ready2race.backend.app.eventRegistration.entity.EventRegistrationUpsertDto
import de.lambda9.ready2race.backend.app.eventRegistration.entity.EventRegistrationViewSort
import de.lambda9.ready2race.backend.app.eventRegistration.entity.ParticipantRegisterRequest
import de.lambda9.ready2race.backend.app.eventRegistration.entity.RegistrationMailRequest
import de.lambda9.ready2race.backend.app.invoice.boundary.InvoiceService
import de.lambda9.ready2race.backend.app.invoice.entity.InvoiceForEventRegistrationSort
import de.lambda9.ready2race.backend.calls.requests.*
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.calls.serialization.jsonMapper
import de.lambda9.ready2race.backend.parsing.Parser.Companion.boolean
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullDefault
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*

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

        get("/mailRecipients") {
            call.respondComprehension {
                !authenticate(Privilege.UpdateEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                RegistrationMailService.getRecipients(eventId)
            }
        }

        post("/mail") {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val eventId = !pathParam("eventId", uuid)

                // Anhänge und Rumpf kommen gemeinsam als Multipart herein - wie beim
                // Vereins-Import, nur dass hier mehrere Dateien erlaubt sind.
                val multiPartData = receiveMultipart()
                val attachments = mutableListOf<EmailAttachment>()
                var request: RegistrationMailRequest? = null

                var done = false
                while (!done) {
                    val part = multiPartData.readPart()
                    if (part == null) {
                        done = true
                    } else {
                        when (part) {
                            is PartData.FileItem -> {
                                attachments.add(
                                    EmailAttachment(
                                        name = part.originalFileName!!,
                                        data = part.provider().toByteArray(),
                                    )
                                )
                            }

                            is PartData.FormItem -> {
                                if (part.name == "request") {
                                    request = jsonMapper.readValue<RegistrationMailRequest>(part.value)
                                }
                            }

                            else -> {}
                        }
                        part.dispose()
                    }
                }

                val req = !KIO.failOnNull(request) { RequestError.BodyMissing(RegistrationMailRequest.example) }
                val validation = req.validate()
                !KIO.failOn(validation is ValidationResult.Invalid) {
                    RequestError.BodyValidationFailed(validation as ValidationResult.Invalid)
                }

                RegistrationMailService.send(eventId, req, attachments, user.id!!)
            }
        }

        get("/eventDocumentsAccepted") {
            call.respondComprehension {
                val user = !authenticate(Privilege.ReadRegistrationOwn)
                val eventId = !pathParam("eventId", uuid)
                EventRegistrationService.getEventDocumentsOfficiallyAccepted(eventId, user)
            }
        }

        post("/acceptDocuments") {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateRegistrationOwn)
                val eventId = !pathParam("eventId", uuid)
                EventRegistrationService.acceptEventDocuments(eventId, user)
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
            val user = !authenticate(Privilege.CreateRegistrationOwn)
            val eventId = !pathParam("eventId", uuid)
            val payload = !receiveKIO(EventRegistrationUpsertDto.example)

            EventRegistrationService.upsertRegistrationForEvent(eventId, payload, user)
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

    get("/missingTeamNumbers") {
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

    post("/selfRegister") {
        call.respondComprehension {
            !checkCaptcha()
            val eventId = !pathParam("eventId", uuid)
            val payload = !receiveKIO(ParticipantRegisterRequest.example)

            EventRegistrationService.participantSelfRegister(eventId, payload)
        }
    }
}