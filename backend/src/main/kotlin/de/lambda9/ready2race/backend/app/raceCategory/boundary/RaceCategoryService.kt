package de.lambda9.ready2race.backend.app.raceCategory.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.raceCategory.control.RaceCategoryRepo
import de.lambda9.ready2race.backend.app.raceCategory.control.raceCategoryDtoList
import de.lambda9.ready2race.backend.app.raceCategory.control.record
import de.lambda9.ready2race.backend.app.raceCategory.entity.RaceCategoryDto
import de.lambda9.ready2race.backend.kio.failOnFalse
import de.lambda9.ready2race.backend.responses.ApiError
import de.lambda9.ready2race.backend.responses.ApiResponse
import de.lambda9.ready2race.backend.responses.ApiResponse.Companion.noData
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import io.ktor.http.*
import java.util.UUID

object RaceCategoryService {

    enum class RaceCategoryError : ServiceError {
        RaceCategoryNotFound;

        override fun respond(): ApiError = when (this) {
            RaceCategoryNotFound -> ApiError(
                status = HttpStatusCode.NotFound,
                message = "RaceCategory not Found"
            )
        }
    }

    fun addRaceCategory(
        request: RaceCategoryDto
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {
        val raceCategoryId = !RaceCategoryRepo.create(request.record()).orDie()
        KIO.ok(ApiResponse.Created(raceCategoryId))
    }

    fun getRaceCategoryList(): App<Nothing, ApiResponse.Dto<List<RaceCategoryDto>>> = KIO.comprehension {
        val raceCategoryList = !RaceCategoryRepo.getMany().orDie()

        raceCategoryList.raceCategoryDtoList().map{ ApiResponse.Dto(it) }
    }


    fun updateRaceCategory(
        request: RaceCategoryDto,
        raceCategoryId: UUID
    ): App<RaceCategoryError, ApiResponse.NoData> = KIO.comprehension {
        !RaceCategoryRepo.update(raceCategoryId) {
            name = request.name
            description = request.description
        }.orDie().failOnFalse { RaceCategoryError.RaceCategoryNotFound }

        noData
    }

    fun deleteRaceCategory(
        raceCategoryId: UUID
    ): App<RaceCategoryError, ApiResponse.NoData> = KIO.comprehension {
        val deleted = !RaceCategoryRepo.delete(raceCategoryId).orDie()
        if(deleted < 1) {
            KIO.fail(RaceCategoryError.RaceCategoryNotFound)
        } else{
            noData
        }
    }

}