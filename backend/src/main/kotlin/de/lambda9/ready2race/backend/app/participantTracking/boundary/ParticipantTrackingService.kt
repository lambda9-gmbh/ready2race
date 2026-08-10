package de.lambda9.ready2race.backend.app.participantTracking.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationTeamRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.control.toParticipantForExecutionDto
import de.lambda9.ready2race.backend.app.eventDay.entity.EventDaySort
import de.lambda9.ready2race.backend.app.participant.control.ParticipantRepo
import de.lambda9.ready2race.backend.app.participant.control.participantDto
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantError
import de.lambda9.ready2race.backend.app.participantTracking.control.*
import de.lambda9.ready2race.backend.app.qrCodeApp.control.QrCodeRepo
import de.lambda9.ready2race.backend.app.participantTracking.control.ParticipantTrackingRepo.insert
import de.lambda9.ready2race.backend.app.participantTracking.entity.*
import de.lambda9.ready2race.backend.app.substitution.boundary.SubstitutionService
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantTrackingChangeRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantTrackingRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantTrackingViewRecord
import de.lambda9.ready2race.backend.kio.onFalseFail
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.ok
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.*

object ParticipantTrackingService {

    fun participantCheckInOut(
        participantId: UUID,
        eventId: UUID,
        userId: UUID,
        checkIn: Boolean,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        !ParticipantRepo.exists(participantId).orDie().onFalseFail { ParticipantError.ParticipantNotFound }

        val currentStatus = !ParticipantTrackingRepo
            .get(participantId, eventId)
            .orDie()
            .map { list ->
                list.maxByOrNull { it.scannedAt!! }?.scanType
            }
        // Ausgecheckt wird nur, wer eingecheckt ist. Der Vergleich läuft bewusst gegen ENTRY und
        // nicht gegen EXIT: sonst käme eine nie gescannte Person (currentStatus == null) durch und
        // das Protokoll bekäme eine Abmeldung ohne zugehörige Anmeldung - eine Rückkehr aus der
        // Arena, in der die Person nie war. Seit ENTRY "in der Arena" bedeutet, ist das die
        // Regel, die das Log für sich lesbar hält.
        //
        // Der Scan am Steg behält diese eigene Formulierung, obwohl es seit dem manuellen
        // Nachtrag auch [ParticipantTrackingLogic.validateSequence] gibt: die beiden Fehler
        // "schon eingecheckt" und "nicht eingecheckt" sind für die Helfer am Steg die eigentliche
        // Auskunft, und die Ganzketten-Prüfung könnte sie nicht auseinanderhalten. Fachlich
        // decken sich beide Regeln - für einen Scan, der immer hinten anwächst, ist das Anhängen
        // an eine abwechselnde Kette genau diese Bedingung.
        !KIO.failOn(currentStatus == ParticipantScanType.ENTRY.name && checkIn) { ParticipantTrackingError.TeamAlreadyCheckedIn }
        !KIO.failOn(currentStatus != ParticipantScanType.ENTRY.name && !checkIn) { ParticipantTrackingError.TeamNotCheckedIn }

        val record = ParticipantTrackingRecord(
            id = UUID.randomUUID(),
            participant = participantId,
            event = eventId,
            scanType = if (checkIn) ParticipantScanType.ENTRY.name else ParticipantScanType.EXIT.name,
            scannedBy = userId,
            scannedAt = LocalDateTime.now(),
            source = ParticipantTrackingSource.QR.name,
        )

        !insert(record).orDie()

        noData
    }

    /**
     * Verlauf und Änderungsspur einer Person. Ausschließlich für Admin und Schiedsrichter - die
     * Begründungen sind interne Angaben und haben in keiner öffentlichen Ansicht etwas zu suchen.
     */
    fun history(
        participantId: UUID,
        eventId: UUID,
    ): App<ServiceError, ApiResponse.Dto<ParticipantTrackingHistoryDto>> = KIO.comprehension {
        !ParticipantRepo.exists(participantId).orDie().onFalseFail { ParticipantError.ParticipantNotFound }

        val entryRecords = !ParticipantTrackingRepo.get(participantId, eventId).orDie()
        val changeRecords = !ParticipantTrackingRepo.changes(participantId, eventId).orDie()

        val entries = !entryRecords.sortedBy { it.scannedAt!! }.traverse { it.toEntryDto() }
        val changes = !changeRecords.traverse { it.toDto() }

        KIO.ok(ApiResponse.Dto(ParticipantTrackingHistoryDto(entries = entries, changes = changes)))
    }

    /**
     * Ein Eintrag von Hand - für den Fall, dass ein Boot ohne Scan abgelegt hat und die Crew
     * trotzdem auf dem Wasser ist.
     */
    fun createManualEntry(
        participantId: UUID,
        eventId: UUID,
        userId: UUID,
        request: ManualTrackingRequest,
    ): App<ServiceError, ApiResponse.Created> = KIO.comprehension {
        !ParticipantRepo.exists(participantId).orDie().onFalseFail { ParticipantError.ParticipantNotFound }

        val trackingId = UUID.randomUUID()
        val existing = !ParticipantTrackingRepo.get(participantId, eventId).orDie()

        !checkSequence(
            existing.map { it.toSequenceEntry() } + ParticipantTrackingLogic.Entry(
                id = trackingId,
                scanType = request.scanType,
                scannedAt = request.scannedAt,
            )
        )

        val now = LocalDateTime.now()

        !insert(
            ParticipantTrackingRecord(
                id = trackingId,
                participant = participantId,
                event = eventId,
                scanType = request.scanType.name,
                scannedBy = userId,
                scannedAt = request.scannedAt,
                source = ParticipantTrackingSource.MANUAL.name,
            )
        ).orDie()

        !ParticipantTrackingRepo.insertChange(
            ParticipantTrackingChangeRecord(
                id = UUID.randomUUID(),
                tracking = trackingId,
                participant = participantId,
                event = eventId,
                changeType = ParticipantTrackingChangeType.CREATE.name,
                newScanType = request.scanType.name,
                newScannedAt = request.scannedAt,
                reason = request.reason,
                createdAt = now,
                createdBy = userId,
            )
        ).orDie()

        KIO.ok(ApiResponse.Created(trackingId))
    }

    /**
     * Die Korrektur eines bestehenden Eintrags - auch eines per QR erfassten. Der Eintrag selbst
     * wird geändert, damit das Protokoll eine Zeile je Ereignis behält; was vorher galt, bleibt in
     * der Änderungsspur stehen.
     *
     * [ParticipantTrackingRecord.source] bleibt unberührt: dass dieser Eintrag ursprünglich vom
     * Scanner kam, wird durch die Korrektur nicht unwahr.
     */
    fun updateEntry(
        trackingId: UUID,
        participantId: UUID,
        eventId: UUID,
        userId: UUID,
        request: ManualTrackingRequest,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        val record = !ParticipantTrackingRepo.findById(trackingId).orDie()

        // Der Eintrag muss zu der Person und der Veranstaltung im Pfad gehören. Sonst liesse sich
        // über die eigene Veranstaltung ein fremder Eintrag verbiegen.
        !KIO.failOn(
            record == null || record.participant != participantId || record.event != eventId
        ) { ParticipantTrackingError.TrackingEntryNotFound }

        val existing = !ParticipantTrackingRepo.get(participantId, eventId).orDie()

        !checkSequence(
            existing.filter { it.id != trackingId }.map { it.toSequenceEntry() } +
                ParticipantTrackingLogic.Entry(
                    id = trackingId,
                    scanType = request.scanType,
                    scannedAt = request.scannedAt,
                )
        )

        val previousScanType = record!!.scanType
        val previousScannedAt = record.scannedAt

        !ParticipantTrackingRepo.update(trackingId) {
            scanType = request.scanType.name
            scannedAt = request.scannedAt
        }.orDie()

        !ParticipantTrackingRepo.insertChange(
            ParticipantTrackingChangeRecord(
                id = UUID.randomUUID(),
                tracking = trackingId,
                participant = participantId,
                event = eventId,
                changeType = ParticipantTrackingChangeType.UPDATE.name,
                previousScanType = previousScanType,
                previousScannedAt = previousScannedAt,
                newScanType = request.scanType.name,
                newScannedAt = request.scannedAt,
                reason = request.reason,
                createdAt = LocalDateTime.now(),
                createdBy = userId,
            )
        ).orDie()

        noData
    }

    private fun ParticipantTrackingViewRecord.toSequenceEntry() = ParticipantTrackingLogic.Entry(
        id = id!!,
        scanType = ParticipantScanType.valueOf(scanType!!),
        scannedAt = scannedAt!!,
    )

    /** Übersetzt den Regelverstoß in den Fehler, den die Oberfläche zeigt. */
    private fun checkSequence(entries: List<ParticipantTrackingLogic.Entry>): App<ServiceError, Unit> =
        when (ParticipantTrackingLogic.validateSequence(entries)) {
            null -> KIO.ok(Unit)
            is ParticipantTrackingLogic.SequenceViolation.Collision ->
                KIO.fail(ParticipantTrackingError.TimestampCollision)

            is ParticipantTrackingLogic.SequenceViolation.OutOfOrder ->
                KIO.fail(ParticipantTrackingError.SequenceConflict)
        }

    fun page(
        eventId: UUID,
        params: PaginationParameters<ParticipantTrackingSort>,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope,
    ): App<Nothing, ApiResponse.Page<ParticipantTrackingDto, ParticipantTrackingSort>> = KIO.comprehension {
        val total = !ParticipantTrackingRepo.count(params.search, eventId, user, scope).orDie()
        val page = !ParticipantTrackingRepo.page(params, eventId, user, scope).orDie()

        page.traverse { it.toDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }


    fun getByParticipantQrCode(
        qrCode: String,
        eventId: UUID
    ): App<ServiceError, ApiResponse.ListDto<TeamForScanOverviewDto>> = KIO.comprehension {
        // Find participant with QR code
        val qrCodeRecord = !QrCodeRepo.findByCode(qrCode).orDie()
        !KIO.failOn(qrCodeRecord == null) { ParticipantTrackingError.QrCodeNotFound }
        !KIO.failOn(qrCodeRecord!!.participant == null) { ParticipantTrackingError.QrCodeNotAssociatedWithParticipant }

        // Get all registrations for this event
        val competitionRegistrationTeams =
            !CompetitionRegistrationTeamRepo.getCompetitionRegistrationTeams(eventId).orDie()

        val registrationIdsToSubstitutions = competitionRegistrationTeams.map { team ->
            team.competitionRegistrationId!! to team.substitutions!!.filterNotNull()
        }

        val participantsForExecution = competitionRegistrationTeams.map { team ->
            team.participants!!.filterNotNull().map {
                !it.toParticipantForExecutionDto(
                    clubId = team.clubId!!,
                    clubName = team.clubName!!,
                    registrationName = team.teamName
                )
            }
        }.flatten()

        val currentlyParticipatingParticipants = !SubstitutionService.getParticipantsCurrentlyParticipatingHelper(
            registrationParticipants = participantsForExecution,
            substitutions = competitionRegistrationTeams.flatMap { it.substitutions!!.filterNotNull() }
        )

        val teamsWithParticipant =
            !competitionRegistrationTeams
                .filter { team ->
                    team.participants!!.any { it!!.participantId == qrCodeRecord.participant!! }
                        || (currentlyParticipatingParticipants.find { it.id == qrCodeRecord.participant!! }?.competitionRegistrationId == team.competitionRegistrationId)
                }.traverse { it.toTeamForScanOverviewDtos() }

        val teamsWithSubstitutions = teamsWithParticipant.map { team ->
            val participants = !team.toParticipantForExecutionDtos()

            // Get current round of the competition to get the substitutions of the current round for this registration
            val currentRoundId = !CompetitionExecutionService.getCurrentRoundId(team.competitionId)
            val substitutionsInRound =
                registrationIdsToSubstitutions.first { it.first == team.competitionRegistrationId }.second.filter {
                    it.competitionSetupRoundId == currentRoundId
                }

            val actuallyParticipating = !CompetitionExecutionService.getActuallyParticipatingParticipants(
                teamParticipants = participants,
                substitutionsForRegistration = substitutionsInRound
            ).map { ps ->
                !ps.traverse { p ->
                    // Get the trackings since the data is lost through the previous function or needs to be fetched for substitutions
                    val knownParticipant =
                        teamsWithParticipant.flatMap { it.participants }.find { it.participantId == p.id }
                    if (knownParticipant == null) {
                        val unknownParticipantTracking = !ParticipantTrackingRepo.get(p.id, eventId).orDie()
                        val lastScan = unknownParticipantTracking.maxByOrNull { it.scannedAt!! }
                        p.toTeamForScanOverviewDto(
                            currentStatus = if (lastScan != null) ParticipantScanType.valueOf(lastScan.scanType!!) else null,
                            lastScanAt = lastScan?.scannedAt
                        )
                    } else {
                        p.toTeamForScanOverviewDto(
                            currentStatus = knownParticipant.currentStatus,
                            lastScanAt = knownParticipant.lastScanAt
                        )
                    }

                }
            }

            team.copy(participants = actuallyParticipating)
        }

        ok(ApiResponse.ListDto(teamsWithSubstitutions))
    }


}