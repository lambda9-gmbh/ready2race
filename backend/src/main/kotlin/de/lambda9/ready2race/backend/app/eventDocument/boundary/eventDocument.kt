package de.lambda9.ready2race.backend.app.eventDocument.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.eventDocument.entity.EventDocumentRequest
import de.lambda9.ready2race.backend.app.eventDocument.entity.EventDocumentViewSort
import de.lambda9.ready2race.backend.calls.requests.*
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import de.lambda9.tailwind.core.KIO
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import java.util.*

fun Route.eventDocument() {
    route("/eventDocument") {

        post {
            val multiPartData = call.receiveMultipart() // todo: default limit 50MB, need custom value?
            // todo: extract for reuse, pack into kio

            // todo: why?
            /*
            * multiPartData.forEachPart { }
            * ->
            * Flow invariant is violated:
		        Flow was collected in [NettyDispatcher@1912dfa1, io.ktor.server.engine.DefaultUncaughtExceptionHandler@be039ae, CoroutineName(call-handler), io.ktor.server.netty.NettyDispatcher$CurrentContext@44e05ec2, ScopeCoroutine{Active}@7a709893, io.ktor.server.application.ClassLoaderAwareContinuationInterceptor@7510bfab],
		        but emission happened in [NettyDispatcher@1912dfa1, io.ktor.server.engine.DefaultUncaughtExceptionHandler@be039ae, CoroutineName(call-handler), io.ktor.server.netty.NettyDispatcher$CurrentContext@44e05ec2, ScopeCoroutine{Active}@7a709893, io.ktor.server.application.ClassLoaderAwareContinuationInterceptor@7510bfab].
		        Please refer to 'flow' documentation or use 'flowOn' instead
            * */

            val uploads = mutableListOf<FileUpload>()
            var documentType: String? = null

            var done = false
            while (!done) {
                val part = multiPartData.readPart()
                if (part == null) {
                    done = true
                } else {
                    when (part) {
                        is PartData.FileItem -> {
                            uploads.add(
                                FileUpload(
                                    part.originalFileName!!,
                                    part.provider().toByteArray(),

                                    )
                            )
                        }

                        is PartData.FormItem -> {
                            if (part.name == "documentType") {
                                documentType = part.value
                            }
                        }

                        else -> {}
                    }
                    part.dispose()
                }
            }

            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val type = !KIO.effect {
                    documentType?.let { UUID.fromString(it) }
                }
                    .mapError { RequestError.Other(Exception("Expected UUID or null as 'documentType'")) } // todo: @improve: specific Error type
                EventDocumentService.saveDocuments(eventId, uploads, type, user.id!!)
            }

        }

        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val params = !pagination<EventDocumentViewSort>()
                EventDocumentService.page(params)
            }
        }

        route("/{eventDocumentId}") {

            get {
                call.respondComprehension {
                    val (_, scope) = !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                    val id = !pathParam("eventDocumentId", uuid)

                    EventDocumentService.downloadDocument(id, scope)
                }
            }

            put {
                val payload = call.receiveKIO(EventDocumentRequest.example)
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val id = !pathParam("eventDocumentId", uuid)

                    val body = !payload
                    EventDocumentService.updateDocument(id, body, user.id!!)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val id = !pathParam("eventDocumentId", uuid)
                    EventDocumentService.deleteDocument(id)
                }
            }

        }
    }
}