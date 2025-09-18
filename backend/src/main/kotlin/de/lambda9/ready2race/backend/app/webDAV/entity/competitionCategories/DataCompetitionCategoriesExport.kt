package de.lambda9.ready2race.backend.app.webDAV.entity.competitionCategories

import com.fasterxml.jackson.databind.JsonNode
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competitionCategory.control.CompetitionCategoryRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVError
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportData
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportType
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.orDie

data class DataCompetitionCategoriesExport(
    val competitionCategories: JsonNode
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {

            val competitionCategories = !CompetitionCategoryRepo.allAsJson().orDie()

            val json = !WebDAVExportService.serializeDataExportNew(record, mapOf("competitionCategories" to competitionCategories))

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_COMPETITION_CATEGORIES), bytes = json))
        }

        fun importData(data: DataCompetitionCategoriesExport): App<WebDAVError.WebDAVImportNextError, Unit> =
            KIO.comprehension {

                !CompetitionCategoryRepo.insertJsonData(data.competitionCategories.toString()).orDie()

                unit
            }
    }
}