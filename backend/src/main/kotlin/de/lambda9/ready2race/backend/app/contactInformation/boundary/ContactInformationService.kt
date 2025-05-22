package de.lambda9.ready2race.backend.app.contactInformation.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.contactInformation.control.ContactInformationRepo
import de.lambda9.ready2race.backend.app.contactInformation.control.toDto
import de.lambda9.ready2race.backend.app.contactInformation.control.toRecord
import de.lambda9.ready2race.backend.app.contactInformation.entity.ContactInformationDto
import de.lambda9.ready2race.backend.app.contactInformation.entity.ContactInformationError
import de.lambda9.ready2race.backend.app.contactInformation.entity.ContactInformationRequest
import de.lambda9.ready2race.backend.app.contactInformation.entity.ContactInformationSort
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.UUID

object ContactInformationService {

    fun addContact(
        request: ContactInformationRequest,
        userId: UUID,
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {

        val record = !request.toRecord(userId)

        ContactInformationRepo.create(record).orDie().map {
            ApiResponse.Created(it)
        }
    }

    fun page(
        params: PaginationParameters<ContactInformationSort>,
    ): App<Nothing, ApiResponse.Page<ContactInformationDto, ContactInformationSort>> = KIO.comprehension {
        val total = !ContactInformationRepo.count(params.search).orDie()
        val page = !ContactInformationRepo.page(params).orDie()

        page.traverse { it.toDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total),
            )
        }
    }

    fun updateContact(
        id: UUID,
        request: ContactInformationRequest,
        userId: UUID,
    ): App<ContactInformationError, ApiResponse.NoData> =
        ContactInformationRepo.update(id) {
            name = request.name
            addressZip = request.addressZip
            addressStreet = request.addressStreet
            email = request.email
            updatedAt = LocalDateTime.now()
            updatedBy = userId
        }.orDie()
            .onNullFail { ContactInformationError.NotFound }
            .map { ApiResponse.NoData }

    fun deleteContact(
        id: UUID,
    ): App<ContactInformationError, ApiResponse.NoData> = KIO.comprehension {
        val deleted = !ContactInformationRepo.delete(id).orDie()

        if (deleted < 1) {
            KIO.fail(ContactInformationError.NotFound)
        } else {
            noData
        }
    }

}