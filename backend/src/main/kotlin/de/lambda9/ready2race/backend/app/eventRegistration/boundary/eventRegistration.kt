package de.lambda9.ready2race.backend.app.eventRegistration.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.eventRegistration.entity.EventRegistrationUpsertDto
import de.lambda9.ready2race.backend.calls.requests.ParamParser.Companion.boolean
import de.lambda9.ready2race.backend.calls.requests.ParamParser.Companion.uuid
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.optionalQueryParam
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.tailwind.core.extensions.kio.onNullDefault
import io.ktor.server.routing.*
import java.util.*

fun Route.eventRegistration() {

    get("/registrationTemplate") {
        call.respondComprehension {
            val user = !authenticate()
            val eventId = !pathParam("eventId", uuid)
            EventRegistrationService.getEventRegistrationTemplate(eventId, user.club!!)
        }
    }

    post("/register") {
        call.respondComprehension {
            val user = !authenticate()
            val eventId = !pathParam("eventId", uuid)
            val payload = !receiveKIO(EventRegistrationUpsertDto.example)
            EventRegistrationService.upsertRegistrationForEvent(eventId, payload, user.club!!, user.id!!)
        }
    }

    get("/registrationResult") {
        call.respondComprehension {
            !authenticate(Privilege.ReadEventRegistrationGlobal)
            val eventId = !pathParam("eventId", uuid)
            val remake = !optionalQueryParam("remake", boolean).onNullDefault { false }
            EventRegistrationService.downloadResult(eventId, remake)
        }
    }
}