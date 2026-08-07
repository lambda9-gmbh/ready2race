# Zeitstrahl Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Zeitstrahl (Backlog B2) — Slots mit fixen Uhrzeiten planen Läufe vor ihrer Entstehung; die Lauf-Kette folgt den Slots und wartet an ungesetzten Läufen; Excel-Import als primärer Planungsweg; dazu `started_at`/`finished_at` (A2/A4).

**Architecture:** Neue Tabelle `event_schedule_slot` mit Write-Through auf `competition_match.start_time` (Ansatz 1 der Spec `docs/superpowers/specs/2026-08-03-zeitstrahl-design.md`). Alle Slot-Zustände sind abgeleitet (eine Funktion), die bestehenden drei Zustandsmodelle werden angepasst, kein viertes gebaut.

**Tech Stack:** Kotlin/Ktor + tailwind-KIO + jOOQ (Backend), Flyway-Migrationen, Apache POI via vorhandenem `XLS`-Reader, React/TS + MUI + TanStack Router (Frontend), OpenAPI-YAML → `@hey-api/openapi-ts`.

## Global Constraints

- **Worktree:** `/Users/thomas/Developer/privat/ready2race/.claude/worktrees/zeitstrahl`, Branch `feature/zeitstrahl` (ab `feature/crf-2026`). Niemals nach `main`. Nichts pushen. Keine AI-Attribution in Commits.
- **Jeder Maven-Aufruf:** vorher `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`. Dev-DB: `cd backend && docker compose up -d db` (Port 7653). jOOQ-Klassen entstehen beim Build aus den Migrationen (`./mvnw compile`).
- **KIO:** Jeder `KIO.fail`/Effekt in einer Comprehension MUSS mit `!` gebunden werden (`!KIO.fail(...)` bzw. `return@comprehension KIO.fail(...)`) — bekannte Bug-Klasse.
- **Backend-Tests:** `cd backend && ./mvnw test` (aktuell 73 grün). **Frontend:** `cd frontend && npm run test && npm run build` (24 grün; nach Branchwechsel ggf. `npm install`).
- **Zeitzone:** Timestamps sind zeitzonenlos und werden als Ortszeit (Europe/Berlin) interpretiert; Postgres-Container läuft auf UTC — in Seeds `set time zone 'Europe/Berlin'`.
- **i18n:** Neue Texte in `frontend/src/i18n/de/translations.json` UND `frontend/src/i18n/en/translations.json` (Struktur spiegeln). Deutsche Texte mit echten Umlauten.
- **`backend/.env` / `frontend/.env`** fehlen im Worktree — aus dem Hauptcheckout `/Users/thomas/Developer/privat/ready2race/` kopieren, bevor lokal gestartet wird.

---

### Task 1: `origin/issue/94` mergen

**Files:**
- Modify: Merge-Konflikte, erwartet v.a. in `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/boundary/CompetitionExecutionService.kt`, `frontend/src/components/event/competition/excecution/CompetitionExecution.tsx`, `frontend/src/api/*.gen.ts`, `backend/src/main/resources/openapi/documentation.yaml`

**Interfaces:**
- Produces: RaceClocker-Code im Branch — `backend/.../app/raceclocker/control/RaceClockerFeed.kt`, `RaceClockerFeedRow.kt`, `CompetitionExecutionService.updateMatchResultFromRaceClocker` (Task 13 baut darauf).

- [ ] **Step 1: Merge starten**

```bash
git merge origin/issue/94 --no-ff -m "Merge origin/issue/94 (RaceClocker integration)"
```

- [ ] **Step 2: Konflikte lösen — Regel: crf-2026-Stand gewinnt**

issue/94 datiert vor Lauf-Kette/Teilergebnissen. Konkret:
- `CompetitionExecutionService.kt`: Bei Konflikten um `prepareForNewPlaces` die crf-2026-Signatur behalten (`prepareForNewPlaces(matchId, userId, stopRunning: Boolean = ...)` mit Teilergebnis-Logik, Zeilen ~500–543). Der RaceClocker-Pull (`updateMatchResultFromRaceClocker`, aus issue/94) ruft danach `prepareForNewPlaces(matchId, userId)` mit den Defaults auf — das ist korrekt, NICHT die issue/94-Variante wiederherstellen.
- `CompetitionExecution.tsx`: beide Seiten behalten — crf-2026-Dialoge (Teilergebnisse, Strafen) plus issue/94-Ergänzungen (`handlePullRaceClockerResults`, `RaceClockerConfigDialog`-Einbindung).
- Generierte Dateien (`frontend/src/api/*.gen.ts`): Konflikt beliebig lösen, wird in Step 4 regeneriert.
- `documentation.yaml`: beide Pfad-Blöcke übernehmen (crf-2026-Endpoints + `/results/from-raceclocker`).

- [ ] **Step 3: Backend-Tests**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
cd backend && docker compose up -d db && ./mvnw test
```
Expected: BUILD SUCCESS, alle Tests grün (73 + RaceClocker-Tests aus issue/94).

- [ ] **Step 4: Frontend-Client regenerieren, Tests, Build**

```bash
cd frontend && npm install && npm run generate && npm run test && npm run build
```
Expected: Build grün. `git diff --stat frontend/src/api` zeigt regenerierte Dateien.

- [ ] **Step 5: Commit (Merge ggf. amenden, falls Step 4 Änderungen brachte)**

```bash
git add -A && git commit --amend --no-edit
```

---

### Task 2: Migration `event_schedule_slot` + `started_at`/`finished_at`

**Files:**
- Create: `backend/src/main/resources/db/migration/V202608031200__event_schedule_slot.sql`

**Interfaces:**
- Produces: jOOQ-Referenzen `EVENT_SCHEDULE_SLOT` (Spalten `ID`, `EVENT`, `START_TIME`, `COMPETITION_SETUP_MATCH`, `NAME`, `DURATION_MINUTES`, `SKIPPED_AT`, `SKIPPED_BY`, `CREATED_AT/BY`, `UPDATED_AT/BY`) und `COMPETITION_MATCH.STARTED_AT` / `COMPETITION_MATCH.FINISHED_AT` für alle Folge-Tasks.

- [ ] **Step 1: Migration schreiben**

```sql
set search_path to ready2race, pg_catalog, public;

-- Zeitstrahl (Backlog B2): Slots planen Läufe, bevor sie existieren. Ein Slot zeigt entweder auf
-- eine Setup-Zeile (der spätere Lauf trägt dieselbe ID, PK = FK) oder ist ein freier Slot
-- (Pause, Siegerehrung). Die Slot-Zeit ist die Quelle der geplanten Startzeit und wird per
-- Write-Through auf competition_match.start_time gespiegelt.
create table event_schedule_slot
(
    id                      uuid primary key,
    event                   uuid      not null references event on delete cascade,
    start_time              timestamp not null,
    competition_setup_match uuid unique references competition_setup_match on delete cascade,
    name                    text,
    duration_minutes        int,
    skipped_at              timestamp,
    skipped_by              uuid references app_user,
    created_at              timestamp not null,
    created_by              uuid references app_user,
    updated_at              timestamp not null,
    updated_by              uuid references app_user,
    constraint chk_slot_match_xor_name check (
        (competition_setup_match is not null and name is null) or
        (competition_setup_match is null and name is not null) )
);

create index on event_schedule_slot (event, start_time);

-- Geplant vs. real: start_time bleibt die geplante Zeit, started_at ist der echte Start
-- (Schiedsrichter-Aktion, später überschrieben von der Zeitnahme), finished_at das persistierte
-- Ende — schließt das Loch, dass "beendet" bisher aus der Ergebnislage zurückgerechnet wurde.
alter table competition_match
    add column started_at timestamp,
    add column finished_at timestamp;
```

Falls die FK-Syntax von den Nachbar-Migrationen abweicht (`V202507040930__competition_execution.sql` als Referenz öffnen): deren Stil exakt übernehmen, Inhalt unverändert.

- [ ] **Step 2: Build regeneriert jOOQ, Kompilierung grün**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
cd backend && docker compose up -d db && ./mvnw compile
```
Expected: BUILD SUCCESS; `target/generated-sources/jooq` enthält `EventScheduleSlot`.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V202608031200__event_schedule_slot.sql
git commit -m "Add event_schedule_slot and match started_at/finished_at columns"
```

---

### Task 3: `EventScheduleLogic` — Zustandsableitung und Shift-Mathematik (TDD)

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventSchedule/entity/EventScheduleSlotState.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventSchedule/boundary/EventScheduleLogic.kt`
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/eventSchedule/EventScheduleLogicTest.kt`

**Interfaces:**
- Produces:
  - `enum class EventScheduleSlotState { FREE, WAITING, LINKED, OBSOLETE, SKIPPED }`
  - `EventScheduleLogic.deriveSlotState(isFree: Boolean, skipped: Boolean, roundMaterialized: Boolean, matchExists: Boolean): EventScheduleSlotState`
  - `data class ShiftSlot(val id: UUID, val startTime: LocalDateTime, val durationMinutes: Int?)`
  - `data class ShiftPreviewEntry(val slotId: UUID, val oldStartTime: LocalDateTime, val newStartTime: LocalDateTime)`
  - `sealed interface ShiftResult { data class Ok(val entries: List<ShiftPreviewEntry>) : ShiftResult; data class CompressionImpossible(val maxReductionMinutes: Long) : ShiftResult }`
  - `EventScheduleLogic.computeShift(slots: List<ShiftSlot>, deltaMinutes: Long, targetSlotId: UUID?): ShiftResult` — `slots` beginnt beim gewählten Start-Slot, aufsteigend sortiert, nur derselbe Renntag.
  - `const val MIN_GAP_MINUTES = 5L`

- [ ] **Step 1: Failing Tests schreiben**

```kotlin
package de.lambda9.ready2race.backend.app.eventSchedule

import de.lambda9.ready2race.backend.app.eventSchedule.boundary.EventScheduleLogic
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.ShiftResult
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.ShiftSlot
import de.lambda9.ready2race.backend.app.eventSchedule.entity.EventScheduleSlotState
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EventScheduleLogicTest {

    // --- deriveSlotState ---

    @Test
    fun freeSlotIsFree() {
        assertEquals(
            EventScheduleSlotState.FREE,
            EventScheduleLogic.deriveSlotState(isFree = true, skipped = false, roundMaterialized = false, matchExists = false),
        )
    }

    @Test
    fun setupSlotWithoutMatchIsWaiting() {
        assertEquals(
            EventScheduleSlotState.WAITING,
            EventScheduleLogic.deriveSlotState(isFree = false, skipped = false, roundMaterialized = false, matchExists = false),
        )
    }

    @Test
    fun setupSlotWithMatchIsLinked() {
        assertEquals(
            EventScheduleSlotState.LINKED,
            EventScheduleLogic.deriveSlotState(isFree = false, skipped = false, roundMaterialized = true, matchExists = true),
        )
    }

    @Test
    fun materializedRoundWithoutMatchIsObsolete() {
        assertEquals(
            EventScheduleSlotState.OBSOLETE,
            EventScheduleLogic.deriveSlotState(isFree = false, skipped = false, roundMaterialized = true, matchExists = false),
        )
    }

    @Test
    fun obsoleteTrumpsSkipped() {
        assertEquals(
            EventScheduleSlotState.OBSOLETE,
            EventScheduleLogic.deriveSlotState(isFree = false, skipped = true, roundMaterialized = true, matchExists = false),
        )
    }

    @Test
    fun skippedTrumpsWaitingLinkedAndFree() {
        assertEquals(
            EventScheduleSlotState.SKIPPED,
            EventScheduleLogic.deriveSlotState(isFree = false, skipped = true, roundMaterialized = false, matchExists = false),
        )
        assertEquals(
            EventScheduleSlotState.SKIPPED,
            EventScheduleLogic.deriveSlotState(isFree = false, skipped = true, roundMaterialized = true, matchExists = true),
        )
        assertEquals(
            EventScheduleSlotState.SKIPPED,
            EventScheduleLogic.deriveSlotState(isFree = true, skipped = true, roundMaterialized = false, matchExists = false),
        )
    }

    // --- computeShift ---

    private val base = LocalDateTime.of(2026, 8, 17, 10, 0)
    private fun slot(minutesAfterBase: Long, duration: Int? = null) =
        ShiftSlot(UUID.randomUUID(), base.plusMinutes(minutesAfterBase), duration)

    @Test
    fun plainShiftMovesEverySlot() {
        val slots = listOf(slot(0), slot(10), slot(20))
        val result = EventScheduleLogic.computeShift(slots, deltaMinutes = 15, targetSlotId = null)
        val ok = assertIs<ShiftResult.Ok>(result)
        assertEquals(listOf(15L, 25L, 35L), ok.entries.map {
            java.time.Duration.between(base, it.newStartTime).toMinutes()
        })
    }

    @Test
    fun compressKeepsTargetTimeAndShrinksGaps() {
        // 10:00, 10:10, 10:20, 10:30 — +6 min, Ziel = letzter Slot.
        // 6 Minuten müssen aus den drei 10er-Abständen (Untergrenze 5) heraus: je -2.
        val slots = listOf(slot(0), slot(10), slot(20), slot(30))
        val result = EventScheduleLogic.computeShift(slots, deltaMinutes = 6, targetSlotId = slots[3].id)
        val ok = assertIs<ShiftResult.Ok>(result)
        assertEquals(listOf(6L, 14L, 22L, 30L), ok.entries.map {
            java.time.Duration.between(base, it.newStartTime).toMinutes()
        })
    }

    @Test
    fun slotsAfterTargetStayUntouched() {
        val slots = listOf(slot(0), slot(10), slot(20))
        val result = EventScheduleLogic.computeShift(slots, deltaMinutes = 4, targetSlotId = slots[1].id)
        val ok = assertIs<ShiftResult.Ok>(result)
        assertEquals(listOf(4L, 10L, 20L), ok.entries.map {
            java.time.Duration.between(base, it.newStartTime).toMinutes()
        })
    }

    @Test
    fun durationRaisesTheFloorOfAGap() {
        // Abstand 10, Dauer des vorderen Slots 8 → nur 2 Minuten Spielraum.
        val slots = listOf(slot(0, duration = 8), slot(10))
        val result = EventScheduleLogic.computeShift(slots, deltaMinutes = 2, targetSlotId = slots[1].id)
        val ok = assertIs<ShiftResult.Ok>(result)
        assertEquals(listOf(2L, 10L), ok.entries.map {
            java.time.Duration.between(base, it.newStartTime).toMinutes()
        })
    }

    @Test
    fun impossibleCompressionReportsMaxReduction() {
        // Zwei 10er-Abstände, Untergrenze 5 → maximal 10 Minuten aufholbar, 12 gefordert.
        val slots = listOf(slot(0), slot(10), slot(20))
        val result = EventScheduleLogic.computeShift(slots, deltaMinutes = 12, targetSlotId = slots[2].id)
        val impossible = assertIs<ShiftResult.CompressionImpossible>(result)
        assertEquals(10L, impossible.maxReductionMinutes)
    }
}
```

- [ ] **Step 2: Test läuft und schlägt fehl**

```bash
cd backend && ./mvnw test -Dtest=EventScheduleLogicTest
```
Expected: Compilation FAILURE („unresolved reference EventScheduleLogic“).

- [ ] **Step 3: Implementierung**

`EventScheduleSlotState.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.eventSchedule.entity

enum class EventScheduleSlotState { FREE, WAITING, LINKED, OBSOLETE, SKIPPED }
```

`EventScheduleLogic.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.eventSchedule.boundary

import de.lambda9.ready2race.backend.app.eventSchedule.entity.EventScheduleSlotState
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

data class ShiftSlot(
    val id: UUID,
    val startTime: LocalDateTime,
    val durationMinutes: Int?,
)

data class ShiftPreviewEntry(
    val slotId: UUID,
    val oldStartTime: LocalDateTime,
    val newStartTime: LocalDateTime,
)

sealed interface ShiftResult {
    data class Ok(val entries: List<ShiftPreviewEntry>) : ShiftResult
    data class CompressionImpossible(val maxReductionMinutes: Long) : ShiftResult
}

object EventScheduleLogic {

    const val MIN_GAP_MINUTES = 5L

    /**
     * Reihenfolge der Prüfungen ist fachlich: "entfällt" ist endgültig (die Runde existiert, der
     * Lauf kann nie mehr entstehen) und schlägt deshalb auch ein manuelles Überspringen.
     */
    fun deriveSlotState(
        isFree: Boolean,
        skipped: Boolean,
        roundMaterialized: Boolean,
        matchExists: Boolean,
    ): EventScheduleSlotState = when {
        !isFree && roundMaterialized && !matchExists -> EventScheduleSlotState.OBSOLETE
        skipped -> EventScheduleSlotState.SKIPPED
        isFree -> EventScheduleSlotState.FREE
        matchExists -> EventScheduleSlotState.LINKED
        else -> EventScheduleSlotState.WAITING
    }

    /**
     * [slots]: ab dem gewählten Start-Slot, aufsteigend sortiert, nur derselbe Renntag.
     * Ohne [targetSlotId] werden alle Slots stumpf um [deltaMinutes] verschoben. Mit Ziel-Slot
     * behält dieser seine Zeit; die Verspätung wird aus den Abständen davor herausgestaucht.
     * Untergrenze je Abstand: duration_minutes des vorderen Slots, mindestens [MIN_GAP_MINUTES].
     */
    fun computeShift(
        slots: List<ShiftSlot>,
        deltaMinutes: Long,
        targetSlotId: UUID?,
    ): ShiftResult {
        if (targetSlotId == null) {
            return ShiftResult.Ok(slots.map {
                ShiftPreviewEntry(it.id, it.startTime, it.startTime.plusMinutes(deltaMinutes))
            })
        }

        val targetIndex = slots.indexOfFirst { it.id == targetSlotId }
        require(targetIndex > 0) { "target slot must come after the shifted slot" }

        val gaps = (0 until targetIndex).map { i ->
            Duration.between(slots[i].startTime, slots[i + 1].startTime).toMinutes()
        }
        val floors = (0 until targetIndex).map { i ->
            maxOf(slots[i].durationMinutes?.toLong() ?: MIN_GAP_MINUTES, MIN_GAP_MINUTES)
        }
        val slacks = gaps.zip(floors) { gap, floor -> (gap - floor).coerceAtLeast(0) }
        val totalSlack = slacks.sum()
        if (totalSlack < deltaMinutes) {
            return ShiftResult.CompressionImpossible(maxReductionMinutes = totalSlack)
        }

        // Proportional stauchen, Rundungsrest von vorn nach hinten minutenweise verteilen.
        val reductions = slacks.map { slack ->
            if (totalSlack == 0L) 0L else deltaMinutes * slack / totalSlack
        }.toMutableList()
        var remainder = deltaMinutes - reductions.sum()
        var i = 0
        while (remainder > 0) {
            if (reductions[i] < slacks[i]) {
                reductions[i] = reductions[i] + 1
                remainder--
            }
            i = (i + 1) % reductions.size
        }

        val entries = mutableListOf(
            ShiftPreviewEntry(slots[0].id, slots[0].startTime, slots[0].startTime.plusMinutes(deltaMinutes))
        )
        for (idx in 0 until targetIndex) {
            val newGap = gaps[idx] - reductions[idx]
            entries.add(
                ShiftPreviewEntry(
                    slots[idx + 1].id,
                    slots[idx + 1].startTime,
                    entries[idx].newStartTime.plusMinutes(newGap),
                )
            )
        }
        // Slots hinter dem Ziel bleiben unangetastet, tauchen aber in der Vorschau auf.
        for (idx in targetIndex + 1 until slots.size) {
            entries.add(ShiftPreviewEntry(slots[idx].id, slots[idx].startTime, slots[idx].startTime))
        }
        return ShiftResult.Ok(entries)
    }
}
```

- [ ] **Step 4: Tests grün**

```bash
cd backend && ./mvnw test -Dtest=EventScheduleLogicTest
```
Expected: PASS (11 Tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventSchedule backend/src/test/kotlin/de/lambda9/ready2race/backend/app/eventSchedule
git commit -m "Add schedule slot state derivation and shift math"
```

---

### Task 4: Repo, DTO, Error, `GET /schedule`, Routing

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventSchedule/control/EventScheduleRepo.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventSchedule/entity/EventScheduleSlotDto.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventSchedule/entity/EventScheduleError.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventSchedule/boundary/EventScheduleService.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventSchedule/boundary/eventSchedule.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/plugins/Routing.kt` (neben `liveDashboard()`, Zeile ~50)

**Interfaces:**
- Consumes: `EventScheduleLogic.deriveSlotState` (Task 3), `EVENT_SCHEDULE_SLOT` (Task 2).
- Produces:
  - `EventScheduleSlotDto(id: UUID, startTime: LocalDateTime, state: EventScheduleSlotState, name: String?, durationMinutes: Int?, competitionId: UUID?, competitionName: String?, roundName: String?, matchName: String?, matchId: UUID?, matchStartedAt: LocalDateTime?, matchFinishedAt: LocalDateTime?)`
  - `EventScheduleDto(slots: List<EventScheduleSlotDto>, unplannedSetupMatches: List<UnplannedSetupMatchDto>)` mit `UnplannedSetupMatchDto(setupMatchId: UUID, competitionId: UUID, competitionName: String, roundName: String, matchName: String?)`
  - `EventScheduleService.getSchedule(eventId): App<EventScheduleError, ApiResponse.Dto<EventScheduleDto>>`
  - `EventScheduleRepo.getSlots(eventId)` — Records mit Aliassen `"competition_id"`, `"competition_name"`, `"round_name"`, `"match_name"`, `"round_materialized"` (Boolean), `"match_exists"` (Boolean), `"match_started_at"`, `"match_finished_at"` plus alle `EVENT_SCHEDULE_SLOT`-Felder
  - `EventScheduleError` (sealed): `EventNotFound(404)`, `SlotNotFound(404)`, `SetupMatchNotFound(404)`, `SetupMatchAlreadyPlanned(409)`, `MatchAlreadyStarted(409)`, `SlotNotSkippable(409)`, `CompressionImpossible(422, maxReductionMinutes)`, `InvalidShiftRequest(422)`, `DuplicateImportRow(422, rowNumbers)`, `ImportFileUnreadable(422)` — der Guard aus Task 10 lebt dagegen als neuer Fall `StartTimeManagedBySchedule(409)` im bestehenden `CompetitionExecutionError` (anderer Fehlerkanal)

- [ ] **Step 1: Error-Typ**

```kotlin
package de.lambda9.ready2race.backend.app.eventSchedule.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*
import java.util.UUID

sealed interface EventScheduleError : ServiceError {
    data class EventNotFound(val eventId: UUID) : EventScheduleError
    data class SlotNotFound(val slotId: UUID) : EventScheduleError
    data class SetupMatchNotFound(val setupMatchId: UUID) : EventScheduleError
    data class SetupMatchAlreadyPlanned(val setupMatchId: UUID) : EventScheduleError
    data class MatchAlreadyStarted(val slotId: UUID) : EventScheduleError
    data class SlotNotSkippable(val slotId: UUID) : EventScheduleError
    data class CompressionImpossible(val maxReductionMinutes: Long) : EventScheduleError
    data object InvalidShiftRequest : EventScheduleError
    data class DuplicateImportRow(val rowNumbers: List<Int>) : EventScheduleError
    data object ImportFileUnreadable : EventScheduleError

    override fun respond(): ApiError = when (this) {
        is EventNotFound -> ApiError(HttpStatusCode.NotFound, "Event with id $eventId not found")
        is SlotNotFound -> ApiError(HttpStatusCode.NotFound, "Schedule slot $slotId not found")
        is SetupMatchNotFound -> ApiError(HttpStatusCode.NotFound, "Setup match $setupMatchId not found in this event")
        is SetupMatchAlreadyPlanned -> ApiError(HttpStatusCode.Conflict, "Setup match $setupMatchId already has a schedule slot")
        is MatchAlreadyStarted -> ApiError(HttpStatusCode.Conflict, "The match of slot $slotId has already started")
        is SlotNotSkippable -> ApiError(HttpStatusCode.Conflict, "Slot $slotId cannot be skipped in its current state")
        is CompressionImpossible -> ApiError(HttpStatusCode.UnprocessableEntity, "Cannot compress: only $maxReductionMinutes minutes available")
        InvalidShiftRequest -> ApiError(HttpStatusCode.UnprocessableEntity, "Shift request parameters are inconsistent")
        is DuplicateImportRow -> ApiError(HttpStatusCode.UnprocessableEntity, "Import contains duplicate matches in rows $rowNumbers")
        ImportFileUnreadable -> ApiError(HttpStatusCode.UnprocessableEntity, "Import file could not be read")
    }
}
```

- [ ] **Step 2: DTOs**

```kotlin
package de.lambda9.ready2race.backend.app.eventSchedule.entity

import java.time.LocalDateTime
import java.util.UUID

data class EventScheduleSlotDto(
    val id: UUID,
    val startTime: LocalDateTime,
    val state: EventScheduleSlotState,
    val name: String?,
    val durationMinutes: Int?,
    val competitionId: UUID?,
    val competitionName: String?,
    val roundName: String?,
    val matchName: String?,
    val matchId: UUID?,
    val matchStartedAt: LocalDateTime?,
    val matchFinishedAt: LocalDateTime?,
)

data class UnplannedSetupMatchDto(
    val setupMatchId: UUID,
    val competitionId: UUID,
    val competitionName: String,
    val roundName: String,
    val matchName: String?,
)

data class EventScheduleDto(
    val slots: List<EventScheduleSlotDto>,
    val unplannedSetupMatches: List<UnplannedSetupMatchDto>,
)
```

- [ ] **Step 3: Repo**

```kotlin
package de.lambda9.ready2race.backend.app.eventSchedule.control

import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.UUID

object EventScheduleRepo {

    /**
     * Alle Slots des Events mit dem Kontext für die Zustandsableitung. "Runde materialisiert" =
     * mindestens eine Setup-Zeile derselben Runde hat einen Lauf.
     */
    fun getSlots(eventId: UUID) = Jooq.query {
        val roundMaterialized = DSL.field(
            DSL.exists(
                selectOne()
                    .from(COMPETITION_SETUP_MATCH.`as`("sibling"))
                    .join(COMPETITION_MATCH.`as`("sibling_match"))
                    .on(COMPETITION_MATCH.`as`("sibling_match").COMPETITION_SETUP_MATCH
                        .eq(COMPETITION_SETUP_MATCH.`as`("sibling").ID))
                    .where(COMPETITION_SETUP_MATCH.`as`("sibling").COMPETITION_SETUP_ROUND
                        .eq(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND))
            )
        ).`as`("round_materialized")

        select(
            EVENT_SCHEDULE_SLOT.asterisk(),
            COMPETITION.ID.`as`("competition_id"),
            COMPETITION_PROPERTIES.NAME.`as`("competition_name"),
            COMPETITION_SETUP_ROUND.NAME.`as`("round_name"),
            COMPETITION_SETUP_MATCH.NAME.`as`("match_name"),
            COMPETITION_MATCH.COMPETITION_SETUP_MATCH.isNotNull.`as`("match_exists"),
            COMPETITION_MATCH.STARTED_AT.`as`("match_started_at"),
            COMPETITION_MATCH.FINISHED_AT.`as`("match_finished_at"),
            COMPETITION_MATCH.CURRENTLY_RUNNING,
            roundMaterialized,
        )
            .from(EVENT_SCHEDULE_SLOT)
            .leftJoin(COMPETITION_SETUP_MATCH)
            .on(EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .leftJoin(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .leftJoin(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .leftJoin(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .leftJoin(COMPETITION_MATCH)
            .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH))
            .where(EVENT_SCHEDULE_SLOT.EVENT.eq(eventId))
            .orderBy(EVENT_SCHEDULE_SLOT.START_TIME.asc(), COMPETITION_SETUP_MATCH.EXECUTION_ORDER.asc())
            .fetch()
    }

    /** Setup-Zeilen des Events ohne Slot — die "nicht verplant"-Liste. */
    fun getUnplannedSetupMatches(eventId: UUID) = Jooq.query {
        select(
            COMPETITION_SETUP_MATCH.ID,
            COMPETITION.ID.`as`("competition_id"),
            COMPETITION_PROPERTIES.NAME.`as`("competition_name"),
            COMPETITION_SETUP_ROUND.NAME.`as`("round_name"),
            COMPETITION_SETUP_MATCH.NAME.`as`("match_name"),
        )
            .from(COMPETITION_SETUP_MATCH)
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .where(COMPETITION.EVENT.eq(eventId))
            .andNotExists(
                selectOne()
                    .from(EVENT_SCHEDULE_SLOT)
                    .where(EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            )
            .orderBy(COMPETITION_PROPERTIES.NAME, COMPETITION_SETUP_MATCH.EXECUTION_ORDER)
            .fetch()
    }
}
```

Hinweis: Die `as`-Alias-Syntax für die korrelierte Subquery ggf. an die im Projekt übliche Form anpassen (`COMPETITION_SETUP_MATCH.as("sibling")` erzeugt in jOOQ-Kotlin eine neue Tabellenreferenz, die in eine `val sibling = ...` gehört — siehe `LiveDashboardRepo.getActivationCandidates` für den Projektstil mit `DSL.exists`).

- [ ] **Step 4: Service + Route**

`EventScheduleService.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.eventSchedule.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.eventSchedule.control.EventScheduleRepo
import de.lambda9.ready2race.backend.app.eventSchedule.entity.*
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.util.UUID

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
                    matchStartedAt = r.get("match_started_at", java.time.LocalDateTime::class.java),
                    matchFinishedAt = r.get("match_finished_at", java.time.LocalDateTime::class.java),
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
}
```

`eventSchedule.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.eventSchedule.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.eventSchedule() {
    route("/event/{eventId}/schedule") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                EventScheduleService.getSchedule(eventId)
            }
        }
    }
}
```

In `Routing.kt` neben `liveDashboard()` registrieren: `eventSchedule()` (Import ergänzen).

- [ ] **Step 5: Kompilieren + bestehende Tests**

```bash
cd backend && ./mvnw test
```
Expected: BUILD SUCCESS, keine Regressionen.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventSchedule backend/src/main/kotlin/de/lambda9/ready2race/backend/plugins/Routing.kt
git commit -m "Add event schedule read endpoint with derived slot states"
```

---

### Task 5: Slot-CRUD mit Write-Through

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventSchedule/boundary/EventScheduleService.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventSchedule/boundary/eventSchedule.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventSchedule/control/EventScheduleRepo.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventSchedule/entity/UpsertScheduleSlotRequest.kt`

**Interfaces:**
- Consumes: `EventScheduleError`, `EVENT_SCHEDULE_SLOT`.
- Produces:
  - `UpsertScheduleSlotRequest(startTime: LocalDateTime, competitionSetupMatch: UUID?, name: String?, durationMinutes: Int?)` mit `Validatable`-Implementierung nach Projektmuster (siehe `UpdateCompetitionMatchRequest`) — XOR von `competitionSetupMatch`/`name` validieren; Beispielobjekt `companion object { val example = ... }` für `receiveKIO`.
  - `EventScheduleService.createSlot(eventId, request, userId)`, `updateSlot(eventId, slotId, request, userId)`, `deleteSlot(eventId, slotId)` — alle mit Write-Through.
  - `EventScheduleRepo.stampMatchStartTime(setupMatchId: UUID, startTime: LocalDateTime, userId: UUID)` — `update competition_match set start_time = ... where competition_setup_match = ...` (no-op, wenn kein Lauf existiert). **Task 9 und 12 nutzen genau diese Funktion.**
- Routen: `post("/slot")`, `put("/slot/{slotId}")`, `delete("/slot/{slotId}")` — alle `Privilege.UpdateEventGlobal`.

- [ ] **Step 1: Request-Typ + Service-Funktionen schreiben.** Kernpunkte:
  - `createSlot`: Event-Existenz prüfen (`EventNotFound`); bei `competitionSetupMatch != null`: Setup-Zeile gehört zum Event (`SetupMatchNotFound` — Query analog `getUnplannedSetupMatches` mit `COMPETITION_SETUP_MATCH.ID.eq(...)`), noch kein Slot vorhanden (`SetupMatchAlreadyPlanned`, unique-Verletzung nicht dem DB-Fehler überlassen). Insert mit `LocalDateTime.now()`-Stempeln. Danach `stampMatchStartTime`.
  - `updateSlot`: Slot laden (`SlotNotFound`), Felder aktualisieren, bei Lauf-Slot `stampMatchStartTime`.
  - `deleteSlot`: Slot laden, löschen. Der Lauf behält seine letzte `start_time` (Spec §8).
- [ ] **Step 2: Routen ergänzen** (Muster `receiveKIO(UpsertScheduleSlotRequest.example)` wie in `competitionExecution.kt:102`).
- [ ] **Step 3: Kompilieren + Tests: `./mvnw test`** — Expected: grün.
- [ ] **Step 4: Commit** — `git commit -m "Add schedule slot CRUD with start time write-through"`

---

### Task 6: Skip/Unskip

**Files:**
- Modify: `EventScheduleService.kt`, `eventSchedule.kt` (Pfade wie Task 5)

**Interfaces:**
- Produces: `EventScheduleService.setSlotSkipped(eventId, slotId, skipped: Boolean, userId)`.
- Routen: `put("/slot/{slotId}/skip")`, `put("/slot/{slotId}/unskip")` — Auth via `authenticateAny(Privilege.UpdateEventGlobal, Privilege.UpdateLiveDashboardGlobal)` (`Extensions.kt:74`), damit Orga **und** Schiedsrichter überspringen dürfen.

- [ ] **Step 1: Service-Funktion.** Regeln (Spec §8):
  - Slot nicht gefunden → `SlotNotFound`.
  - `skip`: erlaubt für Zustand FREE, WAITING und LINKED **ohne** `started_at` am Lauf; sonst `MatchAlreadyStarted` (LINKED gestartet) bzw. `SlotNotSkippable` (OBSOLETE). Setzt `skipped_at = now()`, `skipped_by = userId`.
  - `unskip`: setzt beide auf `null`; erlaubt solange kein Lauf des Slots `started_at` trägt.
- [ ] **Step 2: Routen registrieren, kompilieren, `./mvnw test`** — grün.
- [ ] **Step 3: Commit** — `git commit -m "Allow skipping schedule slots with audit"`

---

### Task 7: `finished_at` persistieren + Zustandsmodelle nachziehen (TDD)

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/boundary/LiveDashboardService.kt:179-218` (finishMatch), `:363-368` (setRunning)
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/boundary/LiveDashboardLogic.kt:42-51`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/control/LiveDashboardRepo.kt:153-189`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/control/CompetitionMatchRepo.kt` (getMatchResults ~:36-99, getUpcomingMatchesForBoard ~:176-235)
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/LiveDashboardLogicTest.kt`

**Interfaces:**
- Produces: `LiveDashboardLogic.deriveMatchState(currentlyRunning: Boolean, startTime: LocalDateTime?, finishedAt: LocalDateTime?, teamResults: List<Boolean>): LiveDashboardMatchState` — **neuer Parameter `finishedAt`**, Aufrufer in `LiveDashboardService.kt:119` anpassen.

- [ ] **Step 1: Failing Test** (in `LiveDashboardLogicTest` ergänzen):

```kotlin
@Test
fun finishedAtBeatsIncompleteResults() {
    // Ohne Ergebnisse beendet: bisher fiel das auf UPCOMING zurück (A4-Loch).
    assertEquals(
        LiveDashboardMatchState.FINISHED,
        LiveDashboardLogic.deriveMatchState(false, start, start.plusMinutes(9), listOf(false, false)),
    )
}

@Test
fun legacyFallbackAllResultsStillFinishes() {
    assertEquals(
        LiveDashboardMatchState.FINISHED,
        LiveDashboardLogic.deriveMatchState(false, start, null, listOf(true, true)),
    )
}
```

- [ ] **Step 2: Fail bestätigen** — `./mvnw test -Dtest=LiveDashboardLogicTest` → Compilation FAILURE.
- [ ] **Step 3: Implementieren**

```kotlin
fun deriveMatchState(
    currentlyRunning: Boolean,
    startTime: LocalDateTime?,
    finishedAt: LocalDateTime?,
    teamResults: List<Boolean>,
): LiveDashboardMatchState = when {
    currentlyRunning -> LiveDashboardMatchState.RUNNING
    finishedAt != null -> LiveDashboardMatchState.FINISHED
    teamResults.isNotEmpty() && teamResults.all { it } -> LiveDashboardMatchState.FINISHED
    startTime == null -> LiveDashboardMatchState.UNSCHEDULED
    else -> LiveDashboardMatchState.UPCOMING
}
```

Dazu in `finishMatch` (Service, nach `!setRunning(matchId, false, userId)`):

```kotlin
!CompetitionMatchRepo.update(matchId) {
    finishedAt = LocalDateTime.now()
    updatedBy = userId
    updatedAt = LocalDateTime.now()
}.orDie()
```

`buildMatchDto` (`LiveDashboardService.kt:105-132`): `match[COMPETITION_MATCH.FINISHED_AT]` mit in die Repo-Query (`LiveDashboardRepo.getMatches`) aufnehmen und an `deriveMatchState` durchreichen.

SQL-Prädikate nachziehen:
- `LiveDashboardRepo.getActivationCandidates:168`: zusätzlich `.and(COMPETITION_MATCH.FINISHED_AT.isNull)` — ein beendeter Lauf ist nie wieder Kandidat.
- `CompetitionMatchRepo.getUpcomingMatchesForBoard`: zusätzlich `COMPETITION_MATCH.FINISHED_AT.isNull`.
- `CompetitionMatchRepo.getMatchResults`: „beendet“ = `FINISHED_AT.isNotNull` **oder** die bisherige notExists-Bedingung (Altdaten-Fallback): `.and(COMPETITION_MATCH.FINISHED_AT.isNotNull.or(<bestehendes notExists>))`.

- [ ] **Step 4: Tests grün** — `./mvnw test` (alle, nicht nur Logic — Aufrufer-Anpassungen prüfen).
- [ ] **Step 5: Commit** — `git commit -m "Persist match finish and use it in all state derivations"`

---

### Task 8: `started_at` — Start-Aktion + echte Laufzeit

**Files:**
- Modify: `LiveDashboardService.kt`, `liveDashboard.kt`, `LiveDashboardRepo.kt` (getMatches um `STARTED_AT` erweitern)
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/entity/LiveDashboardDto.kt` (Match-DTO)
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/boundary/EventInfoService.kt:213-246` (getRunningMatches, elapsed)

**Interfaces:**
- Produces:
  - `LiveDashboardMatchDto` erhält `startedAt: LocalDateTime?`; `elapsedMinutes` rechnet ab `startedAt` (`null`, wenn nicht gestartet — Spec §4: keine Laufzeit-Anzeige in Vorbereitung).
  - Route `put("/match/{matchId}/start")` (Privilege.UpdateLiveDashboardGlobal) → `LiveDashboardService.markMatchStarted(eventId, matchId, userId)`: setzt `started_at = now()` nur wenn bisher `null` (idempotent), setzt zugleich `currently_running = true`.

- [ ] **Step 1: Service + Route implementieren.** `elapsedMinutes`-Zeile (`LiveDashboardService.kt:128`) ersetzen:

```kotlin
elapsedMinutes = startedAt?.let { Duration.between(it, now).toMinutes().coerceAtLeast(0) },
startedAt = startedAt,
```

Gleiche Ersetzung in `EventInfoService` (Running-Matches, ~:223-225): elapsed nur bei vorhandenem `started_at`, Feld in die Repo-Query `CompetitionMatchRepo.getRunningMatches` aufnehmen.

- [ ] **Step 2: Kompilieren + `./mvnw test`** — grün.
- [ ] **Step 3: Commit** — `git commit -m "Track real match start separately from the planned time"`

---

### Task 9: Kette folgt Slots (Kernstück, TDD)

**Files:**
- Modify: `LiveDashboardService.kt:179-218` (finishMatch), `:370-381` (activateNext)
- Modify: `EventScheduleRepo.kt` (neue Query `getChainSlots`)
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/boundary/CompetitionExecutionService.kt` — am Ende von `createNewRound` (~:97-299, nach der Namens-Override-Schleife ~:275-295)
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventSchedule/boundary/ScheduleChain.kt`
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/eventSchedule/ScheduleChainTest.kt`

**Interfaces:**
- Produces (pure, testbar ohne DB):

```kotlin
data class ChainSlot(
    val slotId: UUID,
    val startTime: LocalDateTime,
    val state: EventScheduleSlotState,   // aus deriveSlotState
    val matchId: UUID?,                  // = competition_setup_match, wenn Lauf existiert
    val matchFinished: Boolean,
    val matchOpen: Boolean,              // mind. ein Team ohne Ergebnis (Kandidaten-Prädikat)
)

sealed interface ChainDecision {
    data class Activate(val matchIds: List<UUID>) : ChainDecision  // parallele Starts
    data object WaitingForRound : ChainDecision                    // Breakpoint: nichts tun
    data object NothingToDo : ChainDecision
}

object ScheduleChain {
    /** [slotsAfter]: Slots mit start_time > Zeit des beendeten Slots, aufsteigend. */
    fun decideNext(slotsAfter: List<ChainSlot>): ChainDecision
}
```

- Consumes: `EventScheduleRepo.stampMatchStartTime` (Task 5), `deriveSlotState` (Task 3).

- [ ] **Step 1: Failing Tests**

```kotlin
package de.lambda9.ready2race.backend.app.eventSchedule

import de.lambda9.ready2race.backend.app.eventSchedule.boundary.ChainDecision
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.ChainSlot
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.ScheduleChain
import de.lambda9.ready2race.backend.app.eventSchedule.entity.EventScheduleSlotState.*
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ScheduleChainTest {

    private val base = LocalDateTime.of(2026, 8, 17, 10, 0)
    private fun slot(
        min: Long,
        state: de.lambda9.ready2race.backend.app.eventSchedule.entity.EventScheduleSlotState,
        matchId: UUID? = null,
        finished: Boolean = false,
        open: Boolean = true,
    ) = ChainSlot(UUID.randomUUID(), base.plusMinutes(min), state, matchId, finished, open)

    @Test
    fun activatesTheNextLinkedOpenMatch() {
        val m = UUID.randomUUID()
        val decision = ScheduleChain.decideNext(listOf(slot(10, LINKED, m)))
        assertEquals(ChainDecision.Activate(listOf(m)), decision)
    }

    @Test
    fun waitsAtAWaitingSlotInsteadOfSkippingAhead() {
        val m = UUID.randomUUID()
        val decision = ScheduleChain.decideNext(
            listOf(slot(10, WAITING), slot(20, LINKED, m)),
        )
        assertIs<ChainDecision.WaitingForRound>(decision)
    }

    @Test
    fun skippedObsoleteAndFreeSlotsArePassedOver() {
        val m = UUID.randomUUID()
        val decision = ScheduleChain.decideNext(
            listOf(slot(10, SKIPPED), slot(20, OBSOLETE), slot(30, FREE), slot(40, LINKED, m)),
        )
        assertEquals(ChainDecision.Activate(listOf(m)), decision)
    }

    @Test
    fun finishedOrClosedMatchesArePassedOver() {
        val m = UUID.randomUUID()
        val decision = ScheduleChain.decideNext(
            listOf(
                slot(10, LINKED, UUID.randomUUID(), finished = true),
                slot(20, LINKED, UUID.randomUUID(), open = false),
                slot(30, LINKED, m),
            ),
        )
        assertEquals(ChainDecision.Activate(listOf(m)), decision)
    }

    @Test
    fun parallelStartsActivateTogether() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val s1 = slot(10, LINKED, a)
        val s2 = ChainSlot(UUID.randomUUID(), s1.startTime, LINKED, b, matchFinished = false, matchOpen = true)
        assertEquals(ChainDecision.Activate(listOf(a, b)), ScheduleChain.decideNext(listOf(s1, s2)))
    }

    @Test
    fun emptyTailMeansNothingToDo() {
        assertIs<ChainDecision.NothingToDo>(ScheduleChain.decideNext(emptyList()))
    }
}
```

- [ ] **Step 2: Fail bestätigen** — `./mvnw test -Dtest=ScheduleChainTest` → Compilation FAILURE.
- [ ] **Step 3: `ScheduleChain` implementieren**

```kotlin
package de.lambda9.ready2race.backend.app.eventSchedule.boundary

import de.lambda9.ready2race.backend.app.eventSchedule.entity.EventScheduleSlotState
import java.time.LocalDateTime
import java.util.UUID

data class ChainSlot(
    val slotId: UUID,
    val startTime: LocalDateTime,
    val state: EventScheduleSlotState,
    val matchId: UUID?,
    val matchFinished: Boolean,
    val matchOpen: Boolean,
)

sealed interface ChainDecision {
    data class Activate(val matchIds: List<UUID>) : ChainDecision
    data object WaitingForRound : ChainDecision
    data object NothingToDo : ChainDecision
}

object ScheduleChain {

    /**
     * Wandert die Slots hinter dem beendeten Lauf vorwärts. Übersprungene, entfallene und freie
     * Slots sowie beendete/abgeschlossene Läufe werden übergangen. Ein wartender Slot stoppt die
     * Suche bewusst OHNE Fehler: Die Kette wartet, bis die Runde gesetzt wird — createNewRound
     * stößt sie dann wieder an (zweiter Auslöser).
     */
    fun decideNext(slotsAfter: List<ChainSlot>): ChainDecision {
        for (slot in slotsAfter) {
            when (slot.state) {
                EventScheduleSlotState.SKIPPED,
                EventScheduleSlotState.OBSOLETE,
                EventScheduleSlotState.FREE -> continue

                EventScheduleSlotState.WAITING -> return ChainDecision.WaitingForRound

                EventScheduleSlotState.LINKED -> {
                    if (slot.matchFinished || !slot.matchOpen) continue
                    // Parallele Starts: alle aktivierbaren Läufe derselben Startzeit gemeinsam.
                    val group = slotsAfter.filter {
                        it.startTime == slot.startTime &&
                            it.state == EventScheduleSlotState.LINKED &&
                            !it.matchFinished && it.matchOpen
                    }
                    return ChainDecision.Activate(group.mapNotNull { it.matchId })
                }
            }
        }
        return ChainDecision.NothingToDo
    }
}
```

- [ ] **Step 4: Tests grün** — `./mvnw test -Dtest=ScheduleChainTest`.
- [ ] **Step 5: Verdrahtung in `finishMatch`**

`EventScheduleRepo.getChainSlots(eventId, after: LocalDateTime)`: Query wie `getSlots` (Task 4), zusätzlich `matchOpen` als `DSL.exists(...)`-Feld mit exakt dem Kandidaten-Prädikat aus `LiveDashboardRepo.getActivationCandidates:171-186`, gefiltert `EVENT_SCHEDULE_SLOT.START_TIME.gt(after)`. Dazu `EventScheduleRepo.getSlotBySetupMatch(matchId)` (liefert `START_TIME` oder null).

In `LiveDashboardService.finishMatch` den Kandidaten-Block (`:202-215`) ersetzen:

```kotlin
val chainEnabled = !EventRepo.getAutoActivateNextMatch(eventId).orDie()
!setRunning(matchId, false, userId)
!CompetitionMatchRepo.update(matchId) {
    finishedAt = LocalDateTime.now()
    updatedBy = userId
    updatedAt = LocalDateTime.now()
}.orDie()

if (chainEnabled) {
    val slotTime = !EventScheduleRepo.getSlotBySetupMatch(matchId).orDie()
    if (slotTime != null) {
        // Zeitstrahl-Modus: der Kette folgen, an wartenden Slots geduldig sein.
        val chainSlots = !buildChainSlots(eventId, after = slotTime)
        when (val decision = ScheduleChain.decideNext(chainSlots)) {
            is ChainDecision.Activate ->
                !decision.matchIds.traverse { setRunning(it, true, userId) }
            ChainDecision.WaitingForRound, ChainDecision.NothingToDo -> Unit
        }
    } else {
        // Legacy: Events ohne Zeitstrahl behalten das bisherige Verhalten.
        val finishedStart = !LiveDashboardRepo.getMatchStartTime(matchId).orDie()
        val candidates = (!LiveDashboardRepo.getActivationCandidates(eventId).orDie())
            .filter { c ->
                val start = c[COMPETITION_MATCH.START_TIME]
                finishedStart == null || (start != null && start > finishedStart)
            }
        !activateNext(candidates, userId)
    }
}
```

`buildChainSlots` ist eine private Service-Funktion, die die `getChainSlots`-Records über `deriveSlotState` in `ChainSlot` mappt (Felder wie in Task 4 Service-Mapping; `matchFinished = match_finished_at != null`).

- [ ] **Step 6: `createNewRound`-Trigger + Write-Through**

Am Ende von `CompetitionExecutionService.createNewRound` (nach der Naming-Schleife, vor dem Rück­gabewert):

```kotlin
// Zeitstrahl: geplante Slot-Zeiten auf die soeben erzeugten Läufe stempeln …
val stamped = !EventScheduleRepo.stampSlotTimesForSetupMatches(createdSetupMatchIds, userId).orDie()
// … und die wartende Kette wieder anstoßen: wenn nichts läuft, aktiviert der nächste fällige
// Slot sich jetzt — das ist der zweite Auslöser des wartenden Breakpoints.
!ScheduleChainService.resumeAfterRoundCreation(eventId, userId)
```

Dazu:
- `EventScheduleRepo.stampSlotTimesForSetupMatches(ids: List<UUID>, userId)`: `update competition_match cm set start_time = s.start_time from event_schedule_slot s where s.competition_setup_match = cm.competition_setup_match and cm.competition_setup_match = any(ids)` (jOOQ: update mit Sub-Select je id via `traverse`, Projektstil).
- `ScheduleChainService.resumeAfterRoundCreation(eventId, userId)` (neue kleine Funktion in `ScheduleChain.kt`-Datei oder `EventScheduleService`): wenn `auto_activate_next_match` an UND kein `competition_match` des Events `currently_running = true`: Referenzzeit = `max(finished_at)` der Event-Matches, deren Setup-Zeile einen Slot hat (Query `EventScheduleRepo.getLastFinishedSlotTime(eventId)` → deren Slot-`start_time`; `null`, wenn noch nichts beendet). `buildChainSlots(eventId, after = referenz ?: LocalDateTime.MIN)` + `decideNext` + aktivieren wie oben.
- `createdSetupMatchIds` sind die IDs der in dieser Runde erzeugten `CompetitionMatchRecord`s — in der bestehenden Erzeugungsschleife einsammeln.
- `eventId` ist im `createNewRound`-Kontext verfügbar (Pfadparameter der Route); als Parameter durchreichen, falls die Funktion ihn noch nicht hat.

- [ ] **Step 7: Alle Tests** — `./mvnw test` → grün.
- [ ] **Step 8: Commit** — `git commit -m "Drive the activation chain along schedule slots with a waiting breakpoint"`

---

### Task 10: `updateMatchData` schützt slot-verwaltete Startzeiten

**Files:**
- Modify: `CompetitionExecutionService.kt` `updateMatchData` (~:437-441)

- [ ] **Step 1:** Vor dem Update: `val slotTime = !EventScheduleRepo.getSlotBySetupMatch(matchId).orDie()`. Wenn `slotTime != null` **und** `request.startTime != slotTime` → `!KIO.fail(CompetitionExecutionError.StartTimeManagedBySchedule)` (neuer Fall im bestehenden `CompetitionExecutionError`, 409, Message „Start time is managed by the event schedule“). Gleiche Zeit oder `startTime == null` im Request bei unverändertem Feld: durchlassen (der Dialog schickt die Startnummern mit demselben Request — der Guard darf Startnummern-Pflege nicht blockieren; deshalb Vergleich statt Pauschalverbot).
- [ ] **Step 2:** `./mvnw test` → grün. **Step 3: Commit** — `git commit -m "Reject manual start time edits for slot-managed matches"`

---

### Task 11: Shift-Endpoint

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventSchedule/entity/ShiftScheduleRequest.kt`
- Modify: `EventScheduleService.kt`, `eventSchedule.kt`

**Interfaces:**
- Produces:
  - `enum class ShiftMode { PLUS_MINUTES, SET_TIME, COMPRESS_TO_TARGET }`
  - `ShiftScheduleRequest(fromSlotId: UUID, mode: ShiftMode, minutes: Long?, newTime: LocalDateTime?, targetSlotId: UUID?, dryRun: Boolean)`
  - `ShiftPreviewDto(entries: List<ShiftPreviewEntryDto>, applied: Boolean)` mit `ShiftPreviewEntryDto(slotId: UUID, oldStartTime: LocalDateTime, newStartTime: LocalDateTime)`
  - `EventScheduleService.shiftSchedule(eventId, request, userId)`
- Consumes: `EventScheduleLogic.computeShift` (Task 3), `stampMatchStartTime` (Task 5).

- [ ] **Step 1: Service-Funktion.** Ablauf:
  1. Slot `fromSlotId` laden (`SlotNotFound`); Slots desselben Renntags (`start_time::date` gleich) ab diesem Slot aufsteigend laden.
  2. `deltaMinutes` bestimmen: `PLUS_MINUTES` → `request.minutes!!`; `SET_TIME` → `Duration.between(fromSlot.startTime, request.newTime!!).toMinutes()`; `COMPRESS_TO_TARGET` → wie PLUS_MINUTES/SET_TIME (eines von beiden muss gesetzt sein) plus `targetSlotId`. Inkonsistente Kombinationen (fehlende Pflichtfelder, Ziel-Slot nicht in der Liste hinter dem Start-Slot, delta ≤ 0 bei COMPRESS) → `InvalidShiftRequest`.
  3. `computeShift(...)`; `CompressionImpossible` → `EventScheduleError.CompressionImpossible(max)`.
  4. `dryRun=true` → Preview zurück, nichts schreiben. Sonst: alle Slot-Zeiten updaten + je Lauf-Slot `stampMatchStartTime`, dann Preview mit `applied=true`.
- [ ] **Step 2: Route** `post("/shift")`, `Privilege.UpdateEventGlobal`, `receiveKIO(ShiftScheduleRequest.example)`.
- [ ] **Step 3:** `./mvnw test` → grün. **Step 4: Commit** — `git commit -m "Add schedule shift with plus/set-time/compress modes and dry run"`

---

### Task 12: Excel-Import (TDD fürs Matching)

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventSchedule/boundary/ScheduleImport.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventSchedule/entity/ScheduleImportDto.kt`
- Modify: `EventScheduleService.kt`, `eventSchedule.kt`, `EventScheduleRepo.kt`
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/eventSchedule/ScheduleImportTest.kt`

**Interfaces:**
- Produces:

```kotlin
data class ImportCandidate(
    val setupMatchId: UUID,
    val competitionTexts: Set<String>,   // identifier, shortName, name — lowercased/trimmed
    val matchName: String?,              // Setup-Zeilen-Name
    val roundName: String,
)

enum class ImportRowStatus { LINKED, FREE, AMBIGUOUS, DUPLICATE }

data class ImportRowResult(
    val rowNumber: Int,
    val startTime: LocalDateTime,
    val competitionText: String?,
    val laufText: String,
    val status: ImportRowStatus,
    val setupMatchId: UUID?,
)

object ScheduleImport {
    /** Reines Matching einer Zeile; Duplikate markiert der Aufrufer über alle Zeilen hinweg. */
    fun matchRow(competition: String?, lauf: String, candidates: List<ImportCandidate>): Pair<ImportRowStatus, UUID?>
}
```

- Import-Spaltenköpfe (Zeile 1 der xlsx, exakt): `Datum`, `Uhrzeit`, `Wettkampf`, `Lauf`. Optional: `Dauer` (Minuten).
- `ScheduleImportResultDto(rows: List<ImportRowResultDto>, applied: Boolean)`; Request: Multipart mit `dryRun`-FormItem (`"true"`/`"false"`).

- [ ] **Step 1: Failing Tests**

```kotlin
package de.lambda9.ready2race.backend.app.eventSchedule

import de.lambda9.ready2race.backend.app.eventSchedule.boundary.ImportCandidate
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.ScheduleImport
import de.lambda9.ready2race.backend.app.eventSchedule.entity.ImportRowStatus
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleImportTest {

    private val af1DM = ImportCandidate(UUID.randomUUID(), setOf("cm 1x", "männer-einer", "12"), "AF1", "Achtelfinale")
    private val af1Int = ImportCandidate(UUID.randomUUID(), setOf("cm 1x", "männer-einer international", "012-int"), "AF1", "Achtelfinale")
    private val finaleA = ImportCandidate(UUID.randomUUID(), setOf("cmix 4x+", "mixed-doppelvierer", "18"), "Finale A", "Finale")

    @Test
    fun exactCompetitionAndMatchNameLinks() {
        assertEquals(
            ImportRowStatus.LINKED to finaleA.setupMatchId,
            ScheduleImport.matchRow("CMix 4x+", "Finale A", listOf(af1DM, finaleA)),
        )
    }

    @Test
    fun matchingIsCaseAndWhitespaceInsensitive() {
        assertEquals(
            ImportRowStatus.LINKED to finaleA.setupMatchId,
            ScheduleImport.matchRow("  cmix 4X+ ", " finale a ", listOf(finaleA)),
        )
    }

    @Test
    fun emptyCompetitionMeansFreeSlot() {
        assertEquals(ImportRowStatus.FREE to null, ScheduleImport.matchRow(null, "Mittagspause", listOf(finaleA)))
        assertEquals(ImportRowStatus.FREE to null, ScheduleImport.matchRow("  ", "Siegerehrung", listOf(finaleA)))
    }

    @Test
    fun noHitFallsBackToFree() {
        assertEquals(ImportRowStatus.FREE to null, ScheduleImport.matchRow("CF 8x", "Finale A", listOf(finaleA)))
    }

    @Test
    fun twoCompetitionsSharingTheTextAreAmbiguous() {
        assertEquals(
            ImportRowStatus.AMBIGUOUS to null,
            ScheduleImport.matchRow("CM 1x", "AF1", listOf(af1DM, af1Int)),
        )
    }
}
```

- [ ] **Step 2: Fail** — `./mvnw test -Dtest=ScheduleImportTest` → Compilation FAILURE.
- [ ] **Step 3: Implementieren**

```kotlin
object ScheduleImport {

    fun matchRow(
        competition: String?,
        lauf: String,
        candidates: List<ImportCandidate>,
    ): Pair<ImportRowStatus, UUID?> {
        val comp = competition?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            ?: return ImportRowStatus.FREE to null
        val laufNorm = lauf.trim().lowercase()

        val hits = candidates.filter { c ->
            comp in c.competitionTexts && c.matchName?.trim()?.lowercase() == laufNorm
        }
        return when {
            hits.size == 1 -> ImportRowStatus.LINKED to hits.single().setupMatchId
            hits.isEmpty() -> ImportRowStatus.FREE to null
            else -> ImportRowStatus.AMBIGUOUS to null
        }
    }
}
```

- [ ] **Step 4: Tests grün.**
- [ ] **Step 5: Endpoint verdrahten.**
  - `EventScheduleRepo.getImportCandidates(eventId)`: Query wie `getUnplannedSetupMatches`, aber ohne den notExists-Filter, zusätzlich `COMPETITION_PROPERTIES.SHORT_NAME`, `COMPETITION_PROPERTIES.IDENTIFIER`; Mapping zu `ImportCandidate` (Texte lowercased/trimmed, leere verwerfen).
  - Datei lesen mit vorhandenem `XLS.read` (`backend/.../xls/XLS.kt`). Dafür zwei neue Parser in `CellParser.Companion` (`backend/.../xls/CellParser.kt`) ergänzen — beide akzeptieren STRING- **und** NUMERIC-Zellen (Excel speichert Datum/Zeit numerisch; `DateUtil.isCellDateFormatted(cell)` → `cell.localDateTimeCellValue`):
    - `localDate(defaultYear: Int)`: STRING via `d.M.` / `d.M.yyyy` / `dd.MM.yyyy` (fehlendes Jahr → `defaultYear`), NUMERIC via `localDateTimeCellValue.toLocalDate()`.
    - `localTime`: STRING via `H:mm` / `HH:mm`, NUMERIC via `localDateTimeCellValue.toLocalTime()`.
    Zeilen-Lesen: `cell("Datum", CellParser.localDate(eventYear))`, `cell("Uhrzeit", CellParser.localTime)`, `optionalCell("Wettkampf", CellParser.string)`, `cell("Lauf", CellParser.string)`, `optionalCell("Dauer", CellParser.int)`. `eventYear` = Jahr des ersten `event_day`-Datums des Events, sonst aktuelles Jahr. Parse-Fehler des Readers → `ImportFileUnreadable`.
  - Duplikat-Erkennung: gleiche `setupMatchId` in >1 Zeile → beide Zeilen `DUPLICATE`; bei `dryRun=false` → `KIO.fail(EventScheduleError.DuplicateImportRow(rows))`.
  - `dryRun=false`: transaktional (Projektmuster für Transaktionen im Service prüfen — `Jooq.transaction`/umgebende Transaktion pro Request; die anderen Multi-Write-Services wie `createNewRound` als Referenz): alle Slots des Events löschen, neue Slots einfügen (LINKED → `competition_setup_match`, FREE/AMBIGUOUS → `name = laufText`), je LINKED `stampMatchStartTime`.
  - Route `post("/import")`: Multipart-Handling nach dem Muster `competitionExecution.kt:114-149` (FileItem + FormItem `dryRun`), `Privilege.UpdateEventGlobal`.
- [ ] **Step 6:** `./mvnw test` → grün. **Step 7: Commit** — `git commit -m "Import the schedule from a flat xlsx with name matching"`

---

### Task 13: RaceClocker-Ist-Start übernehmen

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/entity/RaceClockerFeedRow.kt` (aus Task 1 im Branch)
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/control/RaceClockerFeed.kt` (parse/Mapping, ~:120-129)
- Modify: `CompetitionExecutionService.updateMatchResultFromRaceClocker` (~:902-964)
- Test: bestehende RaceClocker-Tests erweitern (`backend/src/test/resources/raceclocker/feed.json` enthält bereits `"Start": "11:00:00.0"`)

**Interfaces:**
- Produces: `RaceClockerFeedRow.start: LocalTime?` — tolerant gemappt: erster Key aus `setOf("Start", "Startzeit")` (case-insensitive), Format `H:mm:ss[.S]`; unparsebare Werte → `null` (kein Fehler, Feld ist Zusatzinfo).

- [ ] **Step 1: Test:** Feed-Parsing liefert `start = LocalTime.of(11, 0)` für das Fixture; ein Test für die Übernahme: nach Pull trägt der Lauf `started_at` = Renntag-Datum + früheste Feed-Startzeit, auch wenn vorher ein manueller (späterer) Stempel stand.
- [ ] **Step 2: Implementieren:**
  - Mapping in `RaceClockerFeed.parse` ergänzen; den Doku-Kommentar in `RaceClockerFeedRow.kt:9-11` anpassen (Start wird jetzt bewusst gemappt, Finish weiterhin nicht).
  - In `updateMatchResultFromRaceClocker` nach erfolgreichem Zuordnen: `earliest = rows.mapNotNull { it.start }.minOrNull()`; wenn nicht null → `started_at = LocalDate(des match.start_time, Fallback heutiges Datum).atTime(earliest)` per `CompetitionMatchRepo.update` setzen — **unbedingt**, auch wenn schon ein Wert steht (externe Zeitnahme ist Quelle der Wahrheit; Kommentar an der Schreibstelle, wie beim Penalty-Überschreiben `CompetitionExecutionService.kt:586-592`).
- [ ] **Step 3:** `./mvnw test` → grün. **Step 4: Commit** — `git commit -m "Take the real start time from the RaceClocker feed"`

---

### Task 14: Öffentliche Endpoints — Platzhalter aus wartenden Slots

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/boundary/EventInfoService.kt` (getUpcomingCompetitionMatches ~:154-165, getAthleteBoard ~:248-302)
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/boundary/AthleteBoardLogic.kt`
- Modify: entsprechende DTOs unter `backend/.../app/eventInfo/entity/`
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/eventInfo/AthleteBoardLogicTest.kt` erweitern

**Interfaces:**
- Produces: `UpcomingMatchInfo`/`AthleteBoardMatchDto` erhalten `pendingRound: Boolean` (default false). Platzhalter-Einträge: `pendingRound = true`, `scheduledStartTime` = Slot-Zeit, Namen aus Setup-Zeile (Wettkampf/Runde/Laufname), keine Teams. Quelle: `EventScheduleRepo.getSlots` gefiltert auf abgeleiteten Zustand WAITING (nicht skipped). **Keine sensiblen Felder** — nur Namen und Zeit (Sparsamkeitsregel des Athleten-Boards).

- [ ] **Step 1:** Platzhalter in beide Antworten mischen (nach `start_time` einsortiert; Athleten-Board: `startState` für Platzhalter = reguläre Ableitung aus der Slot-Zeit via `AthleteBoardLogic.startState`). Test: ein WAITING-Slot erscheint in `upcoming` mit `pendingRound = true`; ein SKIPPED-Slot erscheint nicht.
- [ ] **Step 2:** `./mvnw test` → grün. **Step 3: Commit** — `git commit -m "Show not-yet-created runs as schedule placeholders on public boards"`

---

### Task 15: OpenAPI + Frontend-Client

**Files:**
- Modify: `backend/src/main/resources/openapi/documentation.yaml`
- Regenerate: `frontend/src/api/*` via `npm run generate`

- [ ] **Step 1: YAML erweitern.** Unter `paths`: `/event/{eventId}/schedule` (GET), `/event/{eventId}/schedule/slot` (POST), `/event/{eventId}/schedule/slot/{slotId}` (PUT, DELETE), `.../skip` (PUT), `.../unskip` (PUT), `/event/{eventId}/schedule/shift` (POST), `/event/{eventId}/schedule/import` (POST, multipart), `/event/{eventId}/liveDashboard/match/{matchId}/start` (PUT). Unter `components/schemas`: `EventScheduleDto`, `EventScheduleSlotDto`, `EventScheduleSlotState` (enum FREE/WAITING/LINKED/OBSOLETE/SKIPPED), `UnplannedSetupMatchDto`, `UpsertScheduleSlotRequest`, `ShiftScheduleRequest`, `ShiftMode`, `ShiftPreviewDto`, `ShiftPreviewEntryDto`, `ScheduleImportResultDto`, `ImportRowResultDto`, `ImportRowStatus`. Bestehende Schemas ergänzen: `LiveDashboardMatchDto.startedAt` (string/date-time, nullable), `UpcomingMatchInfo.pendingRound` + `AthleteBoardMatchDto.pendingRound` (boolean). Feldnamen exakt wie in den Kotlin-DTOs (Tasks 4, 11, 12, 14) — die Serialisierung ist namensbasiert.
- [ ] **Step 2:** `cd frontend && npm run generate` — Expected: `types.gen.ts`/`sdk.gen.ts` enthalten `getEventSchedule`, `shiftEventSchedule`, `importEventSchedule`, `EventScheduleSlotDto` etc.
- [ ] **Step 3:** `npm run build` → grün. **Step 4: Commit** — `git commit -m "Describe the schedule API and regenerate the client"`

---

### Task 16: Frontend — Zeitplan-Tab (TDD für Gruppierung)

**Files:**
- Create: `frontend/src/components/event/schedule/EventSchedule.tsx` (Tab-Inhalt: Agenda + Aktionen)
- Create: `frontend/src/components/event/schedule/ScheduleSlotDialog.tsx` (Slot anlegen/bearbeiten)
- Create: `frontend/src/components/event/schedule/common.ts` (pure Helfer)
- Test: `frontend/src/components/event/schedule/common.test.ts`
- Modify: `frontend/src/pages/event/EventPage.tsx:76-84` (`EVENT_TABS` + `'schedule'`), Tab-Leiste (~:222-256), neues `TabPanel`
- Modify: `frontend/src/i18n/de/translations.json`, `frontend/src/i18n/en/translations.json`

**Interfaces:**
- Consumes: `getEventSchedule`, `createScheduleSlot`, `updateScheduleSlot`, `deleteScheduleSlot`, `skipScheduleSlot`, `unskipScheduleSlot` aus `frontend/src/api` (Task 15; generierte Namen nach dem Generate prüfen und exakt verwenden).
- Produces (`common.ts`):

```typescript
export type DaySection = {date: string; slots: EventScheduleSlotDto[]}
export const groupSlotsByDay: (slots: EventScheduleSlotDto[]) => DaySection[]
export const slotLabel: (slot: EventScheduleSlotDto) => string
// "CMix 4x+ – Achtelfinale – AF1" bzw. Freitext-Name
```

- [ ] **Step 1: Failing Test `common.test.ts`**

```typescript
import {describe, expect, it} from 'vitest'
import {groupSlotsByDay, slotLabel} from './common'
import {EventScheduleSlotDto} from '@api/types.gen'

const slot = (startTime: string, over: Partial<EventScheduleSlotDto> = {}): EventScheduleSlotDto => ({
    id: crypto.randomUUID(),
    startTime,
    state: 'WAITING',
    name: null,
    durationMinutes: null,
    competitionId: null,
    competitionName: 'CM 1x',
    roundName: 'Achtelfinale',
    matchName: 'AF1',
    matchId: null,
    matchStartedAt: null,
    matchFinishedAt: null,
    ...over,
})

describe('groupSlotsByDay', () => {
    it('splits by calendar day and keeps time order', () => {
        const sections = groupSlotsByDay([
            slot('2026-08-17T08:00:00'),
            slot('2026-08-16T09:30:00'),
            slot('2026-08-17T10:00:00'),
        ])
        expect(sections.map(s => s.date)).toEqual(['2026-08-16', '2026-08-17'])
        expect(sections[1].slots.map(s => s.startTime)).toEqual([
            '2026-08-17T08:00:00',
            '2026-08-17T10:00:00',
        ])
    })
})

describe('slotLabel', () => {
    it('joins competition, round and match name', () => {
        expect(slotLabel(slot('2026-08-17T08:00:00'))).toBe('CM 1x – Achtelfinale – AF1')
    })
    it('uses the free-slot name as-is', () => {
        expect(slotLabel(slot('2026-08-17T12:00:00', {name: 'Mittagspause', competitionName: null, roundName: null, matchName: null, state: 'FREE'}))).toBe('Mittagspause')
    })
})
```

- [ ] **Step 2:** `cd frontend && npm run test` → FAIL (Modul fehlt).
- [ ] **Step 3: `common.ts` implementieren**

```typescript
import {EventScheduleSlotDto} from '@api/types.gen'

export type DaySection = {date: string; slots: EventScheduleSlotDto[]}

export const groupSlotsByDay = (slots: EventScheduleSlotDto[]): DaySection[] => {
    const sorted = [...slots].sort((a, b) => a.startTime.localeCompare(b.startTime))
    const byDay = new Map<string, EventScheduleSlotDto[]>()
    for (const s of sorted) {
        const day = s.startTime.slice(0, 10)
        byDay.set(day, [...(byDay.get(day) ?? []), s])
    }
    return [...byDay.entries()].map(([date, daySlots]) => ({date, slots: daySlots}))
}

export const slotLabel = (slot: EventScheduleSlotDto): string =>
    slot.name ?? [slot.competitionName, slot.roundName, slot.matchName].filter(Boolean).join(' – ')
```

(Import-Alias `@api` an die Projektkonvention anpassen — vorhandene Komponenten wie `LiveDashboardPage.tsx` als Referenz für den tatsächlichen Importpfad.)

- [ ] **Step 4:** `npm run test` → PASS.
- [ ] **Step 5: `EventSchedule.tsx` + `ScheduleSlotDialog.tsx` bauen.** Muster: `Shiftplan.tsx` für die Tab-Einbettung, `BaseDialog`/`FormInput*` wie in `CompetitionExecution.tsx`. Inhalt:
  - Laden via `getEventSchedule` (useEffect + reload-Trigger, Fehler über `useFeedback()`; kein Polling — Orga-Ansicht).
  - Pro `DaySection` eine Tabelle: Zeit (`format.time`-i18n), `slotLabel`, Status-`Chip` (Farben: WAITING=warning, LINKED=primary, FREE=default, OBSOLETE=default+durchgestrichen, SKIPPED=default; beendete Läufe — `matchFinishedAt` — success), Dauer, Aktionen (Bearbeiten, Löschen mit `confirmAction`, Überspringen/Aufheben mit `confirmAction`).
  - Slot-Dialog: Radiogruppe „Lauf“/„Frei“; bei Lauf: drei kaskadierende `Select`s (Wettkampf → Runde → Lauf) — Datenquelle: `unplannedSetupMatches` aus derselben Antwort, gruppiert nach `competitionName`/`roundName`; bei Frei: Textfeld `name`. Zeit via `FormInputDateTime`, Dauer via Zahlenfeld (Minuten).
  - Buttons oberhalb: „Slot hinzufügen“, „Zeitplan anpassen“ (Task 17), „Excel-Import“ (Task 18).
  - Unter der Agenda: Liste „Nicht verplante Läufe“ aus `unplannedSetupMatches` mit „einplanen“-Klick (öffnet den Slot-Dialog vorbefüllt).
- [ ] **Step 6: Tab registrieren** in `EventPage.tsx` (`'schedule'` in `EVENT_TABS`, `<Tab>` hinter `organization` mit `user.checkPrivilege(readEventGlobal)`, `<TabPanel index={'schedule'}>` mit `<EventSchedule/>`).
- [ ] **Step 7: i18n** — de (Auszug, en analog englisch):

```json
"event": {
    "schedule": {
        "tab": "Zeitplan",
        "addSlot": "Slot hinzufügen",
        "freeSlot": "Freier Slot",
        "matchSlot": "Lauf",
        "duration": "Dauer (Minuten)",
        "state": {"FREE": "Programmpunkt", "WAITING": "Lauf noch nicht gesetzt", "LINKED": "Verknüpft", "OBSOLETE": "Entfällt", "SKIPPED": "Übersprungen", "finished": "Beendet"},
        "skip": "Überspringen", "unskip": "Überspringen aufheben",
        "skipConfirm": "Slot „{{label}}“ um {{time}} wirklich überspringen? Die Kette lässt ihn dann aus.",
        "unplanned": "Nicht verplante Läufe",
        "adjust": "Zeitplan anpassen",
        "import": "Excel-Import"
    }
}
```

- [ ] **Step 8:** `npm run test && npm run build` → grün. **Step 9: Commit** — `git commit -m "Add the schedule tab with day-grouped agenda and slot dialog"`

---

### Task 17: Frontend — Shift-Dialog

**Files:**
- Create: `frontend/src/components/event/schedule/ScheduleShiftDialog.tsx`
- Modify: `EventSchedule.tsx` (Button + Einbindung), i18n beide Sprachen

**Interfaces:**
- Consumes: `shiftEventSchedule` (Task 15), Slots des gewählten Tages als Props.

- [ ] **Step 1: Dialog bauen.** Formular: Start-Slot (`Select` über die Slots des Tages, Default = erster ohne `matchFinishedAt`), Modus-Radiogruppe (+X Minuten / Startzeit setzen / Aufholen bis), abhängige Felder (Zahl, `FormInputDateTime`, Ziel-Slot-`Select`). Button „Vorschau“ → `shiftEventSchedule({dryRun: true, ...})`; Ergebnis als Tabelle Slot / alt / neu (geänderte Zeilen hervorheben). 422 mit `CompressionImpossible` → Warntext mit `maxReductionMinutes`. Button „Anwenden“ (nur nach Vorschau aktiv) → gleicher Request `dryRun: false`, danach `reload()` und Dialog zu. **Kein Persistieren vor „Anwenden“.**
- [ ] **Step 2: i18n** (`event.schedule.shift.*`: `title`, `fromSlot`, `modePlus`, `modeSetTime`, `modeCompress`, `targetSlot`, `preview`, `apply`, `old`, `new`, `impossible`: "Aufholen nicht möglich — nur {{max}} Minuten Spielraum. Wähle einen späteren Ziel-Slot.").
- [ ] **Step 3:** `npm run build` → grün. **Step 4: Commit** — `git commit -m "Add the schedule shift dialog with preview-before-apply"`

---

### Task 18: Frontend — Import-Dialog

**Files:**
- Create: `frontend/src/components/event/schedule/ScheduleImportDialog.tsx`
- Modify: `EventSchedule.tsx`, i18n beide Sprachen

**Interfaces:**
- Consumes: `importEventSchedule` (multipart; generierte Signatur nach Task 15 prüfen — Datei + `dryRun`).

- [ ] **Step 1: Dialog bauen.** Datei-Auswahl (`<input type="file" accept=".xlsx">` im MUI-Button-Wrapper, wie der bestehende Ergebnis-Upload in `CompetitionExecution.tsx` — als Muster übernehmen). Nach Auswahl automatisch `dryRun: true` → Vorschau-Tabelle: Zeile, Zeit, Text, Status-Chip (LINKED grün mit Ziel-Label, FREE grau, AMBIGUOUS orange mit Hinweis, DUPLICATE rot). Hinweisbox: „Der Import ersetzt alle vorhandenen Slots.“ Warnung, wenn aktuell Slots mit laufenden/beendeten Läufen existieren (aus dem geladenen Schedule ableitbar: `matchStartedAt`/`matchFinishedAt` gesetzt). „Importieren“ deaktiviert bei DUPLICATE-Zeilen; sonst gleicher Request `dryRun: false`, Erfolg → reload.
- [ ] **Step 2: i18n** (`event.schedule.importDialog.*`: `title`, `choose`, `replacesAll`, `rowLinked`, `rowFree`, `rowAmbiguous`: "Nicht eindeutig — wird als freier Slot importiert", `rowDuplicate`: "Doppelt in der Datei — Import blockiert", `apply`).
- [ ] **Step 3:** `npm run build` → grün. **Step 4: Commit** — `git commit -m "Add the schedule Excel import dialog with row-by-row preview"`

---

### Task 19: Frontend — Dashboard-Integration

**Files:**
- Modify: `frontend/src/pages/event/LiveDashboardPage.tsx:105-108, 235-250`
- Modify: `frontend/src/components/event/liveDashboard/LiveDashboardMatchCard.tsx`
- Modify: `frontend/src/components/event/info/views/UpcomingMatchesView.tsx`, `frontend/src/components/event/info/athleteBoard/AthleteBoardMatchCard.tsx`
- Modify: `frontend/src/components/event/competition/excecution/CompetitionExecution.tsx:1081` (Read-only-Feld)
- Modify: i18n beide Sprachen, `frontend/src/i18n/de/translations.json:495` (Hinweistext des Ketten-Schalters)

- [ ] **Step 1: Schiedsrichter-Dashboard.**
  - Karten zeigen „geplant HH:MM“ und — wenn `startedAt` — „gestartet HH:MM“; Laufzeit nur mit `startedAt` (Feld kommt aus Task 8/15).
  - „Start“-Button auf laufend-vorbereiteten Karten (`currentlyRunning && !startedAt`) → `PUT .../match/{id}/start`.
  - Wartende Slots: `LiveDashboardDto` wird in Task 14 um `pendingSlots: List<PendingSlotDto>` erweitert (`PendingSlotDto(slotId: UUID, startTime: LocalDateTime, competitionName: String?, roundName: String?, matchName: String?)` — WAITING-Slots, nicht skipped), Task 15 nimmt das Schema in die OpenAPI auf. Hier rendern: Karte mit Status-Chip „Lauf noch nicht gesetzt“, einsortiert nach `startTime` zwischen den Anstehend-Karten.
  - „Überspringen“-Button (mit `confirmAction`) auf Platzhalter-Karten und auf der „Als Nächstes“-Karte → `skipScheduleSlot`.
- [ ] **Step 2: Kiosk/Athleten-Board:** `pendingRound === true` → Karte ohne Teams mit Text `event.info.pendingRound` („Lauf noch nicht gesetzt“); Athleten-Board-Karte: bestehende `unscheduled`-Optik als Vorlage (`AthleteBoardMatchCard.tsx:52-58`). Keine neuen Interaktionen.
- [ ] **Step 3: Ausführungs-Dialog:** `CompetitionExecution.tsx` lädt beim Mount einmal `getEventSchedule` und bildet `const slotManagedMatchIds = new Set(schedule.slots.filter(s => s.matchId).map(s => s.matchId))` (kein Backend-Umbau nötig). Ist die Match-ID des Dialogs enthalten: `FormInputDateTime` durch read-only-Anzeige der Zeit mit Hinweis `event.schedule.managedHint` („Wird über den Zeitplan gepflegt“) ersetzen; das Formular schickt dann `startTime` unverändert mit (Task-10-Guard vergleicht auf Gleichheit). Events ohne Zeitstrahl (leere Slot-Liste): unverändert.
- [ ] **Step 4: Hinweistext des Schalters** (`translations.json:495`) ergänzen: „Mit gepflegtem Zeitplan (Tab Zeitplan) folgt die Kette den Slots und wartet an noch nicht gesetzten Läufen.“
- [ ] **Step 5:** `npm run test && npm run build` → grün. **Step 6: Commit** — `git commit -m "Surface schedule placeholders, real starts and skip on the dashboards"`

---

### Task 20: Seed + Gesamtverifikation

**Files:**
- Modify: `.superpowers/sdd/seed-live-dashboard.sql` (gitignored; liegt nur im Hauptcheckout `/Users/thomas/Developer/privat/ready2race/.superpowers/sdd/` — dorthin kopieren/ändern, ins Worktree übernehmen)

- [ ] **Step 1: Seed erweitern:** `set time zone 'Europe/Berlin'` beibehalten. Slots mit UUID-Präfix `5eed` für das Seed-Event: 2 Renntage; Tag 1: freier Slot „Obleute-Besprechung“, drei LINKED-Slots auf die bestehenden Seed-Läufe (Zeiten passend zu deren `start_time`), ein WAITING-Slot auf eine Setup-Zeile einer noch nicht erzeugten Runde, ein freier Slot „Mittagspause“ (45 min). Cleanup-Block um `delete from event_schedule_slot where id::text like '5eed%'` ergänzen.
- [ ] **Step 2: Funktionaler Durchstich** (Backend + Frontend lokal starten, siehe Global Constraints; `.env`-Dateien kopieren):
  1. Zeitplan-Tab: Agenda zeigt beide Tage, Zustände stimmen.
  2. `auto_activate_next_match` am Seed-Event einschalten; Lauf im Schiedsrichter-Dashboard beenden → nächster LINKED-Slot wird aktiv; vor dem WAITING-Slot stoppt die Aktivierung, Karte „Lauf noch nicht gesetzt“ sichtbar.
  3. „Nächste Runde erstellen“ in der Wettkampf-Ausführung → Slot füllt sich, `start_time` = Slot-Zeit, Kette aktiviert.
  4. Shift-Dialog: +10 min mit Vorschau; Stauchen mit unerreichbarem Ziel → Warnung.
  5. Import: Mini-xlsx mit 4 Zeilen (1 frei, 2 LINKED, 1 unbekannt) → Vorschau korrekt, Import ersetzt Slots.
- [ ] **Step 3: Komplette Testläufe**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
cd backend && ./mvnw test
cd ../frontend && npm run test && npm run build
```
Expected: alles grün.

- [ ] **Step 4: Commit** (nur committen, was nicht gitignored ist — der Seed selbst bleibt lokal):

```bash
git add -A && git status   # prüfen: keine .env, kein Seed
git commit -m "Zeitstrahl: functional test fixes"   # nur falls Fixes anfielen
```

---

## Offen bleibende Punkte (bewusst außerhalb dieses Plans)

- Wave-Name-Export mit Startzeit (Spec §7, Follow-up).
- Reset-Bug im Lauf-Daten-Dialog (separater Fix, Chip existiert).
- Merge von `feature/zeitstrahl` zurück nach `feature/crf-2026` — erst nach Review durch Thomas.
