package de.lambda9.ready2race.backend.plugins

import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import java.io.File

fun Application.configureStaticFiles(staticFilesPath: String) {
    routing {
        val file = File(staticFilesPath)
        println(file.absolutePath)
        staticFiles("static", File(staticFilesPath))
    }
}
