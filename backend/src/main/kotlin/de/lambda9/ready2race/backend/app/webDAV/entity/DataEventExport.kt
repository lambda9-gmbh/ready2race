package de.lambda9.ready2race.backend.app.webDAV.entity

import com.fasterxml.jackson.databind.JsonNode
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.bankAccount.control.PayeeBankAccountRepo
import de.lambda9.ready2race.backend.app.contactInformation.control.ContactInformationUsageRepo
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayRepo
import de.lambda9.ready2race.backend.app.eventInfo.control.BoardRepo
import de.lambda9.ready2race.backend.app.participantRequirement.control.EventHasParticipantRequirementRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.ready2race.backend.kio.onFalseFail
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.orDie


data class DataEventExport(
    val event: JsonNode,
    val contactInformationUsage: JsonNode,
    val payeeBankAccount: JsonNode,
    val eventDays: JsonNode,
    val eventHasParticipantRequirements: JsonNode,
    // Nullable: Exporte von vor dem Board-Umbau (10.08.2026) tragen stattdessen
    // `infoViewConfigurations` — das Konzept ist obsolet, solche Daten werden beim
    // Import stillschweigend ausgelassen statt den ganzen Import scheitern zu lassen.
    val boards: JsonNode?,
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord,
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {
            val eventId = record.dataReference
                ?: return@comprehension KIO.fail(WebDAVError.FileNotFound(record.id, null))


            !EventRepo.exists(eventId).orDie().onFalseFail { WebDAVError.FileNotFound(record.id, eventId) }

            val event = !EventRepo.getAsJson(eventId).orDie()

            val contactInformationUsage = !ContactInformationUsageRepo.getAsJson(eventId).orDie()

            val payeeBankAccount = !PayeeBankAccountRepo.getAsJson(eventId).orDie()

            val eventDays = !EventDayRepo.getAsJson(eventId).orDie()

            val eventHasParticipantRequirements = !EventHasParticipantRequirementRepo.getAsJson(eventId).orDie()

            val boards = !BoardRepo.getAsJson(eventId).orDie()

            val json = !WebDAVExportService.serializeDataExportNew(
                record,
                mapOf(
                    "event" to event,
                    "contactInformationUsage" to contactInformationUsage,
                    "payeeBankAccount" to payeeBankAccount,
                    "eventDays" to eventDays,
                    "eventHasParticipantRequirements" to eventHasParticipantRequirements,
                    "boards" to boards
                )
            )

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_EVENT), bytes = json))
        }

        fun importData(data: DataEventExport): App<WebDAVError.WebDAVImportNextError, Unit> = KIO.comprehension {

            !EventRepo.insertJsonData(data.event.toString()).orDie()

            !ContactInformationUsageRepo.insertJsonData(data.contactInformationUsage.toString()).orDie()

            !PayeeBankAccountRepo.insertJsonData(data.payeeBankAccount.toString()).orDie()

            !EventDayRepo.insertJsonData(data.eventDays.toString()).orDie()

            !EventHasParticipantRequirementRepo.insertJsonData(data.eventHasParticipantRequirements.toString()).orDie()

            data.boards?.let { !BoardRepo.insertJsonData(it.toString()).orDie() }

            unit
        }
    }
}