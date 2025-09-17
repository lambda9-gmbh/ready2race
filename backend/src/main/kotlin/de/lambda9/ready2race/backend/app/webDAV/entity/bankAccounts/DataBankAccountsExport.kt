package de.lambda9.ready2race.backend.app.webDAV.entity.bankAccounts

import com.fasterxml.jackson.databind.JsonNode
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.bankAccount.control.BankAccountRepo
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

data class DataBankAccountsExport(
    val bankAccounts: JsonNode
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {

            val bankAccounts = !BankAccountRepo.allAsJson().orDie()

            val json = !WebDAVExportService.serializeDataExportNew(record, mapOf("bankAccounts" to bankAccounts))

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_BANK_ACCOUNTS), bytes = json))
        }

        fun importData(data: DataBankAccountsExport): App<WebDAVError.WebDAVImportNextError, Unit> = KIO.comprehension {

            !BankAccountRepo.insertJsonData(data.bankAccounts.toString()).orDie()

            unit
        }
    }
}