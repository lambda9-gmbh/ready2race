package de.lambda9.ready2race.backend.app.webDAV.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService.initializeExportData
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVImportService.getImportOptionFolders
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVImportService.getImportOptionTypes
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportRequest
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVImportRequest
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import io.ktor.server.routing.*

fun Route.webDAV() {
    route("/webDAV") {
        route("/export") {
            post {

                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateWebDavGlobal)

                    val body = !receiveKIO(WebDAVExportRequest.example)
                    initializeExportData(body, user.id!!)
                }
            }
            get {
                call.respondComprehension {
                    !authenticate(Privilege.ReadWebDavGlobal)

                    WebDAVExportService.getExportStatus()
                }
            }
        }

        route("/import") {

            post {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateWebDavGlobal)

                    val body = !receiveKIO(WebDAVImportRequest.example)
                    WebDAVImportService.initializeImportData(body, user.id!!)
                }
            }

            get {
                call.respondComprehension {
                    !authenticate(Privilege.ReadWebDavGlobal)

                    WebDAVImportService.getImportStatus()
                }
            }

            route("/options") {
                get {
                    call.respondComprehension {
                        !authenticate(Privilege.ReadWebDavGlobal)

                        getImportOptionFolders()
                    }
                }

                route("/{folderName}") {
                    get {
                        call.respondComprehension {
                            !authenticate(Privilege.ReadWebDavGlobal)
                            val folderName = !pathParam("folderName")

                            getImportOptionTypes(folderName)
                        }
                    }
                }
            }
        }
    }
}