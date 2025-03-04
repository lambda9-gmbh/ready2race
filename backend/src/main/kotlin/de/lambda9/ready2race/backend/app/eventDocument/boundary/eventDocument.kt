package de.lambda9.ready2race.backend.app.eventDocument.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.eventDocument.entity.EventDocumentRequest
import de.lambda9.ready2race.backend.app.eventDocument.entity.EventDocumentViewSort
import de.lambda9.ready2race.backend.requests.*
import de.lambda9.ready2race.backend.responses.respondKIO
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
            // todo: extract for reuse
            // todo: why?
            /*
            * multiPartData.forEachPart { }
            * ->
            * Flow invariant is violated:
		        Flow was collected in [NettyDispatcher@1912dfa1, io.ktor.server.engine.DefaultUncaughtExceptionHandler@be039ae, CoroutineName(call-handler), io.ktor.server.netty.NettyDispatcher$CurrentContext@44e05ec2, ScopeCoroutine{Active}@7a709893, io.ktor.server.application.ClassLoaderAwareContinuationInterceptor@7510bfab],
		        but emission happened in [NettyDispatcher@1912dfa1, io.ktor.server.engine.DefaultUncaughtExceptionHandler@be039ae, CoroutineName(call-handler), io.ktor.server.netty.NettyDispatcher$CurrentContext@44e05ec2, ScopeCoroutine{Active}@7a709893, io.ktor.server.application.ClassLoaderAwareContinuationInterceptor@7510bfab].
		        Please refer to 'flow' documentation or use 'flowOn' instead
            * */

            val uploads = mutableListOf<Pair<String, ByteArray>>()
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
                                part.originalFileName!! to part.provider().toByteArray()
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

            call.respondKIO {
                KIO.comprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val eventId = !pathParam("eventId") { UUID.fromString(it) }
                    val type = !KIO.effect {
                        documentType?.let { UUID.fromString(it) }
                    }.mapError { RequestError.ParameterUnparsable("documentType") } // todo: better Error type
                    EventDocumentService.saveDocuments(eventId, uploads, type, user.id!!)
                }
            }

        }

        get {
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.ReadEventGlobal)
                    val params = !pagination<EventDocumentViewSort>()
                    EventDocumentService.page(params)
                }
            }
        }

        route("/{eventDocumentId}") {

            get {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.ReadEventGlobal)
                        val id = !pathParam("eventDocumentId") { UUID.fromString(it) }
                        EventDocumentService.downloadDocument(id)
                    }
                }
            }

            put {
                val payload = call.receiveV(EventDocumentRequest.example)
                call.respondKIO {
                    KIO.comprehension {
                        val user = !authenticate(Privilege.UpdateEventGlobal)
                        val id = !pathParam("eventDocumentId") { UUID.fromString(it) }
                        val body = !payload
                        EventDocumentService.updateDocument(id, body, user.id!!)
                    }
                }
            }

            delete {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.UpdateEventGlobal)
                        val id = !pathParam("eventDocumentId") { UUID.fromString(it) }
                        EventDocumentService.deleteDocument(id)
                    }
                }
            }

        }
    }
}