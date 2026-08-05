package de.lambda9.ready2race.backend.app.eventSchedule.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayRepo
import de.lambda9.ready2race.backend.app.eventSchedule.control.EventScheduleRepo
import de.lambda9.ready2race.backend.app.eventSchedule.entity.*
import de.lambda9.ready2race.backend.app.liveDashboard.boundary.LiveDashboardService
import de.lambda9.ready2race.backend.app.liveDashboard.entity.OpenResultHandling
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.EventScheduleSlotRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.ready2race.backend.xls.CellParser
import de.lambda9.ready2race.backend.xls.XLS
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import org.jooq.Record
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/** Eine geparste, noch nicht gematchte Import-Zeile — Zwischenformat zwischen XLS-Read und Matching (Task 12). */
private data class ImportRow(
    val rowNumber: Int,
    val startTime: LocalDateTime,
    val competition: String?,
    val lauf: String,
    val duration: Int?,
)

object EventScheduleService {

    fun getSchedule(eventId: UUID): App<EventScheduleError, ApiResponse.Dto<EventScheduleDto>> =
        KIO.comprehension {
            val exists = !EventRepo.exists(eventId).orDie()
            if (!exists) {
                return@comprehension KIO.fail(EventScheduleError.EventNotFound(eventId))
            }

            val slotRecords = !EventScheduleRepo.getSlots(eventId).orDie()
            val unplanned = !EventScheduleRepo.getUnplannedSetupMatches(eventId).orDie()

            val slots = slotRecords.map { r ->
                val isFree = r[EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH] == null
                val matchExists = r.get("match_exists", Boolean::class.java) == true
                EventScheduleSlotDto(
                    id = r[EVENT_SCHEDULE_SLOT.ID]!!,
                    startTime = r[EVENT_SCHEDULE_SLOT.START_TIME]!!,
                    state = EventScheduleLogic.deriveSlotState(
                        isFree = isFree,
                        skipped = r[EVENT_SCHEDULE_SLOT.SKIPPED_AT] != null,
                        roundMaterialized = r.get("round_materialized", Boolean::class.java) == true,
                        matchExists = matchExists,
                    ),
                    name = r[EVENT_SCHEDULE_SLOT.NAME],
                    durationMinutes = r[EVENT_SCHEDULE_SLOT.DURATION_MINUTES],
                    competitionId = r.get("competition_id", UUID::class.java),
                    competitionName = r.get("competition_name", String::class.java),
                    roundName = r.get("round_name", String::class.java),
                    matchName = r.get("match_name", String::class.java),
                    matchId = if (matchExists) r[EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH] else null,
                    setupMatchId = r[EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH],
                    setupRoundId = r.get("setup_round_id", UUID::class.java),
                    matchStartedAt = r.get("match_started_at", java.time.LocalDateTime::class.java),
                    matchFinishedAt = r.get("match_finished_at", java.time.LocalDateTime::class.java),
                    matchCurrentlyRunning = r[COMPETITION_MATCH.CURRENTLY_RUNNING] == true,
                )
            }

            val unplannedDtos = unplanned.map { r ->
                UnplannedSetupMatchDto(
                    setupMatchId = r[COMPETITION_SETUP_MATCH.ID]!!,
                    competitionId = r.get("competition_id", UUID::class.java)!!,
                    competitionName = r.get("competition_name", String::class.java) ?: "",
                    roundName = r.get("round_name", String::class.java) ?: "",
                    matchName = r.get("match_name", String::class.java),
                )
            }

            KIO.ok(ApiResponse.Dto(EventScheduleDto(slots, unplannedDtos)))
        }

    fun createSlot(
        eventId: UUID,
        request: UpsertScheduleSlotRequest,
        userId: UUID,
    ): App<EventScheduleError, ApiResponse.Created> = KIO.comprehension {
        val eventExists = !EventRepo.exists(eventId).orDie()
        if (!eventExists) {
            return@comprehension KIO.fail(EventScheduleError.EventNotFound(eventId))
        }

        val setupMatchId = request.competitionSetupMatch
        if (setupMatchId != null) {
            val belongsToEvent = !EventScheduleRepo.setupMatchExistsForEvent(eventId, setupMatchId).orDie()
            if (!belongsToEvent) {
                return@comprehension KIO.fail(EventScheduleError.SetupMatchNotFound(setupMatchId))
            }

            val alreadyPlanned = !EventScheduleRepo.slotExistsForSetupMatch(setupMatchId).orDie()
            if (alreadyPlanned) {
                return@comprehension KIO.fail(EventScheduleError.SetupMatchAlreadyPlanned(setupMatchId))
            }
        }

        val now = LocalDateTime.now()
        val record = EventScheduleSlotRecord(
            id = UUID.randomUUID(),
            event = eventId,
            startTime = request.startTime,
            competitionSetupMatch = setupMatchId,
            name = request.name,
            durationMinutes = request.durationMinutes,
            createdAt = now,
            createdBy = userId,
            updatedAt = now,
            updatedBy = userId,
        )
        val id = !EventScheduleRepo.createSlot(record).orDie()

        if (setupMatchId != null) {
            !EventScheduleRepo.stampMatchStartTime(setupMatchId, request.startTime, userId).orDie()
        }

        KIO.ok(ApiResponse.Created(id))
    }

    fun updateSlot(
        eventId: UUID,
        slotId: UUID,
        request: UpsertScheduleSlotRequest,
        userId: UUID,
    ): App<EventScheduleError, ApiResponse.NoData> = KIO.comprehension {
        val setupMatchId = request.competitionSetupMatch
        if (setupMatchId != null) {
            val belongsToEvent = !EventScheduleRepo.setupMatchExistsForEvent(eventId, setupMatchId).orDie()
            if (!belongsToEvent) {
                return@comprehension KIO.fail(EventScheduleError.SetupMatchNotFound(setupMatchId))
            }

            val alreadyPlanned =
                !EventScheduleRepo.slotExistsForSetupMatch(setupMatchId, excludeSlotId = slotId).orDie()
            if (alreadyPlanned) {
                return@comprehension KIO.fail(EventScheduleError.SetupMatchAlreadyPlanned(setupMatchId))
            }
        }

        !EventScheduleRepo.updateSlot(eventId, slotId) {
            startTime = request.startTime
            competitionSetupMatch = request.competitionSetupMatch
            name = request.name
            durationMinutes = request.durationMinutes
            updatedAt = LocalDateTime.now()
            updatedBy = userId
        }.orDie().onNullFail { EventScheduleError.SlotNotFound(slotId) }

        if (setupMatchId != null) {
            !EventScheduleRepo.stampMatchStartTime(setupMatchId, request.startTime, userId).orDie()
        }

        noData
    }

    fun deleteSlot(
        eventId: UUID,
        slotId: UUID,
    ): App<EventScheduleError, ApiResponse.NoData> = KIO.comprehension {
        // Der Lauf behält seine letzte start_time (Spec §8) - keine Rückabwicklung auf competition_match.
        val deleted = !EventScheduleRepo.deleteSlot(eventId, slotId).orDie()

        if (deleted < 1) {
            KIO.fail(EventScheduleError.SlotNotFound(slotId))
        } else {
            noData
        }
    }

    /**
     * Skip/Unskip mit Audit (Spec §8). skip: erlaubt für FREE, WAITING und LINKED ohne
     * `started_at` am Lauf; OBSOLETE ist endgültig (SlotNotSkippable), ein bereits gestarteter
     * Lauf schlägt mit MatchAlreadyStarted fehl. unskip: erlaubt solange kein Lauf des Slots
     * `started_at` trägt.
     */
    fun setSlotSkipped(
        eventId: UUID,
        slotId: UUID,
        skipped: Boolean,
        userId: UUID,
    ): App<EventScheduleError, ApiResponse.NoData> = KIO.comprehension {
        val row = !EventScheduleRepo.getSlotWithContext(eventId, slotId).orDie()
            .onNullFail { EventScheduleError.SlotNotFound(slotId) }

        val isFree = row[EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH] == null
        val matchExists = row.get("match_exists", Boolean::class.java) == true
        val matchStartedAt = row.get("match_started_at", LocalDateTime::class.java)
        val roundMaterialized = row.get("round_materialized", Boolean::class.java) == true
        val alreadySkipped = row[EVENT_SCHEDULE_SLOT.SKIPPED_AT] != null

        if (skipped) {
            val state = EventScheduleLogic.deriveSlotState(
                isFree = isFree,
                skipped = alreadySkipped,
                roundMaterialized = roundMaterialized,
                matchExists = matchExists,
            )

            if (state == EventScheduleSlotState.OBSOLETE) {
                return@comprehension KIO.fail(EventScheduleError.SlotNotSkippable(slotId))
            }
            if (matchStartedAt != null) {
                return@comprehension KIO.fail(EventScheduleError.MatchAlreadyStarted(slotId))
            }

            !EventScheduleRepo.updateSlot(eventId, slotId) {
                skippedAt = LocalDateTime.now()
                skippedBy = userId
            }.orDie().onNullFail { EventScheduleError.SlotNotFound(slotId) }

            // Überspringen kann genau den Slot betreffen, an dem die Kette wartet — danach prüfen,
            // ob sie weiterlaufen kann.
            !ScheduleChainService.resumeIfParked(eventId, userId)
        } else {
            if (matchStartedAt != null) {
                return@comprehension KIO.fail(EventScheduleError.MatchAlreadyStarted(slotId))
            }

            !EventScheduleRepo.updateSlot(eventId, slotId) {
                skippedAt = null
                skippedBy = null
            }.orDie().onNullFail { EventScheduleError.SlotNotFound(slotId) }
        }

        noData
    }

    /**
     * Skip einer ganzen Setup-Runde auf einmal - dieselben Regeln wie [setSlotSkipped], nur für alle
     * Slots dieser Runde im Event. Zwei Durchgänge, damit die Aktion atomar wirkt: erst validieren
     * (nichts schreiben, solange nicht klar ist, dass die ganze Runde durchgeht), dann anwenden.
     *
     * - Bereits übersprungene Slots bleiben, wie sie sind - kein Fehler, kein erneutes Schreiben.
     * - OBSOLETE Slots (die Setup-Zeile existiert nicht mehr) werden übergangen: es gibt nichts mehr
     *   zu überspringen, das blockiert die restliche Runde aber nicht.
     * - Ein bereits gestarteter Lauf lässt die GANZE Aktion mit MatchAlreadyStarted scheitern, auch
     *   wenn andere Slots der Runde noch skippable wären - kein Teilerfolg, damit der Nutzer nicht
     *   glaubt, die Runde sei vollständig übersprungen, obwohl ein laufender Start übrig bleibt.
     */
    fun setRoundSkipped(
        eventId: UUID,
        setupRoundId: UUID,
        userId: UUID,
    ): App<EventScheduleError, ApiResponse.NoData> = KIO.comprehension {
        val rows = !EventScheduleRepo.getSlots(eventId, setupRoundId).orDie()

        val toSkip = mutableListOf<UUID>()
        for (row in rows) {
            val slotId = row[EVENT_SCHEDULE_SLOT.ID]!!
            val alreadySkipped = row[EVENT_SCHEDULE_SLOT.SKIPPED_AT] != null
            if (alreadySkipped) {
                continue
            }

            val isFree = row[EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH] == null
            val matchExists = row.get("match_exists", Boolean::class.java) == true
            val roundMaterialized = row.get("round_materialized", Boolean::class.java) == true
            val state = EventScheduleLogic.deriveSlotState(
                isFree = isFree,
                skipped = false,
                roundMaterialized = roundMaterialized,
                matchExists = matchExists,
            )

            if (state == EventScheduleSlotState.OBSOLETE) {
                continue
            }

            val matchStartedAt = row.get("match_started_at", LocalDateTime::class.java)
            if (matchStartedAt != null) {
                return@comprehension KIO.fail(EventScheduleError.MatchAlreadyStarted(slotId))
            }

            toSkip.add(slotId)
        }

        val now = LocalDateTime.now()
        !toSkip.traverse { slotId ->
            EventScheduleRepo.updateSlot(eventId, slotId) {
                skippedAt = now
                skippedBy = userId
            }.orDie().map { }
        }

        // Wie beim Einzel-Skip: die Kette könnte an einem der jetzt übersprungenen Slots geparkt
        // gewesen sein.
        !ScheduleChainService.resumeIfParked(eventId, userId)

        noData
    }

    /**
     * Beendet den Lauf eines LINKED-Slots vom Zeitplan aus (C1) - das Regattabüro darf das in
     * JEDEM `chainProgressionMode` (anders als [LiveDashboardService.finishMatch], das bei
     * REGATTABUERO mit `FinishReservedForOffice` scheitert). Ruft dieselbe Beenden-Logik wie das
     * Schiedsrichter-Dashboard auf ([LiveDashboardService.finishMatchInternal], dort dokumentiert,
     * warum sie dort statt hier liegt), nur ohne den Modus-Gate - der Modus selbst entscheidet
     * trotzdem weiterhin, ob die Kette danach zieht (DEAKTIVIERT beendet nur den Lauf).
     */
    fun finishSlot(
        eventId: UUID,
        slotId: UUID,
        userId: UUID,
        openResults: OpenResultHandling? = null,
    ): App<EventScheduleError, ApiResponse.NoData> = KIO.comprehension {
        val row = !EventScheduleRepo.getSlotWithContext(eventId, slotId).orDie()
            .onNullFail { EventScheduleError.SlotNotFound(slotId) }

        val matchId = linkedMatchIdOrNull(row)
            ?: return@comprehension KIO.fail(EventScheduleError.SlotNotLinked(slotId))

        val mode = !EventRepo.getChainProgressionMode(eventId).orDie()
        !LiveDashboardService.finishMatchInternal(eventId, matchId, userId, openResults, mode)

        noData
    }

    /**
     * Aktiviert den Lauf eines LINKED-Slots vom Zeitplan aus (C1) - Notfall-Override wie
     * `LiveDashboardService.setMatchRunning`, nur vom Büro statt vom Schiedsrichter-Dashboard aus
     * und in JEDEM Modus erlaubt.
     */
    fun activateSlot(
        eventId: UUID,
        slotId: UUID,
        userId: UUID,
    ): App<EventScheduleError, ApiResponse.NoData> = KIO.comprehension {
        val row = !EventScheduleRepo.getSlotWithContext(eventId, slotId).orDie()
            .onNullFail { EventScheduleError.SlotNotFound(slotId) }

        val matchId = linkedMatchIdOrNull(row)
            ?: return@comprehension KIO.fail(EventScheduleError.SlotNotLinked(slotId))

        !CompetitionMatchRepo.update(matchId) {
            currentlyRunning = true
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()

        noData
    }

    /**
     * matchId eines Slots, wenn er LINKED ist - sonst null. Gemeinsame Vorbedingung für
     * [finishSlot]/[activateSlot]: das Büro darf nur einen echten, verknüpften Lauf beenden oder
     * aktivieren, keinen Platzhalter. Erwartet dieselbe Zeile wie [getSlotWithContext].
     */
    private fun linkedMatchIdOrNull(row: Record): UUID? {
        val isFree = row[EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH] == null
        val matchExists = row.get("match_exists", Boolean::class.java) == true
        val roundMaterialized = row.get("round_materialized", Boolean::class.java) == true
        val skipped = row[EVENT_SCHEDULE_SLOT.SKIPPED_AT] != null

        val state = EventScheduleLogic.deriveSlotState(
            isFree = isFree,
            skipped = skipped,
            roundMaterialized = roundMaterialized,
            matchExists = matchExists,
        )

        return row[EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH].takeIf { state == EventScheduleSlotState.LINKED }
    }

    /**
     * Verschiebt den Zeitstrahl ab [ShiftScheduleRequest.fromSlotId] (Task 11). Betroffen sind nur
     * Slots desselben Renntags ab diesem Slot (aufsteigend) — [EventScheduleRepo.getSlots] liefert
     * bereits nach start_time sortiert, ein Tageswechsel kommt danach also nie zurück.
     * Slot-bezogene Validierung (Ziel muss in dieser Tages-Teilliste hinter dem Start-Slot liegen,
     * delta <= 0 bei COMPRESS_TO_TARGET) passiert hier bewusst VOR dem Aufruf von [EventScheduleLogic.computeShift],
     * dessen `require(targetIndex > 0)` sonst eine IllegalArgumentException werfen würde statt eines
     * fachlichen Fehlers.
     */
    fun shiftSchedule(
        eventId: UUID,
        request: ShiftScheduleRequest,
        userId: UUID,
    ): App<EventScheduleError, ApiResponse.Dto<ShiftPreviewDto>> = KIO.comprehension {
        val allSlots = !EventScheduleRepo.getSlots(eventId).orDie()

        val fromIndex = allSlots.indexOfFirst { it[EVENT_SCHEDULE_SLOT.ID] == request.fromSlotId }
        if (fromIndex == -1) {
            return@comprehension KIO.fail(EventScheduleError.SlotNotFound(request.fromSlotId))
        }

        val fromStartTime = allSlots[fromIndex][EVENT_SCHEDULE_SLOT.START_TIME]!!
        val day = fromStartTime.toLocalDate()

        val daySlotRecords = allSlots.drop(fromIndex)
            .takeWhile { it[EVENT_SCHEDULE_SLOT.START_TIME]!!.toLocalDate() == day }

        val daySlots = daySlotRecords.map {
            ShiftSlot(
                id = it[EVENT_SCHEDULE_SLOT.ID]!!,
                startTime = it[EVENT_SCHEDULE_SLOT.START_TIME]!!,
                durationMinutes = it[EVENT_SCHEDULE_SLOT.DURATION_MINUTES],
            )
        }
        val setupMatchIdBySlot = daySlotRecords.associate {
            it[EVENT_SCHEDULE_SLOT.ID]!! to it[EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH]
        }

        val deltaMinutes = when (request.mode) {
            ShiftMode.PLUS_MINUTES -> request.minutes!!
            ShiftMode.SET_TIME -> Duration.between(fromStartTime, request.newTime!!).toMinutes()
            ShiftMode.COMPRESS_TO_TARGET ->
                request.minutes ?: Duration.between(fromStartTime, request.newTime!!).toMinutes()
        }

        if (deltaMinutes == 0L) {
            return@comprehension KIO.fail(EventScheduleError.InvalidShiftRequest)
        }

        val targetSlotId = if (request.mode == ShiftMode.COMPRESS_TO_TARGET) {
            val targetIndex = daySlots.indexOfFirst { it.id == request.targetSlotId }
            if (targetIndex <= 0 || deltaMinutes < 0) {
                return@comprehension KIO.fail(EventScheduleError.InvalidShiftRequest)
            }
            request.targetSlotId
        } else {
            null
        }

        val entries = when (val result = EventScheduleLogic.computeShift(daySlots, deltaMinutes, targetSlotId)) {
            is ShiftResult.CompressionImpossible ->
                return@comprehension KIO.fail(EventScheduleError.CompressionImpossible(result.maxReductionMinutes))

            is ShiftResult.Ok -> result.entries
        }

        // Ein Shift bleibt im Renntag — über Mitternacht hinaus wäre der Plan des Folgetags still betroffen.
        if (entries.any { it.newStartTime.toLocalDate() != day }) {
            return@comprehension KIO.fail(EventScheduleError.InvalidShiftRequest)
        }

        // Bei einem Vorziehen (deltaMinutes < 0) darf der verschobene Block seinen Vorgänger nicht
        // überholen: der letzte UNverschobene Slot desselben Tages VOR dem Start-Slot behält seine
        // Zeit, keiner der verschobenen Slots darf davor zu liegen kommen - sonst würde die
        // Reihenfolge im Zeitstrahl durcheinandergeraten. Kein Vorgänger am selben Tag (Start-Slot
        // ist der erste des Tages) heißt: keine Grenze von dieser Seite.
        if (deltaMinutes < 0) {
            val predecessor = allSlots.getOrNull(fromIndex - 1)
                ?.takeIf { it[EVENT_SCHEDULE_SLOT.START_TIME]!!.toLocalDate() == day }
                ?.get(EVENT_SCHEDULE_SLOT.START_TIME)

            if (EventScheduleLogic.overtakesPredecessor(entries, predecessor)) {
                return@comprehension KIO.fail(EventScheduleError.InvalidShiftRequest)
            }
        }

        if (!request.dryRun) {
            val changed = entries.filter { it.oldStartTime != it.newStartTime }
            !changed.traverse { entry ->
                KIO.comprehension {
                    !EventScheduleRepo.updateSlot(eventId, entry.slotId) {
                        startTime = entry.newStartTime
                        updatedAt = LocalDateTime.now()
                        updatedBy = userId
                    }.orDie().onNullFail { EventScheduleError.SlotNotFound(entry.slotId) }

                    val setupMatchId = setupMatchIdBySlot[entry.slotId]
                    if (setupMatchId != null) {
                        !EventScheduleRepo.stampMatchStartTime(setupMatchId, entry.newStartTime, userId).orDie()
                    }

                    KIO.unit
                }
            }
        }

        KIO.ok(
            ApiResponse.Dto(
                ShiftPreviewDto(
                    entries = entries.map { ShiftPreviewEntryDto(it.slotId, it.oldStartTime, it.newStartTime) },
                    applied = !request.dryRun,
                )
            )
        )
    }

    /**
     * Excel-Import des Zeitstrahls (Task 12). `dryRun=true` liefert nur die Vorschau (Matching je
     * Zeile, keine Schreiboperation). `dryRun=false` ersetzt bei Erfolg den ganzen Zeitstrahl des
     * Events: erst wird auf Duplikate geprüft (gleiche Setup-Zeile in mehreren Zeilen verlinkt -
     * das ist immer ein Fehler, nicht nur eine Warnung, weil sonst zwei Slots dieselbe
     * competition_match.start_time beanspruchen würden), dann werden alle bestehenden Slots
     * gelöscht und durch die Import-Zeilen ersetzt.
     */
    fun importSchedule(
        eventId: UUID,
        fileBytes: ByteArray,
        dryRun: Boolean,
        userId: UUID,
    ): App<EventScheduleError, ApiResponse.Dto<ScheduleImportResultDto>> = KIO.comprehension {
        val eventExists = !EventRepo.exists(eventId).orDie()
        if (!eventExists) {
            return@comprehension KIO.fail(EventScheduleError.EventNotFound(eventId))
        }

        val eventDays = !EventDayRepo.getByEvent(eventId).orDie()
        val eventYear = eventDays.minOfOrNull { it.date }?.year ?: LocalDate.now().year

        val parsedRows = !XLS.read(fileBytes.inputStream()) {
            val date = !cell("Datum", CellParser.localDate(eventYear))
            val time = !cell("Uhrzeit", CellParser.localTime)
            val competition = !optionalCell("Wettkampf", CellParser.string)
            val lauf = !cell("Lauf", CellParser.string)
            val duration = !optionalCell("Dauer", CellParser.int)
            ImportRow(
                rowNumber = rowNum,
                startTime = LocalDateTime.of(date, time),
                competition = competition,
                lauf = lauf,
                duration = duration,
            )
        }.mapError { EventScheduleError.ImportFileUnreadable }

        val candidateRecords = !EventScheduleRepo.getImportCandidates(eventId).orDie()

        // Anzeigeinfo je Setup-Zeile für targetLabel - getrennt von den (lowercased) Match-Texten.
        val displayInfoBySetupMatch = candidateRecords.associate { r ->
            r[COMPETITION_SETUP_MATCH.ID]!! to Triple(
                r.get("competition_name", String::class.java) ?: "",
                r.get("round_name", String::class.java) ?: "",
                r.get("match_name", String::class.java),
            )
        }

        val candidates = candidateRecords.map { r ->
            val texts = listOfNotNull(
                r[COMPETITION_PROPERTIES.IDENTIFIER],
                r.get("competition_name", String::class.java),
                r[COMPETITION_PROPERTIES.SHORT_NAME],
            ).map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

            ImportCandidate(
                setupMatchId = r[COMPETITION_SETUP_MATCH.ID]!!,
                competitionTexts = texts,
                matchName = r.get("match_name", String::class.java),
                roundName = r.get("round_name", String::class.java) ?: "",
            )
        }

        // rowNumber kommt direkt vom RowReader (physische Excel-Zeilennummer), nicht aus dem
        // Listen-Index - POI überspringt beim Iterieren leere Zeilen stillschweigend, mit einer
        // Leerzeile in der Datei würde Index + 2 sonst auf die falsche Excel-Zeile zeigen.
        val matched = parsedRows.map { row ->
            val (status, setupMatchId) = ScheduleImport.matchRow(row.competition, row.lauf, candidates)
            ImportRowResult(
                rowNumber = row.rowNumber,
                startTime = row.startTime,
                competitionText = row.competition,
                laufText = row.lauf,
                status = status,
                setupMatchId = setupMatchId,
            ) to row.duration
        }

        // Duplikat-Erkennung über alle Zeilen hinweg: gleiche setupMatchId in >1 Zeile -> ALLE
        // betroffenen Zeilen werden DUPLICATE, nicht nur die zweite.
        val setupMatchCounts = matched.mapNotNull { it.first.setupMatchId }.groupingBy { it }.eachCount()
        val finalRows = matched.map { (result, duration) ->
            val isDuplicate = result.setupMatchId != null && (setupMatchCounts[result.setupMatchId] ?: 0) > 1
            (if (isDuplicate) result.copy(status = ImportRowStatus.DUPLICATE) else result) to duration
        }

        fun targetLabel(setupMatchId: UUID): String? {
            val (competitionName, roundName, matchName) = displayInfoBySetupMatch[setupMatchId] ?: return null
            return listOfNotNull(competitionName, roundName, matchName).joinToString(" – ")
        }

        val rowDtos = finalRows.map { (result, _) ->
            ImportRowResultDto(
                rowNumber = result.rowNumber,
                startTime = result.startTime,
                competitionText = result.competitionText,
                laufText = result.laufText,
                status = result.status,
                targetLabel = if (result.status == ImportRowStatus.LINKED) {
                    result.setupMatchId?.let { targetLabel(it) }
                } else {
                    null
                },
            )
        }

        if (dryRun) {
            return@comprehension KIO.ok(ApiResponse.Dto(ScheduleImportResultDto(rowDtos, applied = false)))
        }

        val duplicateRowNumbers = finalRows.filter { it.first.status == ImportRowStatus.DUPLICATE }.map { it.first.rowNumber }
        if (duplicateRowNumbers.isNotEmpty()) {
            return@comprehension KIO.fail(EventScheduleError.DuplicateImportRow(duplicateRowNumbers))
        }

        !EventScheduleRepo.deleteAllSlots(eventId).orDie()

        val now = LocalDateTime.now()
        val newSlotRecords = finalRows.map { (result, duration) ->
            val isLinked = result.status == ImportRowStatus.LINKED
            EventScheduleSlotRecord(
                id = UUID.randomUUID(),
                event = eventId,
                startTime = result.startTime,
                competitionSetupMatch = if (isLinked) result.setupMatchId else null,
                name = if (isLinked) null else result.laufText,
                durationMinutes = duration,
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId,
            )
        }
        !EventScheduleRepo.createSlots(newSlotRecords).orDie()

        !finalRows.filter { it.first.status == ImportRowStatus.LINKED }.traverse { (result, _) ->
            EventScheduleRepo.stampMatchStartTime(result.setupMatchId!!, result.startTime, userId).orDie()
        }

        KIO.ok(ApiResponse.Dto(ScheduleImportResultDto(rowDtos, applied = true)))
    }
}
