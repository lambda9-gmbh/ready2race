package de.lambda9.ready2race.backend.app.webDAV.entity


data class FileExportEventStatusDto(
    val eventName: String,
    val fileExportTypes: List<WebDAVExportType>,
)