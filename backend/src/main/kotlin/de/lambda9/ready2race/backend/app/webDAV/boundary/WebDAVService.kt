package de.lambda9.ready2race.backend.app.webDAV.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.eventDocument.control.EventDocumentRepo
import de.lambda9.ready2race.backend.app.eventRegistration.control.EventRegistrationReportRepo
import de.lambda9.ready2race.backend.app.invoice.control.InvoiceRepo
import de.lambda9.ready2race.backend.app.webDAV.control.WebDAVExportProcessRepo
import de.lambda9.ready2race.backend.app.webDAV.control.WebDAVExportRepo
import de.lambda9.ready2race.backend.app.webDAV.control.toRecord
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVError
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportNextError
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportRequest
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportType
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


            // todo: ?? handle case where files are not generated for the event
            val eventRegistrationIds = !EventRegistrationReportRepo.getExistingEventIds(events.keys.toList()).orDie()

            val invoices = !InvoiceRepo.getByEvents(events.keys.toList()).orDie().map { records ->
                records.map { it.event!! to it.id!! }
            }

            val eventDocuments = !EventDocumentRepo.getByEventIds(events.keys.toList()).orDie()
                .map { records ->
                    records.map {
                        it.event to it.id
                    }
                }

            // todo start_lists ??
            // todo competition_results
            // todo document_templates ? - maybe as json with the properties as well

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
                    documentType = documentType.name,
                    dataReference = dataReference,
                    path = "${request.name}/${events[eventId]}/${getFolderName(documentType)}",
                    exportedAt = null,
                    errorAt = null,
                    error = null
                )
            }

            val registrationResultRecords = eventRegistrationIds.map {
                buildExportRecord(
                    eventId = it,
                    documentType = WebDAVExportType.REGISTRATION_RESULTS,
                    dataReference = it
                )
            }

            val invoiceRecords = invoices.map { (eventId, invoiceId) ->
                buildExportRecord(
                    eventId = eventId,
                    documentType = WebDAVExportType.INVOICES,
                    dataReference = invoiceId
                )
            }

            val eventDocumentRecords = eventDocuments.map { (eventId, documentId) ->
                buildExportRecord(
                    eventId = eventId,
                    documentType = WebDAVExportType.DOCUMENTS,
                    dataReference = documentId
                )
            }


            val client = OkHttpClient();
            val authHeader = Credentials.basic(config.webDAV.authUser, config.webDAV.authPassword)
            fun createFolder(path: String): App<WebDAVError, Unit> = KIO.comprehension {
                val callRequest = Request.Builder()
                    .url(buildUrl(webDAVConfig = config.webDAV, pathSegments = "${request.name}/$path"))
                    .method("MKCOL", null)
                    .header("Authorization", authHeader)
                    .build()

                val response = !makeCall(client, callRequest)
                    .mapError { WebDAVError.Unexpected }

                !KIO.failOn(response.code != 201) {
                    logger.warn { "WebDAV Export failed on createFolder: Status ${response.code}; Message: ${response.message} " }
                    WebDAVError.ThirdPartyError
                }

                unit
            }

            // Check if the root folder already exists on the server
            val checkFolderResult = !makeCall(
                client, Request.Builder()
                    .url(buildUrl(webDAVConfig = config.webDAV, pathSegments = request.name))
                    .method("PROPFIND", null)
                    .header("Authorization", authHeader)
                    .build()
            )
                .mapError { WebDAVError.Unexpected }

            !KIO.failOn(checkFolderResult.isSuccessful) { WebDAVError.ExportFolderAlreadyExists }
            !KIO.failOn(checkFolderResult.code != 404) { WebDAVError.ThirdPartyError }


            // Create root folder
            !createFolder("")

            events.forEach { (eventId, eventName) ->

                // Create event folder
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
            }

            val exportRecords = (registrationResultRecords + invoiceRecords + eventDocumentRecords)
            !WebDAVExportRepo.create(exportRecords).orDie()

            noData
        }

    private fun makeCall(client: OkHttpClient, callRequest: Request): App<Throwable, Response> =
        KIO.comprehension {
            KIO.ok(client.newCall(callRequest).execute())
        }

    private fun buildUrl(
        webDAVConfig: Config.WebDAV,
        pathSegments: String
    ): HttpUrl {
        return HttpUrl.Builder()
            .scheme(webDAVConfig.urlScheme)
            .host(webDAVConfig.host)
            .addPathSegment(webDAVConfig.path)
            .addPathSegments("remote.php/dav/files/${webDAVConfig.authUser}/$pathSegments")
            .build()
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

                else -> return@comprehension KIO.fail(WebDAVExportNextError.FileNotFound(exportRecord.id, null))
            }
        }

        val client = OkHttpClient();
        val authHeader = Credentials.basic(config.webDAV.authUser, config.webDAV.authPassword)
        val mimeType = URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"
        val requestBody = file.bytes.toRequestBody(mimeType.toMediaTypeOrNull())

        !KIO.effect {
            client.newCall(
                Request.Builder()
                    .url(buildUrl(webDAVConfig = config.webDAV, pathSegments = "${nextExport.path}/${file.name}"))
                    .put(requestBody)
                    .header("Authorization", authHeader)
                    .build()
            ).execute()
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
}