package de.lambda9.ready2race.backend.app.contactInformation.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.contactInformation.control.ContactInformationRepo
import de.lambda9.ready2race.backend.app.contactInformation.control.ContactInformationUsageRepo
import de.lambda9.ready2race.backend.app.contactInformation.control.toDto
import de.lambda9.ready2race.backend.app.contactInformation.control.toRecord
import de.lambda9.ready2race.backend.app.contactInformation.entity.AssignContactInformationRequest
import de.lambda9.ready2race.backend.app.contactInformation.entity.AssignedContactInformationDto
import de.lambda9.ready2race.backend.app.contactInformation.entity.ContactInformationDto
import de.lambda9.ready2race.backend.app.contactInformation.entity.ContactInformationError
import de.lambda9.ready2race.backend.app.contactInformation.entity.ContactInformationRequest
import de.lambda9.ready2race.backend.app.contactInformation.entity.ContactInformationSort
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.kio.onFalseFail
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.UUID

object ContactInformationService {

    fun getAssigned(
        event: UUID?,
    ): App<ServiceError, ApiResponse.Dto<AssignedContactInformationDto>> = KIO.comprehension {
        if (event != null) {
            !EventService.checkEventExisting(event)
        }

        val record = !ContactInformationUsageRepo.getByEvent(event).orDie()
        val assigned = record?.let { !ContactInformationRepo.get(it.contactInformation).orDie() }
        val dto = assigned?.toDto()?.not()

        KIO.ok(
            ApiResponse.Dto(
                AssignedContactInformationDto(
                    assigned = dto
                )
            )
        )
    }

    fun assignContact(
        request: AssignContactInformationRequest,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        if (request.event != null) {
            !EventService.checkEventExisting(request.event)
        }
        if (request.contact != null) {
            !ContactInformationRepo.exists(request.contact).orDie().onFalseFail { ContactInformationError.NotFound }
            val record = !request.toRecord(userId)
            !ContactInformationUsageRepo.upsert(record).orDie()
        } else {
            !ContactInformationUsageRepo.deleteByEvent(request.event).orDie()
        }

        noData
    }

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