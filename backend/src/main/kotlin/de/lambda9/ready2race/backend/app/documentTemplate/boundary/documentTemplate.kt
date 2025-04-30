package de.lambda9.ready2race.backend.app.documentTemplate.boundary

import com.fasterxml.jackson.module.kotlin.readValue
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.documentTemplate.entity.AssignDocumentTemplateRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.DocumentTemplateRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.DocumentTemplateSort
import de.lambda9.ready2race.backend.app.documentTemplate.entity.DocumentType
import de.lambda9.ready2race.backend.calls.requests.*
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.calls.serialization.jsonMapper
import de.lambda9.tailwind.core.KIO
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*

fun Route.documentTemplate() {

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

            val uploads = mutableListOf<FileUpload>()
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
                                FileUpload(
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
                val req = !KIO.failOnNull(templateRequest) { RequestError.BodyMissing(DocumentTemplateRequest.example) }
                DocumentTemplateService.addTemplate(uploads.first(), req)
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