package de.lambda9.ready2race.backend.app.webDAV.entity.competitionCategories

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competitionCategory.control.CompetitionCategoryRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.app.webDAV.control.toExport
import de.lambda9.ready2race.backend.app.webDAV.control.toRecord
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVError
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportData
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportType
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_CATEGORY
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.andThen
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import de.lambda9.tailwind.jooq.Jooq

data class DataCompetitionCategoriesExport(
    val competitionCategories: List<CompetitionCategoryExport>
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {
            val competitionCategories = !Jooq.query { selectFrom(COMPETITION_CATEGORY).fetch() }.orDie()
                .andThen { list -> list.traverse { it.toExport() } }

            val exportData = DataCompetitionCategoriesExport(
                competitionCategories = competitionCategories
            )

            val json = !WebDAVExportService.serializeDataExport(record, exportData)

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_COMPETITION_CATEGORIES), bytes = json))
        }

        fun importData(data: DataCompetitionCategoriesExport): App<WebDAVError.WebDAVImportNextError, Unit> =
            KIO.comprehension {

                val overlaps =
                    !CompetitionCategoryRepo.getOverlapIds(data.competitionCategories.map { it.id }).orDie()
                val records = !data.competitionCategories
                    .filter { category -> !overlaps.any { it == category.id } }
                    .traverse { it.toRecord() }

                if (records.isNotEmpty()) {
                    !CompetitionCategoryRepo.create(records).orDie()
                }

                unit
            }
    }
}