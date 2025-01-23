package de.lambda9.ready2race.backend.app.raceCategory.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.raceCategory.control.RaceCategoryRepo
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceCategoryRecord
import de.lambda9.ready2race.backend.responses.ApiError
import de.lambda9.ready2race.backend.responses.ApiResponse
import de.lambda9.ready2race.backend.responses.ApiResponse.Companion.noData
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import io.ktor.http.*

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
        request: String
    ): App<Nothing, ApiResponse.NoData> = KIO.comprehension {
        RaceCategoryRepo.create(
            RaceCategoryRecord(
                name = request
            )
        )
        noData
    }

    fun getRaceCategoryList(): App<Nothing, ApiResponse.Dto<List<String>>> = KIO.comprehension {
        val raceCategoryList = !RaceCategoryRepo.getMany().orDie()

        KIO.ok(ApiResponse.Dto(raceCategoryList))
    }

    // updates in the RaceProperties with "on cascade update" -- todo: should there also be "on cascade set null"?
    fun updateRaceCategory(
        prevName: String,
        newName: String
    ): App<RaceCategoryError, ApiResponse.NoData> = KIO.comprehension {
        !RaceCategoryRepo.update(prevName) {
            name = newName
        }.orDie()

        noData
    }

    fun deleteRaceCategory(
        name: String
    ): App<RaceCategoryError, ApiResponse.NoData> = KIO.comprehension {
        RaceCategoryRepo.delete(name)
        noData
    }

}