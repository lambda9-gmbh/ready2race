package de.lambda9.ready2race.backend.app.webDAV.entity.event

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVError
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportData
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportType
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit

/*
data class DataEventsExport(
    val events: List<EventExport>,
    val contactInformationUsages: List<ContactInformationUsageExport>,
    val payeeBankAccounts: List<PayeeBankAccountExport>,
    val eventDays: List<EventDayExport>,
    val eventHasParticipantRequirements: List<EventHasParticipantRequirementExport>,
    val infoViewConfigurations: List<InfoViewConfigurationExport>,
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {

            // todo

            val json = !WebDAVExportService.serializeDataExport(record, exportData)

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_FEES), bytes = json))
        }

        fun importData(data: DataEventsExport): App<WebDAVError.WebDAVImportNextError, Unit> = KIO.comprehension {
            try {

                // todo

            } catch (ex: Exception) {
                return@comprehension KIO.fail(WebDAVError.Unexpected)
            }
            unit
        }
    }
}*/
