package de.lambda9.ready2race.backend.plugins

import de.lambda9.ready2race.backend.config.Config
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*

fun Application.configureHTTP(mode: Config.Mode) {
    install(DefaultHeaders) {
        header("X-Engine", "Ktor")
    }

    install(Compression) {
        gzip()
        deflate()

        // Audio, video, image and text/event-stream are excluded by Ktor itself. These formats are
        // containers that are already compressed, so gzipping them only costs cpu time.
        excludeContentType(ContentType.Application.Pdf)
        excludeContentType(ContentType.Application.Zip)
        excludeContentType(ContentType.Application.OctetStream)
        excludeContentType(ContentType.parse("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))

        // Below this size the gzip header outweighs anything we save.
        minimumSize(1024)
    }

    if (mode == Config.Mode.DEV) {
        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.ContentType)
            allowHeader("X-Api-Session")
            exposeHeader(HttpHeaders.ContentDisposition)
            exposeHeader("X-Api-Session")
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
            allowCredentials = true
        }
    }
}
