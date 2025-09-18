package de.lambda9.ready2race.backend.app.webDAV.boundary

import com.fasterxml.jackson.databind.JsonNode
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competition.control.CompetitionRepo
import de.lambda9.ready2race.backend.app.competition.entity.CompetitionError
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionExecutionError
import de.lambda9.ready2race.backend.app.competitionExecution.entity.StartListFileType
import de.lambda9.ready2race.backend.app.competitionSetup.control.*
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.eventDocument.control.EventDocumentRepo
import de.lambda9.ready2race.backend.app.eventRegistration.control.EventRegistrationReportRepo
import de.lambda9.ready2race.backend.app.invoice.control.InvoiceRepo
import de.lambda9.ready2race.backend.app.results.boundary.ResultsService
import de.lambda9.ready2race.backend.app.results.control.ResultsRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.checkRequestTypeDependencies
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.webDAVExportTypeDependencies
import de.lambda9.ready2race.backend.app.webDAV.control.*
import de.lambda9.ready2race.backend.app.webDAV.entity.*
import de.lambda9.ready2race.backend.app.webDAV.entity.bankAccounts.DataBankAccountsExport
import de.lambda9.ready2race.backend.app.webDAV.entity.competition.DataCompetitionExport
import de.lambda9.ready2race.backend.app.webDAV.entity.competitionCategories.DataCompetitionCategoriesExport
import de.lambda9.ready2race.backend.app.webDAV.entity.competitionSetup.CompetitionSetupDataType
import de.lambda9.ready2race.backend.app.webDAV.entity.competitionSetupTemplates.DataCompetitionSetupTemplatesExport
import de.lambda9.ready2race.backend.app.webDAV.entity.competitionTemplates.DataCompetitionTemplatesExport
import de.lambda9.ready2race.backend.app.webDAV.entity.contactInformation.DataContactInformationExport
import de.lambda9.ready2race.backend.app.webDAV.entity.emailIndividualTemplates.DataEmailIndividualTemplatesExport
import de.lambda9.ready2race.backend.app.webDAV.entity.event.DataEventExport
import de.lambda9.ready2race.backend.app.webDAV.entity.eventDocumentTypes.DataEventDocumentTypesExport
import de.lambda9.ready2race.backend.app.webDAV.entity.fees.DataFeesExport
import de.lambda9.ready2race.backend.app.webDAV.entity.matchResultsImportConfigs.DataMatchResultImportConfigsExport
import de.lambda9.ready2race.backend.app.webDAV.entity.namedParticipants.DataNamedParticipantsExport
import de.lambda9.ready2race.backend.app.webDAV.entity.participantRequirements.DataParticipantRequirementsExport
import de.lambda9.ready2race.backend.app.webDAV.entity.participants.DataParticipantsExport
import de.lambda9.ready2race.backend.app.webDAV.entity.ratingCategories.DataRatingCategoriesExport
import de.lambda9.ready2race.backend.app.webDAV.entity.startlistExportConfigs.DataStartlistExportConfigsExport
import de.lambda9.ready2race.backend.app.webDAV.entity.users.DataUsersExport
import de.lambda9.ready2race.backend.app.webDAV.entity.workTypes.DataWorkTypesExport
import de.lambda9.ready2race.backend.calls.comprehension.CallComprehensionScope
import de.lambda9.ready2race.backend.calls.requests.logger
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.calls.serialization.jsonMapper
import de.lambda9.ready2race.backend.config.Config
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDependencyRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportFolderRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.ready2race.backend.kio.accessConfig
import de.lambda9.ready2race.backend.kio.comprehension
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.failIf
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.coroutineScope
import java.net.URLConnection
import java.time.LocalDateTime
import java.util.*

object WebDAVExportService {

    suspend fun CallComprehensionScope.initializeExportData(
        request: WebDAVExportRequest,
        userId: UUID
    ): App<ServiceError, ApiResponse.NoData> {

        val events = !EventRepo.getEvents(request.events.map { it.eventId }).orDie()
            .failIf({ it.size != request.events.size }) { EventError.NotFound }
            .map { records ->
                renameDuplicateNameEntities(records.associate { it.id to it.name })
            }

        // Checks if exportTypes that have other types depending on them are present
        !checkRequestTypeDependencies((request.selectedDatabaseExports + request.events.flatMap { it.selectedExports }))

        val config = !accessConfig()
        !KIO.failOn(config.webDAV == null) { WebDAVError.ConfigIncomplete }
        if (config.webDAV == null) {
            return KIO.fail(WebDAVError.ConfigIncomplete)
        }


        val exportProcess = !request.toRecord(userId)
        val processId = !WebDAVExportProcessRepo.create(exportProcess).orDie()


        fun getFolderName(documentType: WebDAVExportType): String? {
            return when (documentType) {
                WebDAVExportType.REGISTRATION_RESULTS -> "Registration-Result"
                WebDAVExportType.INVOICES -> "Invoices"
                WebDAVExportType.DOCUMENTS -> "Documents"
                WebDAVExportType.RESULTS -> "Results"
                WebDAVExportType.DB_COMPETITION, WebDAVExportType.START_LISTS -> "Competitions"
                else -> null
            }
        }

        fun buildExportFolderRecord(
            parentFolder: UUID?,
            path: String,
        ): WebdavExportFolderRecord {
            return WebdavExportFolderRecord(
                id = UUID.randomUUID(),
                webdavExportProcess = processId,
                parentFolder = parentFolder,
                path = path,
            )
        }

        fun buildExportRecord(
            eventId: UUID,
            documentType: WebDAVExportType,
            dataReference: UUID,
            parentFolder: UUID?,
            additionalPath: String? = "",
        ): WebdavExportRecord {
            return WebdavExportRecord(
                id = UUID.randomUUID(),
                webdavExportProcess = processId,
                eventName = events[eventId] ?: "",
                documentType = documentType.name,
                dataReference = dataReference,
                path = "${request.name}/${events[eventId]}/${getFolderName(documentType)}" + additionalPath,
                parentFolder = parentFolder
            )
        }


        val client = HttpClient(CIO)
        val authHeader = WebDAVService.buildBasicAuthHeader(config.webDAV)


        // Check if the root folder already exists on the server

        val checkFolderUrl = WebDAVService.getUrl(
            webDAVConfig = config.webDAV,
            pathSegments = request.name
        )

        val checkFolderResponse =
            client.request(checkFolderUrl) {
                method = HttpMethod("PROPFIND")
                header("Authorization", authHeader)
            }

        !KIO.failOn(checkFolderResponse.status.isSuccess()) {
            client.close()
            WebDAVError.ExportFolderAlreadyExists
        }
        val responseMsg = checkFolderResponse.bodyAsText()
        !KIO.failOn(checkFolderResponse.status.value != 404) {
            client.close()
            WebDAVError.CannotMakeFolder("/", responseMsg)
        }


        // Create root folder
        !createFolder(
            path = request.name,
            client = client,
            webDAVConfig = config.webDAV
        )

        val requestedDocs = listOf(
            WebDAVExportType.REGISTRATION_RESULTS,
            WebDAVExportType.INVOICES,
            WebDAVExportType.DOCUMENTS,
            WebDAVExportType.RESULTS,
            WebDAVExportType.START_LISTS,
            WebDAVExportType.DB_EVENT,
        ).associateWith { type -> request.events.filter { it.selectedExports.contains(type) }.map { it.eventId } }


        val eventRegistrationIds = requestedDocs[WebDAVExportType.REGISTRATION_RESULTS]!!.let { eventsRequesting ->
            if (eventsRequesting.isNotEmpty()) {
                !EventRegistrationReportRepo.getExistingEventIds(requestedDocs[WebDAVExportType.REGISTRATION_RESULTS]!!)
                    .orDie()
            } else emptyList()
        }


        val invoices = requestedDocs[WebDAVExportType.INVOICES]!!.let { eventsRequesting ->
            if (eventsRequesting.isNotEmpty()) {
                !InvoiceRepo.getByEvents(requestedDocs[WebDAVExportType.INVOICES]!!).orDie()
                    .map { records ->
                        records.map { it.event!! to it.id!! }
                    }
            } else emptyList()
        }

        val eventDocuments = requestedDocs[WebDAVExportType.DOCUMENTS]!!.let { eventsRequesting ->
            if (eventsRequesting.isNotEmpty()) {
                !EventDocumentRepo.getByEventIds(requestedDocs[WebDAVExportType.DOCUMENTS]!!).orDie()
                    .map { records ->
                        records.map {
                            it.event to it.id
                        }
                    }
            } else emptyList()
        }


        val eventsHavingResults = requestedDocs[WebDAVExportType.RESULTS]!!.let { eventsRequesting ->
            if (eventsRequesting.isNotEmpty()) {
                !ResultsRepo.getEventsHavingResultsByEventIds(requestedDocs[WebDAVExportType.RESULTS]!!).orDie()
                    .map { it.distinct() }
            } else emptyList()
        }


        val startListsMatchRecords = requestedDocs[WebDAVExportType.START_LISTS]!!.let { eventsRequesting ->
            if (eventsRequesting.isNotEmpty()) {
                !CompetitionMatchRepo
                    .getMatchForEventByEvents(requestedDocs[WebDAVExportType.START_LISTS]!!)
                    .orDie()
                    .map { matches -> matches.filter { it.teams!!.isNotEmpty() } } // Filter matches with no teams (with deregistered teams) - otherwise that error only appears at the creation of the startlist
            } else emptyList()
        }

        val selectedCompetitions = request.events.flatMap { it.selectedCompetitions }
        val existingCompetitions = !CompetitionRepo.getExisting(selectedCompetitions).orDie()
        !KIO.failOn(existingCompetitions.size != selectedCompetitions.size) {
            client.close()
            CompetitionError.CompetitionNotFound
        }

        // FOLDER RECORDS
        val exportFolderEventRecords: MutableList<WebdavExportFolderRecord> = mutableListOf()
        val exportFolderTypeRecords: MutableList<WebdavExportFolderRecord> = mutableListOf()
        val exportFolderCompetitionRecords: MutableList<WebdavExportFolderRecord> = mutableListOf()

        // EXPORT RECORDS
        val exportRecords: MutableList<WebdavExportRecord> = mutableListOf()

        // EXPORT DATA RECORDS
        val exportDataRecords: MutableList<Pair<WebDAVExportType, WebdavExportDataRecord>> = mutableListOf()

        events.forEach { (eventId, eventName) ->
            val pathStart = "${request.name}/$eventName"
            val eventFolder = buildExportFolderRecord(
                parentFolder = null,
                path = pathStart
            )
            exportFolderEventRecords.add(eventFolder)

            fun addExportDocFolderRecord(type: WebDAVExportType): WebdavExportFolderRecord {
                val record = buildExportFolderRecord(
                    parentFolder = eventFolder.id,
                    path = "$pathStart/${getFolderName(type)}",
                )
                exportFolderTypeRecords.add(record)
                return record
            }

            fun addExportRecord(type: WebDAVExportType, reference: UUID, parentFolderId: UUID) {
                exportRecords.add(
                    buildExportRecord(
                        eventId = eventId,
                        documentType = type,
                        dataReference = reference,
                        parentFolder = parentFolderId
                    )
                )
            }

            // REGISTRATION RESULTS
            if (eventRegistrationIds.contains(eventId)) {
                val folderRecord = addExportDocFolderRecord(WebDAVExportType.REGISTRATION_RESULTS)
                addExportRecord(WebDAVExportType.REGISTRATION_RESULTS, eventId, folderRecord.id)
            }

            //INVOICES
            if (invoices.any { it.first == eventId }) {
                val folderRecord = addExportDocFolderRecord(WebDAVExportType.INVOICES)

                invoices.filter { it.first == eventId }.forEach { (_, invoiceId) ->
                    addExportRecord(WebDAVExportType.INVOICES, invoiceId, folderRecord.id)
                }
            }

            // EVENT DOCUMENTS
            if (eventDocuments.any { it.first == eventId }) {
                val folderRecord = addExportDocFolderRecord(WebDAVExportType.DOCUMENTS)

                eventDocuments.filter { it.first == eventId }.forEach { (_, documentId) ->
                    addExportRecord(WebDAVExportType.DOCUMENTS, documentId, folderRecord.id)
                }
            }

            // RESULTS
            if (eventsHavingResults.contains(eventId)) {
                val folderRecord = addExportDocFolderRecord(WebDAVExportType.RESULTS)
                addExportRecord(WebDAVExportType.RESULTS, eventId, folderRecord.id)
            }


            // START LISTS and COMPETITIONS

            data class CompetitionFolderNameComponents(
                val id: UUID,
                val identifier: String,
                val name: String
            )

            // DB_COMPETITION
            val selectedDataCompetitions = existingCompetitions.filter { it.event == eventId }
            val competitionDataNameComponents = selectedDataCompetitions.map {
                CompetitionFolderNameComponents(id = it.id!!, identifier = it.identifier!!, name = it.name!!)
            }
            val competitionDataIds = competitionDataNameComponents.map { it.id }


            // START_LISTS (matches)
            val matchesForEvent = startListsMatchRecords.filter { it.eventId == eventId }
            val startListCompetitionNameCompetitions = matchesForEvent.groupBy { it.competitionId!! }.mapValues {
                CompetitionFolderNameComponents(
                    id = it.key,
                    identifier = it.value.first().competitionIdentifier!!,
                    name = it.value.first().competitionName!!
                )
            }.values.toList()

            // Combine the DB_COMPETITION and START_LISTS competitions
            val competitionNameComponents =
                (competitionDataNameComponents + startListCompetitionNameCompetitions.filter {
                    !competitionDataIds.contains(it.id)
                })

            // Makes sure that the competition folder names are unique
            val competitionFolderNames =
                renameDuplicateNameEntities(competitionNameComponents.associate { it.id to "${it.identifier}-${it.name}" })

            if (competitionFolderNames.isNotEmpty()) {
                val competitionsTypeFolderRecord = addExportDocFolderRecord(WebDAVExportType.DB_COMPETITION)

                // FOLDERS
                val competitionFolderRecords = competitionFolderNames.mapValues {
                    buildExportFolderRecord(
                        parentFolder = competitionsTypeFolderRecord.id,
                        path = "${competitionsTypeFolderRecord.path}/${it.value}",
                    )
                }
                exportFolderCompetitionRecords.addAll(competitionFolderRecords.values)

                // START LISTS
                matchesForEvent.groupBy { it.competitionId }.forEach { (competitionId, matchRecords) ->
                    matchRecords.forEach { match ->
                        exportRecords.add(
                            buildExportRecord(
                                eventId = eventId,
                                documentType = WebDAVExportType.START_LISTS,
                                dataReference = match.matchId!!,
                                parentFolder = competitionFolderRecords[competitionId]!!.id,
                                additionalPath = "/${competitionFolderNames[competitionId]}"
                            )
                        )
                    }
                }

                // COMPETITION DATA
                selectedDataCompetitions.forEach { competition ->
                    exportDataRecords.add(
                        WebDAVExportType.DB_COMPETITION to WebdavExportDataRecord(
                            id = UUID.randomUUID(),
                            webdavExportProcess = processId,
                            documentType = WebDAVExportType.DB_COMPETITION.name,
                            dataReference = competition.id,
                            path = "$pathStart/${getFolderName(WebDAVExportType.DB_COMPETITION)}/${competitionFolderNames[competition.id]}",
                            parentFolder = competitionFolderRecords[competition.id]!!.id
                        )
                    )
                }
            }


            // DB_EVENTS EXPORT
            if (requestedDocs[WebDAVExportType.DB_EVENT]!!.contains(eventId)) {
                exportDataRecords.add(
                    WebDAVExportType.DB_EVENT to WebdavExportDataRecord(
                        id = UUID.randomUUID(),
                        webdavExportProcess = processId,
                        documentType = WebDAVExportType.DB_EVENT.name,
                        dataReference = eventId,
                        path = pathStart,
                        parentFolder = eventFolder.id
                    )
                )
            }


        }

        // CREATE EXPORT FOLDER QUEUE - Because of the parent_folder references it has to be in this order
        !WebDAVExportFolderRepo.create(exportFolderEventRecords).orDie()
        !WebDAVExportFolderRepo.create(exportFolderTypeRecords).orDie()
        !WebDAVExportFolderRepo.create(exportFolderCompetitionRecords).orDie()

        // CREATE EXPORT QUEUE
        !WebDAVExportRepo.create(exportRecords).orDie()


        // ---------------------- DATABASE EXPORTS --------------------------

        val databaseExportTypes = request.selectedDatabaseExports.filter {
            it.name.startsWith("DB_")
        }

        if (databaseExportTypes.isNotEmpty()) {
            val manifestFile = !ManifestExport.createExportFile(databaseExportTypes)
            val response = sendFile(client, config.webDAV, manifestFile, path = "${request.name}/${manifestFile.name}")
            val content = if (!response.status.isSuccess()) response.bodyAsText() else null
            !KIO.failOn(!response.status.isSuccess()) {
                logger.error { "Export of manifest.json was unsuccessful. $content" }
                client.close()
                WebDAVError.ManifestExportFailed
            }
        }

        // add exportData records
        exportDataRecords.addAll(
            databaseExportTypes.map {
                it to
                    WebdavExportDataRecord(
                        id = UUID.randomUUID(),
                        webdavExportProcess = processId,
                        documentType = it.toString(),
                        dataReference = null,
                        path = request.name,
                    )
            })
        // create all exportData entries (including event and competition)
        !WebDAVExportDataRepo.create(exportDataRecords.map { (_, exportDataRecords) -> exportDataRecords }).orDie()

        // add a dependency for each record if it is dependent on other records.
        val dependencyRecords = exportDataRecords.flatMap { (exportType, exportRecord) ->
            webDAVExportTypeDependencies[exportType]?.flatMap { dependentOnType ->
                // There could be multiple records of one DB_ type like COMPETITION - so we get all records with that type
                val dependentOnRecords = exportDataRecords.filter { it.first == dependentOnType }.map { it.second }
                dependentOnRecords.map { dependentOnRecord ->
                    WebdavExportDependencyRecord(
                        webdavExportData = exportRecord.id,
                        dependingOn = dependentOnRecord.id
                    )
                }

            } ?: emptyList()
        }
        !WebDAVExportDependencyRepo.create(dependencyRecords).orDie()


        client.close()

        return noData
    }

    // Todo: @evaluate if a rollback might be a good option in case of errors (except for the error update in the records)
    // Todo: Set errors to dependent export_data entries if the depending_on export fails
    // Todo: If there is an error that will definitely stay - set an error to the other files to reduce load on server
    suspend fun exportNext(env: JEnv): App<WebDAVError.WebDAVInternError, Unit> =
        coroutineScope {
            comprehension(env) {
                val config = !accessConfig()
                if (config.webDAV == null) {
                    return@comprehension KIO.fail(WebDAVError.ConfigIncomplete)
                }

                val client = HttpClient(CIO)

                // CREATE FOLDER

                val nextExportFolder = !WebDAVExportFolderRepo.getNextFolder().orDie()
                if (nextExportFolder != null) {
                    !createFolder(path = nextExportFolder.path!!, webDAVConfig = config.webDAV, client = client)
                        .mapError {
                            !WebDAVExportFolderRepo.update(nextExportFolder.id!!) {
                                error = it.message
                                errorAt = LocalDateTime.now()
                            }.orDie()
                            !setErrorOnChildrenOfFolder(nextExportFolder.id!!)
                            client.close()
                            it
                        }
                    !WebDAVExportFolderRepo.update(nextExportFolder.id!!) {
                        doneAt = LocalDateTime.now()
                    }.orDie()
                    client.close()
                    return@comprehension unit
                }


                // EXPORT FILE INTO FOLDER (if all folders are created already)

                val nextExport = !WebDAVExportRepo.getNextExport().orDie()

                val nextDataExport = if (nextExport == null) {
                    !WebDAVExportDataRepo.getNextExport().orDie()
                } else null

                // DOCUMENTS etc.
                val file = if (nextExport != null) {
                    fun setFileNotFoundError(msg: String? = "File not found") = WebDAVExportRepo.update(nextExport) {
                        error = msg
                        errorAt = LocalDateTime.now()
                    }.orDie().map {
                        client.close()
                        WebDAVError.FileNotFound(it.id, it.dataReference)
                    }

                    nextExport.let { exportRecord ->
                        when (exportRecord.documentType) {
                            WebDAVExportType.REGISTRATION_RESULTS.name ->
                                !EventRegistrationReportRepo.getDownload(exportRecord.dataReference!!).orDie()
                                    .onNullFail {
                                        !setFileNotFoundError()
                                    }
                                    .map { File(name = it.name!!, bytes = it.data!!) }

                            WebDAVExportType.INVOICES.name ->
                                !InvoiceRepo.getDownload(exportRecord.dataReference!!).orDie()
                                    .onNullFail {
                                        !setFileNotFoundError()
                                    }
                                    .map { File(name = it.filename!!, bytes = it.data!!) }

                            WebDAVExportType.DOCUMENTS.name ->
                                !EventDocumentRepo.getDownload(exportRecord.dataReference!!).orDie()
                                    .onNullFail {
                                        !setFileNotFoundError()
                                    }
                                    .map { File(name = it.name!!, bytes = it.data!!) }

                            WebDAVExportType.RESULTS.name ->
                                !ResultsService.generateResultsDocument(exportRecord.dataReference!!)
                                    .mapError {
                                        !setFileNotFoundError("Failed to generate document")
                                    }

                            WebDAVExportType.START_LISTS.name ->
                                !CompetitionExecutionService.getStartList(
                                    matchId = exportRecord.dataReference!!,
                                    startListType = StartListFileType.PDF,
                                    startTimeRequired = false
                                ).mapError {
                                    !setFileNotFoundError("Failed to generate document")
                                }
                                    .map { File(name = it.name, bytes = it.bytes) }

                            else -> {
                                return@comprehension KIO.fail(!setFileNotFoundError())
                            }
                        }
                    }
                }
                // DATABASE EXPORTS
                else if (nextDataExport != null) {

                    when (nextDataExport.documentType) {
                        WebDAVExportType.DB_USERS.name -> {
                            !DataUsersExport.createExportFile(nextDataExport)
                        }

                        WebDAVExportType.DB_PARTICIPANTS.name -> {
                            !DataParticipantsExport.createExportFile(nextDataExport)
                        }

                        WebDAVExportType.DB_BANK_ACCOUNTS.name -> {
                            !DataBankAccountsExport.createExportFile(nextDataExport)
                        }

                        WebDAVExportType.DB_CONTACT_INFORMATION.name -> {
                            !DataContactInformationExport.createExportFile(nextDataExport)
                        }

                        WebDAVExportType.DB_EMAIL_INDIVIDUAL_TEMPLATES.name -> {
                            !DataEmailIndividualTemplatesExport.createExportFile(nextDataExport)
                        }

                        WebDAVExportType.DB_EVENT_DOCUMENT_TYPES.name -> {
                            !DataEventDocumentTypesExport.createExportFile(nextDataExport)
                        }

                        WebDAVExportType.DB_MATCH_RESULT_IMPORT_CONFIGS.name -> {
                            !DataMatchResultImportConfigsExport.createExportFile(nextDataExport)
                        }

                        WebDAVExportType.DB_STARTLIST_EXPORT_CONFIGS.name -> {
                            !DataStartlistExportConfigsExport.createExportFile(nextDataExport)
                        }

                        WebDAVExportType.DB_WORK_TYPES.name -> {
                            !DataWorkTypesExport.createExportFile(nextDataExport)
                        }

                        WebDAVExportType.DB_PARTICIPANT_REQUIREMENTS.name -> {
                            !DataParticipantRequirementsExport.createExportFile(nextDataExport)
                        }

                        WebDAVExportType.DB_RATING_CATEGORIES.name -> {
                            !DataRatingCategoriesExport.createExportFile(nextDataExport)
                        }

                        WebDAVExportType.DB_COMPETITION_CATEGORIES.name -> {
                            !DataCompetitionCategoriesExport.createExportFile(nextDataExport)
                        }

                        WebDAVExportType.DB_FEES.name -> {
                            !DataFeesExport.createExportFile(nextDataExport)
                        }

                        WebDAVExportType.DB_NAMED_PARTICIPANTS.name -> {
                            !DataNamedParticipantsExport.createExportFile(nextDataExport)
                        }

                        WebDAVExportType.DB_COMPETITION_SETUP_TEMPLATES.name -> {
                            !DataCompetitionSetupTemplatesExport.createExportFile(nextDataExport)
                        }

                        WebDAVExportType.DB_COMPETITION_TEMPLATES.name -> {
                            !DataCompetitionTemplatesExport.createExportFile(nextDataExport)
                        }

                        WebDAVExportType.DB_EVENT.name -> {
                            !DataEventExport.createExportFile(nextDataExport)
                        }

                        WebDAVExportType.DB_COMPETITION.name -> {
                            !DataCompetitionExport.createExportFile(nextDataExport)
                        }

                        else -> {
                            !setErrorOnDataExport(nextDataExport, "Unknown export type")
                            client.close()
                            return@comprehension KIO.fail(
                                WebDAVError.FileNotFound(
                                    nextDataExport.id,
                                    nextDataExport.dataReference
                                )
                            )
                        }
                    }
                } else {
                    client.close()
                    return@comprehension KIO.fail(WebDAVError.NoFilesToExport)
                }


                val path = if (nextExport != null) {
                    "${nextExport.path}/${file.name}"
                } else {
                    "${nextDataExport!!.path}/${file.name}"
                }

                val response = sendFile(client, config.webDAV, file, path)

                if (!response.status.isSuccess()) {
                    val content = response.bodyAsText()
                    if (nextExport != null) {
                        !WebDAVExportRepo.update(nextExport) {
                            error = "${response.status.value} - $content"
                            errorAt = LocalDateTime.now()
                        }.orDie()

                    } else {
                        !setErrorOnDataExport(nextDataExport!!, "${response.status.value} - $content")
                    }
                    client.close()
                    return@comprehension KIO.fail(
                        WebDAVError.CannotTransferFile(
                            exportId = nextExport.let { it?.id ?: nextDataExport!!.id },
                            errorMsg = "${response.status.value} - $content"
                        )
                    )
                }

                if (nextExport != null) {
                    !WebDAVExportRepo.update(nextExport) {
                        exportedAt = LocalDateTime.now()
                    }.orDie()
                } else {
                    !WebDAVExportDataRepo.update(nextDataExport!!) {
                        exportedAt = LocalDateTime.now()
                    }.orDie()
                }

                client.close()

                unit
            }
        }

    private suspend fun sendFile(client: HttpClient, config: Config.WebDAV, file: File, path: String): HttpResponse =
        coroutineScope {
            val authHeader = WebDAVService.buildBasicAuthHeader(config)
            val mimeType = URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"

            val url = WebDAVService.getUrl(
                webDAVConfig = config,
                pathSegments = path
            )

            client.put(url) {
                method = HttpMethod("PROPFIND")
                header("Authorization", authHeader)
                setBody(file.bytes)
                contentType(ContentType.parse(mimeType))
            }
        }

    private suspend fun createFolder(
        client: HttpClient,
        path: String,
        webDAVConfig: Config.WebDAV
    ): App<WebDAVError.WebDavInternExternError, Unit> =
        coroutineScope {

            try {
                val folderUrl = WebDAVService.getUrl(
                    webDAVConfig = webDAVConfig,
                    pathSegments = path
                )

                val response = client.request(folderUrl) {
                    method = HttpMethod("MKCOL")
                    header("Authorization", WebDAVService.buildBasicAuthHeader(webDAVConfig))
                }

                if (response.status.isSuccess()) {
                    unit
                } else {
                    val responseMsg = response.bodyAsText()
                    KIO.fail(WebDAVError.CannotMakeFolder(path, responseMsg))
                }
            } catch (ex: Exception) {
                KIO.fail(WebDAVError.Unexpected(ex.stackTraceToString()))
            }
        }

    private fun setErrorOnChildrenOfFolder(parentFolderId: UUID): App<Nothing, Unit> = KIO.comprehension {

        !WebDAVExportRepo.updateManyByParentFolderId(parentFolderId) {
            errorAt = LocalDateTime.now()
            error = "Error in parent folder: $parentFolderId"
        }.orDie()

        val childFolderRecords = !WebDAVExportFolderRepo.getByParentFolderId(parentFolderId).orDie()
        !WebDAVExportFolderRepo.updateMany(childFolderRecords) {
            errorAt = LocalDateTime.now()
            error = "Error in parent folder: $parentFolderId"
        }.orDie()

        childFolderRecords.forEach { childFolder ->
            !setErrorOnChildrenOfFolder(childFolder.id)
        }

        unit
    }

    private fun renameDuplicateNameEntities(entities: Map<UUID, String>): Map<UUID, String> {
        return entities.mapValues { (id, name) ->
            val entitiesWithName = entities.filter { it.value == name }
            if (entitiesWithName.size == 1
            ) {
                name
            } else { // This covers the case of multiple entities having the same name
                "${name}-${entitiesWithName.keys.toList().indexOf(id) + 1}"
            }
        }
    }

    // Similar function in WebDAVImportService
    private fun setErrorOnDataExport(dataExport: WebdavExportDataRecord, errorMsg: String): App<Nothing, Unit> =
        KIO.comprehension {
            !WebDAVExportDataRepo.update(dataExport) {
                error = errorMsg
                errorAt = LocalDateTime.now()
            }.orDie()
            setErrorOnDependentDataExports(dataExport.id, errorMsg)
        }

    private fun setErrorOnDependentDataExports(
        failedExportId: UUID,
        errorMessage: String
    ): App<Nothing, Unit> = KIO.comprehension {
        // Update all exports that depend on this failed export and get the updated records
        val dependentRecords = !WebDAVExportDataRepo.updateByDependingOnId(failedExportId) {
            errorAt = LocalDateTime.now()
            error = "Dependency with id $failedExportId failed: $errorMessage"
        }.orDie()

        // Recursively set errors on dependencies of dependencies
        dependentRecords.forEach { record ->
            !setErrorOnDependentDataExports(record.id, errorMessage)
        }

        unit
    }

    fun serializeDataExportNew(
        record: WebdavExportDataRecord,
        exportData: Map<String, String>, // key to json
    ): App<WebDAVError.WebDAVInternError, ByteArray> = KIO.comprehension {
        try {
            val combinedJson = mutableMapOf<String, JsonNode>()

            exportData.forEach { (key, jsonString) ->
                val parsedJson = jsonMapper.readTree(jsonString)
                combinedJson[key] = parsedJson
            }

            val json = jsonMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(combinedJson)
                .toByteArray()
            KIO.ok(json)
        } catch (e: Exception) {
            !setErrorOnDataExport(record, "Failed to serialize: ${e.message}")
            KIO.fail(
                WebDAVError.FileNotFound(
                    exportId = record.id,
                    referenceId = record.dataReference
                )
            )
        }
    }

    fun getExportStatus(): App<Nothing, ApiResponse.ListDto<WebDAVExportStatusDto>> = KIO.comprehension {

        val records = !WebDAVExportProcessRepo.all().orDie()

        KIO.ok(
            ApiResponse.ListDto(
                !records.traverse { record ->
                    val fileExports = record.fileExports!!.filterNotNull()

                    val events = fileExports.groupBy { it.eventName }.keys.toList()
                    val exportTypes = fileExports
                        .groupBy { it.documentType }.keys.toList()
                        .map { WebDAVExportType.valueOf(it) }
                    val filesExported = fileExports.filter { it.exportedAt != null }.size
                    val filesWithError = fileExports.filter { it.errorAt != null }.size

                    record.toDto(
                        events = events,
                        exportTypes = exportTypes,
                        filesExported = filesExported,
                        filesWithError = filesWithError
                    )
                })
        )
    }

    fun getSetupRoundsWithDependenciesAsJson(templateOrSetupIds: List<UUID>): App<Nothing, Map<CompetitionSetupDataType, String>> =
        KIO.comprehension {
            val rounds = !CompetitionSetupRoundRepo.getBySetupIdsAsJson(templateOrSetupIds).orDie()
            val roundIds = !CompetitionSetupRoundRepo.getIdsBySetupIds(templateOrSetupIds).orDie()

            // get by rounds
            val evaluations = !CompetitionSetupGroupStatisticEvaluationRepo.getAsJson(roundIds).orDie()
            val places = !CompetitionSetupPlaceRepo.getAsJson(roundIds).orDie()
            val matches = !CompetitionSetupMatchRepo.getAsJson(roundIds).orDie()

            val matchRecords = !CompetitionSetupMatchRepo.get(roundIds).orDie()

            // get by groupIds in matches
            val groupIds =
                !CompetitionSetupGroupRepo.getIds(matchRecords.mapNotNull { it.competitionSetupGroup }).orDie()
            val groups =
                !CompetitionSetupGroupRepo.getAsJson(matchRecords.mapNotNull { it.competitionSetupGroup }).orDie()

            // get by matches and groups
            val participants =
                !CompetitionSetupParticipantRepo.getAsJson((matchRecords.map { it.id } + groupIds))
                    .orDie()

            KIO.ok(
                mapOf(
                    CompetitionSetupDataType.ROUNDS to rounds,
                    CompetitionSetupDataType.GROUPS to groups,
                    CompetitionSetupDataType.EVALUATIONS to evaluations,
                    CompetitionSetupDataType.MATCHES to matches,
                    CompetitionSetupDataType.PARTICIPANTS to participants,
                    CompetitionSetupDataType.PLACES to places,
                )
            )
        }
}