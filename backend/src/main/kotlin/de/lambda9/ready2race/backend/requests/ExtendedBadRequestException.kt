package de.lambda9.ready2race.backend.requests

import io.ktor.server.plugins.*

class ExtendedBadRequestException( // todo: never used?
    val requestError: RequestError,
    message: String = "Request validation failed.",
) : BadRequestException(message, null)