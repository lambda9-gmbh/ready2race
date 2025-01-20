package de.lambda9.ready2race.backend.http

interface ToApiError {

    fun respond(): ApiError
}