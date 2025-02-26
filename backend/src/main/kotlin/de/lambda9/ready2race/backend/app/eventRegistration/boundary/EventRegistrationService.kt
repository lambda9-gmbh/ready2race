package de.lambda9.ready2race.backend.app.eventRegistration.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.eventRegistration.control.EventRegistrationRepo
import de.lambda9.ready2race.backend.app.eventRegistration.entity.EventRegistrationError
import de.lambda9.ready2race.backend.app.eventRegistration.entity.EventRegistrationTemplateDto
import de.lambda9.ready2race.backend.responses.ApiResponse
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.util.*

object EventRegistrationService {

    fun getEventRegistrationTemplate(
        eventId: UUID,
    ): App<EventRegistrationError, ApiResponse.Dto<EventRegistrationTemplateDto>> = KIO.comprehension {
        val template = !EventRegistrationRepo.getEvenRegistrationTemplate(eventId).orDie()
            .onNullFail { EventRegistrationError.EventNotFound }

        KIO.ok(template).map { ApiResponse.Dto(it) }
    }

}