package de.lambda9.ready2race.backend.app.webDAV.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.appuser.control.AppUserRepo
import de.lambda9.ready2race.backend.app.club.control.ClubRepo
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
import de.lambda9.ready2race.backend.app.role.control.RoleRepo
import de.lambda9.ready2race.backend.app.webDAV.control.*
import de.lambda9.ready2race.backend.app.webDAV.entity.*
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

object WebDAVService {


    suspend fun CallComprehensionScope.initializeExportData(
        request: WebDAVExportRequest,
        userId: UUID
    ): App<ServiceError, ApiResponse.NoData> {

        val events = !EventRepo.getEvents(request.events).orDie()
            .failIf({ it.size != request.events.size }) { EventError.NotFound }
            .map { records ->
                renameDuplicateNameEntities(records.associate { it.id to it.name })
            }


        val config = !accessConfig()
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
                WebDAVExportType.START_LISTS -> "Start-Lists"
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
        val authHeader = buildBasicAuthHeader(config.webDAV)


        // Check if the root folder already exists on the server

        val checkFolderUrl = getUrl(
            webDAVConfig = config.webDAV,
            pathSegments = request.name
        )

        val checkFolderResponse =
            client.request(checkFolderUrl) {
                method = HttpMethod("PROPFIND")
                header("Authorization", authHeader)
            }

        !KIO.failOn(checkFolderResponse.status.isSuccess()) { WebDAVError.ExportFolderAlreadyExists }
        !KIO.failOn(checkFolderResponse.status.value != 404) { WebDAVError.CannotMakeFolder("/") }


        // Create root folder
        !createFolder(
            path = request.name,
            client = client,
            webDAVConfig = config.webDAV
        )


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


        // ---------------------- DATABASE EXPORTS --------------------------

        fun buildExportDataRecord(
            dataType: WebDAVExportType,
            dataReference: UUID? = null,
        ): WebdavExportDataRecord {
            return WebdavExportDataRecord(
                id = UUID.randomUUID(),
                webdavExportProcess = processId,
                documentType = dataType.name,
                dataReference = dataReference,
                path = request.name,
            )
        }

        val exportDataRecords = mutableMapOf<WebDAVExportType, UUID>()

        val databaseExportTypes = request.selectedResources.filter {
            it.name.startsWith("DB_")
        }

        if (databaseExportTypes.isNotEmpty()) {
            val maifestFile = !ManifestExport.createExportFile(databaseExportTypes)
            val response = sendFile(client, config.webDAV, maifestFile, path = "${request.name}/${maifestFile.name}")
            val content = if (!response.status.isSuccess()) response.bodyAsText() else null
            !KIO.failOn(!response.status.isSuccess()) {
                logger.error { "Export of manifest.json was unsuccessful. $content" }
                WebDAVError.ManifestExportFailed
            }
        }

        databaseExportTypes.forEach { exportType ->
            when (exportType) {

                WebDAVExportType.DB_USERS -> {
                    val users = !AppUserRepo.existsExceptSystemAdmin().orDie()
                    val roles = !RoleRepo.existsExceptStatic().orDie()

                    if (users || roles) {
                        val record = buildExportDataRecord(exportType)
                        !WebDAVExportDataRepo.createOne(record).orDie()
                        exportDataRecords[exportType] = record.id
                    }
                }

                WebDAVExportType.DB_CLUBS -> {
                    val clubExists = !ClubRepo.any().orDie()
                    if (clubExists) {
                        val record = buildExportDataRecord(exportType)
                        !WebDAVExportDataRepo.createOne(record).orDie()
                        exportDataRecords[exportType] = record.id

                        exportDataRecords[WebDAVExportType.DB_USERS]?.let { usersId ->
                            !WebDAVExportDependencyRepo.create(
                                listOf(
                                    WebdavExportDependencyRecord(
                                        webdavExportData = record.id,
                                        dependingOn = usersId
                                    )
                                )
                            ).orDie()
                        }
                    }
                }

                else -> {}
            }
        }

        client.close()

        return noData
    }

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
                            it
                        }
                    !WebDAVExportFolderRepo.update(nextExportFolder.id!!) {
                        doneAt = LocalDateTime.now()
                    }.orDie()
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
                    }.map { WebDAVError.FileNotFound(it.id, it.dataReference) }

                    nextExport.let { exportRecord ->
                        when (exportRecord.documentType) {
                            WebDAVExportType.REGISTRATION_RESULTS.name ->
                                !EventRegistrationReportRepo.getDownload(exportRecord.dataReference!!).orDie()
                                    .onNullFail {
                                        !setFileNotFoundError().orDie()
                                    }
                                    .map { File(name = it.name!!, bytes = it.data!!) }

                            WebDAVExportType.INVOICES.name ->
                                !InvoiceRepo.getDownload(exportRecord.dataReference!!).orDie()
                                    .onNullFail {
                                        !setFileNotFoundError().orDie()
                                    }
                                    .map { File(name = it.filename!!, bytes = it.data!!) }

                            WebDAVExportType.DOCUMENTS.name ->
                                !EventDocumentRepo.getDownload(exportRecord.dataReference!!).orDie()
                                    .onNullFail {
                                        !setFileNotFoundError().orDie()
                                    }
                                    .map { File(name = it.name!!, bytes = it.data!!) }

                            WebDAVExportType.RESULTS.name ->
                                !ResultsService.generateResultsDocument(exportRecord.dataReference!!)
                                    .mapError {
                                        !setFileNotFoundError("Failed to generate document").orDie()
                                    }

                            WebDAVExportType.START_LISTS.name ->
                                !CompetitionExecutionService.getStartList(
                                    matchId = exportRecord.dataReference!!,
                                    startListType = StartListFileType.PDF,
                                    startTimeRequired = false
                                ).mapError {
                                    !setFileNotFoundError("Failed to generate document").orDie()
                                }
                                    .map { File(name = it.name, bytes = it.bytes) }

                            else -> return@comprehension KIO.fail(!setFileNotFoundError().orDie())
                        }
                    }
                }
                // DATABASE EXPORTS
                else if (nextDataExport != null) {

                    when (nextDataExport.documentType) {
                        WebDAVExportType.DB_USERS.name -> {
                            !DataUsersExport.createExportFile(nextDataExport)
                        }

                        WebDAVExportType.DB_CLUBS.name -> {
                            !DataClubsExport.createExportFile(nextDataExport)
                        }

                        else -> {
                            logger.warn { "Unknown export type: ${nextDataExport.documentType}" }
                            !WebDAVExportDataRepo.update(nextDataExport) {
                                error = "Unknown export type"
                                errorAt = LocalDateTime.now()
                            }.orDie()
                            return@comprehension KIO.fail(
                                WebDAVError.FileNotFound(
                                    nextDataExport.id,
                                    nextDataExport.dataReference
                                )
                            )
                        }
                    }
                } else {
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
                        return@comprehension KIO.fail(
                            WebDAVError.CannotTransferFile(
                                exportId = nextExport.id,
                                errorMsg = "${response.status.value} - $content"
                            )
                        )
                    } else {
                        !WebDAVExportDataRepo.update(nextDataExport!!) {
                            error = "${response.status.value} - $content"
                            errorAt = LocalDateTime.now()
                        }.orDie()
                        return@comprehension KIO.fail(
                            WebDAVError.CannotTransferFile(
                                exportId = nextDataExport.id,
                                errorMsg = "${response.status.value} - $content"
                            )
                        )
                    }
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
            val authHeader = buildBasicAuthHeader(config)
            val mimeType = URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"

            val url = getUrl(
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

    private fun getUrl(
        webDAVConfig: Config.WebDAV,
        pathSegments: String
    ): String {
        return URLBuilder(
            protocol = URLProtocol.createOrDefault(webDAVConfig.urlScheme),
            host = webDAVConfig.host,
            pathSegments = listOf(
                webDAVConfig.path,
                "remote.php",
                "dav",
                "files",
                webDAVConfig.authUser,
            ) + pathSegments.split("/").filter { it.isNotEmpty() },
        ).buildString()
    }

    private fun buildBasicAuthHeader(webDAVConfig: Config.WebDAV): String {
        val credentials = "${webDAVConfig.authUser}:${webDAVConfig.authPassword}"
        val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray())
        return "Basic $encoded"
    }

    private suspend fun createFolder(
        client: HttpClient,
        path: String,
        webDAVConfig: Config.WebDAV
    ): App<WebDAVError.WebDavInternExternError, Unit> =
        coroutineScope {

            try {
                val folderUrl = getUrl(
                    webDAVConfig = webDAVConfig,
                    pathSegments = path
                )

                val response = client.request(folderUrl) {
                    method = HttpMethod("MKCOL")
                    header("Authorization", buildBasicAuthHeader(webDAVConfig))
                }

                if (response.status.isSuccess()) {
                    unit
                } else {
                    KIO.fail(WebDAVError.CannotMakeFolder(path))
                }
            } catch (ex: Exception) {
                KIO.fail(WebDAVError.Unexpected)
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

    fun serializeDataExport(
        record: WebdavExportDataRecord,
        exportData: Any,
        type: WebDAVExportType
    ): App<WebDAVError.WebDAVInternError, ByteArray> = KIO.comprehension {
        try {
            val json = jsonMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(exportData)
                .toByteArray()
            KIO.ok(json)
        } catch (e: Exception) {
            logger.error(e) { "Failed to serialize $type to JSON" }
            !WebDAVExportDataRepo.update(record) {
                error = "Failed to serialize: ${e.message}"
                errorAt = LocalDateTime.now()
            }.orDie()
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

    suspend fun CallComprehensionScope.getImportOptions(): App<WebDAVError.WebDAVExternError, ApiResponse.ListDto<String>> {
        val config = !accessConfig()
        if (config.webDAV == null) {
            return KIO.fail(WebDAVError.ConfigIncomplete)
        }

        val client = HttpClient(CIO)
        val authHeader = buildBasicAuthHeader(config.webDAV)

        val propfindUrl = getUrl(
            webDAVConfig = config.webDAV,
            pathSegments = ""
        )

        val propfindBody = """
                        <?xml version="1.0"?>
                        <d:propfind xmlns:d="DAV:">
                            <d:prop>
                                <d:resourcetype/>
                                <d:displayname/>
                            </d:prop>
                        </d:propfind>
                    """.trimIndent()

        val resp = client.request(propfindUrl) {
            method = HttpMethod("PROPFIND")
            header("Authorization", authHeader)
            header("Depth", "1")
            setBody(propfindBody)
            contentType(ContentType.Application.Xml)
        }

        !KIO.failOn(!resp.status.isSuccess()) { WebDAVError.CannotListFolders }

        val responseBody = resp.bodyAsText()
        val folderNames = parseFolderNamesFromPropfind(responseBody)

        client.close()

        return KIO.ok(ApiResponse.ListDto(folderNames))
    }


    private fun parseFolderNamesFromPropfind(xmlResponse: String): List<String> {
        val folderNames = mutableListOf<String>()

        // Use regex to find all response blocks
        val responsePattern = "<d:response>(.*?)</d:response>".toRegex()
        val matches = responsePattern.findAll(xmlResponse)

        for (match in matches) {
            val responseContent = match.groupValues[1]

            // Check if this is a collection (folder)
            val isCollection = responseContent.contains("<d:collection/>") ||
                              responseContent.contains("<d:collection></d:collection>")

            if (isCollection) {
                // Extract display name
                val displayNamePattern = "<d:displayname>(.*?)</d:displayname>".toRegex()
                val displayNameMatch = displayNamePattern.find(responseContent)

                if (displayNameMatch != null) {
                    val displayName = displayNameMatch.groupValues[1]
                    // Skip the root folder (which has the username as displayname)
                    // We want actual subfolders only
                    if (displayName.isNotEmpty() && !responseContent.contains("/dav/files/admin/</d:href>")) {
                        folderNames.add(displayName)
                    }
                }
            }
        }

        return folderNames.distinct()
    }
}