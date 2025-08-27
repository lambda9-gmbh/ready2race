package de.lambda9.ready2race.backend.app.webDAV.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.app.competitionExecution.entity.StartListFileType
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.eventDocument.control.EventDocumentRepo
import de.lambda9.ready2race.backend.app.eventRegistration.control.EventRegistrationReportRepo
import de.lambda9.ready2race.backend.app.invoice.control.InvoiceRepo
import de.lambda9.ready2race.backend.app.results.boundary.ResultsService
import de.lambda9.ready2race.backend.app.results.control.ResultsRepo
import de.lambda9.ready2race.backend.app.webDAV.control.*
import de.lambda9.ready2race.backend.app.webDAV.entity.*
import de.lambda9.ready2race.backend.calls.requests.logger
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.config.Config
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportFolderRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.ready2race.backend.kio.accessConfig
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.failIf
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLConnection
import java.time.LocalDateTime
import java.util.*

object WebDAVService {

    fun initializeExportData(request: WebDAVExportRequest, userId: UUID): App<ServiceError, ApiResponse.NoData> =
        KIO.comprehension {

            val events = !EventRepo.getEvents(request.events).orDie()
                .failIf({ it.size != request.events.size }) { EventError.NotFound }
                .map { records ->
                    renameDuplicateNameEntities(records.associate { it.id to it.name })
                }


            val config = !accessConfig()
            if (config.webDAV == null) {
                return@comprehension KIO.fail(WebDAVError.ConfigIncomplete)
            }


            val exportProcess = !request.toRecord(userId)
            val processId = !WebDAVExportProcessRepo.create(exportProcess).orDie()


            fun getFolderName(documentType: WebDAVExportType): String {
                return when (documentType) {
                    WebDAVExportType.REGISTRATION_RESULTS -> "Registration-Result"
                    WebDAVExportType.INVOICES -> "Invoices"
                    WebDAVExportType.DOCUMENTS -> "Documents"
                    WebDAVExportType.RESULTS -> "Results"
                    WebDAVExportType.START_LISTS -> "Start-Lists"
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


            val client = OkHttpClient();
            val authHeader = getAuthHeader(config.webDAV)

            val checkFolderUrl = !buildUrl(
                webDAVConfig = config.webDAV,
                pathSegments = request.name
            ).mapError { WebDAVError.ConfigUnparsable }
            // Check if the root folder already exists on the server
            val checkFolderResult = !KIO.effect {
                client.newCall(
                    Request.Builder()
                        .url(checkFolderUrl)
                        .method("PROPFIND", null)
                        .header("Authorization", authHeader)
                        .build()
                ).execute().use { response -> response.code to response.body?.string() }
            }.mapError { WebDAVError.Unexpected }

            !KIO.failOn(checkFolderResult.first == 200 || checkFolderResult.first == 207) { WebDAVError.ExportFolderAlreadyExists }
            !KIO.failOn(checkFolderResult.first != 404) { WebDAVError.CannotMakeFolder("/") }


            // Create root folder
            !createFolder(
                path = "${request.name}/",
                client = client,
                webDAVConfig = config.webDAV
            )


            // GET FILES

            // todo start_lists ??
            // todo document_templates ? - maybe as json with the properties as well

            // todo: ?? handle case where files are not yet generated for the event
            val eventRegistrationIds =
                if (request.selectedResources.any { it == WebDAVExportType.REGISTRATION_RESULTS }) {
                    !EventRegistrationReportRepo.getExistingEventIds(events.keys.toList()).orDie()
                } else null

            val invoices = if (request.selectedResources.any { it == WebDAVExportType.INVOICES }) {
                !InvoiceRepo.getByEvents(events.keys.toList()).orDie().map { records ->
                    records.map { it.event!! to it.id!! }
                }
            } else null

            val eventDocuments = if (request.selectedResources.any { it == WebDAVExportType.DOCUMENTS }) {
                !EventDocumentRepo.getByEventIds(events.keys.toList()).orDie()
                    .map { records ->
                        records.map {
                            it.event to it.id
                        }
                    }
            } else null

            val eventsHavingResults = if (request.selectedResources.any { it == WebDAVExportType.RESULTS }) {
                !ResultsRepo.getEventsHavingResultsByEventIds(eventIds = events.keys.toList()).orDie()
                    .map { eventsHavingResults ->
                        eventsHavingResults.distinct()
                    }
            } else null

            val startListsMatchRecords = if (request.selectedResources.any { it == WebDAVExportType.START_LISTS }) {
                !CompetitionMatchRepo.getMatchForEventByEvents(events.keys.toList()).orDie()
            } else null


            // FOLDER RECORDS
            val exportFolderEventRecords: MutableList<WebdavExportFolderRecord> = mutableListOf()
            val exportFolderTypeRecords: MutableList<WebdavExportFolderRecord> = mutableListOf()
            val exportFolderCompetitionRecords: MutableList<WebdavExportFolderRecord> = mutableListOf()

            // EXPORT RECORDS
            val exportRecords: MutableList<WebdavExportRecord> = mutableListOf()

            events.forEach { (eventId, eventName) ->
                val pathStart = "${request.name}/$eventName"
                val eventFolder = buildExportFolderRecord(
                    parentFolder = null,
                    path = pathStart
                )
                exportFolderEventRecords.add(eventFolder)

                // REGISTRATION RESULTS
                if (eventRegistrationIds != null && eventRegistrationIds.any { it == eventId }) {
                    val folderRecord = buildExportFolderRecord(
                        parentFolder = eventFolder.id,
                        path = "$pathStart/${getFolderName(WebDAVExportType.REGISTRATION_RESULTS)}",
                    )
                    exportFolderTypeRecords.add(folderRecord)

                    exportRecords.add(
                        buildExportRecord(
                            eventId = eventId,
                            documentType = WebDAVExportType.REGISTRATION_RESULTS,
                            dataReference = eventId,
                            parentFolder = folderRecord.id
                        )
                    )
                }

                //INVOICES
                if (invoices != null && invoices.any { it.first == eventId }) {
                    val folderRecord = buildExportFolderRecord(
                        parentFolder = eventFolder.id,
                        path = "$pathStart/${getFolderName(WebDAVExportType.INVOICES)}",
                    )
                    exportFolderTypeRecords.add(folderRecord)

                    invoices.filter { it.first == eventId }.forEach { (_, invoiceId) ->
                        exportRecords.add(
                            buildExportRecord(
                                eventId = eventId,
                                documentType = WebDAVExportType.INVOICES,
                                dataReference = invoiceId,
                                parentFolder = folderRecord.id
                            )
                        )
                    }
                }

                // EVENT DOCUMENTS
                if (eventDocuments != null && eventDocuments.any { it.first == eventId }) {
                    val folderRecord = buildExportFolderRecord(
                        parentFolder = eventFolder.id,
                        path = "$pathStart/${getFolderName(WebDAVExportType.DOCUMENTS)}",
                    )
                    exportFolderTypeRecords.add(folderRecord)

                    eventDocuments.filter { it.first == eventId }.forEach { (_, documentId) ->
                        exportRecords.add(
                            buildExportRecord(
                                eventId = eventId,
                                documentType = WebDAVExportType.DOCUMENTS,
                                dataReference = documentId,
                                parentFolder = folderRecord.id
                            )
                        )
                    }
                }

                // RESULTS
                if (eventsHavingResults != null && eventsHavingResults.any { it == eventId }) {
                    val folderRecord = buildExportFolderRecord(
                        parentFolder = eventFolder.id,
                        path = "$pathStart/${getFolderName(WebDAVExportType.RESULTS)}",
                    )
                    exportFolderTypeRecords.add(folderRecord)

                    exportRecords.add(
                        buildExportRecord(
                            eventId = eventId,
                            documentType = WebDAVExportType.RESULTS,
                            dataReference = eventId,
                            parentFolder = folderRecord.id
                        )
                    )
                }

                // START LISTS
                val matchesForEvent = startListsMatchRecords?.filter { it.eventId == eventId }
                if (!matchesForEvent.isNullOrEmpty()) {
                    val typeFolderRecord = buildExportFolderRecord(
                        parentFolder = eventFolder.id,
                        path = "$pathStart/${getFolderName(WebDAVExportType.START_LISTS)}",
                    )
                    exportFolderTypeRecords.add(typeFolderRecord)


                    // Makes sure that the competition folder names are unique
                    val competitionFolderNames =
                        renameDuplicateNameEntities(matchesForEvent.groupBy { it.competitionId!! }
                            .mapValues { "${it.value.first().competitionIdentifier}-${it.value.first().competitionName}" })

                    matchesForEvent.groupBy { it.competitionId }.forEach { (competitionId, matchRecords) ->
                        // CREATE COMPETITION FOLDERS
                        val competitionFolderRecord = buildExportFolderRecord(
                            parentFolder = typeFolderRecord.id,
                            path = "$pathStart/${getFolderName(WebDAVExportType.START_LISTS)}/${competitionFolderNames[competitionId]}",
                        )
                        exportFolderCompetitionRecords.add(competitionFolderRecord)

                        // CREATE EXPORT RECORDS
                        matchRecords.forEach { match ->
                            exportRecords.add(
                                buildExportRecord(
                                    eventId = eventId,
                                    documentType = WebDAVExportType.START_LISTS,
                                    dataReference = match.matchId!!,
                                    parentFolder = competitionFolderRecord.id,
                                    additionalPath = "/${competitionFolderNames[competitionId]}"
                                )
                            )
                        }
                    }
                }
            }

            // CREATE EXPORT FOLDER QUEUE - Because of the parent_folder references it has to be in this order
            !WebDAVExportFolderRepo.create(exportFolderEventRecords).orDie()
            !WebDAVExportFolderRepo.create(exportFolderTypeRecords).orDie()
            !WebDAVExportFolderRepo.create(exportFolderCompetitionRecords).orDie()

            // CREATE EXPORT QUEUE
            !WebDAVExportRepo.create(exportRecords).orDie()

            noData
        }


    // Todo: If there is an error that will definitely stay - set an error to the other files to reduce load on server
    fun exportNext(): App<WebDAVError.WebDAVInternError, Unit> = KIO.comprehension {
        val config = !accessConfig()
        if (config.webDAV == null) {
            return@comprehension KIO.fail(WebDAVError.ConfigIncomplete)
        }

        val client = OkHttpClient()


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
                    it
                }
            !WebDAVExportFolderRepo.update(nextExportFolder.id!!) {
                doneAt = LocalDateTime.now()
            }.orDie()
            return@comprehension unit
        }


        // EXPORT FILE INTO FOLDER (if all folders are created already)
        val nextExport = !WebDAVExportRepo.getNextExport().orDie().onNullFail { WebDAVError.NoFilesToExport }

        fun setFileNotFoundError(msg: String? = "File not found") = WebDAVExportRepo.update(nextExport) {
            error = msg
            errorAt = LocalDateTime.now()
        }

        val file = nextExport.let { exportRecord ->
            when (exportRecord.documentType) {
                WebDAVExportType.REGISTRATION_RESULTS.name ->
                    !EventRegistrationReportRepo.getDownload(exportRecord.dataReference!!).orDie()
                        .onNullFail {
                            !setFileNotFoundError().orDie()
                            WebDAVError.FileNotFound(exportRecord.id, exportRecord.dataReference)
                        }
                        .map { File(name = it.name!!, bytes = it.data!!) }

                WebDAVExportType.INVOICES.name ->
                    !InvoiceRepo.getDownload(exportRecord.dataReference!!).orDie()
                        .onNullFail {
                            !setFileNotFoundError().orDie()
                            WebDAVError.FileNotFound(exportRecord.id, exportRecord.dataReference)
                        }
                        .map { File(name = it.filename!!, bytes = it.data!!) }

                WebDAVExportType.DOCUMENTS.name ->
                    !EventDocumentRepo.getDownload(exportRecord.dataReference!!).orDie()
                        .onNullFail {
                            !setFileNotFoundError().orDie()
                            WebDAVError.FileNotFound(exportRecord.id, exportRecord.dataReference)
                        }
                        .map { File(name = it.name!!, bytes = it.data!!) }

                WebDAVExportType.RESULTS.name ->
                    !ResultsService.generateResultsDocument(exportRecord.dataReference!!)
                        .mapError {
                            !setFileNotFoundError("Failed to generate document").orDie()
                            WebDAVError.FileNotFound(exportRecord.id, exportRecord.dataReference)
                        }

                WebDAVExportType.START_LISTS.name ->
                    !CompetitionExecutionService.downloadStartlist(
                        matchId = exportRecord.dataReference!!,
                        type = StartListFileType.PDF
                    ).mapError {
                        !setFileNotFoundError("Failed to generate document").orDie()
                        WebDAVError.FileNotFound(exportRecord.id, exportRecord.dataReference)
                    }
                        .map { File(name = it.name, bytes = it.bytes) }

                else -> return@comprehension KIO.fail(WebDAVError.FileNotFound(exportRecord.id, null))
            }
        }

        val authHeader = getAuthHeader(config.webDAV)
        val mimeType = URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"
        val requestBody = file.bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val url = !buildUrl(
            webDAVConfig = config.webDAV,
            pathSegments = "${nextExport.path}/${file.name}"
        ).mapError { WebDAVError.ConfigUnparsable }

        val (responseCode, responseMsg) = !KIO.effect {
            client.newCall(
                Request.Builder()
                    .url(url)
                    .put(requestBody)
                    .header("Authorization", authHeader)
                    .build()
            ).execute().use { response -> response.code to response.body?.string() }
        }.mapError {
            !WebDAVExportRepo.update(nextExport) {
                error = it.stackTraceToString()
                errorAt = LocalDateTime.now()
            }.orDie()
            WebDAVError.Unexpected
        }

        if (responseCode != 201) {
            !WebDAVExportRepo.update(nextExport) {
                error = "$responseCode - $responseMsg"
                errorAt = LocalDateTime.now()
            }.orDie()
            return@comprehension KIO.fail(
                WebDAVError.CannotTransferFile(
                    exportId = nextExport.id,
                    errorMsg = "$responseCode - $responseMsg"
                )
            )
        }

        !WebDAVExportRepo.update(nextExport) {
            exportedAt = LocalDateTime.now()
        }.orDie()

        unit
    }

    private fun buildUrl(
        webDAVConfig: Config.WebDAV,
        pathSegments: String
    ): App<Throwable, HttpUrl> = KIO.comprehension {
        KIO.ok(
            HttpUrl.Builder()
                .scheme(webDAVConfig.urlScheme)
                .host(webDAVConfig.host)
                .addPathSegment(webDAVConfig.path)
                .addPathSegments("remote.php/dav/files/${webDAVConfig.authUser}/$pathSegments")
                .build()
        )
    }

    private fun getAuthHeader(webDAVConfig: Config.WebDAV): String {
        return Credentials.basic(webDAVConfig.authUser, webDAVConfig.authPassword)
    }

    private fun createFolder(
        client: OkHttpClient,
        path: String,
        webDAVConfig: Config.WebDAV
    ): App<WebDAVError.WebDavInternExternError, Unit> =
        KIO.comprehension {
            val folderUrl = !buildUrl(
                webDAVConfig = webDAVConfig,
                pathSegments = path
            ).mapError { WebDAVError.ConfigUnparsable }

            val response = !KIO.effect {
                client.newCall(
                    Request.Builder()
                        .url(folderUrl)
                        .method("MKCOL", null)
                        .header("Authorization", getAuthHeader(webDAVConfig))
                        .build()
                ).execute().use { response -> response.code to response.body?.string() }
            }.mapError { WebDAVError.Unexpected }

            !KIO.failOn(response.first != 201) {
                WebDAVError.CannotMakeFolder(folderPath = path)
            }

            unit
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

    fun getExportStatus(): App<Nothing, ApiResponse.ListDto<WebDAVExportStatusDto>> = KIO.comprehension {

        val records = !WebDAVExportProcessRepo.all().orDie()

        KIO.ok(
            ApiResponse.ListDto(
                !records.traverse { record ->
                    val fileExports = record.fileExports!!.filterNotNull()

                    val events = fileExports.groupBy { it.eventName }.keys.toList() // TODO EVENT NAME MUST BE UNIQUE
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
}