package de.lambda9.ready2race.backend.app.documentTemplate.boundary

import com.fasterxml.jackson.module.kotlin.readValue
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.documentTemplate.entity.AssignDocumentTemplateRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.AssignGapDocumentTemplateRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.DocumentTemplateRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.DocumentTemplateSort
import de.lambda9.ready2race.backend.app.documentTemplate.entity.DocumentType
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentTemplateError
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentTemplateRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentTemplateViewSort
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentType
import de.lambda9.ready2race.backend.calls.comprehension.CallComprehensionScope
import de.lambda9.ready2race.backend.calls.requests.*
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.calls.serialization.jsonMapper
import de.lambda9.ready2race.backend.file.File
import de.lambda9.ready2race.backend.parsing.Parser.Companion.enum
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import de.lambda9.ready2race.backend.pdf.checkValidPdf
import de.lambda9.tailwind.core.KIO
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*

/** Grobe Vorprüfung des Font-Uploads anhand der Dateiendung, bevor der Inhalt gelesen wird. */
private fun hasValidFontExtension(fileName: String): Boolean =
    fileName.substringAfterLast('.', "").lowercase() in setOf("ttf", "otf")

/** Ergebnis von [readGapDocumentTemplateMultipart]: die rohen, noch ungeprüften Teile des Requests. */
private data class GapDocumentTemplateMultipart(
    val templateFile: File?,
    val fontFile: File?,
    val request: GapDocumentTemplateRequest?,
)

/**
 * Liest die Multipart-Teile für Anlegen und Aktualisieren einer Lückentext-Vorlage: das JSON unter
 * "request", optional eine Schriftdatei unter "font" und - nur wenn [acceptTemplateFile] gesetzt ist
 * (beim Anlegen) - die PDF-Vorlage als unbenannter Datei-Teil. Beim Aktualisieren gibt es keinen
 * PDF-Teil, ein solcher wird dort still ignoriert, genau wie zuvor. Mehrere Teile für denselben Slot
 * (Font oder Vorlage) sind ein Fehler.
 */
private suspend fun CallComprehensionScope.readGapDocumentTemplateMultipart(
    multiPartData: MultiPartData,
    acceptTemplateFile: Boolean,
): GapDocumentTemplateMultipart {
    var templateFile: File? = null
    var fontFile: File? = null
    var request: GapDocumentTemplateRequest? = null

    var done = false
    while (!done) {
        val part = multiPartData.readPart()
        if (part == null) {
            done = true
        } else {
            when (part) {
                is PartData.FileItem -> {
                    if (part.name == "font") {
                        if (fontFile == null) {
                            fontFile = File(
                                part.originalFileName ?: "",
                                part.provider().toByteArray(),
                            )
                        } else {
                            !KIO.fail(RequestError.File.Multiple)
                        }
                    } else if (acceptTemplateFile) {
                        if (templateFile == null) {
                            templateFile = File(
                                part.originalFileName!!,
                                part.provider().toByteArray(),
                            )
                        } else {
                            !KIO.fail(RequestError.File.Multiple)
                        }
                    }
                }

                is PartData.FormItem -> {
                    if (part.name == "request") {
                        request = jsonMapper.readValue<GapDocumentTemplateRequest>(part.value)
                    }
                }

                else -> {}
            }
            part.dispose()
        }
    }

    return GapDocumentTemplateMultipart(templateFile, fontFile, request)
}

fun Route.documentTemplate() {

    route("/gapDocumentTemplate") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.UpdateEventGlobal)
                val params = !pagination<GapDocumentTemplateViewSort>()
                GapDocumentTemplateService.page(params)
            }
        }

        post {
            call.respondComprehension {
                !authenticate(Privilege.UpdateEventGlobal)

                val multiPartData = call.receiveMultipart()
                val parsed = readGapDocumentTemplateMultipart(multiPartData, acceptTemplateFile = true)

                val request = !KIO.failOnNull(parsed.request) { RequestError.BodyMissing(GapDocumentTemplateRequest.example) }
                val file = !KIO.failOnNull(parsed.templateFile) { RequestError.File.Missing }
                !KIO.failOn(!checkValidPdf(file.bytes)) { RequestError.File.UnsupportedType }
                if (parsed.fontFile != null && parsed.fontFile.bytes.isNotEmpty()) {
                    !KIO.failOn(!hasValidFontExtension(parsed.fontFile.name)) { GapDocumentTemplateError.InvalidFont }
                }

                GapDocumentTemplateService.addTemplate(file, request, parsed.fontFile)
            }
        }

        route("/{gapDocumentTemplateId}") {
            put {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val id = !pathParam("gapDocumentTemplateId", uuid)

                    val multiPartData = call.receiveMultipart()
                    val parsed = readGapDocumentTemplateMultipart(multiPartData, acceptTemplateFile = false)

                    val payload = !KIO.failOnNull(parsed.request) { RequestError.BodyMissing(GapDocumentTemplateRequest.example) }
                    if (parsed.fontFile != null && parsed.fontFile.bytes.isNotEmpty()) {
                        !KIO.failOn(!hasValidFontExtension(parsed.fontFile.name)) { GapDocumentTemplateError.InvalidFont }
                    }

                    GapDocumentTemplateService.updateTemplate(id, payload, parsed.fontFile)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val id = !pathParam("gapDocumentTemplateId", uuid)
                    GapDocumentTemplateService.deleteTemplate(id)
                }
            }

            get {
                call.respondComprehension {
                    !authenticate(Privilege.ReadEventGlobal)
                    val id = !pathParam("gapDocumentTemplateId", uuid)
                    GapDocumentTemplateService.download(id)
                }
            }

            get("/preview") {
                call.respondComprehension {
                    !authenticate(Privilege.ReadEventGlobal)
                    val id = !pathParam("gapDocumentTemplateId", uuid)
                    GapDocumentTemplateService.getPreview(id)
                }
            }
        }
    }

    route("/gapDocumentTemplateType") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                GapDocumentTemplateService.getTypes()
            }
        }
        put("/{documentType}/assignTemplate") {
            call.respondComprehension {
                !authenticate(Privilege.UpdateEventGlobal)
                val docType = !pathParam("documentType", enum<GapDocumentType>())
                val body = !receiveKIO(AssignGapDocumentTemplateRequest.example)
                GapDocumentTemplateService.assignTemplate(docType, body)
            }
        }
    }

    route("/documentTemplate") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.UpdateEventGlobal)
                val params = !pagination<DocumentTemplateSort>()
                DocumentTemplateService.page(params)
            }
        }

        // todo: see upload in eventDocument
        post {
            val multipartData = call.receiveMultipart()

            val uploads = mutableListOf<File>()
            var templateRequest: DocumentTemplateRequest? = null

            var done = false
            while(!done) {
                val part = multipartData.readPart()
                if (part == null) {
                    done = true
                } else {
                    when (part) {
                        is PartData.FileItem -> {
                            uploads.add(
                                File(
                                    part.originalFileName!!,
                                    part.provider().toByteArray()
                                )
                            )
                        }

                        is PartData.FormItem -> {
                            if (part.name == "request") {
                                templateRequest = jsonMapper.readValue<DocumentTemplateRequest>(part.value)
                            }
                        }

                        else -> {}
                    }
                    part.dispose()
                }
            }

            call.respondComprehension {
                !authenticate(Privilege.UpdateEventGlobal)
                !KIO.failOn(uploads.size != 1) {
                    if (uploads.isEmpty()) {
                        RequestError.File.Missing
                    } else {
                        RequestError.File.Multiple
                    }
                }
                !KIO.failOn(!checkValidPdf(uploads.first().bytes)) { RequestError.File.UnsupportedType }
                val req = !KIO.failOnNull(templateRequest) { RequestError.BodyMissing(DocumentTemplateRequest.example) }
                DocumentTemplateService.addTemplate(uploads.first(), req)
            }
        }

        route("/{documentTemplateId}") {

            put {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val id = !pathParam("documentTemplateId", uuid)
                    val payload = !receiveKIO(DocumentTemplateRequest.example)
                    DocumentTemplateService.updateTemplate(id, payload)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val id = !pathParam("documentTemplateId", uuid)
                    DocumentTemplateService.deleteTemplate(id)
                }
            }

            get("/preview") {
                call.respondComprehension {
                    !authenticate(Privilege.ReadEventGlobal)
                    val id = !pathParam("documentTemplateId", uuid)
                    val type = !queryParam("documentType", enum<DocumentType>())
                    DocumentTemplateService.getPreview(id, type)
                }
            }
        }
    }

    route("/documentTemplateType") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                DocumentTemplateService.getTypes()
            }
        }
        put("/{documentType}/assignTemplate") {
            call.respondComprehension {
                !authenticate(Privilege.UpdateEventGlobal)
                val docType = !pathParam("documentType") { DocumentType.valueOf(it) }
                val body = !receiveKIO(AssignDocumentTemplateRequest.example)
                DocumentTemplateService.assignTemplate(docType, body)
            }
        }
    }
}