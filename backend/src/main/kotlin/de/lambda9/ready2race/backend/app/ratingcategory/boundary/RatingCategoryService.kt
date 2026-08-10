package de.lambda9.ready2race.backend.app.ratingcategory.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.ratingcategory.control.*
import de.lambda9.ready2race.backend.app.ratingcategory.entity.*
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.calls.responses.createdResponse
import de.lambda9.ready2race.backend.calls.responses.noDataResponse
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.andThen
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.UUID

object RatingCategoryService {

    fun addCategory(
        request: RatingCategoryRequest,
        userId: UUID,
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {
        val record = !request.toRecord(userId)
        RatingCategoryRepo.create(record).orDie().createdResponse()
    }

    fun page(
        params: PaginationParameters<RatingCategorySort>,
    ): App<Nothing, ApiResponse.Page<RatingCategoryDto, RatingCategorySort>> = KIO.comprehension {
        val total = !RatingCategoryRepo.count(params.search).orDie()
        val page = !RatingCategoryRepo.page(params).orDie()

        page.traverse { it.toDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun updateCategory(
        id: UUID,
        request: RatingCategoryRequest,
        userId: UUID,
    ): App<RatingCategoryError, ApiResponse.NoData> =
        RatingCategoryRepo.update(id) {
            name = request.name
            description = request.description
            updatedAt = LocalDateTime.now()
            updatedBy = userId
        }.orDie()
            .onNullFail { RatingCategoryError.NotFound }
            .noDataResponse()

    fun deleteCategory(
        id: UUID,
    ): App<RatingCategoryError, ApiResponse.NoData> = KIO.comprehension {
        val deleted = !RatingCategoryRepo.delete(id).orDie()

        if (deleted < 1) {
            KIO.fail(RatingCategoryError.NotFound)
        } else {
            noData
        }
    }


    fun assignToEvent(
        eventId: UUID,
        userId: UUID,
        request: RatingCategoriesToEventRequest,
    ): App<EventError, ApiResponse.NoData> = KIO.comprehension {

        !EventService.checkEventExisting(eventId)

        // Neu zugeordnete Kategorien hängen sich hinten an die Abschnittsreihenfolge an, in der
        // Reihenfolge, in der sie in der Anfrage stehen.
        val firstFreePosition = !EventRatingCategoryRepo.nextSortOrder(eventId).orDie()
        val records = !request.ratingCategories.mapIndexed { index, category ->
            category.toRecord(eventId, userId, firstFreePosition + index)
        }.traverse { it }
        !EventRatingCategoryRepo.insert(records).orDie()

        KIO.ok(ApiResponse.NoData)
    }

    /**
     * Schreibt die Reihenfolge der Ergebnisabschnitte fest. Die Anfrage trägt die vollständige
     * Liste; Kategorien, die sie nicht nennt, rutschen dahinter und behalten untereinander ihre
     * bisherige Reihenfolge.
     */
    fun updateOrderForEvent(
        eventId: UUID,
        userId: UUID,
        request: RatingCategoryOrderRequest,
    ): App<EventError, ApiResponse.NoData> = KIO.comprehension {

        !EventService.checkEventExisting(eventId)

        val assigned = !EventRatingCategoryRepo.getByEvent(eventId).orDie()

        val requested = request.ratingCategories.filter { id -> assigned.any { it.ratingCategory == id } }
        val remaining = assigned
            .filter { it.ratingCategory !in requested }
            .sortedBy { it.sortOrder }
            .map { it.ratingCategory }

        !(requested + remaining).mapIndexed { position, categoryId ->
            EventRatingCategoryRepo.updateSortOrder(eventId, categoryId, position, userId).orDie()
        }.traverse { it }

        KIO.ok(ApiResponse.NoData)
    }

    fun getRatingCategoriesForEvent(
        eventId: UUID
    ): App<EventError, ApiResponse.ListDto<RatingCategoryToEventDto>> = KIO.comprehension {
        !EventService.checkEventExisting(eventId)

        val eventRCs = !EventRatingCategoryViewRepo.get(eventId).orDie()
            .andThen { list -> list.traverse { it.toDto() } }

        KIO.ok(ApiResponse.ListDto(eventRCs))
    }

    fun removeFromEvent(
        eventId: UUID,
        ratingCategoryId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        !EventRatingCategoryRepo.delete(eventId, ratingCategoryId).orDie()

        KIO.ok(ApiResponse.NoData)
    }

    fun getParticipantAgesAreValid(
        eventId: UUID,
        ratingCategoryId: UUID,
        participantYears: Pair<Int, Int>,
    ): App<RatingCategoryError, Boolean> = KIO.comprehension {
        val ageRestriction = !EventRatingCategoryRepo.getByEventAndRatingCategory(eventId, ratingCategoryId).orDie()
            .onNullFail { RatingCategoryError.NotFound }
            .map { record ->
                if (record.yearRestrictionFrom == null && record.yearRestrictionTo == null) {
                    null
                } else {
                    AgeRestriction(
                        from = record.yearRestrictionFrom,
                        to = record.yearRestrictionTo,
                    )
                }
            }
        if (ageRestriction == null) {
            KIO.ok(true)
        } else {

            KIO.ok(
                ((ageRestriction.from != null && ageRestriction.from <= participantYears.first) || ageRestriction.from == null) &&
                    ((ageRestriction.to != null && ageRestriction.to >= participantYears.second) || ageRestriction.to == null)
            )
        }

    }
}