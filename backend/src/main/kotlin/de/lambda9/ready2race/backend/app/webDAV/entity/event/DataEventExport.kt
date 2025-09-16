package de.lambda9.ready2race.backend.app.webDAV.entity.event

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.bankAccount.control.PayeeBankAccountRepo
import de.lambda9.ready2race.backend.app.contactInformation.control.ContactInformationUsageRepo
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayRepo
import de.lambda9.ready2race.backend.app.eventInfo.control.InfoViewConfigurationRepo
import de.lambda9.ready2race.backend.app.participantRequirement.control.EventHasParticipantRequirementRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.app.webDAV.control.toExport
import de.lambda9.ready2race.backend.app.webDAV.control.toRecord
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVError
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportData
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportType
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.ready2race.backend.kio.onTrueFail
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.andThen
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse


data class DataEventExport(
    val event: EventExport,
    val contactInformationUsage: ContactInformationUsageExport?,
    val payeeBankAccount: PayeeBankAccountExport?,
    val eventDays: List<EventDayExport>,
    val eventHasParticipantRequirements: List<EventHasParticipantRequirementExport>,
    val infoViewConfigurations: List<InfoViewConfigurationExport>,
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord,
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {
            val eventId = record.dataReference
                ?: return@comprehension KIO.fail(WebDAVError.FileNotFound(record.id, null))


            val event = !EventRepo.get(eventId).orDie()
                .onNullFail { WebDAVError.FileNotFound(record.id, eventId) }
                .andThen { it.toExport() }

            val contactInformationUsage = !ContactInformationUsageRepo.getByEvent(eventId).orDie()
                .andThen { it?.toExport() ?: KIO.ok(null) }

            val payeeBankAccount = !PayeeBankAccountRepo.getByEvent(eventId).orDie()
                .andThen { it?.toExport() ?: KIO.ok(null) }

            val eventDays = !EventDayRepo.getByEvent(eventId).orDie()
                .andThen { list -> list.traverse { it.toExport() } }

            val eventHasParticipantRequirements =
                !EventHasParticipantRequirementRepo.getByEvent(eventId).orDie()
                    .andThen { list -> list.traverse { it.toExport() } }

            val infoViewConfigurations = !InfoViewConfigurationRepo.getByEvent(eventId).orDie()
                .andThen { list -> list.traverse { it.toExport() } }

            val exportData = DataEventExport(
                event = event,
                contactInformationUsage = contactInformationUsage,
                payeeBankAccount = payeeBankAccount,
                eventDays = eventDays,
                eventHasParticipantRequirements = eventHasParticipantRequirements,
                infoViewConfigurations = infoViewConfigurations,
            )

            val json = !WebDAVExportService.serializeDataExport(record, exportData)

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_EVENT), bytes = json))
        }

        fun importData(data: DataEventExport): App<WebDAVError.WebDAVImportNextError, Unit> = KIO.comprehension {

            // EVENT
            !EventRepo.exists(data.event.id).orDie().onTrueFail { WebDAVError.EntityAlreadyExists(data.event.id) }
            val eventRecord = !data.event.toRecord()
            !EventRepo.create(eventRecord).orDie()


            // CONTACT INFORMATION
            if (data.contactInformationUsage != null) {
                val cIURecord = !data.contactInformationUsage.toRecord()
                !ContactInformationUsageRepo.create(cIURecord).orDie()
            }


            // PAYEE BANK ACCOUNT

            if (data.payeeBankAccount != null) {
                val pBARecord = !data.payeeBankAccount.toRecord()
                !PayeeBankAccountRepo.create(pBARecord).orDie()
            }


            // EVENT DAYS
            val eventDayRecords = !data.eventDays.traverse { it.toRecord() }
            if (eventDayRecords.isNotEmpty()) {
                !EventDayRepo.create(eventDayRecords).orDie()
            }


            // EVENT HAS PARTICIPANT REQUIREMENTS
            val eventHasPRRecords = !data.eventHasParticipantRequirements.traverse { it.toRecord() }
            if (eventHasPRRecords.isNotEmpty()) {
                !EventHasParticipantRequirementRepo.create(eventHasPRRecords).orDie()
            }


            // INFO VIEW CONFIGURATIONS
            val infoViewConfigurationRecords = !data.infoViewConfigurations.traverse { it.toRecord() }
            if (infoViewConfigurationRecords.isNotEmpty()) {
                !InfoViewConfigurationRepo.create(infoViewConfigurationRecords).orDie()
            }

            unit
        }
    }
}