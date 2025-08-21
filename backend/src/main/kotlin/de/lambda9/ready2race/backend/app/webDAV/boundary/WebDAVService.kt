package de.lambda9.ready2race.backend.app.webDAV.boundary

import de.lambda9.ready2race.backend.app.App
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
import de.lambda9.tailwind.core.extensions.kio.orDie
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object WebDAVService {

    fun exportData(request: WebDAVExportRequest): App<WebDAVError, ApiResponse.NoData> = KIO.comprehension {

        val selectedEvents = request.events.mapIndexed { idx, eventId -> eventId to "Event${idx}" }.toMap() // todo
        // todo: check events existing

        val eventInvoices: List<Pair<UUID, File>> =
            !InvoiceRepo.getByEvents(selectedEvents.keys.toList()).orDie()
                .map { records ->
                    records.map {
                        it.event!! to File(
                            name = it.filename!!,
                            bytes = it.data!!,
                        )
                    }
                }

        // todo: handle case where files are not generated for the event
        val eventRegistrationResults: List<Pair<UUID, File>> =
            !EventRegistrationReportRepo.getDownloads(selectedEvents.keys.toList()).orDie()
                .map { records ->
                    records.map {
                        it.event!! to File(
                            name = it.name!!,
                            bytes = it.data!!,
                        )
                    }
                }

        val eventDocuments: List<Pair<UUID, File>> =
            !EventDocumentRepo.getDownloadsByEvents(selectedEvents.keys.toList()).orDie()
                .map { records ->
                    records.map {
                        it.event!! to File(
                            name = it.name!!,
                            bytes = it.data!!,
                        )
                    }
                }


        // todo start_lists ??
        // todo competition_results
        // todo document_templates ? - maybe as json with the properties as well

        val eventFiles = (eventInvoices + eventRegistrationResults + eventDocuments)

        val zippedFiles = createZip(
            eventFiles.map { Triple("${request.name}/${selectedEvents[it.first]!!}", it.second.name, it.second.bytes) }
        )

        val body = zippedFiles.toRequestBody("application/zip".toMediaType())

        val config = !accessConfig()
        if (config.webDAV == null) {
            return@comprehension KIO.fail(WebDAVError.ConfigIncomplete)
        }

        val callResult = !sendExportRequest(config.webDAV, body, fileName = "${request.name}.zip").mapError {
            WebDAVError.ExportThirdPartyError
        }
        !KIO.failOn(callResult.first != 201){ WebDAVError.ExportThirdPartyError }

        noData
    }

    private fun sendExportRequest(webDAVConfig: Config.WebDAV, body: RequestBody, fileName: String): App<Throwable, Pair<Int, String?>> = KIO.comprehension {

        val url = HttpUrl.Builder()
            .scheme(webDAVConfig.urlScheme)
            .host(webDAVConfig.host)
            .addPathSegment(webDAVConfig.path)
            .addPathSegments("remote.php/dav/files/${webDAVConfig.authUser}/${fileName}")
            .build()

        val headers = Headers.Builder()
            .add("Authorization", Credentials.basic(webDAVConfig.authUser, webDAVConfig.authPassword)).build()

        val httpRequest = Request.Builder()
            .url(url)
            .headers(headers)
            .put(body)
            .build()

        val client = OkHttpClient();

        val callResult = client.newCall(httpRequest).execute().use { it.code to it.body?.string() }

        logger.info { "Attempt to export data to WebDAV Server. Request: $httpRequest Response: ${callResult.first} ${callResult.second}" }

        KIO.ok(callResult)
    }


    private fun createZip(files: List<Triple<String, String, ByteArray>>): ByteArray { // folder, fileName, data
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((folder, name, data) in files) {
                val path = if (folder.isNotEmpty()) "$folder/$name" else name
                val entry = ZipEntry(path)
                zos.putNextEntry(entry)
                zos.write(data)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

}