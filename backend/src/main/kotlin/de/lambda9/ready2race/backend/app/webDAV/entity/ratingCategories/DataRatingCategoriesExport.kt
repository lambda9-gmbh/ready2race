package de.lambda9.ready2race.backend.app.webDAV.entity.ratingCategories

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ratingcategory.control.RatingCategoryRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.app.webDAV.control.toExport
import de.lambda9.ready2race.backend.app.webDAV.control.toRecord
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVError
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportData
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportType
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.RATING_CATEGORY
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.andThen
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import de.lambda9.tailwind.jooq.Jooq

data class DataRatingCategoriesExport(
    val ratingCategories: List<RatingCategoryExport>
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {
            val ratingCategories = !Jooq.query { selectFrom(RATING_CATEGORY).fetch() }.orDie()
                .andThen { list -> list.traverse { it.toExport() } }

            val exportData = DataRatingCategoriesExport(
                ratingCategories = ratingCategories
            )

            val json = !WebDAVExportService.serializeDataExport(record, exportData)

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_RATING_CATEGORIES), bytes = json))
        }

        fun importData(data: DataRatingCategoriesExport): App<WebDAVError.WebDAVImportNextError, Unit> =
            KIO.comprehension {
                val overlaps = !RatingCategoryRepo.getOverlapIds(data.ratingCategories.map { it.id }).orDie()
                val records = !data.ratingCategories
                    .filter { !overlaps.contains(it.id) }
                    .traverse { it.toRecord() }

                if (records.isNotEmpty()) {
                    !RatingCategoryRepo.create(records).orDie()
                }

                unit
            }
    }
}