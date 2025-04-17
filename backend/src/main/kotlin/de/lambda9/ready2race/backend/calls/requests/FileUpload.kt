package de.lambda9.ready2race.backend.calls.requests

data class FileUpload(
    val fileName: String,
    val bytes: ByteArray,
)
