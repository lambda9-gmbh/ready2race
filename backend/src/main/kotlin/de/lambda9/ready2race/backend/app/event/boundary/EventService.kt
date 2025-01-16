package de.lambda9.ready2race.backend.app.event.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.control.record
import de.lambda9.ready2race.backend.app.event.entity.EventDto
import de.lambda9.ready2race.backend.app.event.entity.EventRequest
import de.lambda9.ready2race.backend.http.ApiResponse
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.util.*

object EventService {

    fun addEvent(
        request: EventRequest,
        userId: UUID
    ): App<Nothing, ApiResponse.ID> = KIO.comprehension {
        val id = !EventRepo.create(
            EventDto(
                id = UUID.randomUUID(),
                properties = request.properties
            ).record(userId)
        ).orDie()
        KIO.ok(
            ApiResponse.ID(id)
        )
    }
}