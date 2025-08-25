package de.lambda9.ready2race.backend.app.webDAV.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.eventDocument.control.EventDocumentRepo
import de.lambda9.ready2race.backend.app.eventRegistration.control.EventRegistrationReportRepo
import de.lambda9.ready2race.backend.app.invoice.control.InvoiceRepo
import de.lambda9.ready2race.backend.app.results.boundary.ResultsService
import de.lambda9.ready2race.backend.app.results.control.ResultsRepo
import de.lambda9.ready2race.backend.app.webDAV.control.WebDAVExportProcessRepo
import de.lambda9.ready2race.backend.app.webDAV.control.WebDAVExportRepo
import de.lambda9.ready2race.backend.app.webDAV.control.toDto
import de.lambda9.ready2race.backend.app.webDAV.control.toRecord
import de.lambda9.ready2race.backend.app.webDAV.entity.*
import de.lambda9.ready2race.backend.calls.requests.logger
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.config.Config
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
                    records.associate { rec ->
                        rec.id to
                            if (records.filter { it.id == rec.id }.size == 1
                            ) {
                                rec.name
                            } else { // This covers the case of multiple events having the same name
                                "${rec.name}-${
                                    records.filter { it.id == rec.id }.indexOf(rec) + 1
                                }"
                            }
                    }
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
                }
            }

            fun buildExportRecord(
                eventId: UUID,
                documentType: WebDAVExportType,
                dataReference: UUID
            ): WebdavExportRecord {
                return WebdavExportRecord(
                    id = UUID.randomUUID(),
                    webdavExportProcess = processId,
                    eventName = events[eventId] ?: "",
                    documentType = documentType.name,
                    dataReference = dataReference,
                    path = "${request.name}/${events[eventId]}/${getFolderName(documentType)}",
                    exportedAt = null,
                    errorAt = null,
                    error = null
                )
            }


            val client = OkHttpClient();
            val authHeader = Credentials.basic(config.webDAV.authUser, config.webDAV.authPassword)
            fun createFolder(path: String): App<WebDAVError, Unit> = KIO.comprehension {
                val folderUrl = !buildUrl(
                    webDAVConfig = config.webDAV,
                    pathSegments = "${request.name}/$path"
                ).mapError { WebDAVError.ConfigUnparsable }

                val response = !KIO.effect {
                    client.newCall(
                        Request.Builder()
                            .url(folderUrl)
                            .method("MKCOL", null)
                            .header("Authorization", authHeader)
                            .build()
                    ).execute().use { response -> response.code to response.body?.string() }
                }.mapError { WebDAVError.Unexpected }

                !KIO.failOn(response.first != 201) {
                    logger.warn { "WebDAV Export failed on createFolder: Status ${response.first}; Message: ${response.second} " }
                    WebDAVError.ThirdPartyError
                }

                unit
            }

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

            !KIO.failOn(checkFolderResult.first == 200) { WebDAVError.ExportFolderAlreadyExists }
            !KIO.failOn(checkFolderResult.first != 404) { WebDAVError.ThirdPartyError }


            // Create root folder
            !createFolder("")


            // GET FILES

            // todo start_lists ??
            // todo document_templates ? - maybe as json with the properties as well

            // todo: ?? handle case where files are not yet generated for the event
            val eventRegistrationIds = !EventRegistrationReportRepo.getExistingEventIds(events.keys.toList()).orDie()
            val registrationResultRecords = eventRegistrationIds.map {
                buildExportRecord(
                    eventId = it,
                    documentType = WebDAVExportType.REGISTRATION_RESULTS,
                    dataReference = it
                )
            }

            val invoices = !InvoiceRepo.getByEvents(events.keys.toList()).orDie().map { records ->
                records.map { it.event!! to it.id!! }
            }
            val invoiceRecords = invoices.map { (eventId, invoiceId) ->
                buildExportRecord(
                    eventId = eventId,
                    documentType = WebDAVExportType.INVOICES,
                    dataReference = invoiceId
                )
            }

            val eventDocuments = !EventDocumentRepo.getByEventIds(events.keys.toList()).orDie()
                .map { records ->
                    records.map {
                        it.event to it.id
                    }
                }
            val eventDocumentRecords = eventDocuments.map { (eventId, documentId) ->
                buildExportRecord(
                    eventId = eventId,
                    documentType = WebDAVExportType.DOCUMENTS,
                    dataReference = documentId
                )
            }

            val eventsHavingResults =
                !ResultsRepo.getEventsHavingResultsByEventIds(eventIds = events.keys.toList()).orDie()
                    .map { eventsHavingResults ->
                        eventsHavingResults.distinct()
                    }
            val resultRecords = eventsHavingResults.map {
                buildExportRecord(
                    eventId = it,
                    documentType = WebDAVExportType.RESULTS,
                    dataReference = it
                )
            }


            // CREATE FOLDERS
            events.forEach { (eventId, eventName) ->

                !createFolder(eventName)

                if (eventRegistrationIds.any { it == eventId }) {
                    !createFolder("$eventName/${getFolderName(WebDAVExportType.REGISTRATION_RESULTS)}")
                }
                if (invoices.any { it.first == eventId }) {
                    !createFolder("$eventName/${getFolderName(WebDAVExportType.INVOICES)}")
                }
                if (eventDocuments.any { it.first == eventId }) {
                    !createFolder("$eventName/${getFolderName(WebDAVExportType.DOCUMENTS)}")
                }
                if (eventsHavingResults.any { it == eventId }) {
                    !createFolder("$eventName/${getFolderName(WebDAVExportType.RESULTS)}")
                }
            }

            // CREATE EXPORT QUEUE
            val exportRecords = (registrationResultRecords + invoiceRecords + eventDocumentRecords + resultRecords)
            !WebDAVExportRepo.create(exportRecords).orDie()

            noData
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

    fun exportNext(): App<WebDAVExportNextError, Unit> = KIO.comprehension {
        val config = !accessConfig()
        if (config.webDAV == null) {
            return@comprehension KIO.fail(WebDAVExportNextError.ConfigIncomplete)
        }


        val nextExport = !WebDAVExportRepo.getNextExport().orDie().onNullFail { WebDAVExportNextError.NoFilesToExport }

        val file = nextExport.let { exportRecord ->
            when (exportRecord.documentType) {
                WebDAVExportType.REGISTRATION_RESULTS.name ->
                    !EventRegistrationReportRepo.getDownload(exportRecord.dataReference!!).orDie()
                        .onNullFail { WebDAVExportNextError.FileNotFound(exportRecord.id, exportRecord.dataReference) }
                        .map { File(name = it.name!!, bytes = it.data!!) }

                WebDAVExportType.INVOICES.name ->
                    !InvoiceRepo.getDownload(exportRecord.dataReference!!).orDie()
                        .onNullFail { WebDAVExportNextError.FileNotFound(exportRecord.id, exportRecord.dataReference) }
                        .map { File(name = it.filename!!, bytes = it.data!!) }

                WebDAVExportType.DOCUMENTS.name ->
                    !EventDocumentRepo.getDownload(exportRecord.dataReference!!).orDie()
                        .onNullFail { WebDAVExportNextError.FileNotFound(exportRecord.id, exportRecord.dataReference) }
                        .map { File(name = it.name!!, bytes = it.data!!) }

                WebDAVExportType.RESULTS.name ->
                    !ResultsService.generateResultsDocument(exportRecord.dataReference!!)
                        .mapError { WebDAVExportNextError.FileNotFound(exportRecord.id, exportRecord.dataReference) }

                else -> return@comprehension KIO.fail(WebDAVExportNextError.FileNotFound(exportRecord.id, null))
            }
        }

        val client = OkHttpClient();
        val authHeader = Credentials.basic(config.webDAV.authUser, config.webDAV.authPassword)
        val mimeType = URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"
        val requestBody = file.bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val url = !buildUrl(
            webDAVConfig = config.webDAV,
            pathSegments = "${nextExport.path}/${file.name}"
        ).mapError { WebDAVExportNextError.ConfigUnparsable }

        !KIO.effect {
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
            WebDAVExportNextError.ThirdPartyError(exportId = nextExport.id, it)
        }

        !WebDAVExportRepo.update(nextExport) {
            exportedAt = LocalDateTime.now()
        }.orDie()

        unit
    }

    fun getExportStatus(): App<Nothing, ApiResponse.ListDto<WebDAVExportStatusDto>> = KIO.comprehension {

        val records = !WebDAVExportProcessRepo.all().orDie()

        KIO.ok(
            ApiResponse.ListDto(
                !records.traverse { record ->
                    val events = record.fileExports!!.groupBy { it!!.eventName!! }.keys.toList()
                    val exportTypes = record.fileExports!!
                        .groupBy { it!!.documentType }.keys.toList()
                        .map { WebDAVExportType.valueOf(it) }
                    val filesExported = record.fileExports!!.filter { it!!.exportedAt != null }.size
                    val error = record.fileExports!!.any { it!!.errorAt != null }

                    record.toDto(
                        events = events,
                        exportTypes = exportTypes,
                        filesExported = filesExported,
                        error = error
                    )
                })
        )
    }
}