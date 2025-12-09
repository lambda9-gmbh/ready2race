package de.lambda9.ready2race.backend.app.globalConfigurations.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.globalConfigurations.control.GlobalConfigurationsRepo
import de.lambda9.ready2race.backend.app.globalConfigurations.entity.UpdateGlobalConfigurationsRequest
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.kio.onNullDie
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.*

object GlobalConfigurationsService {

    fun updateConfigurations(
        request: UpdateGlobalConfigurationsRequest,
        userId: UUID,
    ): App<Nothing, ApiResponse.NoData> = KIO.comprehension {
        !GlobalConfigurationsRepo.update {
            createClubOnRegistration = request.allowClubCreationOnRegistration
            updatedAt = LocalDateTime.now()
            updatedBy = userId
        }.orDie()

        noData
    }

    fun getCreateClubOnRegistration(): App<Nothing, ApiResponse.Dto<Boolean>> = KIO.comprehension {
        GlobalConfigurationsRepo.get().orDie()
            .onNullDie("Global configurations are always present")
            .map {
                ApiResponse.Dto(
                    it.createClubOnRegistration
                )
            }
    }
}