package de.lambda9.ready2race.backend.app.raceTemplate.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.raceProperties.control.RacePropertiesRepo
import de.lambda9.ready2race.backend.app.raceProperties.control.record
import de.lambda9.ready2race.backend.app.raceTemplate.control.RaceTemplateRepo
import de.lambda9.ready2race.backend.app.raceTemplate.entity.RaceTemplateRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceTemplateRecord
import de.lambda9.ready2race.backend.responses.ApiResponse
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.util.*

object RaceTemplateService {

    fun addRaceTemplate(
        request: RaceTemplateRequest,
        userId: UUID,
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {
        val raceTemplateId = !RaceTemplateRepo.create(
            RaceTemplateRecord(
                createdBy = userId,
                updatedBy = userId
            )
        ).orDie()

        !RacePropertiesRepo.create(request.raceProperties.record(null, raceTemplateId)).orDie()

        KIO.ok(ApiResponse.Created(raceTemplateId))
    }
}