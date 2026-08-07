# Referee Live Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A mobile-first, privilege-gated live dashboard at `/event/$eventId/liveDashboard` showing running/upcoming/finished matches with per-team invoice status and per-participant requirement checks incl. a server-computed time-window traffic light.

**Architecture:** New backend module `app/liveDashboard` exposes one aggregated endpoint `GET /event/{eventId}/liveDashboard` (privilege `READ LIVE_DASHBOARD GLOBAL`). `participant_requirement` gains two optional time-window columns. Frontend adds one page (two tabs: Live / Läufe) polling that endpoint every 10 s. Spec: `docs/superpowers/specs/2026-07-29-live-dashboard-schiedsrichter-design.md`.

**Tech Stack:** Kotlin/Ktor + KIO (tailwind) + jOOQ + Flyway + PostgreSQL; React 18 + TypeScript + MUI v6 + @tanstack/react-router + generated `@hey-api` client; kotlin.test.

## Global Constraints

- Branch: `feature/live-dashboard-schiedsrichter` (already created from `main`). Commit after every task, plain English messages, no AI attribution in commits (transparency note lives in the spec document).
- Backend build needs the build DB: `cd backend && docker compose up -d build-db` (Postgres on port 7652). `./mvnw generate-sources` runs Flyway migrate + jOOQ codegen against it; `./mvnw compile` includes that.
- After any change to `backend/src/main/resources/openapi/documentation.yaml`, regenerate the frontend client: `cd frontend && npm run generate` (writes `src/api/sdk.gen.ts` / `types.gen.ts`).
- German UI strings must use proper Umlaute (ä, ö, ü, ß). i18n keys must be added to **all three** files: `frontend/src/i18n/de/translations.json`, `.../en/translations.json`, `.../da/translations.json`.
- Migration naming: `V<yyyyMMddHHmm>__snake_case.sql` in `backend/src/main/resources/db/migration/`.
- Frontend components directory for the execution domain is spelled `excecution` (repo typo) — do not "fix" it; new code lives in `components/event/liveDashboard/` anyway.
- Time-window semantics (fixed by spec): `deltaMinutes = Duration.between(checkedAt, startTime).toMinutes()` (positive = check before start). `TOO_EARLY` if `delta > checkEarliestMinutesBefore`; `LATE` if `delta < checkLatestMinutesBefore`; both bounds inclusive-OK at equality. No window configured or no `startTime` → no `timeCheck` at all. Window configured but no check → status `NOT_CHECKED`, `deltaMinutes = null`.

---

### Task 1: DB migration for requirement time window + jOOQ regeneration

**Files:**
- Create: `backend/src/main/resources/db/migration/V202607291400__participant_requirement_check_time_window.sql`

**Interfaces:**
- Produces: columns `participant_requirement.check_earliest_minutes_before` / `check_latest_minutes_before` (both `int null`); after codegen the Kotlin record `ParticipantRequirementRecord` has properties `checkEarliestMinutesBefore: Int?` / `checkLatestMinutesBefore: Int?` and the table reference `PARTICIPANT_REQUIREMENT.CHECK_EARLIEST_MINUTES_BEFORE` / `.CHECK_LATEST_MINUTES_BEFORE` exists. The view `participant_requirement_for_event` (`select pr.*`) picks the columns up automatically on the next `afterMigrate.sql` run — no view change needed.

- [ ] **Step 1: Write the migration**

```sql
set search_path to ready2race, pg_catalog, public;

alter table participant_requirement
    add column check_earliest_minutes_before int,
    add column check_latest_minutes_before   int;
```

- [ ] **Step 2: Run migration + codegen**

Run:
```bash
cd backend && docker compose up -d build-db && ./mvnw generate-sources
```
Expected: BUILD SUCCESS; Flyway applies `V202607291400`, jOOQ regenerates into `target/generated-sources/jooq`.

- [ ] **Step 3: Verify generated code**

Run: `grep -rn "checkEarliestMinutesBefore" backend/target/generated-sources/jooq | head -5`
Expected: hits in `ParticipantRequirementRecord` (and the `participant_requirement_for_event` view record).

- [ ] **Step 4: Verify existing code still compiles**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS. Note: `ParticipantRequirementRecord(...)` positional constructors would break — the codebase uses named args (`Conversions.kt` uses named args, nothing else constructs the record), so no changes expected. If compile fails, fix call sites by adding the two new named args as `null`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V202607291400__participant_requirement_check_time_window.sql
git commit -m "Add check time window columns to participant_requirement"
```

---

### Task 2: Time window in ParticipantRequirement API (backend + OpenAPI)

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/participantRequirement/entity/ParticipantRequirementUpsertDto.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/participantRequirement/entity/ParticipantRequirementDto.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/participantRequirement/control/Conversions.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/participantRequirement/boundary/ParticipantRequirementService.kt` (the update function around line 259)
- Modify: `backend/src/main/resources/openapi/documentation.yaml` (schemas `ParticipantRequirementDto`, `ParticipantRequirementUpsertDto`)
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/participantRequirement/ParticipantRequirementUpsertDtoTest.kt`

**Interfaces:**
- Consumes: record fields from Task 1.
- Produces: `ParticipantRequirementDto.checkEarliestMinutesBefore: Int?` / `.checkLatestMinutesBefore: Int?` (same names on UpsertDto); OpenAPI schema fields `checkEarliestMinutesBefore` / `checkLatestMinutesBefore` (`type: integer, nullable: true`) on both schemas. Task 5 reads the columns via jOOQ directly; Task 8 uses the generated TS types.

- [ ] **Step 1: Write the failing validation test**

```kotlin
package de.lambda9.ready2race.backend.app.participantRequirement

import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementUpsertDto
import de.lambda9.ready2race.backend.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ParticipantRequirementUpsertDtoTest {

    private fun dto(earliest: Int?, latest: Int?) = ParticipantRequirementUpsertDto(
        name = "Waage",
        description = null,
        optional = false,
        checkInApp = false,
        checkEarliestMinutesBefore = earliest,
        checkLatestMinutesBefore = latest,
    )

    @Test
    fun validWithoutWindow() {
        assertEquals(ValidationResult.Valid, dto(null, null).validate())
    }

    @Test
    fun validWithOneSidedWindow() {
        assertEquals(ValidationResult.Valid, dto(120, null).validate())
        assertEquals(ValidationResult.Valid, dto(null, 15).validate())
    }

    @Test
    fun validWithFullWindow() {
        assertEquals(ValidationResult.Valid, dto(120, 15).validate())
    }

    @Test
    fun invalidWhenEarliestNotGreaterThanLatest() {
        assertNotEquals(ValidationResult.Valid, dto(15, 120).validate())
        assertNotEquals(ValidationResult.Valid, dto(60, 60).validate())
    }

    @Test
    fun invalidWhenNotPositive() {
        assertNotEquals(ValidationResult.Valid, dto(0, null).validate())
        assertNotEquals(ValidationResult.Valid, dto(null, -5).validate())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -q -Dtest=ParticipantRequirementUpsertDtoTest`
Expected: COMPILATION ERROR (constructor has no such parameters).

- [ ] **Step 3: Extend the DTOs, conversions, and update service**

`ParticipantRequirementUpsertDto.kt` (full new content):

```kotlin
package de.lambda9.ready2race.backend.app.participantRequirement.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.IntValidators
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank

data class ParticipantRequirementUpsertDto(
    val name: String,
    val description: String?,
    val optional: Boolean?,
    val checkInApp: Boolean?,
    val checkEarliestMinutesBefore: Int?,
    val checkLatestMinutesBefore: Int?,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::name validate notBlank,
        this::checkEarliestMinutesBefore validate IntValidators.min(1),
        this::checkLatestMinutesBefore validate IntValidators.min(1),
        if (checkEarliestMinutesBefore != null && checkLatestMinutesBefore != null
            && checkEarliestMinutesBefore <= checkLatestMinutesBefore
        ) {
            ValidationResult.Invalid.Message { "checkEarliestMinutesBefore must be greater than checkLatestMinutesBefore" }
        } else {
            ValidationResult.Valid
        },
    )

    companion object {
        val example
            get() = ParticipantRequirementUpsertDto(
                name = "Name",
                description = "Description",
                optional = false,
                checkInApp = false,
                checkEarliestMinutesBefore = 120,
                checkLatestMinutesBefore = 15,
            )
    }
}
```

Note: `IntValidators` is `Validators<Int?>` — verify null passes (check `Validators.simple`; existing DTOs validate nullable ints the same way, e.g. `AppUserRegisterRequest`). If `simple` does NOT skip null, wrap: `checkEarliestMinutesBefore?.let { ... } ?: Valid`.

`ParticipantRequirementDto.kt`:

```kotlin
data class ParticipantRequirementDto(
    val id: UUID,
    val name: String,
    val description: String?,
    val optional: Boolean,
    val checkInApp: Boolean,
    val checkEarliestMinutesBefore: Int?,
    val checkLatestMinutesBefore: Int?,
)
```

`Conversions.kt` — extend all four conversions that build `ParticipantRequirementRecord` or `ParticipantRequirementDto` with the two fields:

- `ParticipantRequirementUpsertDto.toRecord(...)`: add `checkEarliestMinutesBefore = checkEarliestMinutesBefore, checkLatestMinutesBefore = checkLatestMinutesBefore,`
- `ParticipantRequirementRecord.toDto()`: add `checkEarliestMinutesBefore = checkEarliestMinutesBefore, checkLatestMinutesBefore = checkLatestMinutesBefore,`
- `ParticipantRequirementForEventRecord.toRequirementDto()`: add the same two (view record has them after Task 1 regen).
- Leave `toNamedParticipantRequirementDto` variants unchanged (their DTOs don't carry the window).

`ParticipantRequirementService.kt` update function (~line 259, the `.update { ... }` block that sets `optional`/`checkInApp`): add

```kotlin
checkEarliestMinutesBefore = request.checkEarliestMinutesBefore
checkLatestMinutesBefore = request.checkLatestMinutesBefore
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -q -Dtest=ParticipantRequirementUpsertDtoTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Extend OpenAPI schemas**

In `backend/src/main/resources/openapi/documentation.yaml`, add to **both** `ParticipantRequirementDto` and `ParticipantRequirementUpsertDto` schema `properties`:

```yaml
        checkEarliestMinutesBefore:
          type: integer
          nullable: true
          description: "Check must be at most this many minutes before match start"
        checkLatestMinutesBefore:
          type: integer
          nullable: true
          description: "Check must exist at latest this many minutes before match start"
```

- [ ] **Step 6: Full backend compile + all tests**

Run: `cd backend && ./mvnw test -q`
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add -A backend/src docs
git commit -m "Add optional check time window to participant requirements"
```

---

### Task 3: LIVE_DASHBOARD privilege (backend + OpenAPI)

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/auth/entity/Privilege.kt`
- Modify: `backend/src/main/resources/openapi/documentation.yaml` (`Resource` enum, ~line 7184)

**Interfaces:**
- Produces: `Privilege.ReadLiveDashboardGlobal` (Kotlin), `LIVE_DASHBOARD` in the OpenAPI `Resource` enum (→ TS type after regen). `initializeDatabase.kt` syncs `Privilege.entries` into the DB at startup — no migration needed; the privilege appears in role management automatically.

- [ ] **Step 1: Add resource + privilege object**

In `Privilege.kt`: add `LIVE_DASHBOARD,` to `enum class Resource` (after `RESULT,`), and next to the other data objects:

```kotlin
    data object ReadLiveDashboardGlobal : Privilege(Action.READ, Resource.LIVE_DASHBOARD, Scope.GLOBAL)
```

- [ ] **Step 2: Add to OpenAPI Resource enum**

In `documentation.yaml`, `Resource:` enum: add `- LIVE_DASHBOARD` after `- RESULT`.

- [ ] **Step 3: Compile**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/app/auth/entity/Privilege.kt backend/src/main/resources/openapi/documentation.yaml
git commit -m "Add LIVE_DASHBOARD privilege resource"
```

---

### Task 4: LiveDashboard entities + pure logic (TDD)

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/entity/LiveDashboardDto.kt` (holds ALL dashboard DTOs + enums in one file — they are small and always change together)
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/boundary/LiveDashboardLogic.kt`
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/LiveDashboardLogicTest.kt`

**Interfaces:**
- Produces (used by Tasks 5 and OpenAPI schemas in Task 5):

```kotlin
enum class LiveDashboardMatchState { RUNNING, FINISHED, UPCOMING, UNSCHEDULED }
enum class LiveDashboardInvoiceState { PAID, OPEN, NONE }
enum class TimeCheckStatus { OK, TOO_EARLY, LATE, NOT_CHECKED }
data class TimeCheckDto(val deltaMinutes: Long?, val status: TimeCheckStatus)
data class LiveDashboardRequirementStatusDto(requirementId: UUID, name: String, description: String?, optional: Boolean, checked: Boolean, checkedAt: LocalDateTime?, note: String?, timeCheck: TimeCheckDto?)
data class LiveDashboardParticipantDto(participantId: UUID, firstName: String, lastName: String, namedRole: String?, year: Int?, gender: String?, externalClubName: String?, requirements: List<LiveDashboardRequirementStatusDto>)
data class LiveDashboardTeamDto(teamId: UUID, teamName: String?, clubName: String?, actualClubName: String?, startNumber: Int?, place: Int?, time: String?, failed: Boolean, failedReason: String?, deregistered: Boolean, deregisteredReason: String?, invoiceState: LiveDashboardInvoiceState, participants: List<LiveDashboardParticipantDto>)
data class LiveDashboardMatchDto(matchId: UUID, state: LiveDashboardMatchState, competitionId: UUID, competitionName: String, categoryName: String?, roundName: String?, matchName: String?, executionOrder: Int, startTime: LocalDateTime?, currentlyRunning: Boolean, elapsedMinutes: Long?, teams: List<LiveDashboardTeamDto>)
data class LiveDashboardDto(val matches: List<LiveDashboardMatchDto>)

object LiveDashboardLogic {
    fun computeTimeCheck(startTime: LocalDateTime?, checkedAt: LocalDateTime?, earliestMinutesBefore: Int?, latestMinutesBefore: Int?): TimeCheckDto?
    fun deriveInvoiceState(paidAts: List<LocalDateTime?>): LiveDashboardInvoiceState
    fun deriveMatchState(currentlyRunning: Boolean, startTime: LocalDateTime?, teamPlaces: List<Int?>): LiveDashboardMatchState
    fun requirementApplies(assignedNamedParticipants: List<UUID?>, participantNamedParticipantId: UUID?): Boolean
}
```

- [ ] **Step 1: Write the entity file**

`entity/LiveDashboardDto.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.liveDashboard.entity

import java.time.LocalDateTime
import java.util.UUID

enum class LiveDashboardMatchState { RUNNING, FINISHED, UPCOMING, UNSCHEDULED }

enum class LiveDashboardInvoiceState { PAID, OPEN, NONE }

enum class TimeCheckStatus { OK, TOO_EARLY, LATE, NOT_CHECKED }

data class TimeCheckDto(
    val deltaMinutes: Long?,
    val status: TimeCheckStatus,
)

data class LiveDashboardRequirementStatusDto(
    val requirementId: UUID,
    val name: String,
    val description: String?,
    val optional: Boolean,
    val checked: Boolean,
    val checkedAt: LocalDateTime?,
    val note: String?,
    val timeCheck: TimeCheckDto?,
)

data class LiveDashboardParticipantDto(
    val participantId: UUID,
    val firstName: String,
    val lastName: String,
    val namedRole: String?,
    val year: Int?,
    val gender: String?,
    val externalClubName: String?,
    val requirements: List<LiveDashboardRequirementStatusDto>,
)

data class LiveDashboardTeamDto(
    val teamId: UUID,
    val teamName: String?,
    val clubName: String?,
    val actualClubName: String?,
    val startNumber: Int?,
    val place: Int?,
    val time: String?,
    val failed: Boolean,
    val failedReason: String?,
    val deregistered: Boolean,
    val deregisteredReason: String?,
    val invoiceState: LiveDashboardInvoiceState,
    val participants: List<LiveDashboardParticipantDto>,
)

data class LiveDashboardMatchDto(
    val matchId: UUID,
    val state: LiveDashboardMatchState,
    val competitionId: UUID,
    val competitionName: String,
    val categoryName: String?,
    val roundName: String?,
    val matchName: String?,
    val executionOrder: Int,
    val startTime: LocalDateTime?,
    val currentlyRunning: Boolean,
    val elapsedMinutes: Long?,
    val teams: List<LiveDashboardTeamDto>,
)

data class LiveDashboardDto(
    val matches: List<LiveDashboardMatchDto>,
)
```

- [ ] **Step 2: Write the failing logic test**

`LiveDashboardLogicTest.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.liveDashboard

import de.lambda9.ready2race.backend.app.liveDashboard.boundary.LiveDashboardLogic
import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardInvoiceState
import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardMatchState
import de.lambda9.ready2race.backend.app.liveDashboard.entity.TimeCheckStatus
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveDashboardLogicTest {

    private val start = LocalDateTime.of(2026, 7, 29, 14, 0)

    // --- computeTimeCheck ---

    @Test
    fun noWindowConfiguredYieldsNoTimeCheck() {
        assertNull(LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(30), null, null))
    }

    @Test
    fun noStartTimeYieldsNoTimeCheck() {
        assertNull(LiveDashboardLogic.computeTimeCheck(null, start.minusMinutes(30), 120, 15))
    }

    @Test
    fun missingCheckYieldsNotChecked() {
        val result = LiveDashboardLogic.computeTimeCheck(start, null, 120, 15)!!
        assertEquals(TimeCheckStatus.NOT_CHECKED, result.status)
        assertNull(result.deltaMinutes)
    }

    @Test
    fun checkWithinWindowIsOk() {
        val result = LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(60), 120, 15)!!
        assertEquals(TimeCheckStatus.OK, result.status)
        assertEquals(60L, result.deltaMinutes)
    }

    @Test
    fun boundariesAreInclusive() {
        assertEquals(TimeCheckStatus.OK, LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(120), 120, 15)!!.status)
        assertEquals(TimeCheckStatus.OK, LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(15), 120, 15)!!.status)
    }

    @Test
    fun checkTooFarBeforeStartIsTooEarly() {
        val result = LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(121), 120, 15)!!
        assertEquals(TimeCheckStatus.TOO_EARLY, result.status)
        assertEquals(121L, result.deltaMinutes)
    }

    @Test
    fun checkTooCloseToStartIsLate() {
        val result = LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(14), 120, 15)!!
        assertEquals(TimeCheckStatus.LATE, result.status)
    }

    @Test
    fun checkAfterStartIsLateWhenLatestConfigured() {
        val result = LiveDashboardLogic.computeTimeCheck(start, start.plusMinutes(5), 120, 15)!!
        assertEquals(TimeCheckStatus.LATE, result.status)
        assertEquals(-5L, result.deltaMinutes)
    }

    @Test
    fun oneSidedEarliestOnlyWindow() {
        assertEquals(TimeCheckStatus.OK, LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(5), 120, null)!!.status)
        assertEquals(TimeCheckStatus.TOO_EARLY, LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(180), 120, null)!!.status)
    }

    @Test
    fun oneSidedLatestOnlyWindow() {
        assertEquals(TimeCheckStatus.OK, LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(180), null, 15)!!.status)
        assertEquals(TimeCheckStatus.LATE, LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(5), null, 15)!!.status)
    }

    // --- deriveInvoiceState ---

    @Test
    fun noInvoicesIsNone() {
        assertEquals(LiveDashboardInvoiceState.NONE, LiveDashboardLogic.deriveInvoiceState(emptyList()))
    }

    @Test
    fun anyUnpaidInvoiceIsOpen() {
        assertEquals(
            LiveDashboardInvoiceState.OPEN,
            LiveDashboardLogic.deriveInvoiceState(listOf(LocalDateTime.now(), null))
        )
    }

    @Test
    fun allPaidIsPaid() {
        assertEquals(
            LiveDashboardInvoiceState.PAID,
            LiveDashboardLogic.deriveInvoiceState(listOf(LocalDateTime.now(), LocalDateTime.now()))
        )
    }

    // --- deriveMatchState ---

    @Test
    fun currentlyRunningWinsOverEverything() {
        assertEquals(
            LiveDashboardMatchState.RUNNING,
            LiveDashboardLogic.deriveMatchState(true, null, listOf(1, 2))
        )
    }

    @Test
    fun allPlacesSetIsFinished() {
        assertEquals(
            LiveDashboardMatchState.FINISHED,
            LiveDashboardLogic.deriveMatchState(false, start, listOf(1, 2))
        )
    }

    @Test
    fun noTeamsIsNeverFinished() {
        assertEquals(
            LiveDashboardMatchState.UPCOMING,
            LiveDashboardLogic.deriveMatchState(false, start, emptyList())
        )
    }

    @Test
    fun missingStartTimeIsUnscheduled() {
        assertEquals(
            LiveDashboardMatchState.UNSCHEDULED,
            LiveDashboardLogic.deriveMatchState(false, null, listOf(null, null))
        )
    }

    @Test
    fun startTimeInPastWithoutPlacesIsStillUpcoming() {
        assertEquals(
            LiveDashboardMatchState.UPCOMING,
            LiveDashboardLogic.deriveMatchState(false, LocalDateTime.now().minusHours(1), listOf(1, null))
        )
    }

    // --- requirementApplies ---

    @Test
    fun globalAssignmentAppliesToEveryone() {
        assertTrue(LiveDashboardLogic.requirementApplies(listOf(null), UUID.randomUUID()))
        assertTrue(LiveDashboardLogic.requirementApplies(listOf(null), null))
    }

    @Test
    fun namedAssignmentAppliesOnlyToMatchingRole() {
        val roleId = UUID.randomUUID()
        assertTrue(LiveDashboardLogic.requirementApplies(listOf(roleId), roleId))
        assertFalse(LiveDashboardLogic.requirementApplies(listOf(roleId), UUID.randomUUID()))
        assertFalse(LiveDashboardLogic.requirementApplies(listOf(roleId), null))
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd backend && ./mvnw test -q -Dtest=LiveDashboardLogicTest`
Expected: COMPILATION ERROR (`LiveDashboardLogic` not defined).

- [ ] **Step 4: Implement the logic**

`boundary/LiveDashboardLogic.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.liveDashboard.boundary

import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardInvoiceState
import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardMatchState
import de.lambda9.ready2race.backend.app.liveDashboard.entity.TimeCheckDto
import de.lambda9.ready2race.backend.app.liveDashboard.entity.TimeCheckStatus
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

object LiveDashboardLogic {

    fun computeTimeCheck(
        startTime: LocalDateTime?,
        checkedAt: LocalDateTime?,
        earliestMinutesBefore: Int?,
        latestMinutesBefore: Int?,
    ): TimeCheckDto? {
        if (earliestMinutesBefore == null && latestMinutesBefore == null) return null
        if (startTime == null) return null
        if (checkedAt == null) return TimeCheckDto(null, TimeCheckStatus.NOT_CHECKED)

        val deltaMinutes = Duration.between(checkedAt, startTime).toMinutes()
        val status = when {
            earliestMinutesBefore != null && deltaMinutes > earliestMinutesBefore -> TimeCheckStatus.TOO_EARLY
            latestMinutesBefore != null && deltaMinutes < latestMinutesBefore -> TimeCheckStatus.LATE
            else -> TimeCheckStatus.OK
        }
        return TimeCheckDto(deltaMinutes, status)
    }

    fun deriveInvoiceState(paidAts: List<LocalDateTime?>): LiveDashboardInvoiceState = when {
        paidAts.isEmpty() -> LiveDashboardInvoiceState.NONE
        paidAts.any { it == null } -> LiveDashboardInvoiceState.OPEN
        else -> LiveDashboardInvoiceState.PAID
    }

    fun deriveMatchState(
        currentlyRunning: Boolean,
        startTime: LocalDateTime?,
        teamPlaces: List<Int?>,
    ): LiveDashboardMatchState = when {
        currentlyRunning -> LiveDashboardMatchState.RUNNING
        teamPlaces.isNotEmpty() && teamPlaces.all { it != null } -> LiveDashboardMatchState.FINISHED
        startTime == null -> LiveDashboardMatchState.UNSCHEDULED
        else -> LiveDashboardMatchState.UPCOMING
    }

    fun requirementApplies(
        assignedNamedParticipants: List<UUID?>,
        participantNamedParticipantId: UUID?,
    ): Boolean = assignedNamedParticipants.any { it == null } ||
        (participantNamedParticipantId != null && assignedNamedParticipants.contains(participantNamedParticipantId))
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && ./mvnw test -q -Dtest=LiveDashboardLogicTest`
Expected: PASS (all tests).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard backend/src/test/kotlin/de/lambda9/ready2race/backend/app/liveDashboard
git commit -m "Add live dashboard entities and status derivation logic"
```

---

### Task 5: LiveDashboard repo, service, route, OpenAPI endpoint

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/control/LiveDashboardRepo.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/entity/LiveDashboardError.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/boundary/LiveDashboardService.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/boundary/liveDashboard.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/plugins/Routing.kt` (register `liveDashboard()` after `eventInfo()`)
- Modify: `backend/src/main/resources/openapi/documentation.yaml` (path + schemas)

**Interfaces:**
- Consumes: `LiveDashboardLogic`, entities from Task 4, `Privilege.ReadLiveDashboardGlobal` from Task 3, columns from Task 1.
- Produces: `GET /api/event/{eventId}/liveDashboard` → `LiveDashboardDto`; OpenAPI `operationId: getLiveDashboard` (→ TS `getLiveDashboard()` after regen, used in Task 7/8).

- [ ] **Step 1: Write the repo**

`control/LiveDashboardRepo.kt` — mirror query style of `CompetitionMatchRepo.getRunningMatches` and `CompetitionMatchTeamRepo.getTeamsForMatchResult` (imports: `de.lambda9.ready2race.backend.database.generated.tables.references.*`, `de.lambda9.tailwind.jooq.Jooq`):

```kotlin
package de.lambda9.ready2race.backend.app.liveDashboard.control

import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object LiveDashboardRepo {

    fun getMatches(eventId: UUID) = Jooq.query {
        select(
            COMPETITION_MATCH.COMPETITION_SETUP_MATCH,
            COMPETITION_MATCH.START_TIME,
            COMPETITION_MATCH.CURRENTLY_RUNNING,
            COMPETITION_SETUP_MATCH.EXECUTION_ORDER,
            COMPETITION_SETUP_MATCH.NAME.`as`("match_name"),
            COMPETITION_SETUP_ROUND.NAME.`as`("round_name"),
            COMPETITION.ID.`as`("competition_id"),
            COMPETITION_VIEW.NAME.`as`("competition_name"),
            COMPETITION_VIEW.CATEGORY_NAME,
        )
            .from(COMPETITION_MATCH)
            .join(COMPETITION_SETUP_MATCH)
            .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .leftJoin(COMPETITION_VIEW).on(COMPETITION_VIEW.ID.eq(COMPETITION.ID))
            .where(COMPETITION.EVENT.eq(eventId))
            .orderBy(
                COMPETITION_MATCH.START_TIME.asc().nullsLast(),
                COMPETITION_SETUP_MATCH.EXECUTION_ORDER.asc()
            )
            .fetch()
    }

    fun getTeams(eventId: UUID) = Jooq.query {
        select(
            COMPETITION_MATCH_TEAM.COMPETITION_MATCH.`as`("match_id"),
            COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION,
            COMPETITION_MATCH_TEAM.START_NUMBER,
            COMPETITION_MATCH_TEAM.PLACE,
            COMPETITION_MATCH_TEAM.FAILED,
            COMPETITION_MATCH_TEAM.FAILED_REASON,
            COMPETITION_REGISTRATION.NAME.`as`("team_name"),
            COMPETITION_DEREGISTRATION.COMPETITION_REGISTRATION.isNotNull.`as`("deregistered"),
            COMPETITION_DEREGISTRATION.REASON.`as`("deregistration_reason"),
            CLUB.ID.`as`("club_id"),
            CLUB.NAME.`as`("club_name"),
            PARTICIPANT.ID.`as`("participant_id"),
            PARTICIPANT.FIRSTNAME,
            PARTICIPANT.LASTNAME,
            PARTICIPANT.YEAR,
            PARTICIPANT.GENDER,
            PARTICIPANT.EXTERNAL_CLUB_NAME,
            COMPETITION_REGISTRATION_NAMED_PARTICIPANT.NAMED_PARTICIPANT.`as`("named_participant_id"),
            NAMED_PARTICIPANT.NAME.`as`("named_role"),
            EVENT.MIXED_TEAM_TERM,
            TIMECODE.TIME,
            TIMECODE.BASE_UNIT,
            TIMECODE.MILLISECOND_PRECISION,
        )
            .from(COMPETITION_MATCH_TEAM)
            .join(COMPETITION_SETUP_MATCH)
            .on(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .join(COMPETITION_REGISTRATION)
            .on(COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID))
            .leftJoin(CLUB).on(CLUB.ID.eq(COMPETITION_REGISTRATION.CLUB))
            .leftJoin(COMPETITION_REGISTRATION_NAMED_PARTICIPANT)
            .on(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID))
            .leftJoin(PARTICIPANT).on(PARTICIPANT.ID.eq(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.PARTICIPANT))
            .leftJoin(NAMED_PARTICIPANT)
            .on(NAMED_PARTICIPANT.ID.eq(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.NAMED_PARTICIPANT))
            .leftJoin(COMPETITION_DEREGISTRATION)
            .on(
                COMPETITION_DEREGISTRATION.COMPETITION_REGISTRATION.eq(COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION)
                    .and(COMPETITION_DEREGISTRATION.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND))
            )
            .leftJoin(EVENT_REGISTRATION).on(EVENT_REGISTRATION.ID.eq(COMPETITION_REGISTRATION.EVENT_REGISTRATION))
            .leftJoin(EVENT).on(EVENT_REGISTRATION.EVENT.eq(EVENT.ID))
            .leftJoin(TIMECODE).on(COMPETITION_MATCH_TEAM.TIMECODE.eq(TIMECODE.ID))
            .where(COMPETITION.EVENT.eq(eventId))
            .and(COMPETITION_MATCH_TEAM.OUT.isTrue.not())
            .fetch()
    }

    fun getEventRequirements(eventId: UUID) = Jooq.query {
        select(
            PARTICIPANT_REQUIREMENT.ID,
            PARTICIPANT_REQUIREMENT.NAME,
            PARTICIPANT_REQUIREMENT.DESCRIPTION,
            PARTICIPANT_REQUIREMENT.OPTIONAL,
            PARTICIPANT_REQUIREMENT.CHECK_EARLIEST_MINUTES_BEFORE,
            PARTICIPANT_REQUIREMENT.CHECK_LATEST_MINUTES_BEFORE,
            EVENT_HAS_PARTICIPANT_REQUIREMENT.NAMED_PARTICIPANT,
        )
            .from(EVENT_HAS_PARTICIPANT_REQUIREMENT)
            .join(PARTICIPANT_REQUIREMENT)
            .on(EVENT_HAS_PARTICIPANT_REQUIREMENT.PARTICIPANT_REQUIREMENT.eq(PARTICIPANT_REQUIREMENT.ID))
            .where(EVENT_HAS_PARTICIPANT_REQUIREMENT.EVENT.eq(eventId))
            .fetch()
    }

    fun getChecks(eventId: UUID) = Jooq.query {
        with(PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT) {
            select(PARTICIPANT, PARTICIPANT_REQUIREMENT, CREATED_AT, NOTE)
                .from(this)
                .where(EVENT.eq(eventId))
                .fetch()
        }
    }

    fun getInvoicePaymentsByClub(eventId: UUID) = Jooq.query {
        with(INVOICE_FOR_EVENT_REGISTRATION) {
            select(CLUB, PAID_AT)
                .from(this)
                .where(EVENT.eq(eventId))
                .fetch()
        }
    }
}
```

- [ ] **Step 2: Write the error entity**

`entity/LiveDashboardError.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.liveDashboard.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*
import java.util.UUID

sealed interface LiveDashboardError : ServiceError {
    data class EventNotFound(val eventId: UUID) : LiveDashboardError

    override fun respond(): ApiError = when (this) {
        is EventNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Event with id $eventId not found"
        )
    }
}
```

- [ ] **Step 3: Write the service**

`boundary/LiveDashboardService.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.liveDashboard.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.liveDashboard.control.LiveDashboardRepo
import de.lambda9.ready2race.backend.app.liveDashboard.entity.*
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.data.Timecode
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.ready2race.backend.singletonOrFallback
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

object LiveDashboardService {

    fun getLiveDashboard(eventId: UUID): App<LiveDashboardError, ApiResponse.Dto<LiveDashboardDto>> =
        KIO.comprehension {
            val exists = !EventRepo.exists(eventId).orDie()
            if (!exists) {
                return@comprehension KIO.fail(LiveDashboardError.EventNotFound(eventId))
            }

            val matchRecords = !LiveDashboardRepo.getMatches(eventId).orDie()
            val teamRecords = !LiveDashboardRepo.getTeams(eventId).orDie()
            val requirementRecords = !LiveDashboardRepo.getEventRequirements(eventId).orDie()
            val checkRecords = !LiveDashboardRepo.getChecks(eventId).orDie()
            val invoiceRecords = !LiveDashboardRepo.getInvoicePaymentsByClub(eventId).orDie()

            // requirement id -> assigned named participants (null element = global assignment)
            val requirementAssignments = requirementRecords.groupBy(
                { it[PARTICIPANT_REQUIREMENT.ID]!! },
                { it[EVENT_HAS_PARTICIPANT_REQUIREMENT.NAMED_PARTICIPANT] },
            )
            val requirementInfos = requirementRecords.distinctBy { it[PARTICIPANT_REQUIREMENT.ID] }

            val checksByKey = checkRecords.associateBy {
                it[PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.PARTICIPANT]!! to
                    it[PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.PARTICIPANT_REQUIREMENT]!!
            }

            val paidAtsByClub = invoiceRecords.groupBy(
                { it[INVOICE_FOR_EVENT_REGISTRATION.CLUB] },
                { it[INVOICE_FOR_EVENT_REGISTRATION.PAID_AT] },
            )

            val teamsByMatch = teamRecords.groupBy { it.get("match_id", UUID::class.java)!! }
            val now = LocalDateTime.now()

            val matches = matchRecords.map { match ->
                val matchId = match[COMPETITION_MATCH.COMPETITION_SETUP_MATCH]!!
                val startTime = match[COMPETITION_MATCH.START_TIME]
                val running = match[COMPETITION_MATCH.CURRENTLY_RUNNING] == true

                val teams = (teamsByMatch[matchId] ?: emptyList())
                    .groupBy { it[COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION]!! }
                    .map { (registrationId, rows) ->
                        val first = rows.first()
                        val clubId = first.get("club_id", UUID::class.java)

                        val participants = rows.mapNotNull { row ->
                            row.get("participant_id", UUID::class.java)?.let { participantId ->
                                val namedParticipantId = row.get("named_participant_id", UUID::class.java)
                                val requirements = requirementInfos
                                    .filter { req ->
                                        LiveDashboardLogic.requirementApplies(
                                            requirementAssignments[req[PARTICIPANT_REQUIREMENT.ID]!!] ?: emptyList(),
                                            namedParticipantId,
                                        )
                                    }
                                    .map { req ->
                                        val check = checksByKey[participantId to req[PARTICIPANT_REQUIREMENT.ID]!!]
                                        LiveDashboardRequirementStatusDto(
                                            requirementId = req[PARTICIPANT_REQUIREMENT.ID]!!,
                                            name = req[PARTICIPANT_REQUIREMENT.NAME]!!,
                                            description = req[PARTICIPANT_REQUIREMENT.DESCRIPTION],
                                            optional = req[PARTICIPANT_REQUIREMENT.OPTIONAL]!!,
                                            checked = check != null,
                                            checkedAt = check?.get(PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.CREATED_AT),
                                            note = check?.get(PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.NOTE),
                                            timeCheck = LiveDashboardLogic.computeTimeCheck(
                                                startTime = startTime,
                                                checkedAt = check?.get(PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.CREATED_AT),
                                                earliestMinutesBefore = req[PARTICIPANT_REQUIREMENT.CHECK_EARLIEST_MINUTES_BEFORE],
                                                latestMinutesBefore = req[PARTICIPANT_REQUIREMENT.CHECK_LATEST_MINUTES_BEFORE],
                                            ),
                                        )
                                    }

                                LiveDashboardParticipantDto(
                                    participantId = participantId,
                                    firstName = row[PARTICIPANT.FIRSTNAME] ?: "",
                                    lastName = row[PARTICIPANT.LASTNAME] ?: "",
                                    namedRole = row.get("named_role", String::class.java),
                                    year = row[PARTICIPANT.YEAR],
                                    gender = row[PARTICIPANT.GENDER]?.name,
                                    externalClubName = row[PARTICIPANT.EXTERNAL_CLUB_NAME],
                                    requirements = requirements,
                                )
                            }
                        }

                        LiveDashboardTeamDto(
                            teamId = registrationId,
                            teamName = first.get("team_name", String::class.java),
                            clubName = first.get("club_name", String::class.java),
                            actualClubName = singletonOrFallback(
                                rows.map { it[PARTICIPANT.EXTERNAL_CLUB_NAME] }.toSet(),
                                first[EVENT.MIXED_TEAM_TERM],
                            ),
                            startNumber = first[COMPETITION_MATCH_TEAM.START_NUMBER],
                            place = first[COMPETITION_MATCH_TEAM.PLACE],
                            time = first[TIMECODE.TIME]?.let {
                                Timecode(
                                    millis = it,
                                    baseUnit = Timecode.BaseUnit.valueOf(first[TIMECODE.BASE_UNIT]!!),
                                    millisecondPrecision = Timecode.MillisecondPrecision.valueOf(first[TIMECODE.MILLISECOND_PRECISION]!!),
                                ).toString()
                            },
                            failed = first[COMPETITION_MATCH_TEAM.FAILED] == true,
                            failedReason = first[COMPETITION_MATCH_TEAM.FAILED_REASON],
                            deregistered = first.get("deregistered", Boolean::class.java) == true,
                            deregisteredReason = first.get("deregistration_reason", String::class.java),
                            invoiceState = LiveDashboardLogic.deriveInvoiceState(
                                clubId?.let { paidAtsByClub[it] } ?: emptyList()
                            ),
                            participants = participants,
                        )
                    }
                    .sortedWith(compareBy(nullsLast()) { it.startNumber })

                LiveDashboardMatchDto(
                    matchId = matchId,
                    state = LiveDashboardLogic.deriveMatchState(running, startTime, teams.map { it.place }),
                    competitionId = match.get("competition_id", UUID::class.java)!!,
                    competitionName = match.get("competition_name", String::class.java) ?: "",
                    categoryName = match[COMPETITION_VIEW.CATEGORY_NAME],
                    roundName = match.get("round_name", String::class.java),
                    matchName = match.get("match_name", String::class.java),
                    executionOrder = match[COMPETITION_SETUP_MATCH.EXECUTION_ORDER] ?: 0,
                    startTime = startTime,
                    currentlyRunning = running,
                    elapsedMinutes = if (running) startTime?.let { Duration.between(it, now).toMinutes() } else null,
                    teams = teams,
                )
            }

            KIO.ok(ApiResponse.Dto(LiveDashboardDto(matches)))
        }
}
```

- [ ] **Step 4: Write the route + register it**

`boundary/liveDashboard.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.liveDashboard.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.liveDashboard() {
    route("/event/{eventId}/liveDashboard") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadLiveDashboardGlobal)
                val eventId = !pathParam("eventId", uuid)

                LiveDashboardService.getLiveDashboard(eventId)
            }
        }
    }
}
```

In `plugins/Routing.kt`: add import `de.lambda9.ready2race.backend.app.liveDashboard.boundary.liveDashboard` and call `liveDashboard()` directly after `eventInfo()`.

- [ ] **Step 5: Compile + run all backend tests**

Run: `cd backend && ./mvnw test -q`
Expected: BUILD SUCCESS. Field-name gotchas to check on compile errors: jOOQ Kotlin properties for the aliased/view tables (e.g. `INVOICE_FOR_EVENT_REGISTRATION.CLUB`/`.PAID_AT`, `EVENT_HAS_PARTICIPANT_REQUIREMENT.NAMED_PARTICIPANT`) — confirm exact names in `backend/target/generated-sources/jooq` if unsure.

- [ ] **Step 6: Add OpenAPI path + schemas**

In `documentation.yaml`, after the `/event/{eventId}/info/running-matches` path block, add:

```yaml
  /event/{eventId}/liveDashboard:
    parameters:
      - $ref: '#/components/parameters/eventId'
    get:
      operationId: getLiveDashboard
      responses:
        200:
          description: Live dashboard data retrieved successfully
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/LiveDashboardDto'
        401:
          $ref: '#/components/responses/401'
        404:
          $ref: '#/components/responses/404'
        500:
          $ref: '#/components/responses/500'
```

(Check that `#/components/responses/401` exists — other authenticated endpoints reference it; if the convention differs, mirror whatever `GET /event/{eventId}/matches` uses.)

In `components/schemas`, next to `RunningMatchInfo`, add:

```yaml
    LiveDashboardMatchState:
      type: string
      enum:
        - RUNNING
        - FINISHED
        - UPCOMING
        - UNSCHEDULED

    LiveDashboardInvoiceState:
      type: string
      enum:
        - PAID
        - OPEN
        - NONE

    TimeCheckStatus:
      type: string
      enum:
        - OK
        - TOO_EARLY
        - LATE
        - NOT_CHECKED

    TimeCheckDto:
      type: object
      required:
        - status
      properties:
        deltaMinutes:
          type: integer
          format: int64
          nullable: true
        status:
          $ref: '#/components/schemas/TimeCheckStatus'

    LiveDashboardRequirementStatusDto:
      type: object
      required:
        - requirementId
        - name
        - optional
        - checked
      properties:
        requirementId:
          type: string
          format: uuid
        name:
          type: string
        description:
          type: string
          nullable: true
        optional:
          type: boolean
        checked:
          type: boolean
        checkedAt:
          type: string
          format: date-time
          nullable: true
        note:
          type: string
          nullable: true
        timeCheck:
          nullable: true
          allOf:
            - $ref: '#/components/schemas/TimeCheckDto'

    LiveDashboardParticipantDto:
      type: object
      required:
        - participantId
        - firstName
        - lastName
        - requirements
      properties:
        participantId:
          type: string
          format: uuid
        firstName:
          type: string
        lastName:
          type: string
        namedRole:
          type: string
          nullable: true
        year:
          type: integer
          nullable: true
        gender:
          type: string
          nullable: true
        externalClubName:
          type: string
          nullable: true
        requirements:
          type: array
          items:
            $ref: '#/components/schemas/LiveDashboardRequirementStatusDto'

    LiveDashboardTeamDto:
      type: object
      required:
        - teamId
        - failed
        - deregistered
        - invoiceState
        - participants
      properties:
        teamId:
          type: string
          format: uuid
        teamName:
          type: string
          nullable: true
        clubName:
          type: string
          nullable: true
        actualClubName:
          type: string
          nullable: true
        startNumber:
          type: integer
          nullable: true
        place:
          type: integer
          nullable: true
        time:
          type: string
          nullable: true
        failed:
          type: boolean
        failedReason:
          type: string
          nullable: true
        deregistered:
          type: boolean
        deregisteredReason:
          type: string
          nullable: true
        invoiceState:
          $ref: '#/components/schemas/LiveDashboardInvoiceState'
        participants:
          type: array
          items:
            $ref: '#/components/schemas/LiveDashboardParticipantDto'

    LiveDashboardMatchDto:
      type: object
      required:
        - matchId
        - state
        - competitionId
        - competitionName
        - executionOrder
        - currentlyRunning
        - teams
      properties:
        matchId:
          type: string
          format: uuid
        state:
          $ref: '#/components/schemas/LiveDashboardMatchState'
        competitionId:
          type: string
          format: uuid
        competitionName:
          type: string
        categoryName:
          type: string
          nullable: true
        roundName:
          type: string
          nullable: true
        matchName:
          type: string
          nullable: true
        executionOrder:
          type: integer
        startTime:
          type: string
          format: date-time
          nullable: true
        currentlyRunning:
          type: boolean
        elapsedMinutes:
          type: integer
          format: int64
          nullable: true
        teams:
          type: array
          items:
            $ref: '#/components/schemas/LiveDashboardTeamDto'

    LiveDashboardDto:
      type: object
      required:
        - matches
      properties:
        matches:
          type: array
          items:
            $ref: '#/components/schemas/LiveDashboardMatchDto'
```

- [ ] **Step 7: Regenerate frontend client and verify**

Run:
```bash
cd frontend && npm run generate && grep -n "getLiveDashboard\|LiveDashboardDto\|LIVE_DASHBOARD" src/api/sdk.gen.ts src/api/types.gen.ts | head
```
Expected: `getLiveDashboard` in sdk, `LiveDashboardDto` types and `LIVE_DASHBOARD` in the `Resource` union in types.

- [ ] **Step 8: Commit**

```bash
git add backend/src frontend/src/api backend/src/main/resources/openapi/documentation.yaml
git commit -m "Add aggregated live dashboard endpoint"
```

---

### Task 6: Frontend wiring — privilege, route, EventPage section, base i18n

**Files:**
- Modify: `frontend/src/authorization/privileges.ts`
- Modify: `frontend/src/routes.tsx`
- Modify: `frontend/src/pages/event/EventPage.tsx` (after the "Öffentliche Informationsanzeige" card, ~line 398)
- Create: `frontend/src/pages/event/LiveDashboardPage.tsx` (placeholder; full UI in Task 7)
- Modify: `frontend/src/i18n/de/translations.json`, `frontend/src/i18n/en/translations.json`, `frontend/src/i18n/da/translations.json`

**Interfaces:**
- Consumes: `LIVE_DASHBOARD` in the generated `Resource` type (Task 5 Step 7).
- Produces: `readLiveDashboardGlobal` privilege const; route `eventLiveDashboardRoute` (path `liveDashboard` under `eventRoute`, i.e. `/event/$eventId/liveDashboard`); i18n namespace `event.liveDashboard.*`. Task 7 replaces the placeholder page component.

- [ ] **Step 1: Add privilege constant**

In `privileges.ts`, after the `readResultOwn` block:

```typescript
export const readLiveDashboardGlobal: Privilege = {
    action: 'READ',
    resource: 'LIVE_DASHBOARD',
    scope: 'GLOBAL',
}
```

- [ ] **Step 2: Placeholder page**

`frontend/src/pages/event/LiveDashboardPage.tsx`:

```tsx
const LiveDashboardPage = () => {
    return null
}

export default LiveDashboardPage
```

- [ ] **Step 3: Add route**

In `routes.tsx`: import `LiveDashboardPage` and `readLiveDashboardGlobal`; after `eventInfoRoute` add:

```tsx
export const eventLiveDashboardRoute = createRoute({
    getParentRoute: () => eventRoute,
    path: 'liveDashboard',
    component: () => <LiveDashboardPage />,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location, readLiveDashboardGlobal)
    },
})
```

Register it in the route tree next to `eventInfoRoute` (search for `eventInfoRoute,` in the `addChildren` array, ~line 497, and add `eventLiveDashboardRoute,`).

- [ ] **Step 4: EventPage section**

In `EventPage.tsx`, directly after the closing `)}` of the `event.info.sectionTitle` card (~line 398), add (import `readLiveDashboardGlobal` from `@authorization/privileges.ts` — match the existing import path style used for `readEventGlobal` — and `SportsScoreOutlined` from `@mui/icons-material/SportsScoreOutlined`):

```tsx
{user.checkPrivilege(readLiveDashboardGlobal) && !data.challengeEvent && (
    <Card sx={{p: 2}}>
        <Typography variant="h6" sx={{mb: 1}}>
            {t('event.liveDashboard.sectionTitle')}
        </Typography>
        <Typography
            variant="body2"
            color="text.secondary"
            sx={{mb: 2}}>
            {t('event.liveDashboard.pageDescription')}
        </Typography>
        <Link to={'/event/$eventId/liveDashboard'} params={{eventId}}>
            <Button
                startIcon={<SportsScoreOutlined/>}
                variant="outlined"
                fullWidth>
                {t('event.liveDashboard.open')}
            </Button>
        </Link>
    </Card>
)}
```

- [ ] **Step 5: Base i18n keys**

In all three translation files, inside the `event` object next to the `info` block, add a `liveDashboard` block.

`de/translations.json`:

```json
"liveDashboard": {
  "sectionTitle": "Schiedsrichter-Dashboard",
  "pageDescription": "Live-Ansicht für Schiedsrichter mit laufenden und anstehenden Läufen, Teilnahmebedingungen und Rechnungsstatus.",
  "open": "Schiedsrichter-Dashboard öffnen",
  "title": "Schiedsrichter-Dashboard"
}
```

`en/translations.json`:

```json
"liveDashboard": {
  "sectionTitle": "Referee dashboard",
  "pageDescription": "Live view for referees with running and upcoming matches, participant requirements, and invoice status.",
  "open": "Open referee dashboard",
  "title": "Referee dashboard"
}
```

`da/translations.json`:

```json
"liveDashboard": {
  "sectionTitle": "Dommer-dashboard",
  "pageDescription": "Live-visning for dommere med igangværende og kommende løb, deltagerkrav og fakturastatus.",
  "open": "Åbn dommer-dashboard",
  "title": "Dommer-dashboard"
}
```

- [ ] **Step 6: Build**

Run: `cd frontend && npm run build`
Expected: tsc + vite succeed with no errors.

- [ ] **Step 7: Commit**

```bash
git add frontend/src
git commit -m "Add live dashboard route, privilege, and event page entry"
```

---

### Task 7: LiveDashboardPage UI (tabs, polling, cards, team dialog)

**Files:**
- Create: `frontend/src/components/event/liveDashboard/common.ts`
- Create: `frontend/src/components/event/liveDashboard/LiveDashboardMatchCard.tsx`
- Create: `frontend/src/components/event/liveDashboard/LiveDashboardTeamDialog.tsx`
- Modify: `frontend/src/pages/event/LiveDashboardPage.tsx` (replace placeholder)
- Modify: i18n `de`/`en`/`da` (`event.liveDashboard.*` additions)

**Interfaces:**
- Consumes: `getLiveDashboard` (sdk), types `LiveDashboardDto`, `LiveDashboardMatchDto`, `LiveDashboardTeamDto`, `LiveDashboardParticipantDto`, `LiveDashboardRequirementStatusDto` from `@api/types.gen.ts`; `useFetch` from `@utils/hooks.ts`; `format` from `date-fns`; route `eventLiveDashboardRoute`.
- Produces: the complete referee dashboard page.

- [ ] **Step 1: Severity helpers**

`common.ts`:

```typescript
import {
    LiveDashboardParticipantDto,
    LiveDashboardRequirementStatusDto,
    LiveDashboardTeamDto,
} from '@api/types.gen.ts'

export type Severity = 'ok' | 'warning' | 'error' | 'neutral'

const rank: Record<Severity, number> = {neutral: 0, ok: 1, warning: 2, error: 3}

export const worstSeverity = (severities: Severity[]): Severity =>
    severities.reduce<Severity>((acc, s) => (rank[s] > rank[acc] ? s : acc), 'neutral')

export const requirementSeverity = (r: LiveDashboardRequirementStatusDto): Severity => {
    if (!r.checked) {
        return r.optional ? 'neutral' : 'error'
    }
    if (r.timeCheck && (r.timeCheck.status === 'LATE' || r.timeCheck.status === 'TOO_EARLY')) {
        return 'warning'
    }
    return 'ok'
}

export const participantSeverity = (p: LiveDashboardParticipantDto): Severity =>
    worstSeverity(p.requirements.map(requirementSeverity))

export const teamSeverity = (team: LiveDashboardTeamDto): Severity =>
    worstSeverity([
        ...team.participants.map(participantSeverity),
        team.invoiceState === 'OPEN' ? 'error' : 'neutral',
    ])

export const severityChipColor: Record<Severity, 'success' | 'warning' | 'error' | 'default'> = {
    ok: 'success',
    warning: 'warning',
    error: 'error',
    neutral: 'default',
}

export const formatMinutes = (totalMinutes: number): string => {
    const abs = Math.abs(totalMinutes)
    const h = Math.floor(abs / 60)
    const m = abs % 60
    return h > 0 ? `${h} h ${m} min` : `${m} min`
}
```

- [ ] **Step 2: Team dialog**

`LiveDashboardTeamDialog.tsx` — shows one team's full check details:

```tsx
import {
    Box,
    Chip,
    Dialog,
    DialogContent,
    DialogTitle,
    Divider,
    IconButton,
    List,
    ListItem,
    ListItemIcon,
    ListItemText,
    Stack,
    Typography,
} from '@mui/material'
import CloseIcon from '@mui/icons-material/Close'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import CancelIcon from '@mui/icons-material/Cancel'
import WarningAmberIcon from '@mui/icons-material/WarningAmber'
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked'
import {useTranslation} from 'react-i18next'
import {format} from 'date-fns'
import {LiveDashboardRequirementStatusDto, LiveDashboardTeamDto} from '@api/types.gen.ts'
import {formatMinutes, requirementSeverity, severityChipColor, Severity} from './common.ts'

type Props = {
    team: LiveDashboardTeamDto | null
    onClose: () => void
}

const severityIcon = (severity: Severity) => {
    switch (severity) {
        case 'ok':
            return <CheckCircleIcon color="success" />
        case 'warning':
            return <WarningAmberIcon color="warning" />
        case 'error':
            return <CancelIcon color="error" />
        case 'neutral':
            return <RadioButtonUncheckedIcon color="disabled" />
    }
}

const LiveDashboardTeamDialog = ({team, onClose}: Props) => {
    const {t} = useTranslation()

    if (team === null) {
        return null
    }

    const requirementSecondary = (r: LiveDashboardRequirementStatusDto): string => {
        const parts: string[] = []
        if (r.checked && r.checkedAt) {
            parts.push(
                t('event.liveDashboard.requirement.checkedAt', {
                    time: format(new Date(r.checkedAt), t('format.datetime')),
                }),
            )
        } else if (!r.checked) {
            parts.push(
                r.optional
                    ? t('event.liveDashboard.requirement.notCheckedOptional')
                    : t('event.liveDashboard.requirement.notChecked'),
            )
        }
        if (r.timeCheck?.deltaMinutes != null) {
            parts.push(
                r.timeCheck.deltaMinutes >= 0
                    ? t('event.liveDashboard.timeCheck.beforeStart', {
                          delta: formatMinutes(r.timeCheck.deltaMinutes),
                      })
                    : t('event.liveDashboard.timeCheck.afterStart', {
                          delta: formatMinutes(r.timeCheck.deltaMinutes),
                      }),
            )
        }
        if (r.note) {
            parts.push(t('event.liveDashboard.requirement.note', {note: r.note}))
        }
        return parts.join(' · ')
    }

    return (
        <Dialog open onClose={onClose} fullWidth maxWidth="sm">
            <DialogTitle sx={{pr: 6}}>
                {team.startNumber != null && `#${team.startNumber} — `}
                {team.teamName ?? team.clubName ?? ''}
                <IconButton onClick={onClose} sx={{position: 'absolute', right: 8, top: 8}}>
                    <CloseIcon />
                </IconButton>
            </DialogTitle>
            <DialogContent>
                <Stack spacing={2}>
                    <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                        {team.clubName && <Typography variant="body2">{team.clubName}</Typography>}
                        <Chip
                            size="small"
                            label={t(`event.liveDashboard.invoice.${team.invoiceState}`)}
                            color={
                                team.invoiceState === 'PAID'
                                    ? 'success'
                                    : team.invoiceState === 'OPEN'
                                      ? 'error'
                                      : 'default'
                            }
                        />
                        {team.deregistered && (
                            <Chip
                                size="small"
                                color="warning"
                                label={t('event.liveDashboard.team.deregistered')}
                            />
                        )}
                    </Stack>
                    {team.participants.map(p => (
                        <Box key={p.participantId}>
                            <Typography variant="subtitle1">
                                {p.firstName} {p.lastName}
                                {p.namedRole && (
                                    <Typography component="span" variant="body2" color="text.secondary">
                                        {' '}
                                        ({p.namedRole})
                                    </Typography>
                                )}
                            </Typography>
                            {p.requirements.length === 0 ? (
                                <Typography variant="body2" color="text.secondary">
                                    {t('event.liveDashboard.requirement.none')}
                                </Typography>
                            ) : (
                                <List dense disablePadding>
                                    {p.requirements.map(r => {
                                        const severity = requirementSeverity(r)
                                        return (
                                            <ListItem key={r.requirementId} disableGutters>
                                                <ListItemIcon sx={{minWidth: 36}}>
                                                    {severityIcon(severity)}
                                                </ListItemIcon>
                                                <ListItemText
                                                    primary={
                                                        <Stack
                                                            direction="row"
                                                            spacing={1}
                                                            alignItems="center">
                                                            <span>{r.name}</span>
                                                            {r.timeCheck &&
                                                                r.timeCheck.status !== 'OK' && (
                                                                    <Chip
                                                                        size="small"
                                                                        color={
                                                                            severityChipColor[severity]
                                                                        }
                                                                        label={t(
                                                                            `event.liveDashboard.timeCheck.${r.timeCheck.status}`,
                                                                        )}
                                                                    />
                                                                )}
                                                        </Stack>
                                                    }
                                                    secondary={requirementSecondary(r)}
                                                />
                                            </ListItem>
                                        )
                                    })}
                                </List>
                            )}
                            <Divider sx={{mt: 1}} />
                        </Box>
                    ))}
                </Stack>
            </DialogContent>
        </Dialog>
    )
}

export default LiveDashboardTeamDialog
```

- [ ] **Step 3: Match card**

`LiveDashboardMatchCard.tsx`:

```tsx
import {Box, Card, CardContent, Chip, List, ListItemButton, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {format} from 'date-fns'
import {LiveDashboardMatchDto, LiveDashboardTeamDto} from '@api/types.gen.ts'
import {formatMinutes, participantSeverity, severityChipColor, teamSeverity} from './common.ts'

type Props = {
    match: LiveDashboardMatchDto
    onTeamClick: (team: LiveDashboardTeamDto) => void
}

const LiveDashboardMatchCard = ({match, onTeamClick}: Props) => {
    const {t} = useTranslation()

    return (
        <Card
            sx={{
                borderColor: match.state === 'RUNNING' ? 'success.main' : undefined,
                borderWidth: match.state === 'RUNNING' ? 2 : undefined,
                borderStyle: match.state === 'RUNNING' ? 'solid' : undefined,
            }}>
            <CardContent sx={{p: 2, '&:last-child': {pb: 2}}}>
                <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={1}>
                    <Box>
                        <Typography variant="subtitle1" fontWeight={600}>
                            {match.competitionName}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                            {[match.categoryName, match.roundName, match.matchName]
                                .filter(Boolean)
                                .join(' · ')}
                        </Typography>
                    </Box>
                    <Stack alignItems="flex-end" spacing={0.5}>
                        <Chip
                            size="small"
                            label={t(`event.liveDashboard.state.${match.state}`)}
                            color={
                                match.state === 'RUNNING'
                                    ? 'success'
                                    : match.state === 'FINISHED'
                                      ? 'default'
                                      : match.state === 'UPCOMING'
                                        ? 'primary'
                                        : 'default'
                            }
                        />
                        {match.startTime ? (
                            <Typography variant="caption" color="text.secondary">
                                {format(new Date(match.startTime), t('format.datetime'))}
                            </Typography>
                        ) : (
                            <Typography variant="caption" color="text.secondary">
                                {t('event.liveDashboard.noStartTime')}
                            </Typography>
                        )}
                        {match.state === 'RUNNING' && match.elapsedMinutes != null && (
                            <Typography variant="caption" color="success.main">
                                {t('event.liveDashboard.runningSince', {
                                    duration: formatMinutes(match.elapsedMinutes),
                                })}
                            </Typography>
                        )}
                    </Stack>
                </Stack>
                <List dense disablePadding sx={{mt: 1}}>
                    {match.teams.map(team => {
                        const severity = teamSeverity(team)
                        const checkedCount = team.participants
                            .flatMap(p => p.requirements)
                            .filter(r => r.checked).length
                        const totalCount = team.participants.flatMap(p => p.requirements).length
                        return (
                            <ListItemButton
                                key={team.teamId}
                                onClick={() => onTeamClick(team)}
                                sx={{px: 1, borderRadius: 1}}>
                                <Stack
                                    direction="row"
                                    spacing={1}
                                    alignItems="center"
                                    width="100%"
                                    justifyContent="space-between">
                                    <Stack direction="row" spacing={1} alignItems="center" minWidth={0}>
                                        {team.startNumber != null && (
                                            <Chip size="small" label={`#${team.startNumber}`} />
                                        )}
                                        <Box minWidth={0}>
                                            <Typography variant="body2" noWrap>
                                                {team.teamName ?? team.clubName ?? ''}
                                            </Typography>
                                            {team.teamName && team.clubName && (
                                                <Typography
                                                    variant="caption"
                                                    color="text.secondary"
                                                    noWrap
                                                    display="block">
                                                    {team.clubName}
                                                </Typography>
                                            )}
                                        </Box>
                                    </Stack>
                                    <Stack direction="row" spacing={0.5} alignItems="center">
                                        {team.place != null && (
                                            <Chip
                                                size="small"
                                                color="primary"
                                                label={t('event.liveDashboard.team.place', {
                                                    place: team.place,
                                                })}
                                            />
                                        )}
                                        {team.time && <Chip size="small" label={team.time} />}
                                        {team.invoiceState === 'OPEN' && (
                                            <Chip
                                                size="small"
                                                color="error"
                                                label={t('event.liveDashboard.invoice.OPEN')}
                                            />
                                        )}
                                        {totalCount > 0 && (
                                            <Chip
                                                size="small"
                                                color={severityChipColor[severity]}
                                                label={`${checkedCount}/${totalCount}`}
                                            />
                                        )}
                                    </Stack>
                                </Stack>
                            </ListItemButton>
                        )
                    })}
                </List>
            </CardContent>
        </Card>
    )
}

export default LiveDashboardMatchCard
```

Note: `participantSeverity` import is unused in this snippet — drop the import if the linter complains.

- [ ] **Step 4: The page**

`LiveDashboardPage.tsx` (replace placeholder):

```tsx
import {useRef, useState} from 'react'
import {
    Alert,
    Badge,
    BottomNavigation,
    BottomNavigationAction,
    Box,
    Paper,
    Stack,
    Typography,
} from '@mui/material'
import LiveTvIcon from '@mui/icons-material/LiveTv'
import FormatListNumberedIcon from '@mui/icons-material/FormatListNumbered'
import {useTranslation} from 'react-i18next'
import {format} from 'date-fns'
import {getLiveDashboard} from '@api/sdk.gen.ts'
import {LiveDashboardDto, LiveDashboardTeamDto} from '@api/types.gen.ts'
import {useFetch} from '@utils/hooks.ts'
import {eventLiveDashboardRoute} from '@routes'
import LiveDashboardMatchCard from '@components/event/liveDashboard/LiveDashboardMatchCard.tsx'
import LiveDashboardTeamDialog from '@components/event/liveDashboard/LiveDashboardTeamDialog.tsx'

const POLL_INTERVAL_MS = 10_000

const LiveDashboardPage = () => {
    const {t} = useTranslation()
    const {eventId} = eventLiveDashboardRoute.useParams()

    const [tab, setTab] = useState<'live' | 'matches'>('live')
    const [dashboard, setDashboard] = useState<LiveDashboardDto | null>(null)
    const [lastUpdated, setLastUpdated] = useState<Date | null>(null)
    const [stale, setStale] = useState(false)
    const [liveChanged, setLiveChanged] = useState(false)
    const [selectedTeam, setSelectedTeam] = useState<LiveDashboardTeamDto | null>(null)
    const runningIdsRef = useRef<string | null>(null)

    useFetch(signal => getLiveDashboard({signal, path: {eventId}}), {
        autoReloadInterval: POLL_INTERVAL_MS,
        deps: [eventId],
        onResponse: ({data}) => {
            if (data !== undefined) {
                setDashboard(data)
                setLastUpdated(new Date())
                setStale(false)
                const ids = data.matches
                    .filter(m => m.state === 'RUNNING')
                    .map(m => m.matchId)
                    .join(',')
                if (runningIdsRef.current !== null && ids !== runningIdsRef.current) {
                    setLiveChanged(true)
                }
                runningIdsRef.current = ids
            } else {
                setStale(true)
            }
        },
    })

    const runningMatches = dashboard?.matches.filter(m => m.state === 'RUNNING') ?? []
    const nextUpcoming = dashboard?.matches.find(m => m.state === 'UPCOMING')
    const scheduledMatches = dashboard?.matches.filter(m => m.state !== 'UNSCHEDULED') ?? []
    const unscheduledMatches = dashboard?.matches.filter(m => m.state === 'UNSCHEDULED') ?? []

    return (
        <Box sx={{pb: 9, maxWidth: 700, mx: 'auto'}}>
            <Stack spacing={2} sx={{p: 2}}>
                <Stack direction="row" justifyContent="space-between" alignItems="center">
                    <Typography variant="h6">{t('event.liveDashboard.title')}</Typography>
                    {lastUpdated && (
                        <Typography variant="caption" color="text.secondary">
                            {t('event.liveDashboard.lastUpdated', {
                                time: format(lastUpdated, t('format.time')),
                            })}
                        </Typography>
                    )}
                </Stack>
                {stale && dashboard && (
                    <Alert severity="warning">{t('event.liveDashboard.staleWarning')}</Alert>
                )}
                {stale && !dashboard && (
                    <Alert severity="error">{t('event.liveDashboard.loadError')}</Alert>
                )}
                {tab === 'live' && (
                    <>
                        {runningMatches.length === 0 && dashboard && (
                            <Alert severity="info">{t('event.liveDashboard.noRunning')}</Alert>
                        )}
                        {runningMatches.map(match => (
                            <LiveDashboardMatchCard
                                key={match.matchId}
                                match={match}
                                onTeamClick={setSelectedTeam}
                            />
                        ))}
                        {runningMatches.length === 0 && nextUpcoming && (
                            <>
                                <Typography variant="subtitle2" color="text.secondary">
                                    {t('event.liveDashboard.nextUp')}
                                </Typography>
                                <LiveDashboardMatchCard
                                    match={nextUpcoming}
                                    onTeamClick={setSelectedTeam}
                                />
                            </>
                        )}
                    </>
                )}
                {tab === 'matches' && (
                    <>
                        {scheduledMatches.map(match => (
                            <LiveDashboardMatchCard
                                key={match.matchId}
                                match={match}
                                onTeamClick={setSelectedTeam}
                            />
                        ))}
                        {unscheduledMatches.length > 0 && (
                            <>
                                <Typography variant="subtitle2" color="text.secondary">
                                    {t('event.liveDashboard.unscheduled')}
                                </Typography>
                                {unscheduledMatches.map(match => (
                                    <LiveDashboardMatchCard
                                        key={match.matchId}
                                        match={match}
                                        onTeamClick={setSelectedTeam}
                                    />
                                ))}
                            </>
                        )}
                        {dashboard && dashboard.matches.length === 0 && (
                            <Alert severity="info">{t('event.liveDashboard.noMatches')}</Alert>
                        )}
                    </>
                )}
            </Stack>
            <LiveDashboardTeamDialog team={selectedTeam} onClose={() => setSelectedTeam(null)} />
            <Paper sx={{position: 'fixed', bottom: 0, left: 0, right: 0, zIndex: 10}} elevation={3}>
                <BottomNavigation
                    showLabels
                    value={tab}
                    onChange={(_, newTab: 'live' | 'matches') => {
                        setTab(newTab)
                        if (newTab === 'live') {
                            setLiveChanged(false)
                        }
                    }}>
                    <BottomNavigationAction
                        value="live"
                        label={t('event.liveDashboard.tabs.live')}
                        icon={
                            <Badge color="error" variant="dot" invisible={!liveChanged}>
                                <LiveTvIcon />
                            </Badge>
                        }
                    />
                    <BottomNavigationAction
                        value="matches"
                        label={t('event.liveDashboard.tabs.matches')}
                        icon={<FormatListNumberedIcon />}
                    />
                </BottomNavigation>
            </Paper>
        </Box>
    )
}

export default LiveDashboardPage
```

Check import conventions: `eventLiveDashboardRoute` import path — other pages import routes via relative path or `@routes` alias; match whatever `EventInfoPage.tsx` uses (e.g. `import {eventInfoRoute} from '../../routes.tsx'`). Also verify `t('format.time')` exists in translations (it's used elsewhere; if only `format.datetime` exists, use that).

- [ ] **Step 5: Full i18n keys**

Extend the `event.liveDashboard` block in all three languages:

`de`:

```json
"liveDashboard": {
  "sectionTitle": "Schiedsrichter-Dashboard",
  "pageDescription": "Live-Ansicht für Schiedsrichter mit laufenden und anstehenden Läufen, Teilnahmebedingungen und Rechnungsstatus.",
  "open": "Schiedsrichter-Dashboard öffnen",
  "title": "Schiedsrichter-Dashboard",
  "tabs": {
    "live": "Live",
    "matches": "Läufe"
  },
  "lastUpdated": "Stand: {{time}}",
  "staleWarning": "Verbindung gestört — Anzeige ist möglicherweise nicht aktuell.",
  "loadError": "Daten konnten nicht geladen werden.",
  "noRunning": "Aktuell läuft kein Rennen.",
  "nextUp": "Als Nächstes",
  "unscheduled": "Ohne Startzeit",
  "noMatches": "Keine Läufe vorhanden.",
  "noStartTime": "Keine Startzeit geplant",
  "runningSince": "Läuft seit {{duration}}",
  "state": {
    "RUNNING": "Läuft",
    "FINISHED": "Beendet",
    "UPCOMING": "Anstehend",
    "UNSCHEDULED": "Ungeplant"
  },
  "invoice": {
    "PAID": "Rechnung bezahlt",
    "OPEN": "Rechnung offen",
    "NONE": "Keine Rechnung"
  },
  "team": {
    "place": "Platz {{place}}",
    "deregistered": "Abgemeldet"
  },
  "requirement": {
    "checkedAt": "Erfüllt am {{time}}",
    "notChecked": "Nicht erfüllt",
    "notCheckedOptional": "Nicht erfüllt (optional)",
    "note": "Notiz: {{note}}",
    "none": "Keine Bedingungen zugewiesen"
  },
  "timeCheck": {
    "TOO_EARLY": "Zu früh erfüllt",
    "LATE": "Spät erfüllt",
    "NOT_CHECKED": "Ausstehend",
    "beforeStart": "{{delta}} vor Start",
    "afterStart": "{{delta}} nach Start"
  }
}
```

`en`:

```json
"liveDashboard": {
  "sectionTitle": "Referee dashboard",
  "pageDescription": "Live view for referees with running and upcoming matches, participant requirements, and invoice status.",
  "open": "Open referee dashboard",
  "title": "Referee dashboard",
  "tabs": {
    "live": "Live",
    "matches": "Matches"
  },
  "lastUpdated": "Updated: {{time}}",
  "staleWarning": "Connection lost — data may be outdated.",
  "loadError": "Could not load data.",
  "noRunning": "No match is currently running.",
  "nextUp": "Up next",
  "unscheduled": "Without start time",
  "noMatches": "No matches available.",
  "noStartTime": "No start time scheduled",
  "runningSince": "Running for {{duration}}",
  "state": {
    "RUNNING": "Running",
    "FINISHED": "Finished",
    "UPCOMING": "Upcoming",
    "UNSCHEDULED": "Unscheduled"
  },
  "invoice": {
    "PAID": "Invoice paid",
    "OPEN": "Invoice open",
    "NONE": "No invoice"
  },
  "team": {
    "place": "Place {{place}}",
    "deregistered": "Deregistered"
  },
  "requirement": {
    "checkedAt": "Fulfilled at {{time}}",
    "notChecked": "Not fulfilled",
    "notCheckedOptional": "Not fulfilled (optional)",
    "note": "Note: {{note}}",
    "none": "No requirements assigned"
  },
  "timeCheck": {
    "TOO_EARLY": "Fulfilled too early",
    "LATE": "Fulfilled late",
    "NOT_CHECKED": "Pending",
    "beforeStart": "{{delta}} before start",
    "afterStart": "{{delta}} after start"
  }
}
```

`da`:

```json
"liveDashboard": {
  "sectionTitle": "Dommer-dashboard",
  "pageDescription": "Live-visning for dommere med igangværende og kommende løb, deltagerkrav og fakturastatus.",
  "open": "Åbn dommer-dashboard",
  "title": "Dommer-dashboard",
  "tabs": {
    "live": "Live",
    "matches": "Løb"
  },
  "lastUpdated": "Opdateret: {{time}}",
  "staleWarning": "Forbindelsen er afbrudt — visningen er muligvis ikke aktuel.",
  "loadError": "Data kunne ikke indlæses.",
  "noRunning": "Der er ingen igangværende løb.",
  "nextUp": "Næste",
  "unscheduled": "Uden starttid",
  "noMatches": "Ingen løb tilgængelige.",
  "noStartTime": "Ingen starttid planlagt",
  "runningSince": "I gang i {{duration}}",
  "state": {
    "RUNNING": "I gang",
    "FINISHED": "Afsluttet",
    "UPCOMING": "Kommende",
    "UNSCHEDULED": "Ikke planlagt"
  },
  "invoice": {
    "PAID": "Faktura betalt",
    "OPEN": "Faktura åben",
    "NONE": "Ingen faktura"
  },
  "team": {
    "place": "Plads {{place}}",
    "deregistered": "Afmeldt"
  },
  "requirement": {
    "checkedAt": "Opfyldt {{time}}",
    "notChecked": "Ikke opfyldt",
    "notCheckedOptional": "Ikke opfyldt (valgfri)",
    "note": "Note: {{note}}",
    "none": "Ingen krav tildelt"
  },
  "timeCheck": {
    "TOO_EARLY": "Opfyldt for tidligt",
    "LATE": "Opfyldt sent",
    "NOT_CHECKED": "Afventer",
    "beforeStart": "{{delta}} før start",
    "afterStart": "{{delta}} efter start"
  }
}
```

- [ ] **Step 6: Build + lint**

Run: `cd frontend && npm run build && npm run lint`
Expected: no type or lint errors (fix unused imports if flagged).

- [ ] **Step 7: Commit**

```bash
git add frontend/src
git commit -m "Add referee live dashboard page with live and match list tabs"
```

---

### Task 8: Time-window fields in the requirement admin dialog

**Files:**
- Modify: `frontend/src/components/event/participantRequirement/ParticipantRequirementDialog.tsx`
- Modify: i18n `de`/`en`/`da` (`participantRequirement.*`)

**Interfaces:**
- Consumes: `checkEarliestMinutesBefore` / `checkLatestMinutesBefore` on `ParticipantRequirementDto` / `ParticipantRequirementUpsertDto` (generated in Task 5 Step 7 — regeneration already picked up Task 2's schema changes).

- [ ] **Step 1: Extend the dialog**

In `ParticipantRequirementDialog.tsx`: extend the form type, defaults, both mappers, and the form body. Number inputs come back as strings — map accordingly:

```tsx
import {FormInputNumber} from '@components/form/input/FormInputNumber.tsx'
```

(Match the actual export style — check whether `FormInputNumber` is a default or named export and import accordingly.)

```tsx
type ParticipantRequirementForm = {
    name: string
    description: string
    optional: boolean
    checkInApp: boolean
    checkEarliestMinutesBefore: string
    checkLatestMinutesBefore: string
}
```

Defaults: add `checkEarliestMinutesBefore: '', checkLatestMinutesBefore: ''`.

```tsx
function mapFormToRequest(formData: ParticipantRequirementForm): ParticipantRequirementUpsertDto {
    return {
        name: formData.name,
        description: takeIfNotEmpty(formData.description),
        optional: formData.optional,
        checkInApp: formData.checkInApp,
        checkEarliestMinutesBefore:
            formData.checkEarliestMinutesBefore !== ''
                ? Number(formData.checkEarliestMinutesBefore)
                : undefined,
        checkLatestMinutesBefore:
            formData.checkLatestMinutesBefore !== ''
                ? Number(formData.checkLatestMinutesBefore)
                : undefined,
    }
}

function mapDtoToForm(dto: ParticipantRequirementDto): ParticipantRequirementForm {
    return {
        name: dto.name,
        description: dto.description ?? '',
        optional: dto.optional,
        checkInApp: dto.checkInApp,
        checkEarliestMinutesBefore: dto.checkEarliestMinutesBefore?.toString() ?? '',
        checkLatestMinutesBefore: dto.checkLatestMinutesBefore?.toString() ?? '',
    }
}
```

Form body — after the `checkInApp` checkbox:

```tsx
<FormInputNumber
    name="checkEarliestMinutesBefore"
    label={t('participantRequirement.checkEarliestMinutesBefore')}
    min={1}
    integer
/>
<FormInputNumber
    name="checkLatestMinutesBefore"
    label={t('participantRequirement.checkLatestMinutesBefore')}
    min={1}
    integer
/>
```

- [ ] **Step 2: i18n keys**

Add to `participantRequirement` block:

`de`:
```json
"checkEarliestMinutesBefore": "Frühestens (Minuten vor Start)",
"checkLatestMinutesBefore": "Spätestens (Minuten vor Start)"
```
`en`:
```json
"checkEarliestMinutesBefore": "Earliest (minutes before start)",
"checkLatestMinutesBefore": "Latest (minutes before start)"
```
`da`:
```json
"checkEarliestMinutesBefore": "Tidligst (minutter før start)",
"checkLatestMinutesBefore": "Senest (minutter før start)"
```

- [ ] **Step 3: Build**

Run: `cd frontend && npm run build`
Expected: success.

- [ ] **Step 4: Commit**

```bash
git add frontend/src
git commit -m "Add check time window fields to participant requirement dialog"
```

---

### Task 9: End-to-end verification

**Files:** none (verification only; small fixes as discovered).

- [ ] **Step 1: Full backend test run**

Run: `cd backend && ./mvnw test -q`
Expected: BUILD SUCCESS, all tests green.

- [ ] **Step 2: Full frontend build + lint**

Run: `cd frontend && npm run build && npm run lint`
Expected: no errors.

- [ ] **Step 3: Manual verification against the running stack**

Start dev DB + backend + frontend (check the project README / `CLAUDE_v01.md` for the exact dev startup; DB: `cd backend && docker compose up -d db`, backend via IDE/`./mvnw`-run of the Ktor main, frontend: `cd frontend && npm run dev` → http://localhost:5123). Then verify with a test event:

1. Create/assign a role with the new `READ.LIVE_DASHBOARD.GLOBAL` privilege; log in as that user.
2. EventPage shows the "Schiedsrichter-Dashboard" card only with the privilege; `/event/<id>/liveDashboard` redirects to `/dashboard` without it.
3. Configure a requirement with window (e.g. 120/15), assign to event, check a participant, set a match start time, toggle "currently running" → Live tab shows the match, team dialog shows delta + traffic light incl. LATE/TOO_EARLY cases (vary the start time to provoke each status).
4. Invoice: produce/mark-paid an invoice for a club → team chip flips OPEN → (none shown when) PAID.
5. Matches tab: chronological order, finished matches show place/time, unscheduled matches at the end under "Ohne Startzeit".
6. Polling: change running state in a second browser session → dashboard updates within 10 s and the Live tab badge appears when on "Läufe". Kill the backend briefly → stale warning appears, last data stays visible.
7. Mobile viewport (375×812): no horizontal scrolling, bottom navigation reachable.

- [ ] **Step 4: Final review & commit any fixes**

```bash
git status && git log --oneline main..HEAD
```
Expected: clean tree, coherent commit series on `feature/live-dashboard-schiedsrichter`.
