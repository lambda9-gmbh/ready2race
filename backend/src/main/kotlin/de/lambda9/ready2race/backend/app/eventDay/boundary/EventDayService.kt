package de.lambda9.ready2race.backend.app.eventDay.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayHasCompetitionRepo
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayRepo
import de.lambda9.ready2race.backend.app.eventDay.control.eventDayDto
import de.lambda9.ready2race.backend.app.eventDay.control.toRecord
import de.lambda9.ready2race.backend.app.eventDay.entity.*
import de.lambda9.ready2race.backend.app.competition.control.CompetitionRepo
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.eventDay.control.TimeslotRepo
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDayHasCompetitionRecord
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.calls.responses.fileResponse
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDayRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.TimeslotRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.ready2race.backend.pdf.FontStyle
import de.lambda9.ready2race.backend.pdf.Padding
import de.lambda9.ready2race.backend.pdf.document
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.traverse
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.*

object EventDayService {

    fun addEventDay(
        request: EventDayRequest,
        userId: UUID,
        eventId: UUID
    ): App<ServiceError, ApiResponse.Created> = KIO.comprehension {

        val event = !EventRepo.get(eventId).orDie()
            .onNullFail { EventError.NotFound }
        !KIO.failOn(event.challengeEvent == true) { EventDayError.IsChallengeEvent }

        val record = !request.toRecord(userId, eventId)
        val id = !EventDayRepo.create(record).orDie()
        KIO.ok(ApiResponse.Created(id))
    }

    fun pageByEvent(
        eventId: UUID,
        params: PaginationParameters<EventDaySort>,
        competitionId: UUID?,
        scope: Privilege.Scope?
    ): App<ServiceError, ApiResponse.Page<EventDayDto, EventDaySort>> = KIO.comprehension {

        !EventService.checkEventExisting(eventId)

        val total =
            if (competitionId == null) !EventDayRepo.countByEvent(eventId, params.search, scope).orDie()
            else !EventDayRepo.countByEventAndCompetition(eventId, competitionId, params.search).orDie()

        val page =
            if (competitionId == null) !EventDayRepo.pageByEvent(eventId, params, scope).orDie()
            else !EventDayRepo.pageByEventAndCompetition(eventId, competitionId, params).orDie()

        page.traverse { it.eventDayDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun getEventDay(
        eventDayId: UUID,
        scope: Privilege.Scope?
    ): App<EventDayError, ApiResponse> = KIO.comprehension {
        val eventDay =
            !EventDayRepo.getEventDay(eventDayId, scope).orDie().onNullFail { EventDayError.EventDayNotFound }
        eventDay.eventDayDto().map { ApiResponse.Dto(it) }
    }

    fun updateEventDay(
        request: EventDayRequest,
        userId: UUID,
        eventDayId: UUID,
    ): App<EventDayError, ApiResponse.NoData> =
        EventDayRepo.update(eventDayId) {
            date = request.date
            name = request.name
            description = request.description
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()
            .onNullFail { EventDayError.EventDayNotFound }
            .map { ApiResponse.NoData }

    fun deleteEvent(
        id: UUID
    ): App<EventDayError, ApiResponse.NoData> = KIO.comprehension {
        val deleted = !EventDayRepo.delete(id).orDie()

        if (deleted < 1) {
            KIO.fail(EventDayError.EventDayNotFound)
        } else {
            noData
        }
    }

    fun updateEventDayHasCompetition(
        request: AssignCompetitionsToDayRequest,
        userId: UUID,
        eventDayId: UUID
    ): App<EventDayError, ApiResponse.NoData> = KIO.comprehension {

        val eventDayExists = !EventDayRepo.exists(eventDayId).orDie()
        if (!eventDayExists) KIO.fail(EventDayError.EventDayNotFound)

        val unknownCompetitions = !CompetitionRepo.findUnknown(request.competitions).orDie()
        if (unknownCompetitions.isNotEmpty()) KIO.fail(EventDayError.CompetitionsNotFound(unknownCompetitions))

        !EventDayHasCompetitionRepo.deleteByEventDay(eventDayId).orDie()
        !EventDayHasCompetitionRepo.create(request.competitions.map {
            EventDayHasCompetitionRecord(
                eventDay = eventDayId,
                competition = it,
                createdAt = LocalDateTime.now(),
                createdBy = userId
            )
        }).orDie()

        noData
    }

    fun downloadEventDaySchedulePdf(eventDay: UUID): App<ServiceError, ApiResponse.File> = KIO.comprehension {
        generateEventDaySchedulePdf(eventDay).fileResponse()
    }

    fun generateEventDaySchedulePdf(eventDayId: UUID)
    : App<ServiceError, File>
    = KIO.comprehension {
        val eventDay = !EventDayRepo.getEventDay(eventDayId, null).orDie()
            .onNullFail { EventDayError.EventDayNotFound }
        val event = !EventRepo.get(eventDay.event).orDie()
            .onNullFail { EventError.NotFound }

        val timeslots = (!TimeslotRepo.getByEventDay(eventDayId).orDie()).sortedBy { it.startTime }
        val bytes = buildPdf(event, listOf(Pair(eventDay, timeslots)))
        KIO.ok(
            File(
                name = "schedule-${event.name}_${eventDay.date}.pdf",
                bytes = bytes,
            )
        )
    }

    fun buildPdf(event: EventRecord, data: List<Pair<EventDayRecord, List<TimeslotRecord>>>, ): ByteArray {
        val doc = document(null) {
            page {
                block {
                    text(
                        fontSize = 14f,
                        fontStyle = FontStyle.BOLD,
                        centered = true,
                    ){
                        "Event Zeitplan"
                    }
                    text(
                        fontSize = 10f,
                        fontStyle = FontStyle.BOLD,
                        centered = true,
                    ){
                        "Event Shedule"
                    }
                    text(
                        fontSize = 14f,
                        fontStyle = FontStyle.BOLD,
                        centered = true,
                    ){
                        ""
                    }
                    text(
                        fontSize = 14f,
                        fontStyle = FontStyle.BOLD,
                        centered = true,
                    ){
                        event.name
                    }
                    text(
                        fontSize = 14f,
                        fontStyle = FontStyle.BOLD,
                        centered = true,
                    ){
                        ""
                    }
                }
                data.forEach {
                    val firstTimeslot = it.second.firstOrNull()
                    val rest = if (firstTimeslot != null) it.second.drop(1) else it.second
                    fun displayDay() {
                        block {
                            text(
                                fontStyle = FontStyle.BOLD
                            ){
                                "${it.first.date}" + (if (it.first.name != null) ":  ${it.first.name}"  else "")
                            }
                            if (it.first.description == null) {
                                text(fontSize = 8f){
                                    "Keine Beschreibung"
                                }
                                text(fontSize = 6f,
                                    newLine = false){
                                    " / No description"
                                }
                            } else {
                                text { it.first.description!! }
                            }
                            text { "" }
                        }
                    }
                    fun timeslotDisplay(timeslot: TimeslotRecord) = block (
                        keepTogether = true,
                        padding = Padding(left = 8F),
                    ) {
                        text { timeslot.name }
                        text { "${timeslot.startTime} - ${timeslot.startTime}" }
                        if (timeslot.description == null) {
                            text(fontSize = 8f){
                                "Keine Beschreibung"
                            }
                            text(fontSize = 6f,
                                newLine = false){
                                " / No description"
                            }
                        } else {
                            text { timeslot.description!! }
                        }
                        text { "" }
                    }
                    block(
                        keepTogether = true
                    ) {
                        displayDay()
                        if (firstTimeslot != null) {
                            timeslotDisplay(firstTimeslot)
                        } else {
                            block (padding = Padding(left = 8F)) {
                                text(fontSize = 8f){
                                    "Keine Zeitplanung vorhanden"
                                }
                                text(fontSize = 6f,
                                    newLine = false){
                                    " / No schedule available"
                                }
                                text { "" }
                            }
                        }
                    }
                    rest.forEach { timeslot ->
                        timeslotDisplay(timeslot)
                    }
                }
            }
        }
        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()

        val bytes = out.toByteArray()
        out.close()

        return bytes
    }
}