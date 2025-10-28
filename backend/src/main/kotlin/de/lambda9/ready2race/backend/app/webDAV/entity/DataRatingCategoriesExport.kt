package de.lambda9.ready2race.backend.app.webDAV.entity

import com.fasterxml.jackson.databind.JsonNode
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ratingcategory.control.RatingCategoryRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.orDie

data class DataRatingCategoriesExport(
    val ratingCategories: JsonNode
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {

            val ratingCategories = !RatingCategoryRepo.allAsJson().orDie()

            val json =
                !WebDAVExportService.serializeDataExportNew(record, mapOf("ratingCategories" to ratingCategories))

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_RATING_CATEGORIES), bytes = json))
        }

        fun importData(data: DataRatingCategoriesExport): App<WebDAVError.WebDAVImportNextError, Unit> =
            KIO.comprehension {

                !RatingCategoryRepo.insertJsonData(data.ratingCategories.toString()).orDie()

                unit
            }
    }
}