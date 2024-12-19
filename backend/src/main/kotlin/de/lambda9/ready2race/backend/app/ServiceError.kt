package de.lambda9.ready2race.backend.app

import de.lambda9.ready2race.backend.http.ApiError

interface ServiceError {

    fun respond(): ApiError
}