package de.lambda9.ready2race.backend.app.participantRequirement.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationRepo
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayRepo
import de.lambda9.ready2race.backend.app.participant.boundary.ParticipantService
import de.lambda9.ready2race.backend.app.participant.control.ParticipantForEventRepo
import de.lambda9.ready2race.backend.app.participant.control.ParticipantRepo
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantError
import de.lambda9.ready2race.backend.app.participantRequirement.control.*
import de.lambda9.ready2race.backend.app.participantRequirement.entity.*
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.requests.logger
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.calls.responses.ToApiError
import de.lambda9.ready2race.backend.csv.CSV
import de.lambda9.ready2race.backend.database.generated.tables.records.EventHasParticipantRequirementRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantHasRequirementForEventRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.ready2race.backend.parsing.Parser.Companion.int
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.ok
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import org.jooq.tools.csv.CSVReader
import java.nio.charset.Charset
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

object ParticipantRequirementService {

    fun addParticipantRequirement(
        request: ParticipantRequirementUpsertDto,
        userId: UUID
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {
        val record = !request.toRecord(userId)
        ParticipantRequirementRepo.create(record).orDie().map {
            ApiResponse.Created(it)
        }
    }

    fun page(
        params: PaginationParameters<ParticipantRequirementSort>
    ): App<Nothing, ApiResponse.Page<ParticipantRequirementDto, ParticipantRequirementSort>> = KIO.comprehension {
        val total = !ParticipantRequirementRepo.count(params.search).orDie()
        val page = !ParticipantRequirementRepo.page(params).orDie()

        page.traverse { it.toDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun activateRequirementForEvent(
        requirementId: UUID,
        eventId: UUID,
        userId: UUID,
        namedParticipantId: UUID? = null,
        qrCodeRequired: Boolean = false
    ): App<ParticipantRequirementError, ApiResponse.NoData> = KIO.comprehension {
        val checkExists = !EventHasParticipantRequirementRepo.exists(eventId, requirementId, namedParticipantId).orDie()
        !KIO.failOn(checkExists) { ParticipantRequirementError.InUse }

        !EventHasParticipantRequirementRepo.create(
            EventHasParticipantRequirementRecord(
                event = eventId,
                participantRequirement = requirementId,
                namedParticipant = namedParticipantId,
                qrCodeRequired = qrCodeRequired,
                createdAt = LocalDateTime.now(),
                createdBy = userId
            )
        ).orDie()

        noData

    }

    fun removeRequirementForEvent(
        requirementId: UUID,
        eventId: UUID,
        namedParticipantId: UUID? = null
    ): App<ParticipantRequirementError, ApiResponse.NoData> = KIO.comprehension {
        val checkExists = !EventHasParticipantRequirementRepo.exists(eventId, requirementId, namedParticipantId).orDie()
        !KIO.failOn(!checkExists) { ParticipantRequirementError.NotFound }
        !EventHasParticipantRequirementRepo.delete(eventId, requirementId, namedParticipantId).orDie()

        noData
    }

    fun pageForEvent(
        params: PaginationParameters<ParticipantRequirementForEventSort>,
        eventId: UUID
    ): App<Nothing, ApiResponse.Page<ParticipantRequirementForEventDto, ParticipantRequirementForEventSort>> =
        KIO.comprehension {
            val total = !ParticipantRequirementForEventRepo.count(params.search, eventId).orDie()
            val page = !ParticipantRequirementForEventRepo.page(params, eventId).orDie()

            page.traverse { it.toDto() }.map {
                ApiResponse.Page(
                    data = it,
                    pagination = params.toPagination(total)
                )
            }
        }

    fun getActiveForEvent(
        params: PaginationParameters<ParticipantRequirementForEventSort>,
        eventId: UUID
    ): App<Nothing, ApiResponse.Page<ParticipantRequirementForEventDto, ParticipantRequirementForEventSort>> =
        KIO.comprehension {
            val total = !ParticipantRequirementForEventRepo.count(params.search, eventId, onlyActive = true).orDie()
            val page = !ParticipantRequirementForEventRepo.page(params, eventId, onlyActive = true).orDie()

            page.traverse { it.toDto() }.map {
                ApiResponse.Page(
                    data = it,
                    pagination = params.toPagination(total)
                )
            }
        }

    /**
     * Ersetzt den KOMPLETTEN Bestätigungszustand einer Bedingung - Massen-Pflege im
     * Verwaltungs-UI, dessen Transfer-Liste den Gesamtzustand als Payload schickt. Wer nicht
     * in [ParticipantRequirementCheckForEventUpsertDto.approvedParticipants] steht, verliert
     * seine Bestätigung.
     *
     * Deshalb ist dieser Weg für Aufrufer mit nur EINEM Datensatz ungeeignet: die Scan-App
     * hat ihn früher mit genau der gescannten Person aufgerufen und damit alle übrigen
     * Bestätigungen der Bedingung gelöscht (Waage-Vorfall am Regattatag). Einzelne
     * Bestätigungen gehen über [approveRequirementForParticipant].
     */
    fun approveRequirementForEvent(
        eventId: UUID,
        dto: ParticipantRequirementCheckForEventUpsertDto,
        userId: UUID
    ): App<ParticipantRequirementError, ApiResponse.NoData> = KIO.comprehension {

        if (!!EventHasParticipantRequirementRepo.exists(eventId, dto.requirementId, dto.namedParticipantId).orDie()) {
            return@comprehension KIO.fail(ParticipantRequirementError.NotFound)
        }

        // Der Rahmen des Abgleichs. Bis zur Migration V202608141900 gab es keinen - eine
        // Bedingung war je Person und Veranstaltung genau einmal abgehakt, und der Abgleich
        // durfte pauschal löschen und schreiben. Seither muss er sagen, WOFÜR er gilt; sonst
        // räumt der Abgleich für Wettkampf B die Waage-Bestätigungen aus Wettkampf A weg und
        // schreibt Häkchen ohne Bezug, die vor dem Start bewusst nicht zählen.
        val requirement = !ParticipantRequirementRepo.get(dto.requirementId).orDie()
            .onNullFail { ParticipantRequirementError.NotFound }
        val scope = RequirementScopeLogic.Scope(
            perEventDay = requirement.perEventDay == true,
            perCompetition = requirement.perCompetition == true,
        )

        if (scope.perCompetition && dto.competitionId == null) {
            return@comprehension KIO.fail(ParticipantRequirementError.CompetitionRequired)
        }

        // Wie beim Scan an der Waage: den Tag bestimmt der Server aus dem Zeitpunkt des
        // Abgleichs, nicht der Aufrufer.
        val eventDay = if (scope.perEventDay) {
            !EventDayRepo.getByEvent(eventId).orDie().map { days ->
                RequirementScopeLogic.eventDayOf(
                    LocalDateTime.now(),
                    days.map { RequirementScopeLogic.EventDayRef(it.id!!, it.date!!) },
                )
            }
        } else {
            null
        }
        val key = RequirementScopeLogic.keyFor(
            scope,
            RequirementScopeLogic.MatchScope(eventDay = eventDay, competition = dto.competitionId),
        )

        // Wer den Rahmen bis eben abgedeckt hat: die Grundlage der Spur. Muss VOR dem Löschen
        // erhoben werden - danach ist nicht mehr feststellbar, wem etwas genommen wurde, und
        // genau diese Frage stellte sich am Regattatag.
        val coveredBefore = !ParticipantHasRequirementForEventRepo.getCoveringParticipantIds(
            eventId = eventId,
            participantRequirementId = dto.requirementId,
            perEventDay = scope.perEventDay,
            eventDayId = key.eventDay,
            perCompetition = scope.perCompetition,
            competitionId = key.competition,
        ).orDie()
        val approvedIds = dto.approvedParticipants.map { it.id }.toSet()
        val now = LocalDateTime.now()

        !ParticipantRequirementLogRepo.create(
            coveredBefore.filterNot { it in approvedIds }.map { participantId ->
                ParticipantRequirementLogRepo.entry(
                    eventId = eventId,
                    participantId = participantId,
                    requirementId = dto.requirementId,
                    action = ParticipantRequirementLogAction.REVOKED,
                    source = ParticipantRequirementLogSource.BULK,
                    eventDayId = key.eventDay,
                    competitionId = key.competition,
                    note = null,
                    userId = userId,
                    at = now,
                )
            } + dto.approvedParticipants.filterNot { it.id in coveredBefore }.map { approved ->
                ParticipantRequirementLogRepo.entry(
                    eventId = eventId,
                    participantId = approved.id,
                    requirementId = dto.requirementId,
                    action = ParticipantRequirementLogAction.APPROVED,
                    source = ParticipantRequirementLogSource.BULK,
                    eventDayId = key.eventDay,
                    competitionId = key.competition,
                    note = approved.note,
                    userId = userId,
                    at = now,
                )
            }
        ).orDie()

        !ParticipantHasRequirementForEventRepo.deleteCoveringWhereParticipantNotInList(
            eventId = eventId,
            participantRequirementId = dto.requirementId,
            approvedParticipants = dto.approvedParticipants.map { it.id },
            perEventDay = scope.perEventDay,
            eventDayId = key.eventDay,
            perCompetition = scope.perCompetition,
            competitionId = key.competition,
        ).orDie()

        // Ein Upsert je Person statt der früheren Trennung "kennt die Datenbank schon / noch
        // nicht": Die alte Trennung fragte nur, ob IRGENDEINE Zeile existiert, und zog bei einem
        // Treffer bloß die Notiz nach - die Bestätigung für den zweiten Wettkampf entstand dann
        // nie. Der Upsert schreibt die fehlende Dimensionszeile und ist bei einer vorhandenen
        // folgenlos.
        !dto.approvedParticipants.traverse<Any?, ParticipantRequirementError, CheckedParticipantRequirement, Unit> { approved ->
            KIO.comprehension {
                // Geschrieben wird nur, wo der Rahmen noch NICHT abgedeckt ist. Ein bedingungsloser
                // Upsert wäre subtil falsch: Er trifft die eindeutige Zeile (Person, Bedingung,
                // Tag, Wettkampf) und legt deshalb bei einer veranstaltungsweiten Bedingung eine
                // zweite Zeile an, sobald die vorhandene aus der Bestandsmigration V202608141900
                // stammt und einen Tag trägt. Abgedeckt war die Person vorher wie nachher - nur
                // stünde sie danach doppelt in der Tabelle.
                if (approved.id !in coveredBefore) {
                    !ParticipantHasRequirementForEventRepo.upsertFulfillment(
                        ParticipantHasRequirementForEventRecord(
                            event = eventId,
                            participant = approved.id,
                            participantRequirement = dto.requirementId,
                            eventDay = key.eventDay,
                            competition = key.competition,
                            note = approved.note,
                            createdBy = userId,
                            createdAt = LocalDateTime.now(),
                        )
                    ).orDie()
                }

                // Die Notiz gehört dem Abgleich: Anders als beim Doppel-Scan an der Waage ist
                // eine geleerte Notiz hier eine Ansage und keine fehlende Angabe - deshalb wird
                // sie ausdrücklich gesetzt, aber nur im Rahmen dieses Abgleichs.
                !ParticipantHasRequirementForEventRepo.updateNoteCovering(
                    eventId = eventId,
                    participantRequirementId = dto.requirementId,
                    participantId = approved.id,
                    note = approved.note,
                    perEventDay = scope.perEventDay,
                    eventDayId = key.eventDay,
                    perCompetition = scope.perCompetition,
                    competitionId = key.competition,
                ).orDie()

                KIO.ok(Unit)
            }
        }

        noData
    }

    /**
     * Bestätigt oder widerruft eine Bedingung für genau EINE Person - der Weg der Scan-App.
     *
     * Rein additiv und idempotent auf Datensatz-Ebene: geschrieben oder gelöscht wird nur der
     * Datensatz dieser Person, andere Personen, Wettkämpfe und Tage bleiben unberührt; ein
     * Doppel-Scan derselben Person ist kein Fehler (siehe
     * [ParticipantHasRequirementForEventRepo.upsertFulfillment]).
     *
     * Die Dimensionen der Erfüllung folgen den Schaltern der Bedingung (V202608141900):
     * bei `perEventDay` zählt der Wettkampftag des Scan-Zeitpunkts - die Waage steht am
     * Veranstaltungsort, "heute" ist der Tag, für den gewogen wird. Bei `perCompetition`
     * entscheidet [ParticipantRequirementApproveForParticipantDto.competitionId]; ohne Angabe
     * wird ohne Wettkampfbezug gespeichert, was bewusst keinen Lauf abdeckt
     * (vorsichtige Richtung, siehe [RequirementScopeLogic.covers]). Der Widerruf löscht
     * dimensionsbewusst über [ParticipantHasRequirementForEventRepo.deleteCovering] - so
     * verschwinden bei veranstaltungsweiten Bedingungen auch die tags-gestempelten Zeilen aus
     * der Bestandsmigration, bei tagesbezogenen aber nur der heutige Tag.
     */
    fun approveRequirementForParticipant(
        eventId: UUID,
        dto: ParticipantRequirementApproveForParticipantDto,
        userId: UUID,
    ): App<ParticipantRequirementError, ApiResponse.NoData> = KIO.comprehension {

        if (!!EventHasParticipantRequirementRepo.exists(eventId, dto.requirementId, dto.namedParticipantId).orDie()) {
            return@comprehension KIO.fail(ParticipantRequirementError.NotFound)
        }

        val requirement = !ParticipantRequirementRepo.get(dto.requirementId).orDie()
            .onNullFail { ParticipantRequirementError.NotFound }
        val scope = RequirementScopeLogic.Scope(
            perEventDay = requirement.perEventDay == true,
            perCompetition = requirement.perCompetition == true,
        )

        // Der Tag wird nur bestimmt, wenn er gebraucht wird - eventDayOf greift bei
        // eintägigen Veranstaltungen auf den einzigen Tag zurück, sonst zählt das Datum.
        val eventDay = if (scope.perEventDay) {
            !EventDayRepo.getByEvent(eventId).orDie().map { days ->
                RequirementScopeLogic.eventDayOf(
                    LocalDateTime.now(),
                    days.map { RequirementScopeLogic.EventDayRef(it.id!!, it.date!!) },
                )
            }
        } else {
            null
        }
        val match = RequirementScopeLogic.MatchScope(eventDay = eventDay, competition = dto.competitionId)

        val key = RequirementScopeLogic.keyFor(scope, match)

        // Die Revisionsspur schreibt beide Richtungen mit (V202608152000). Sie ist der einzige Ort,
        // an dem eine zurückgenommene Bestätigung überhaupt eine Spur hinterlässt: In der
        // Erfüllungstabelle ist die Zeile danach weg, und beim Waage-Vorfall war das der Grund,
        // warum sich nicht mehr sagen ließ, wer sie entfernt hat.
        !ParticipantRequirementLogRepo.create(
            listOf(
                ParticipantRequirementLogRepo.entry(
                    eventId = eventId,
                    participantId = dto.participantId,
                    requirementId = dto.requirementId,
                    action = if (dto.approved) ParticipantRequirementLogAction.APPROVED
                    else ParticipantRequirementLogAction.REVOKED,
                    source = ParticipantRequirementLogSource.SCAN,
                    eventDayId = key.eventDay,
                    competitionId = key.competition,
                    note = dto.note,
                    userId = userId,
                )
            )
        ).orDie()

        if (dto.approved) {
            !ParticipantHasRequirementForEventRepo.upsertFulfillment(
                ParticipantHasRequirementForEventRecord(
                    event = eventId,
                    participant = dto.participantId,
                    participantRequirement = dto.requirementId,
                    eventDay = key.eventDay,
                    competition = key.competition,
                    note = dto.note,
                    createdBy = userId,
                    createdAt = LocalDateTime.now(),
                )
            ).orDie()
        } else {
            !ParticipantHasRequirementForEventRepo.deleteCovering(
                eventId = eventId,
                participantRequirementId = dto.requirementId,
                participantId = dto.participantId,
                perEventDay = scope.perEventDay,
                eventDayId = match.eventDay,
                perCompetition = scope.perCompetition,
                competitionId = match.competition,
            ).orDie()
        }

        noData
    }

    /**
     * Der Bezugsrahmen für die Scan-App: heutiger Wettkampftag und die Wettkämpfe der Person.
     *
     * Der Tag wird hier bestimmt und nicht in der App, weil ihn dieselbe Regel liefern muss wie
     * beim Speichern der Bestätigung ([approveRequirementForParticipant]) - eine falsch gestellte
     * Uhr am Waage-Tablet oder eine abweichende Zeitzone würden sonst zu einem Häkchen führen,
     * das die Anzeige für heute hält und die Prüfung für einen anderen Tag.
     */
    fun getScanScopeForParticipant(
        eventId: UUID,
        participantId: UUID,
    ): App<Nothing, ApiResponse.Dto<ParticipantScanScopeDto>> = KIO.comprehension {

        val today = !EventDayRepo.getByEvent(eventId).orDie().map { days ->
            RequirementScopeLogic.eventDayOf(
                LocalDateTime.now(),
                days.map { RequirementScopeLogic.EventDayRef(it.id!!, it.date!!) },
            )
        }
        val competitions = !ParticipantScanScopeRepo.getCompetitionsOfParticipant(eventId, participantId).orDie()

        KIO.ok(
            ApiResponse.Dto(
                ParticipantScanScopeDto(
                    todayEventDayId = today,
                    competitions = competitions,
                )
            )
        )
    }

    /**
     * Die Revisionsspur einer Veranstaltung (V202608152000) - wer hat wann welche Bestätigung
     * gesetzt oder zurückgenommen, und auf welchem Weg.
     *
     * [limit] ist gedeckelt, weil die Ansicht eine Liste zeigt und keine Auswertung: An einem
     * Regattatag entstehen leicht Tausende Einträge, und die Frage, die hier beantwortet wird
     * ("was ist mit dieser Bedingung passiert?"), braucht die jüngsten.
     */
    fun getLog(
        eventId: UUID,
        requirementId: UUID?,
        participantId: UUID?,
        limit: Int?,
    ): App<Nothing, ApiResponse.ListDto<ParticipantRequirementLogEntryDto>> = KIO.comprehension {
        val entries = !ParticipantRequirementLogRepo.getForEvent(
            eventId = eventId,
            requirementId = requirementId,
            participantId = participantId,
            limit = (limit ?: 200).coerceIn(1, 1000),
        ).orDie()
        KIO.ok(ApiResponse.ListDto(entries))
    }

    /**
     * Derselbe Bezugsrahmen für den Abgleich im Verwaltungs-UI: heutiger Wettkampftag und ALLE
     * Wettkämpfe der Veranstaltung. Der Abgleich geht von der Bedingung aus, nicht von einer
     * Person - deshalb die vollständige Liste statt der Meldungen einer Person.
     */
    fun getScanScopeForEvent(eventId: UUID): App<Nothing, ApiResponse.Dto<ParticipantScanScopeDto>> =
        KIO.comprehension {
            val today = !EventDayRepo.getByEvent(eventId).orDie().map { days ->
                RequirementScopeLogic.eventDayOf(
                    LocalDateTime.now(),
                    days.map { RequirementScopeLogic.EventDayRef(it.id!!, it.date!!) },
                )
            }
            val competitions = !ParticipantScanScopeRepo.getCompetitionsOfEvent(eventId).orDie()

            KIO.ok(ApiResponse.Dto(ParticipantScanScopeDto(todayEventDayId = today, competitions = competitions)))
        }

    /**
     * Die Gemeldeten, denen noch Bedingungen fehlen, als xlsx - Grundlage dafür, die betroffenen
     * Vereine anzuschreiben.
     *
     * [requirementId] grenzt auf eine Bedingung ein; ohne Angabe zählen alle an der
     * Veranstaltung aktiven. Personen ohne offene Bedingung fallen heraus, die Datei enthält
     * also genau die, bei denen etwas zu tun ist.
     */
    fun exportOpenRequirements(
        eventId: UUID,
        requirementId: UUID?,
    ): App<ToApiError, ApiResponse.File> = KIO.comprehension {

        val scopes = !OpenRequirementExportRepo.getActiveRequirementScopes(eventId).orDie()
            .map { all -> if (requirementId == null) all else all.filter { it.id == requirementId } }

        if (requirementId != null && scopes.isEmpty()) {
            return@comprehension KIO.fail(
                ParticipantRequirementError.InvalidConfig("Missing requirement" to requirementId.toString())
            )
        }

        // GLOBAL: die Route lässt nur ReadEventGlobal durch, der Export soll alle Vereine sehen.
        val participants =
            !ParticipantForEventRepo.getByEvent(eventId, clubId = null, scope = Privilege.Scope.GLOBAL).orDie()
        val roleNames = !OpenRequirementExportRepo.getNamedParticipantNames().orDie()
        val competitions = !OpenRequirementExportRepo.getCompetitionsByParticipant(eventId).orDie()
        val registrantEmails = !OpenRequirementExportRepo.getRegistrantEmailByClub(eventId).orDie()

        val rows = participants.mapNotNull { p ->
            val roles = p.namedParticipantIds?.filterNotNull() ?: emptyList()
            val checked = p.participantRequirementsChecked?.mapNotNull { it?.id } ?: emptyList()

            val open = OpenRequirementLogic.openFor(scopes, roles, checked)
            if (open.isEmpty()) return@mapNotNull null

            OpenRequirementExport.Row(
                club = p.externalClubName ?: p.clubName ?: "",
                lastname = p.lastname ?: "",
                firstname = p.firstname ?: "",
                year = p.year,
                roles = roles.mapNotNull { roleNames[it] },
                email = p.email,
                // Der Meldende hängt an clubId - im View `participant_for_event` ist das der
                // Verein der Meldung (event_registration), auch bei Gaststartern, deren
                // Anzeigename oben aus externalClubName kommt.
                registrantEmail = p.clubId?.let { registrantEmails[it] },
                competitions = competitions[p.id] ?: emptyList(),
                openRequirements = open.map { it.name },
            )
        }

        val suffix = requirementId?.let { "-" + scopes.first().name } ?: ""
        val date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        KIO.ok(
            ApiResponse.File(
                name = "${date}_offene-Bedingungen$suffix.xlsx".replace(Regex("[/\\\\:*?\"<>|]"), "-"),
                bytes = OpenRequirementExport.build(rows),
            )
        )
    }

    fun checkRequirementForEvent(
        eventId: UUID,
        csvFile: File,
        config: ParticipantRequirementCheckForEventConfigDto,
        userId: UUID
    ): App<ToApiError, ApiResponse.NoData> = KIO.comprehension {

        // TODO: Add optional checked note

        // Load namedParticipantId from database if this is a named participant requirement
        val namedParticipantId =
            !EventHasParticipantRequirementRepo.getNamedParticipantId(eventId, config.requirementId).orDie()

        if (!!EventHasParticipantRequirementRepo.exists(eventId, config.requirementId, namedParticipantId).orDie()) {
            return@comprehension KIO.fail(ParticipantRequirementError.InvalidConfig("Missing requirement" to config.requirementId.toString()))
        }

        val uncheckedParticipants =
            !ParticipantForEventRepo.getParticipantsForEventWithMissingRequirement(eventId, config.requirementId)
                .orDie()

        // Even if the @uncheckedParticipants list is empty, we should still try to parse and validate the uploaded csv and return any errors.
        val validParticipants = !parseParticipantListUpload(csvFile, config)

        // persist requirements for all matches
        !validParticipants.traverse { vp ->
            uncheckedParticipants.filter { up ->
                RequirementMatchLogic.matches(
                    listFirstname = vp.firstname,
                    listLastname = vp.lastname,
                    listYear = vp.year,
                    listClub = vp.club,
                    registeredFirstname = up.firstname,
                    registeredLastname = up.lastname,
                    registeredYear = up.year,
                    registeredClub = up.externalClubName ?: up.clubName,
                    namedParticipantId = namedParticipantId,
                    registeredRoles = up.namedParticipantIds?.filterNotNull(),
                )
            }.traverse { candidate ->
                ParticipantHasRequirementForEventRepo.create(
                    ParticipantHasRequirementForEventRecord(
                        event = eventId,
                        participant = candidate.id!!,
                        participantRequirement = config.requirementId,
                        createdBy = userId,
                        createdAt = LocalDateTime.now(),
                    )
                )
            }
        }.orDie()

        // TODO return number of found matches and show in FE?
        noData
    }

    private fun parseParticipantListUpload(
        file: File,
        config: ParticipantRequirementCheckForEventConfigDto,
    ): App<ToApiError, List<ValidRequirementParticipant>> = KIO.comprehension {

        val entries = !CSV.read(
            `in` = file.bytes.inputStream(),
            noHeader = config.noHeader,
            separator = config.separator ?: ',',
            charset = config.charset ?: "UTF-8",
        ) {
            val valid = config.requirementColName == null ||
                RequirementMatchLogic.isAccepted(
                    cellValue = !cell(config.requirementColName),
                    acceptedValues = config.requirementIsValidValues,
                )

            if (valid) {
                ValidRequirementParticipant(
                    firstname = !cell(config.firstnameColName),
                    lastname = !cell(config.lastnameColName),
                    year = !optionalCell(config.yearsColName, int),
                    club = !optionalCell(config.clubColName),
                )
            } else {
                null
            }
        }

        ok(entries.filterNotNull())
    }

    private data class ValidRequirementParticipant(
        val firstname: String?,
        val lastname: String?,
        val year: Int?,
        val club: String?
    )

    fun updateParticipantRequirement(
        participantRequirementId: UUID,
        request: ParticipantRequirementUpsertDto,
        userId: UUID,
    ): App<ParticipantRequirementError, ApiResponse.NoData> =
        ParticipantRequirementRepo.update(participantRequirementId) {
            name = request.name
            description = request.description
            publicNote = request.publicNote
            optional = request.optional ?: false
            checkInApp = request.checkInApp ?: false
            publiclyVisible = request.publiclyVisible ?: false
            perEventDay = request.perEventDay ?: false
            perCompetition = request.perCompetition ?: false
            checkEarliestMinutesBefore = request.checkEarliestMinutesBefore
            checkLatestMinutesBefore = request.checkLatestMinutesBefore
            updatedAt = LocalDateTime.now()
            updatedBy = userId
        }.orDie()
            .onNullFail { ParticipantRequirementError.NotFound }
            .map { ApiResponse.NoData }

    fun deleteParticipantRequirement(
        participantRequirementId: UUID,
    ): App<ParticipantRequirementError, ApiResponse.NoData> = KIO.comprehension {

        // TODO check if in use
        val inUse = false

        if (inUse) {
            return@comprehension KIO.fail(
                ParticipantRequirementError.InUse
            )
        }

        val deleted = !ParticipantRequirementRepo.delete(participantRequirementId).orDie()

        if (deleted < 1) {
            KIO.fail(ParticipantRequirementError.NotFound)
        } else {
            noData
        }
    }

    fun assignRequirementToNamedParticipant(
        eventId: UUID,
        requirementId: UUID,
        namedParticipantId: UUID,
        qrCodeRequired: Boolean,
        userId: UUID
    ): App<Nothing, ApiResponse.NoData> = KIO.comprehension {
        !ParticipantRequirementForEventRepo.assignRequirementToNamedParticipant(
            eventId = eventId,
            participantRequirementId = requirementId,
            namedParticipantId = namedParticipantId,
            qrCodeRequired = qrCodeRequired,
            createdBy = userId
        ).orDie()
        noData
    }

    fun updateQrCodeRequirement(
        eventId: UUID,
        requirementId: UUID,
        namedParticipantId: UUID?,
        qrCodeRequired: Boolean
    ): App<Nothing, ApiResponse.NoData> = KIO.comprehension {
        !ParticipantRequirementForEventRepo.updateQrCodeRequirement(
            eventId = eventId,
            participantRequirementId = requirementId,
            namedParticipantId = namedParticipantId,
            qrCodeRequired = qrCodeRequired
        ).orDie()
        noData
    }

    fun getForParticipant(
        eventId: UUID,
        participantId: UUID,
        onlyForApp: Boolean,
    ): App<ParticipantError, ApiResponse.ListDto<ParticipantRequirementForEventDto>> = KIO.comprehension {

        // TODO: Refactor this - This is a shortcut to get the substitution changes on the requirements
        val participant =
            !ParticipantRepo.get(participantId).orDie().onNullFail { ParticipantError.ParticipantNotFound }
        val participantForEvent = !ParticipantService.pageForEvent(
            PaginationParameters(
                limit = null,
                search = null,
                sort = null,
                offset = null
            ),
            eventId = eventId,
            clubId = null,
            scope = Privilege.Scope.GLOBAL,
            specificParticipantId = participant.id,
        ).map { page -> page.data.firstOrNull() }.onNullFail { ParticipantError.ParticipantNotFound }

        val requirementsForEvent = !ParticipantRequirementForEventRepo.get(
            eventId = eventId,
            onlyActive = true,
            onlyForApp = onlyForApp
        ).orDie()

        val requirementsForParticipant = requirementsForEvent.filter { eventReq ->
            eventReq.requirements!!.any { npReq -> participantForEvent.namedParticipantIds.any { it == npReq!!.id } } || eventReq.requirements?.size == 0
        }

        ok(
            ApiResponse.ListDto(
                !requirementsForParticipant.traverse { it.toDto() }
            )
        )
    }

}