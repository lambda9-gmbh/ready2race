package de.lambda9.ready2race.backend.app.documentTemplate.boundary

import com.fasterxml.jackson.module.kotlin.readValue
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.documentTemplate.entity.AssignDocumentTemplateRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.DocumentTemplateRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.DocumentTemplateSort
import de.lambda9.ready2race.backend.app.documentTemplate.entity.DocumentType
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentTemplateRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentTemplateViewSort
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
            val multiPartData = call.receiveMultipart()

            var upload: File? = null
            var templateRequest: GapDocumentTemplateRequest? = null

            var done = false
            while (!done) {
                val part = multiPartData.readPart()
                if (part == null) {
                    done = true
                } else {
                    when (part) {
                        is PartData.FileItem -> {
                            if (upload == null) {
                                upload = File(
                                    part.originalFileName!!,
                                    part.provider().toByteArray(),
                                )
                            } else {
                                // TODO: @Fix this does nothing, wrap the multipart receiving into the respondComprehension and add the '!'
                                KIO.fail(RequestError.File.Multiple)
                            }
                        }

                        is PartData.FormItem -> {
                            if (part.name == "request") {
                                templateRequest = jsonMapper.readValue<GapDocumentTemplateRequest>(part.value)
                            }
                        }

                        else -> {}
                    }
                    part.dispose()
                }
            }

            call.respondComprehension {
                !authenticate(Privilege.UpdateEventGlobal)
                val request = !KIO.failOnNull(templateRequest) { RequestError.BodyMissing(GapDocumentTemplateRequest.example) }
                val file = !KIO.failOnNull(upload) { RequestError.File.Missing }
                !KIO.failOn(!checkValidPdf(file.bytes)) { RequestError.File.UnsupportedType }

                GapDocumentTemplateService.addTemplate(file, request)
            }
        }

        route("/{gapDocumentTemplateId}") {
            put {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val id = !pathParam("gapDocumentTemplateId", uuid)
                    val payload = !receiveKIO(GapDocumentTemplateRequest.example)
                    GapDocumentTemplateService.updateTemplate(id, payload)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val id = !pathParam("documentTemplateId", uuid)
                    GapDocumentTemplateService.deleteTemplate(id)
                }
            }

            get("/preview") {
                call.respondComprehension {
                    !authenticate(Privilege.ReadEventGlobal)
                    val id = !pathParam("documentTemplateId", uuid)
                    GapDocumentTemplateService.getPreview(id)
                }
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