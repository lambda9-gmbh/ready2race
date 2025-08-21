package de.lambda9.ready2race.backend.app.webDAV.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.eventDocument.control.EventDocumentRepo
import de.lambda9.ready2race.backend.app.eventRegistration.control.EventRegistrationReportRepo
import de.lambda9.ready2race.backend.app.invoice.control.InvoiceRepo
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVError
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportRequest
import de.lambda9.ready2race.backend.calls.requests.logger
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.config.Config
import de.lambda9.ready2race.backend.file.File
import de.lambda9.ready2race.backend.kio.accessConfig
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.failIf
import de.lambda9.tailwind.core.extensions.kio.orDie
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.http2.Header
import java.io.ByteArrayOutputStream
import java.net.URLConnection
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object WebDAVService {

    fun exportData(request: WebDAVExportRequest): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

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


        // todo: handle case where files are not generated for the event
        val eventRegistrationResults: Map<UUID, File> =
            !EventRegistrationReportRepo.getDownloads(events.keys.toList()).orDie()
                .map { records ->
                    records.associate { it.event!! to File(name = it.name!!, bytes = it.data!!) }
                }


        // todo: Multilingual folder names
        val eventInvoices: List<Triple<String, UUID, File>> =
            !InvoiceRepo.getByEvents(events.keys.toList()).orDie()
                .map { records ->
                    records.map {
                        Triple("Invoices", it.event!!, File(name = it.filename!!, bytes = it.data!!))
                    }
                }

        val eventDocuments: List<Triple<String, UUID, File>> =
            !EventDocumentRepo.getDownloadsByEvents(events.keys.toList()).orDie()
                .map { records ->
                    records.map {
                        Triple("Documents", it.event!!, File(name = it.name!!, bytes = it.data!!))
                    }
                }


        // todo start_lists ??
        // todo competition_results
        // todo document_templates ? - maybe as json with the properties as well

        val config = !accessConfig()
        if (config.webDAV == null) {
            return@comprehension KIO.fail(WebDAVError.ConfigIncomplete)
        }

        val client = OkHttpClient();
        val authHeader = Credentials.basic(config.webDAV.authUser, config.webDAV.authPassword)

        fun createFolder(path: String): App<WebDAVError, Unit> = KIO.comprehension {
            val callRequest = Request.Builder()
                .url(buildUrl(webDAVConfig = config.webDAV, pathSegments = "${request.name}/$path"))
                .method("MKCOL", null)
                .header("Authorization", authHeader)
                .build()

            val (responseCode, responseMessage) = !makeCall(client, callRequest)
                .mapError { WebDAVError.ExportThirdPartyError }

            !KIO.failOn(responseCode == 405) { WebDAVError.ExportFolderAlreadyExists }
            !KIO.failOn(responseCode != 201) {
                logger.error { "WenDAV Export failed on createFolder: Status $responseCode; Message: $responseMessage " }
                WebDAVError.ExportThirdPartyError
            }

            unit
        }

        fun putFile(path: String, filename: String, data: ByteArray): App<WebDAVError, Unit> =
            KIO.comprehension {
                val mimeType = URLConnection.guessContentTypeFromName(filename) ?: "application/octet-stream"
                val requestBody = data.toRequestBody(mimeType.toMediaTypeOrNull())
                val callRequest = Request.Builder()
                    .url(buildUrl(webDAVConfig = config.webDAV, pathSegments = "${request.name}/$path"))
                    .put(requestBody)
                    .header("Authorization", authHeader)
                    .build()

                val (responseCode, responseMessage) = !makeCall(client, callRequest)
                    .mapError { WebDAVError.ExportThirdPartyError }

                !KIO.failOn(responseCode != 201) {
                    logger.error { "WenDAV Export failed on putFile: Status $responseCode; Message: $responseMessage " }
                    WebDAVError.ExportThirdPartyError
                }

                unit
            }

        val folderFiles = (eventInvoices + eventDocuments)


        // Create root folder
        !createFolder("")

        events.forEach { (eventId, eventName) ->

            // Create event folder
            !createFolder(eventName)

            // Export registrationResult
            eventRegistrationResults[eventId].let { file ->
                if (file != null) {
                    !putFile(
                        path = "${eventName}/${file.name}",
                        filename = file.name,
                        data = file.bytes,
                    )
                }
            }

            // Create documentType folders and export the files into them
            folderFiles
                .filter { it.second == eventId } // Only for this event
                .groupBy { it.first } // Group by folder name
                .mapValues { folder -> folder.value.map { it.third } } // --> Map<FolderName, List<File>>
                .forEach { (folderName, files) ->

                    // Create folder
                    !createFolder("$eventName/$folderName")

                    // Export files into the folders
                    files.forEach { file ->
                        !putFile(
                            path = "${eventName}/$folderName/${file.name}",
                            filename = file.name,
                            data = file.bytes,
                        )
                    }
                }
        }

        noData
    }

    private fun makeCall(client: OkHttpClient, callRequest: Request): App<Throwable, Pair<Int, String?>> =
        KIO.comprehension {
            KIO.ok(client.newCall(callRequest).execute().use {
                it.code to it.body?.string()
            })
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

}