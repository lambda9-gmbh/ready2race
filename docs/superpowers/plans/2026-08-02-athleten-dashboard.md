# Athleten-Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eine öffentliche, loginfreie Seite, die Athleten an Start und Ziel den laufenden Lauf, den nächsten Lauf und das letzte Ergebnis zeigt — auf einem fest montierten Bildschirm ebenso wie auf dem Telefon.

**Architecture:** Ein neuer öffentlicher Endpoint `GET /event/{eventId}/info/athlete-board` löst seine Konfiguration serverseitig aus der bestehenden Tabelle `info_view_configuration` auf (neuer Enum-Wert `ATHLETE_BOARD`, Limits im vorhandenen `filters`-JSONB) und liefert alle drei Blöcke in einer konsistenten, schlanken Antwort. Im Frontend rendert eine Komponente daraus drei Bereiche; getragen wird sie von einer neuen öffentlichen Route und zusätzlich von der bestehenden Kiosk-Rotation.

**Tech Stack:** Kotlin / Ktor / jOOQ / Flyway / Maven (Backend), React 18 / TypeScript / MUI 6 / TanStack Router / hey-api (Frontend). Spec: `docs/superpowers/specs/2026-08-02-athleten-dashboard-design.md`.

## Global Constraints

- Deutsche Texte immer mit echten Umlauten (ä, ö, ü, ß) — nie ae/oe/ue/ss. Gilt auch für Kommentare und Übersetzungsdateien.
- Commits erwähnen weder Claude noch KI. Keine `Co-Authored-By`-Zeile.
- Der Endpoint `/event/{eventId}/info/athlete-board` ist **öffentlich**: kein `authenticate(...)`, keine Privilegienprüfung. Die drei bestehenden Info-Endpoints daneben sind das Vorbild.
- Generierter jOOQ-Code ist **nicht** eingecheckt (`backend/target/generated-sources/`). Nach jeder Migration muss `./mvnw generate-sources` laufen, damit neue Enum-Werte im Kotlin-Code sichtbar sind.
- Die OpenAPI-Datei `backend/src/main/resources/openapi/documentation.yaml` wird **von Hand** gepflegt (die `api/*.tsp`-Dateien sind Stümpfe und speisen sie nicht). Frontend-Typen entstehen daraus über `npm run generate`.
- Datenbank für Build und Tests: `cd backend && docker compose up -d`. Der Build nutzt `build-db` auf Port 7652.
- Defaults der Konfiguration, überall identisch: `running` 3, `upcoming` 3, `results` 1, `showCountdown` true, Aktualisierungstakt 15 Sekunden. Limits werden auf 1..20 begrenzt.
- Vor jedem Commit müssen die jeweils betroffenen Prüfungen grün sein: Backend `./mvnw test`, Frontend `npx tsc -b` und `npm run lint`.

---

## File Structure

**Backend — neu**

| Datei | Verantwortung |
| --- | --- |
| `backend/src/main/resources/db/migration/V202608021200__athlete_board_info_view.sql` | Enum-Wert `ATHLETE_BOARD` |
| `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/entity/AthleteBoardConfig.kt` | Aufgelöste Konfiguration (reines Datenobjekt) |
| `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/entity/AthleteBoardStartState.kt` | Zustand der Startzeit einer Lauf-Karte |
| `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/entity/AthleteBoardDto.kt` | Antwort-DTOs des Endpoints |
| `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/boundary/AthleteBoardLogic.kt` | Reine, testbare Logik: Konfiguration auflösen, Startzustand, Sortierung |
| `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/eventInfo/AthleteBoardLogicTest.kt` | Tests dazu |

**Backend — geändert**

| Datei | Änderung |
| --- | --- |
| `.../app/eventInfo/control/Conversions.kt` | Abbildungen der bestehenden Info-DTOs auf die Board-DTOs |
| `.../app/eventInfo/boundary/EventInfoService.kt` | `getAthleteBoard(eventId)` |
| `.../app/eventInfo/boundary/eventInfo.kt` | Route `GET /athlete-board` |
| `backend/src/main/resources/openapi/documentation.yaml` | Pfad + Schemata |

**Frontend — neu**

| Datei | Verantwortung |
| --- | --- |
| `frontend/src/components/event/info/athleteBoard/useAthleteBoardData.ts` | Laden, Takt, Pause im Hintergrund, letzter guter Stand |
| `frontend/src/components/event/info/athleteBoard/useServerClock.ts` | Sekundentakt, verankert an der Serverzeit |
| `frontend/src/components/event/info/athleteBoard/AthleteBoardMatchCard.tsx` | Eine Lauf-Karte |
| `frontend/src/components/event/info/athleteBoard/AthleteBoardResultCard.tsx` | Eine Ergebnis-Karte |
| `frontend/src/components/event/info/views/AthleteBoardView.tsx` | Die drei Blöcke, Kopfzeile, Leer- und Fehlerzustände |
| `frontend/src/pages/event/AthleteBoardPage.tsx` | Träger-Seite ohne Kiosk-Bedienelemente |

**Frontend — geändert**

| Datei | Änderung |
| --- | --- |
| `frontend/src/routes.tsx` | Route `/event/$eventId/board` |
| `frontend/src/components/event/info/InfoViewDisplay.tsx` | `case 'ATHLETE_BOARD'` |
| `frontend/src/components/event/info/ViewConfigurationForm.tsx` | Neuer Typ + drei Zahlenfelder + Häkchen |
| `frontend/src/i18n/{de,en,da}/translations.json` | Neue Schlüssel |
| `frontend/src/api/types.gen.ts`, `frontend/src/api/sdk.gen.ts` | Erzeugt, nicht von Hand bearbeitet |

---

## Task 1: Reine Logik — Konfiguration, Startzustand, Sortierung

Diese Aufgabe braucht keine Datenbank-Änderung und keinen generierten Code. Sie ist der testbare Kern.

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/entity/AthleteBoardConfig.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/entity/AthleteBoardStartState.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/boundary/AthleteBoardLogic.kt`
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/eventInfo/AthleteBoardLogicTest.kt`

**Interfaces:**
- Consumes: nichts.
- Produces:
  - `AthleteBoardConfig(runningLimit: Int, upcomingLimit: Int, resultsLimit: Int, showCountdown: Boolean, refreshIntervalSeconds: Int)`
  - `enum class AthleteBoardStartState { UNSCHEDULED, COUNTDOWN, SCHEDULED, OVERDUE }`
  - `AthleteBoardLogic.resolveConfig(filters: JsonNode?, displayDurationSeconds: Int?): AthleteBoardConfig`
  - `AthleteBoardLogic.startState(startTime: LocalDateTime?, now: LocalDateTime, showCountdown: Boolean): AthleteBoardStartState`
  - `AthleteBoardLogic.sortByStartTime(items: List<T>, startTime: (T) -> LocalDateTime?): List<T>`

- [ ] **Step 1: Datenbank starten (einmalig für alle Backend-Aufgaben)**

```bash
cd backend && docker compose up -d
```

Erwartet: Container `db` und `build-db` laufen. Ohne sie schlägt jeder Maven-Lauf in der Phase `generate-sources` fehl.

- [ ] **Step 2: Die beiden Datentypen anlegen**

`backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/entity/AthleteBoardConfig.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.eventInfo.entity

/**
 * Aufgelöste Konfiguration der Athleten-Anzeige. Entsteht aus einer Zeile in
 * `info_view_configuration` vom Typ ATHLETE_BOARD oder aus den Vorgabewerten,
 * wenn keine solche Zeile existiert.
 */
data class AthleteBoardConfig(
    val runningLimit: Int,
    val upcomingLimit: Int,
    val resultsLimit: Int,
    val showCountdown: Boolean,
    val refreshIntervalSeconds: Int,
)
```

`backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/entity/AthleteBoardStartState.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.eventInfo.entity

/**
 * Wie die Startzeit einer Lauf-Karte anzuzeigen ist.
 *
 * Bedeutung trägt der Zustand nur im Block `upcoming`. Im Block `running` wird er
 * nicht ausgewertet — dort steht ohnehin die Startzeit statt einer Restzeit.
 */
enum class AthleteBoardStartState {
    /** Keine Startzeit gepflegt — die Runde ist noch nicht gesetzt. */
    UNSCHEDULED,

    /** Startzeit liegt in der Zukunft, Countdown ist eingeschaltet. */
    COUNTDOWN,

    /** Startzeit liegt in der Zukunft, Countdown ist abgeschaltet. */
    SCHEDULED,

    /** Startzeit ist verstrichen, der Lauf ist aber noch nicht gestartet. */
    OVERDUE,
}
```

- [ ] **Step 3: Den Test schreiben**

`backend/src/test/kotlin/de/lambda9/ready2race/backend/app/eventInfo/AthleteBoardLogicTest.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.eventInfo

import com.fasterxml.jackson.databind.ObjectMapper
import de.lambda9.ready2race.backend.app.eventInfo.boundary.AthleteBoardLogic
import de.lambda9.ready2race.backend.app.eventInfo.entity.AthleteBoardStartState
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AthleteBoardLogicTest {

    private val mapper = ObjectMapper()
    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 2, 10, 0)

    private fun filters(json: String) = mapper.readTree(json)

    // --- resolveConfig ---

    @Test
    fun missingConfigurationYieldsDefaults() {
        val config = AthleteBoardLogic.resolveConfig(null, null)
        assertEquals(3, config.runningLimit)
        assertEquals(3, config.upcomingLimit)
        assertEquals(1, config.resultsLimit)
        assertTrue(config.showCountdown)
        assertEquals(15, config.refreshIntervalSeconds)
    }

    @Test
    fun fullConfigurationIsRead() {
        val config = AthleteBoardLogic.resolveConfig(
            filters("""{"running":5,"upcoming":4,"results":2,"showCountdown":false}"""),
            30,
        )
        assertEquals(5, config.runningLimit)
        assertEquals(4, config.upcomingLimit)
        assertEquals(2, config.resultsLimit)
        assertFalse(config.showCountdown)
        assertEquals(30, config.refreshIntervalSeconds)
    }

    @Test
    fun partialConfigurationKeepsDefaultsPerField() {
        val config = AthleteBoardLogic.resolveConfig(filters("""{"showCountdown":false}"""), null)
        assertEquals(3, config.runningLimit)
        assertEquals(3, config.upcomingLimit)
        assertEquals(1, config.resultsLimit)
        assertFalse(config.showCountdown)
        assertEquals(15, config.refreshIntervalSeconds)
    }

    @Test
    fun nonNumericLimitFallsBackToDefault() {
        val config = AthleteBoardLogic.resolveConfig(filters("""{"running":"viele"}"""), null)
        assertEquals(3, config.runningLimit)
    }

    @Test
    fun limitsAreClamped() {
        val config = AthleteBoardLogic.resolveConfig(
            filters("""{"running":500,"upcoming":0}"""),
            null,
        )
        assertEquals(20, config.runningLimit)
        assertEquals(1, config.upcomingLimit)
    }

    @Test
    fun nonPositiveDisplayDurationFallsBackToDefaultInterval() {
        val config = AthleteBoardLogic.resolveConfig(null, 0)
        assertEquals(15, config.refreshIntervalSeconds)
    }

    // --- startState ---

    @Test
    fun matchWithoutStartTimeIsUnscheduled() {
        assertEquals(
            AthleteBoardStartState.UNSCHEDULED,
            AthleteBoardLogic.startState(null, now, true),
        )
    }

    @Test
    fun futureStartWithCountdownEnabled() {
        assertEquals(
            AthleteBoardStartState.COUNTDOWN,
            AthleteBoardLogic.startState(now.plusMinutes(5), now, true),
        )
    }

    @Test
    fun futureStartWithCountdownDisabled() {
        assertEquals(
            AthleteBoardStartState.SCHEDULED,
            AthleteBoardLogic.startState(now.plusMinutes(5), now, false),
        )
    }

    @Test
    fun passedStartTimeIsOverdueInsteadOfNegativeCountdown() {
        assertEquals(
            AthleteBoardStartState.OVERDUE,
            AthleteBoardLogic.startState(now.minusMinutes(3), now, true),
        )
    }

    @Test
    fun passedStartTimeIsOverdueEvenWithoutCountdown() {
        assertEquals(
            AthleteBoardStartState.OVERDUE,
            AthleteBoardLogic.startState(now.minusMinutes(3), now, false),
        )
    }

    @Test
    fun startTimeExactlyNowIsOverdue() {
        assertEquals(
            AthleteBoardStartState.OVERDUE,
            AthleteBoardLogic.startState(now, now, true),
        )
    }

    // --- sortByStartTime ---

    @Test
    fun matchesWithoutStartTimeSortToTheEnd() {
        val input: List<Pair<String, LocalDateTime?>> = listOf(
            "ohne" to null,
            "spaet" to now.plusMinutes(30),
            "frueh" to now.plusMinutes(5),
        )
        val sorted = AthleteBoardLogic.sortByStartTime(input) { it.second }
        assertEquals(listOf("frueh", "spaet", "ohne"), sorted.map { it.first })
    }

    @Test
    fun sortingIsStableForEqualStartTimes() {
        val same = now.plusMinutes(10)
        val input: List<Pair<String, LocalDateTime?>> = listOf(
            "a" to same,
            "b" to same,
            "c" to null,
        )
        val sorted = AthleteBoardLogic.sortByStartTime(input) { it.second }
        assertEquals(listOf("a", "b", "c"), sorted.map { it.first })
    }
}
```

- [ ] **Step 4: Test laufen lassen und Fehlschlag bestätigen**

```bash
cd backend && ./mvnw test -Dtest=AthleteBoardLogicTest
```

Erwartet: FEHLSCHLAG beim Kompilieren — `Unresolved reference: AthleteBoardLogic`.

- [ ] **Step 5: Die Logik schreiben**

`backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/boundary/AthleteBoardLogic.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.eventInfo.boundary

import com.fasterxml.jackson.databind.JsonNode
import de.lambda9.ready2race.backend.app.eventInfo.entity.AthleteBoardConfig
import de.lambda9.ready2race.backend.app.eventInfo.entity.AthleteBoardStartState
import java.time.LocalDateTime

/**
 * Reine Logik der Athleten-Anzeige, bewusst ohne Datenbank- und Ktor-Bezug,
 * damit sie ohne laufende Umgebung geprüft werden kann.
 */
object AthleteBoardLogic {

    const val DEFAULT_RUNNING_LIMIT = 3
    const val DEFAULT_UPCOMING_LIMIT = 3
    const val DEFAULT_RESULTS_LIMIT = 1
    const val DEFAULT_SHOW_COUNTDOWN = true
    const val DEFAULT_REFRESH_INTERVAL_SECONDS = 15

    private const val MIN_LIMIT = 1
    private const val MAX_LIMIT = 20

    /**
     * Löst die Konfiguration Feld für Feld gegen die Vorgabewerte auf. Eine Konfiguration,
     * die nur einen Wert setzt, behält für alle übrigen die Vorgabe.
     */
    fun resolveConfig(filters: JsonNode?, displayDurationSeconds: Int?): AthleteBoardConfig =
        AthleteBoardConfig(
            runningLimit = filters.limitOr("running", DEFAULT_RUNNING_LIMIT),
            upcomingLimit = filters.limitOr("upcoming", DEFAULT_UPCOMING_LIMIT),
            resultsLimit = filters.limitOr("results", DEFAULT_RESULTS_LIMIT),
            showCountdown = filters?.get("showCountdown")
                ?.takeIf { it.isBoolean }
                ?.booleanValue()
                ?: DEFAULT_SHOW_COUNTDOWN,
            refreshIntervalSeconds = displayDurationSeconds?.takeIf { it > 0 }
                ?: DEFAULT_REFRESH_INTERVAL_SECONDS,
        )

    private fun JsonNode?.limitOr(field: String, default: Int): Int =
        this?.get(field)
            ?.takeIf { it.isInt }
            ?.intValue()
            ?.coerceIn(MIN_LIMIT, MAX_LIMIT)
            ?: default

    /**
     * Eine verstrichene Startzeit ergibt OVERDUE statt eines negativen Countdowns.
     */
    fun startState(
        startTime: LocalDateTime?,
        now: LocalDateTime,
        showCountdown: Boolean,
    ): AthleteBoardStartState = when {
        startTime == null -> AthleteBoardStartState.UNSCHEDULED
        !startTime.isAfter(now) -> AthleteBoardStartState.OVERDUE
        showCountdown -> AthleteBoardStartState.COUNTDOWN
        else -> AthleteBoardStartState.SCHEDULED
    }

    /**
     * Aufsteigend nach Startzeit; Läufe ohne gepflegte Startzeit stehen am Ende.
     */
    fun <T> sortByStartTime(items: List<T>, startTime: (T) -> LocalDateTime?): List<T> =
        items.sortedWith(compareBy(nullsLast<LocalDateTime>()) { startTime(it) })
}
```

- [ ] **Step 6: Test laufen lassen und Erfolg bestätigen**

```bash
cd backend && ./mvnw test -Dtest=AthleteBoardLogicTest
```

Erwartet: BUILD SUCCESS, 13 Tests, 0 Fehler.

- [ ] **Step 7: Committen**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/entity/AthleteBoardConfig.kt backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/entity/AthleteBoardStartState.kt backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/boundary/AthleteBoardLogic.kt backend/src/test/kotlin/de/lambda9/ready2race/backend/app/eventInfo/AthleteBoardLogicTest.kt
git commit -m "Add athlete board configuration and start state logic"
```

---

## Task 2: Migration für den Enum-Wert

**Files:**
- Create: `backend/src/main/resources/db/migration/V202608021200__athlete_board_info_view.sql`

**Interfaces:**
- Consumes: nichts.
- Produces: den generierten Enum-Wert `InfoViewType.ATHLETE_BOARD` im Paket `de.lambda9.ready2race.backend.database.generated.enums`.

- [ ] **Step 1: Migration anlegen**

```sql
set search_path to ready2race, pg_catalog, public;

-- Vierter Anzeigetyp: die Athleten-Anzeige an Start und Ziel.
-- `alter type ... add value` läuft ab PostgreSQL 12 innerhalb einer Transaktion,
-- solange der neue Wert nicht in derselben Transaktion verwendet wird.
alter type info_view_type add value 'ATHLETE_BOARD';
```

- [ ] **Step 2: Migration anwenden und jOOQ-Code neu erzeugen**

```bash
cd backend && ./mvnw generate-sources
```

Erwartet: BUILD SUCCESS. Flyway meldet die Migration `202608021200`.

- [ ] **Step 3: Prüfen, dass der Enum-Wert im generierten Code steht**

```bash
find backend/target -name "InfoViewType.kt" -exec grep -l ATHLETE_BOARD {} +
```

Erwartet: mindestens ein Pfad wird ausgegeben. Kommt nichts, wurde die Migration nicht angewendet — dann Schritt 2 prüfen, bevor es weitergeht.

- [ ] **Step 4: Committen**

```bash
git add backend/src/main/resources/db/migration/V202608021200__athlete_board_info_view.sql
git commit -m "Add athlete board info view type"
```

---

## Task 3: DTOs, Abbildungen, Service und Route

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/entity/AthleteBoardDto.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/control/Conversions.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/boundary/EventInfoService.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/boundary/eventInfo.kt`

**Interfaces:**
- Consumes: `AthleteBoardLogic.resolveConfig`, `AthleteBoardLogic.startState`, `AthleteBoardLogic.sortByStartTime`, `AthleteBoardConfig`, `AthleteBoardStartState` (Task 1); `InfoViewType.ATHLETE_BOARD` (Task 2).
- Produces:
  - `EventInfoService.getAthleteBoard(eventId: UUID): App<EventInfoProblem, ApiResponse.Dto<AthleteBoardDto>>`
  - Route `GET /event/{eventId}/info/athlete-board`
  - DTOs `AthleteBoardDto`, `AthleteBoardMatch`, `AthleteBoardTeam`, `AthleteBoardParticipant`, `AthleteBoardResult`, `AthleteBoardResultTeam` (Felder siehe Step 1)

- [ ] **Step 1: Antwort-DTOs anlegen**

`backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/entity/AthleteBoardDto.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.eventInfo.entity

import java.time.LocalDateTime
import java.util.UUID

/**
 * Antwort der Athleten-Anzeige. Bewusst schlanker als die Info-DTOs daneben:
 * Jahrgang, Geschlecht, Teilnehmer-IDs und der externe Vereinsname fehlen,
 * weil sie nicht angezeigt werden und im Mobilfunknetz kosten.
 */
data class AthleteBoardDto(
    val eventName: String,
    val serverTime: LocalDateTime,
    val refreshIntervalSeconds: Int,
    val showCountdown: Boolean,
    val running: List<AthleteBoardMatch>,
    val upcoming: List<AthleteBoardMatch>,
    val results: List<AthleteBoardResult>,
)

data class AthleteBoardMatch(
    val matchId: UUID,
    val competitionName: String,
    val categoryName: String?,
    val roundName: String?,
    val matchName: String?,
    val startTime: LocalDateTime?,
    val startState: AthleteBoardStartState,
    val teams: List<AthleteBoardTeam>,
)

data class AthleteBoardTeam(
    /** Startposition im Lauf, aus `competition_match_team.start_number`. */
    val lane: Int?,
    val clubName: String?,
    val teamName: String?,
    val participants: List<AthleteBoardParticipant>,
)

data class AthleteBoardParticipant(
    val name: String,
    val role: String?,
)

data class AthleteBoardResult(
    val matchId: UUID,
    val competitionName: String,
    val categoryName: String?,
    val roundName: String?,
    val matchName: String?,
    val startTime: LocalDateTime?,
    val teams: List<AthleteBoardResultTeam>,
)

data class AthleteBoardResultTeam(
    val place: Int?,
    val lane: Int,
    val clubName: String?,
    val teamName: String?,
    val timeString: String?,
    val failed: Boolean,
    val failedReason: String?,
)
```

- [ ] **Step 2: Abbildungen ergänzen**

An das Ende von `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/control/Conversions.kt` anhängen:

```kotlin
private fun participantName(firstName: String, lastName: String) = "$firstName $lastName"

fun UpcomingMatchParticipantInfo.toAthleteBoardParticipant() = AthleteBoardParticipant(
    name = participantName(firstName, lastName),
    role = namedRole,
)

fun RunningMatchTeamInfo.toAthleteBoardTeam() = AthleteBoardTeam(
    lane = startNumber,
    // Der tatsächliche Verein gewinnt; die Auflösung passiert hier statt in jeder Ansicht.
    clubName = actualClubName ?: clubName,
    teamName = teamName,
    participants = participants.map { it.toAthleteBoardParticipant() },
)

fun UpcomingMatchTeamInfo.toAthleteBoardTeam() = AthleteBoardTeam(
    lane = startNumber,
    clubName = actualClubName ?: clubName,
    teamName = teamName,
    participants = participants.map { it.toAthleteBoardParticipant() },
)

fun RunningMatchInfo.toAthleteBoardMatch(now: LocalDateTime, showCountdown: Boolean) =
    AthleteBoardMatch(
        matchId = matchId,
        competitionName = competitionName,
        categoryName = categoryName,
        roundName = roundName,
        matchName = matchName,
        startTime = startTime,
        startState = AthleteBoardLogic.startState(startTime, now, showCountdown),
        teams = teams.map { it.toAthleteBoardTeam() },
    )

fun UpcomingCompetitionMatchInfo.toAthleteBoardMatch(now: LocalDateTime, showCountdown: Boolean) =
    AthleteBoardMatch(
        matchId = matchId,
        competitionName = competitionName,
        categoryName = categoryName,
        roundName = roundName,
        matchName = matchName,
        startTime = scheduledStartTime,
        startState = AthleteBoardLogic.startState(scheduledStartTime, now, showCountdown),
        teams = teams.map { it.toAthleteBoardTeam() },
    )

fun LatestMatchResultInfo.toAthleteBoardResult() = AthleteBoardResult(
    matchId = matchId,
    competitionName = competitionName,
    categoryName = categoryName,
    roundName = roundName,
    matchName = matchName,
    startTime = startTime,
    // Abgemeldete Mannschaften sind nicht gefahren und würden ohne Platz und ohne Zeit
    // wie ein Darstellungsfehler aussehen.
    teams = teams
        .filterNot { it.deregistered }
        .map {
            AthleteBoardResultTeam(
                place = it.place,
                lane = it.startNumber,
                clubName = it.actualClubName ?: it.clubName,
                teamName = it.teamName,
                timeString = it.timeString,
                failed = it.failed,
                failedReason = it.failedReason,
            )
        },
)
```

Dazu diese Importe oben in `Conversions.kt` ergänzen:

```kotlin
import de.lambda9.ready2race.backend.app.eventInfo.boundary.AthleteBoardLogic
import java.time.LocalDateTime
```

Der bestehende Import `de.lambda9.ready2race.backend.app.eventInfo.entity.*` deckt die neuen DTOs bereits ab, falls vorhanden; steht dort stattdessen eine Liste einzelner Importe, sind `AthleteBoardMatch`, `AthleteBoardParticipant`, `AthleteBoardResult`, `AthleteBoardResultTeam`, `AthleteBoardTeam`, `LatestMatchResultInfo`, `RunningMatchInfo`, `RunningMatchTeamInfo`, `UpcomingCompetitionMatchInfo`, `UpcomingMatchParticipantInfo` und `UpcomingMatchTeamInfo` einzeln zu ergänzen.

- [ ] **Step 3: Service-Methode ergänzen**

In `EventInfoService.kt` unterhalb von `getRunningMatches` einfügen:

```kotlin
    fun getAthleteBoard(eventId: UUID): App<EventInfoProblem, ApiResponse.Dto<AthleteBoardDto>> =
        KIO.comprehension {
            val eventName = !EventRepo.getName(eventId).orDie()
            if (eventName == null) {
                // Das `!` ist zwingend: In einer KIO-Comprehension bindet nur dieser
                // Operator den Wert. Ohne ihn wird das Fail-Objekt bloß erzeugt und
                // verworfen, und eine unbekannte Event-ID ergäbe 500 statt 404.
                !KIO.fail<EventInfoProblem>(EventInfoProblem.EventNotFound(eventId))
            }

            // findByEvent liefert nur aktive Zeilen, aufsteigend nach sort_order.
            // Gedacht ist genau eine ATHLETE_BOARD-Zeile; bei mehreren gewinnt die erste.
            val views = !InfoViewConfigurationRepo.findByEvent(eventId).orDie()
            val boardView = views.firstOrNull { it.viewType == InfoViewType.ATHLETE_BOARD }

            val config = AthleteBoardLogic.resolveConfig(
                filters = boardView?.filters?.let { ObjectMapper().readTree(it.data()) },
                displayDurationSeconds = boardView?.displayDurationSeconds,
            )

            val now = LocalDateTime.now()

            val running = !getRunningMatches(eventId, config.runningLimit)
            val upcoming = !getUpcomingCompetitionMatches(eventId, config.upcomingLimit)
            val results = !getLatestMatchResults(eventId, config.resultsLimit, null)

            KIO.ok(
                ApiResponse.Dto(
                    AthleteBoardDto(
                        eventName = eventName!!,
                        serverTime = now,
                        refreshIntervalSeconds = config.refreshIntervalSeconds,
                        showCountdown = config.showCountdown,
                        running = running.data.map {
                            it.toAthleteBoardMatch(now, config.showCountdown)
                        },
                        upcoming = AthleteBoardLogic.sortByStartTime(
                            upcoming.data.map { it.toAthleteBoardMatch(now, config.showCountdown) }
                        ) { it.startTime },
                        results = results.data.map { it.toAthleteBoardResult() },
                    )
                )
            )
        }
```

Dazu in `EventInfoService.kt` diese Importe ergänzen:

```kotlin
import com.fasterxml.jackson.databind.ObjectMapper
import de.lambda9.ready2race.backend.app.eventInfo.control.toAthleteBoardMatch
import de.lambda9.ready2race.backend.app.eventInfo.control.toAthleteBoardResult
import de.lambda9.ready2race.backend.database.generated.enums.InfoViewType
```

`EventRepo`, `InfoViewConfigurationRepo`, `AthleteBoardLogic` und `LocalDateTime` sind dort bereits importiert.

- [ ] **Step 4: Route ergänzen**

In `eventInfo.kt` innerhalb des Blocks `route("/event/{eventId}/info")` hinter `get("/running-matches")` einfügen:

```kotlin
        // Alles, was die Athleten-Anzeige braucht, in einer Antwort.
        // Öffentlich wie die drei Endpoints darüber — bewusst ohne authenticate.
        get("/athlete-board") {
            call.respondComprehension {
                val eventId = !pathParam("eventId", uuid)

                EventInfoService.getAthleteBoard(eventId)
            }
        }
```

- [ ] **Step 5: Kompilieren und Tests laufen lassen**

```bash
cd backend && ./mvnw test
```

Erwartet: BUILD SUCCESS, alle Tests grün.

- [ ] **Step 6: Den Endpoint tatsächlich abrufen**

Backend starten und mit einer echten Veranstaltungs-ID prüfen. Die ID liefert:

```bash
cd backend && docker compose exec -T db psql -U developer -d ready2race -c "set search_path to ready2race; select id, name from event limit 5;"
```

Dann in einem zweiten Terminal das Backend starten und abrufen:

```bash
curl -s "http://localhost:8080/api/event/<EVENT_ID>/info/athlete-board" | head -c 600
```

Erwartet: JSON mit den Schlüsseln `eventName`, `serverTime`, `refreshIntervalSeconds`, `showCountdown`, `running`, `upcoming`, `results`. Bei unbekannter ID: HTTP 404.

Lässt sich das Backend in der Umgebung nicht starten, ist dieser Schritt zu überspringen und im Abschlussbericht als ungeprüft zu vermerken — nicht stillschweigend als erledigt abhaken.

- [ ] **Step 7: Committen**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/
git commit -m "Serve the athlete board from a single public endpoint"
```

---

## Task 4: OpenAPI-Beschreibung und erzeugte Frontend-Typen

**Files:**
- Modify: `backend/src/main/resources/openapi/documentation.yaml`
- Modify (erzeugt): `frontend/src/api/types.gen.ts`, `frontend/src/api/sdk.gen.ts`

**Interfaces:**
- Consumes: die DTO-Felder aus Task 3.
- Produces: `getAthleteBoard({signal, path: {eventId}})` in `@api/sdk.gen` und die Typen `AthleteBoardDto`, `AthleteBoardMatch`, `AthleteBoardTeam`, `AthleteBoardParticipant`, `AthleteBoardResult`, `AthleteBoardResultTeam`, `AthleteBoardStartState` in `@api/types.gen`.

- [ ] **Step 1: Pfad ergänzen**

In `documentation.yaml` direkt hinter dem Block `/event/{eventId}/info/running-matches:` einfügen (gleiche Einrückung, zwei Leerzeichen):

```yaml
  /event/{eventId}/info/athlete-board:
    parameters:
      - $ref: '#/components/parameters/eventId'
    get:
      operationId: getAthleteBoard
      description: "Public board for athletes at start and finish: running, next and last finished matches in one response"
      responses:
        200:
          description: Athlete board retrieved successfully
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/AthleteBoardDto'
        404:
          $ref: '#/components/responses/404'
        500:
          $ref: '#/components/responses/500'
```

- [ ] **Step 2: Schemata ergänzen**

In `documentation.yaml` unter `components.schemas`, direkt vor dem Eintrag `RunningMatchInfo:` einfügen (vier Leerzeichen Einrückung, wie die Nachbarn):

```yaml
    AthleteBoardStartState:
      type: string
      enum:
        - UNSCHEDULED
        - COUNTDOWN
        - SCHEDULED
        - OVERDUE

    AthleteBoardParticipant:
      type: object
      required:
        - name
      properties:
        name:
          type: string
        role:
          type: string
          nullable: true

    AthleteBoardTeam:
      type: object
      required:
        - participants
      properties:
        lane:
          type: integer
          nullable: true
        clubName:
          type: string
          nullable: true
        teamName:
          type: string
          nullable: true
        participants:
          type: array
          items:
            $ref: '#/components/schemas/AthleteBoardParticipant'

    AthleteBoardMatch:
      type: object
      required:
        - matchId
        - competitionName
        - startState
        - teams
      properties:
        matchId:
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
        startTime:
          type: string
          format: date-time
          nullable: true
        startState:
          $ref: '#/components/schemas/AthleteBoardStartState'
        teams:
          type: array
          items:
            $ref: '#/components/schemas/AthleteBoardTeam'

    AthleteBoardResultTeam:
      type: object
      required:
        - lane
        - failed
      properties:
        place:
          type: integer
          nullable: true
        lane:
          type: integer
        clubName:
          type: string
          nullable: true
        teamName:
          type: string
          nullable: true
        timeString:
          type: string
          nullable: true
        failed:
          type: boolean
        failedReason:
          type: string
          nullable: true

    AthleteBoardResult:
      type: object
      required:
        - matchId
        - competitionName
        - teams
      properties:
        matchId:
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
        startTime:
          type: string
          format: date-time
          nullable: true
        teams:
          type: array
          items:
            $ref: '#/components/schemas/AthleteBoardResultTeam'

    AthleteBoardDto:
      type: object
      required:
        - eventName
        - serverTime
        - refreshIntervalSeconds
        - showCountdown
        - running
        - upcoming
        - results
      properties:
        eventName:
          type: string
        serverTime:
          type: string
          format: date-time
        refreshIntervalSeconds:
          type: integer
        showCountdown:
          type: boolean
        running:
          type: array
          items:
            $ref: '#/components/schemas/AthleteBoardMatch'
        upcoming:
          type: array
          items:
            $ref: '#/components/schemas/AthleteBoardMatch'
        results:
          type: array
          items:
            $ref: '#/components/schemas/AthleteBoardResult'
```

- [ ] **Step 3: Den Enum-Wert im bestehenden `InfoViewType`-Schema ergänzen**

Im Schema `InfoViewType` in derselben Datei (dort stehen heute `UPCOMING_MATCHES`, `LATEST_MATCH_RESULTS`, `RUNNING_MATCHES`) einen vierten Eintrag anhängen:

```yaml
        - ATHLETE_BOARD
```

- [ ] **Step 4: Frontend-Typen erzeugen**

```bash
cd frontend && npm run generate
```

Erwartet: `src/api/types.gen.ts` und `src/api/sdk.gen.ts` werden neu geschrieben.

- [ ] **Step 5: Prüfen, dass die neuen Namen entstanden sind**

```bash
grep -n "AthleteBoardDto\|getAthleteBoard\|ATHLETE_BOARD" frontend/src/api/types.gen.ts frontend/src/api/sdk.gen.ts | head -20
```

Erwartet: Treffer in beiden Dateien, darunter `export type AthleteBoardDto`, `export const getAthleteBoard` und `'ATHLETE_BOARD'` im Typ `InfoViewType`.

- [ ] **Step 6: TypeScript prüfen**

```bash
cd frontend && npx tsc -b
```

Erwartet: keine Ausgabe, Rückgabewert 0.

- [ ] **Step 7: Committen**

```bash
git add backend/src/main/resources/openapi/documentation.yaml frontend/src/api/
git commit -m "Describe the athlete board endpoint in the API definition"
```

---

## Task 5: Frontend — Daten, Uhr und Karten

**Files:**
- Create: `frontend/src/components/event/info/athleteBoard/useAthleteBoardData.ts`
- Create: `frontend/src/components/event/info/athleteBoard/useServerClock.ts`
- Create: `frontend/src/components/event/info/athleteBoard/AthleteBoardMatchCard.tsx`
- Create: `frontend/src/components/event/info/athleteBoard/AthleteBoardResultCard.tsx`

**Interfaces:**
- Consumes: `getAthleteBoard`, `AthleteBoardDto`, `AthleteBoardMatch`, `AthleteBoardResult` aus Task 4; Übersetzungsschlüssel aus Task 6 (die Karten dürfen vor Task 6 fehlende Schlüssel anzeigen — `tsc` und `lint` stören sich nicht daran).
- Produces:
  - `useAthleteBoardData(eventId: string): {data: AthleteBoardDto | null; lastUpdated: Date | null; notFound: boolean; initialLoad: boolean}`
  - `useServerClock(serverTime: string | undefined): Date`
  - `AthleteBoardMatchCard({match, now, showCountdown}: {match: AthleteBoardMatch; now: Date; showCountdown: boolean})`
  - `AthleteBoardResultCard({result}: {result: AthleteBoardResult})`

- [ ] **Step 1: Den Daten-Hook schreiben**

`frontend/src/components/event/info/athleteBoard/useAthleteBoardData.ts`:

```ts
import {useCallback, useEffect, useRef, useState} from 'react'
import {getAthleteBoard} from '@api/sdk.gen'
import {AthleteBoardDto} from '@api/types.gen'

const FALLBACK_INTERVAL_SECONDS = 15

export interface AthleteBoardState {
    data: AthleteBoardDto | null
    lastUpdated: Date | null
    notFound: boolean
    initialLoad: boolean
}

/**
 * Lädt die Athleten-Anzeige im Takt, den der Server vorgibt.
 *
 * Zwei Eigenschaften sind hier wichtiger als Kürze:
 * - Bei einem Netzabbruch bleibt der letzte gute Stand stehen. Ein fest montierter
 *   Bildschirm, der nach einem Aussetzer leer bleibt, ist der schlechteste Ausgang.
 * - Im Hintergrund wird nicht geladen; beim Zurückkehren sofort einmal.
 */
export const useAthleteBoardData = (eventId: string): AthleteBoardState => {
    const [data, setData] = useState<AthleteBoardDto | null>(null)
    const [lastUpdated, setLastUpdated] = useState<Date | null>(null)
    const [notFound, setNotFound] = useState(false)
    const [initialLoad, setInitialLoad] = useState(true)

    const timerRef = useRef<number | null>(null)
    const intervalRef = useRef(FALLBACK_INTERVAL_SECONDS)
    const abortRef = useRef<AbortController | null>(null)
    const mountedRef = useRef(true)

    useEffect(() => {
        mountedRef.current = true
        return () => {
            mountedRef.current = false
        }
    }, [])

    const clearTimer = () => {
        if (timerRef.current !== null) {
            window.clearTimeout(timerRef.current)
            timerRef.current = null
        }
    }

    const load = useCallback(async () => {
        abortRef.current?.abort()
        const controller = new AbortController()
        abortRef.current = controller

        try {
            const result = await getAthleteBoard({signal: controller.signal, path: {eventId}})
            if (!mountedRef.current) return
            if (result.response.status === 404) {
                setNotFound(true)
            } else if (result.data) {
                setNotFound(false)
                setData(result.data)
                setLastUpdated(new Date())
                intervalRef.current =
                    result.data.refreshIntervalSeconds > 0
                        ? result.data.refreshIntervalSeconds
                        : FALLBACK_INTERVAL_SECONDS
            }
        } catch {
            // Netzabbruch: letzten guten Stand stehen lassen und im naechsten Takt neu versuchen.
        } finally {
            if (mountedRef.current) {
                setInitialLoad(false)
                clearTimer()
                if (!document.hidden) {
                    timerRef.current = window.setTimeout(() => {
                        void load()
                    }, intervalRef.current * 1000)
                }
            }
        }
    }, [eventId])

    useEffect(() => {
        void load()

        const onVisibilityChange = () => {
            if (document.hidden) {
                clearTimer()
            } else {
                void load()
            }
        }

        document.addEventListener('visibilitychange', onVisibilityChange)
        return () => {
            document.removeEventListener('visibilitychange', onVisibilityChange)
            clearTimer()
            abortRef.current?.abort()
        }
    }, [load])

    return {data, lastUpdated, notFound, initialLoad}
}
```

- [ ] **Step 2: Die Uhr schreiben**

`frontend/src/components/event/info/athleteBoard/useServerClock.ts`:

```ts
import {useEffect, useRef, useState} from 'react'

/**
 * Liefert die laufende Serverzeit im Sekundentakt.
 *
 * Verankert wird an der `serverTime` der letzten Antwort; lokal wird nur die seither
 * verstrichene Zeit addiert. Damit zeigt auch ein Bildschirm mit falsch gestellter Uhr
 * den richtigen Countdown.
 */
export const useServerClock = (serverTime: string | undefined): Date => {
    const [tick, setTick] = useState(() => Date.now())
    const anchorRef = useRef<{server: number; local: number} | null>(null)

    if (serverTime) {
        const server = new Date(serverTime).getTime()
        if (!Number.isNaN(server) && anchorRef.current?.server !== server) {
            anchorRef.current = {server, local: Date.now()}
        }
    }

    useEffect(() => {
        const id = window.setInterval(() => setTick(Date.now()), 1000)
        return () => window.clearInterval(id)
    }, [])

    const anchor = anchorRef.current
    return anchor ? new Date(anchor.server + (tick - anchor.local)) : new Date(tick)
}
```

- [ ] **Step 3: Die Lauf-Karte schreiben**

`frontend/src/components/event/info/athleteBoard/AthleteBoardMatchCard.tsx`:

```tsx
import {Box, Card, CardContent, Chip, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {AthleteBoardMatch} from '@api/types.gen'

interface AthleteBoardMatchCardProps {
    match: AthleteBoardMatch
    now: Date
    showCountdown: boolean
}

const formatTime = (value: string) =>
    new Date(value).toLocaleTimeString(undefined, {hour: '2-digit', minute: '2-digit'})

const formatRemaining = (seconds: number) => {
    const total = Math.max(0, Math.floor(seconds))
    const minutes = Math.floor(total / 60)
    const rest = total % 60
    return minutes > 0 ? `${minutes} min` : `${rest} s`
}

const AthleteBoardMatchCard = ({match, now, showCountdown}: AthleteBoardMatchCardProps) => {
    const {t} = useTranslation()

    const startsInSeconds = match.startTime
        ? (new Date(match.startTime).getTime() - now.getTime()) / 1000
        : null

    // Der Server liefert den Zustand beim Abruf; zwischen zwei Abrufen laeuft die Uhr
    // lokal weiter, deshalb wird der Uebergang zu "erwartet" hier noch einmal geprueft.
    const overdue =
        match.startState === 'OVERDUE' || (startsInSeconds !== null && startsInSeconds <= 0)

    const renderTiming = () => {
        if (!match.startTime) {
            return (
                <Typography sx={{fontSize: 'clamp(0.8rem, 1.4vw, 1.1rem)'}} color="text.secondary">
                    {t('event.info.athleteBoard.unscheduled')}
                </Typography>
            )
        }
        return (
            <Stack alignItems="flex-end">
                <Typography
                    sx={{fontSize: 'clamp(1.1rem, 2.4vw, 2rem)', fontWeight: 700, lineHeight: 1.1}}>
                    {formatTime(match.startTime)}
                </Typography>
                {overdue ? (
                    <Typography
                        sx={{fontSize: 'clamp(0.75rem, 1.3vw, 1rem)'}}
                        color="text.secondary">
                        {t('event.info.athleteBoard.expected')}
                    </Typography>
                ) : (
                    showCountdown &&
                    startsInSeconds !== null && (
                        <Typography
                            sx={{fontSize: 'clamp(0.75rem, 1.3vw, 1rem)'}}
                            color="text.secondary">
                            {t('event.info.athleteBoard.startsIn', {
                                time: formatRemaining(startsInSeconds),
                            })}
                        </Typography>
                    )
                )}
            </Stack>
        )
    }

    return (
        <Card variant="outlined" sx={{mb: 1.5}}>
            <CardContent sx={{p: 'clamp(0.75rem, 1.2vw, 1.5rem)'}}>
                <Stack direction="row" justifyContent="space-between" alignItems="flex-start" gap={1}>
                    <Box sx={{minWidth: 0}}>
                        <Typography
                            sx={{fontSize: 'clamp(1rem, 1.8vw, 1.6rem)', fontWeight: 700}}
                            noWrap>
                            {match.competitionName}
                        </Typography>
                        <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap">
                            {match.roundName && (
                                <Typography
                                    sx={{fontSize: 'clamp(0.75rem, 1.2vw, 1rem)'}}
                                    color="text.secondary">
                                    {match.roundName}
                                </Typography>
                            )}
                            {match.matchName && match.matchName !== match.roundName && (
                                <Chip label={match.matchName} size="small" variant="outlined" />
                            )}
                            {match.categoryName && (
                                <Chip label={match.categoryName} size="small" color="primary" variant="outlined" />
                            )}
                        </Stack>
                    </Box>
                    {renderTiming()}
                </Stack>

                <Stack sx={{mt: 1.5}} divider={<Box sx={{height: '1px', bgcolor: 'divider'}} />}>
                    {match.teams.map((team, index) => (
                        <Stack
                            key={`${match.matchId}-${team.lane ?? index}`}
                            direction="row"
                            alignItems="center"
                            gap={1.5}
                            sx={{py: 0.75}}>
                            <Typography
                                sx={{
                                    fontSize: 'clamp(1.6rem, 3.4vw, 3rem)',
                                    fontWeight: 800,
                                    lineHeight: 1,
                                    minWidth: '1.8em',
                                    textAlign: 'center',
                                }}>
                                {team.lane ?? '–'}
                            </Typography>
                            <Box sx={{minWidth: 0}}>
                                <Typography
                                    sx={{fontSize: 'clamp(0.95rem, 1.6vw, 1.4rem)', fontWeight: 600}}>
                                    {team.clubName ?? ''}
                                    {team.teamName ? ` | ${team.teamName}` : ''}
                                </Typography>
                                {team.participants.length > 0 && (
                                    <Typography
                                        sx={{fontSize: 'clamp(0.7rem, 1.1vw, 0.95rem)'}}
                                        color="text.secondary">
                                        {team.participants
                                            .map(p => (p.role ? `${p.name} (${p.role})` : p.name))
                                            .join(', ')}
                                    </Typography>
                                )}
                            </Box>
                        </Stack>
                    ))}
                </Stack>
            </CardContent>
        </Card>
    )
}

export default AthleteBoardMatchCard
```

- [ ] **Step 4: Die Ergebnis-Karte schreiben**

`frontend/src/components/event/info/athleteBoard/AthleteBoardResultCard.tsx`:

```tsx
import {Box, Card, CardContent, Chip, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {AthleteBoardResult} from '@api/types.gen'

interface AthleteBoardResultCardProps {
    result: AthleteBoardResult
}

const AthleteBoardResultCard = ({result}: AthleteBoardResultCardProps) => {
    const {t} = useTranslation()

    const teams = [...result.teams].sort((a, b) => {
        // Platzierte zuerst, danach die ohne Platz (DNF und Konsorten).
        if (a.place == null && b.place == null) return a.lane - b.lane
        if (a.place == null) return 1
        if (b.place == null) return -1
        return a.place - b.place
    })

    return (
        <Card variant="outlined" sx={{mb: 1.5}}>
            <CardContent sx={{p: 'clamp(0.75rem, 1.2vw, 1.5rem)'}}>
                <Typography sx={{fontSize: 'clamp(1rem, 1.8vw, 1.6rem)', fontWeight: 700}} noWrap>
                    {result.competitionName}
                </Typography>
                <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap">
                    {result.roundName && (
                        <Typography
                            sx={{fontSize: 'clamp(0.75rem, 1.2vw, 1rem)'}}
                            color="text.secondary">
                            {result.roundName}
                        </Typography>
                    )}
                    {result.matchName && result.matchName !== result.roundName && (
                        <Chip label={result.matchName} size="small" variant="outlined" />
                    )}
                </Stack>

                <Stack sx={{mt: 1.5}} divider={<Box sx={{height: '1px', bgcolor: 'divider'}} />}>
                    {teams.map((team, index) => (
                        <Stack
                            key={`${result.matchId}-${team.lane}-${index}`}
                            direction="row"
                            alignItems="center"
                            gap={1.5}
                            sx={{py: 0.75}}>
                            <Typography
                                sx={{
                                    fontSize: 'clamp(1.4rem, 2.8vw, 2.4rem)',
                                    fontWeight: 800,
                                    lineHeight: 1,
                                    minWidth: '1.8em',
                                    textAlign: 'center',
                                }}>
                                {team.place ?? '–'}
                            </Typography>
                            <Box sx={{flex: 1, minWidth: 0}}>
                                <Typography
                                    sx={{fontSize: 'clamp(0.95rem, 1.6vw, 1.4rem)', fontWeight: 600}}>
                                    {team.clubName ?? ''}
                                    {team.teamName ? ` | ${team.teamName}` : ''}
                                </Typography>
                                <Typography
                                    sx={{fontSize: 'clamp(0.7rem, 1.1vw, 0.95rem)'}}
                                    color="text.secondary">
                                    {t('event.info.athleteBoard.lane')} {team.lane}
                                </Typography>
                            </Box>
                            <Typography
                                sx={{fontSize: 'clamp(0.9rem, 1.5vw, 1.3rem)', fontWeight: 600}}
                                color={team.failed ? 'text.secondary' : 'text.primary'}>
                                {team.failed
                                    ? (team.failedReason ?? t('event.info.athleteBoard.failed'))
                                    : (team.timeString ?? '')}
                            </Typography>
                        </Stack>
                    ))}
                </Stack>
            </CardContent>
        </Card>
    )
}

export default AthleteBoardResultCard
```

- [ ] **Step 5: TypeScript und Linter prüfen**

```bash
cd frontend && npx tsc -b && npm run lint
```

Erwartet: beide ohne Fehler. Warnungen des Linters, die es im Bestand schon gibt, sind hinzunehmen; neue Fehler nicht.

- [ ] **Step 6: Committen**

```bash
git add frontend/src/components/event/info/athleteBoard/
git commit -m "Add data, clock and cards for the athlete board"
```

---

## Task 6: Frontend — Ansicht, Seite, Route, Kiosk-Einbindung und Texte

**Files:**
- Create: `frontend/src/components/event/info/views/AthleteBoardView.tsx`
- Create: `frontend/src/pages/event/AthleteBoardPage.tsx`
- Modify: `frontend/src/routes.tsx`
- Modify: `frontend/src/components/event/info/InfoViewDisplay.tsx`
- Modify: `frontend/src/components/event/info/ViewConfigurationForm.tsx`
- Modify: `frontend/src/i18n/de/translations.json`, `frontend/src/i18n/en/translations.json`, `frontend/src/i18n/da/translations.json`

**Interfaces:**
- Consumes: `useAthleteBoardData`, `useServerClock`, `AthleteBoardMatchCard`, `AthleteBoardResultCard` (Task 5); `InfoViewType` mit `ATHLETE_BOARD` (Task 4).
- Produces: Route `/event/$eventId/board`; `AthleteBoardView({eventId})`.

- [ ] **Step 1: Die Ansicht schreiben**

`frontend/src/components/event/info/views/AthleteBoardView.tsx`:

```tsx
import {ReactNode} from 'react'
import {Box, CircularProgress, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useAthleteBoardData} from '../athleteBoard/useAthleteBoardData'
import {useServerClock} from '../athleteBoard/useServerClock'
import AthleteBoardMatchCard from '../athleteBoard/AthleteBoardMatchCard'
import AthleteBoardResultCard from '../athleteBoard/AthleteBoardResultCard'

interface AthleteBoardViewProps {
    eventId: string
}

const STALE_AFTER_MISSED_INTERVALS = 2

const AthleteBoardView = ({eventId}: AthleteBoardViewProps) => {
    const {t} = useTranslation()
    const {data, lastUpdated, notFound, initialLoad} = useAthleteBoardData(eventId)
    const now = useServerClock(data?.serverTime)

    if (notFound) {
        return (
            <Box sx={{display: 'flex', height: '100%', alignItems: 'center', justifyContent: 'center', p: 3}}>
                <Typography variant="h5" color="text.secondary">
                    {t('event.info.athleteBoard.eventNotFound')}
                </Typography>
            </Box>
        )
    }

    if (initialLoad && !data) {
        return (
            <Box sx={{display: 'flex', height: '100%', alignItems: 'center', justifyContent: 'center'}}>
                <CircularProgress />
            </Box>
        )
    }

    const staleThresholdMs =
        (data?.refreshIntervalSeconds ?? 15) * STALE_AFTER_MISSED_INTERVALS * 1000
    const stale =
        lastUpdated !== null && Date.now() - lastUpdated.getTime() > staleThresholdMs

    const column = (
        title: string,
        emptyText: string,
        items: ReactNode[],
    ) => (
        <Box sx={{flex: 1, minWidth: 0}}>
            <Typography
                sx={{
                    fontSize: 'clamp(1rem, 1.9vw, 1.8rem)',
                    fontWeight: 700,
                    mb: 1,
                    textTransform: 'uppercase',
                    letterSpacing: '0.04em',
                }}>
                {title}
            </Typography>
            {items.length > 0 ? (
                items
            ) : (
                <Typography
                    sx={{fontSize: 'clamp(0.85rem, 1.3vw, 1.1rem)'}}
                    color="text.secondary">
                    {emptyText}
                </Typography>
            )}
        </Box>
    )

    return (
        <Box sx={{height: '100%', overflow: 'auto', p: 'clamp(0.75rem, 1.5vw, 2rem)'}}>
            <Stack
                direction="row"
                justifyContent="space-between"
                alignItems="baseline"
                gap={2}
                sx={{mb: 2}}>
                <Typography sx={{fontSize: 'clamp(1.1rem, 2.2vw, 2.2rem)', fontWeight: 800}} noWrap>
                    {data?.eventName ?? ''}
                </Typography>
                <Stack alignItems="flex-end">
                    <Typography sx={{fontSize: 'clamp(1.1rem, 2.2vw, 2.2rem)', fontWeight: 800}}>
                        {now.toLocaleTimeString(undefined, {hour: '2-digit', minute: '2-digit'})}
                    </Typography>
                    {lastUpdated && (
                        <Typography
                            sx={{fontSize: 'clamp(0.65rem, 1vw, 0.85rem)'}}
                            color={stale ? 'warning.main' : 'text.secondary'}>
                            {t('event.info.athleteBoard.asOf', {
                                time: lastUpdated.toLocaleTimeString(undefined, {
                                    hour: '2-digit',
                                    minute: '2-digit',
                                }),
                            })}
                            {stale ? ` — ${t('event.info.athleteBoard.stale')}` : ''}
                        </Typography>
                    )}
                </Stack>
            </Stack>

            <Stack
                direction={{xs: 'column', lg: 'row'}}
                gap={{xs: 2, lg: 3}}
                alignItems="stretch">
                {column(
                    t('event.info.athleteBoard.running'),
                    t('event.info.athleteBoard.noRunning'),
                    (data?.running ?? []).map(match => (
                        <AthleteBoardMatchCard
                            key={match.matchId}
                            match={match}
                            now={now}
                            showCountdown={false}
                        />
                    )),
                )}
                {column(
                    t('event.info.athleteBoard.upcoming'),
                    t('event.info.athleteBoard.noUpcoming'),
                    (data?.upcoming ?? []).map(match => (
                        <AthleteBoardMatchCard
                            key={match.matchId}
                            match={match}
                            now={now}
                            showCountdown={data?.showCountdown ?? true}
                        />
                    )),
                )}
                {column(
                    t('event.info.athleteBoard.results'),
                    t('event.info.athleteBoard.noResults'),
                    (data?.results ?? []).map(result => (
                        <AthleteBoardResultCard key={result.matchId} result={result} />
                    )),
                )}
            </Stack>
        </Box>
    )
}

export default AthleteBoardView
```

- [ ] **Step 2: Die Seite schreiben**

`frontend/src/pages/event/AthleteBoardPage.tsx`:

```tsx
import {Box} from '@mui/material'
import AthleteBoardView from '@components/event/info/views/AthleteBoardView'
import {athleteBoardRoute} from '@routes'

/**
 * Traegerseite der Athleten-Anzeige. Bewusst ohne Bedienelemente: ein fest montierter
 * Bildschirm hat keine Maus, und auf dem Telefon ist eine Seite, die nur zeigt,
 * schneller verstanden.
 */
const AthleteBoardPage = () => {
    const {eventId} = athleteBoardRoute.useParams()

    return (
        <Box sx={{height: '100vh', overflow: 'hidden'}}>
            <AthleteBoardView eventId={eventId} />
        </Box>
    )
}

export default AthleteBoardPage
```

- [ ] **Step 3: Route eintragen**

In `frontend/src/routes.tsx` den Import ergänzen (bei den übrigen Seiten-Importen):

```tsx
import AthleteBoardPage from './pages/event/AthleteBoardPage.tsx'
```

Diese Datei benutzt durchgehend relative Pfade mit `.tsx`-Endung (etwa `./pages/event/EventsPage.tsx`) — nicht die `@pages`-Kurzform.

Direkt hinter `eventInfoRoute` die neue Route einfügen:

```tsx
export const athleteBoardRoute = createRoute({
    getParentRoute: () => eventRoute,
    path: 'board',
    component: () => <AthleteBoardPage />,
})
```

Kein `beforeLoad` — die Seite ist öffentlich.

Und im Routenbaum bei `eventRoute.addChildren([...])` hinter `eventInfoRoute` eintragen:

```tsx
                athleteBoardRoute,
```

- [ ] **Step 4: Kiosk-Rotation einbinden**

In `frontend/src/components/event/info/InfoViewDisplay.tsx` den Import ergänzen:

```tsx
import AthleteBoardView from './views/AthleteBoardView'
```

und im `switch` einen Fall hinzufügen:

```tsx
            case 'ATHLETE_BOARD':
                // Limits und Countdown-Schalter kommen aus der Antwort des Endpoints,
                // damit Kiosk und eigene Seite dieselbe Konfiguration zeigen.
                return <AthleteBoardView eventId={eventId} />
```

- [ ] **Step 5: Admin-Formular erweitern**

In `frontend/src/components/event/info/ViewConfigurationForm.tsx` die Auswahlliste ergänzen:

```tsx
        {id: 'ATHLETE_BOARD', label: t('event.info.viewTypes.athleteBoard')},
```

Darunter, direkt nach `const dataLimit = watch('dataLimit')`, ergänzen:

```tsx
    const viewType = watch('viewType')
    // filters ist im erzeugten Typ `{[key: string]: unknown} | undefined` — kein Cast noetig.
    const filters = watch('filters')

    const filterNumber = (key: string, fallback: number) => {
        const value = filters?.[key]
        return typeof value === 'number' ? value : fallback
    }

    const setFilter = (key: string, value: number | boolean) =>
        setValue('filters', {...(filters ?? {}), [key]: value})
```

Und innerhalb der `<Box sx={{display: 'flex', flexDirection: 'column', gap: 3, pt: 1}}>` hinter dem `dataLimit`-Block einfügen:

```tsx
                        {viewType === 'ATHLETE_BOARD' && (
                            <Box sx={{display: 'flex', flexDirection: 'column', gap: 2}}>
                                <Typography variant="subtitle2">
                                    {t('event.info.athleteBoard.settingsTitle')}
                                </Typography>
                                <TextField
                                    type="number"
                                    size="small"
                                    label={t('event.info.athleteBoard.limitRunning')}
                                    value={filterNumber('running', 3)}
                                    onChange={e => setFilter('running', Number(e.target.value))}
                                    inputProps={{min: 1, max: 20}}
                                />
                                <TextField
                                    type="number"
                                    size="small"
                                    label={t('event.info.athleteBoard.limitUpcoming')}
                                    value={filterNumber('upcoming', 3)}
                                    onChange={e => setFilter('upcoming', Number(e.target.value))}
                                    inputProps={{min: 1, max: 20}}
                                />
                                <TextField
                                    type="number"
                                    size="small"
                                    label={t('event.info.athleteBoard.limitResults')}
                                    value={filterNumber('results', 1)}
                                    onChange={e => setFilter('results', Number(e.target.value))}
                                    inputProps={{min: 1, max: 20}}
                                />
                                <FormControlLabel
                                    control={
                                        <Checkbox
                                            checked={filters?.showCountdown !== false}
                                            onChange={e =>
                                                setFilter('showCountdown', e.target.checked)
                                            }
                                        />
                                    }
                                    label={t('event.info.athleteBoard.showCountdown')}
                                />
                            </Box>
                        )}
```

Die Import-Zeile von `@mui/material` in dieser Datei um `Checkbox`, `FormControlLabel` und `TextField` erweitern.

- [ ] **Step 6: Übersetzungen ergänzen**

In allen drei Dateien unter `event.info.viewTypes` einen vierten Schlüssel `athleteBoard` und unter `event.info` einen Block `athleteBoard` anlegen.

`frontend/src/i18n/de/translations.json` — `viewTypes.athleteBoard`: `"Athleten-Anzeige"`, und:

```json
"athleteBoard": {
  "running": "Aktueller Lauf",
  "upcoming": "Nächster Lauf",
  "results": "Letztes Ergebnis",
  "noRunning": "Zurzeit kein Lauf auf dem Wasser",
  "noUpcoming": "Kein weiterer Lauf gesetzt",
  "noResults": "Noch kein Ergebnis",
  "lane": "Bahn",
  "expected": "erwartet",
  "unscheduled": "Zeit offen",
  "failed": "nicht gewertet",
  "startsIn": "in {{time}}",
  "asOf": "Stand {{time}}",
  "stale": "Verbindung unterbrochen",
  "eventNotFound": "Veranstaltung nicht gefunden",
  "settingsTitle": "Athleten-Anzeige",
  "limitRunning": "Anzahl laufender Läufe",
  "limitUpcoming": "Anzahl nächster Läufe",
  "limitResults": "Anzahl Ergebnisse",
  "showCountdown": "Countdown bis zum Start anzeigen"
}
```

`frontend/src/i18n/en/translations.json` — `viewTypes.athleteBoard`: `"Athlete board"`, und:

```json
"athleteBoard": {
  "running": "Current race",
  "upcoming": "Next race",
  "results": "Last result",
  "noRunning": "No race on the water right now",
  "noUpcoming": "No further race scheduled",
  "noResults": "No result yet",
  "lane": "Lane",
  "expected": "expected",
  "unscheduled": "Time open",
  "failed": "not classified",
  "startsIn": "in {{time}}",
  "asOf": "As of {{time}}",
  "stale": "Connection lost",
  "eventNotFound": "Event not found",
  "settingsTitle": "Athlete board",
  "limitRunning": "Number of running races",
  "limitUpcoming": "Number of next races",
  "limitResults": "Number of results",
  "showCountdown": "Show countdown to start"
}
```

`frontend/src/i18n/da/translations.json` — `viewTypes.athleteBoard`: `"Atletvisning"`, und:

```json
"athleteBoard": {
  "running": "Aktuelt løb",
  "upcoming": "Næste løb",
  "results": "Seneste resultat",
  "noRunning": "Intet løb på vandet lige nu",
  "noUpcoming": "Ingen flere løb planlagt",
  "noResults": "Endnu intet resultat",
  "lane": "Bane",
  "expected": "forventet",
  "unscheduled": "Tid ikke fastsat",
  "failed": "ikke placeret",
  "startsIn": "om {{time}}",
  "asOf": "Opdateret {{time}}",
  "stale": "Forbindelsen afbrudt",
  "eventNotFound": "Arrangementet blev ikke fundet",
  "settingsTitle": "Atletvisning",
  "limitRunning": "Antal igangværende løb",
  "limitUpcoming": "Antal kommende løb",
  "limitResults": "Antal resultater",
  "showCountdown": "Vis nedtælling til start"
}
```

- [ ] **Step 7: Prüfen, dass die drei Übersetzungsdateien gültiges JSON sind und dieselben Schlüssel haben**

```bash
cd frontend && python3 -c "
import json
keys = {}
for loc in ('de', 'en', 'da'):
    d = json.load(open(f'src/i18n/{loc}/translations.json'))
    keys[loc] = set(d['event']['info']['athleteBoard'])
    assert 'athleteBoard' in d['event']['info']['viewTypes'], loc
assert keys['de'] == keys['en'] == keys['da'], keys
print('ok', len(keys['de']), 'Schluessel je Sprache')
"
```

Erwartet: `ok 19 Schluessel je Sprache`.

- [ ] **Step 8: TypeScript und Linter prüfen**

```bash
cd frontend && npx tsc -b && npm run lint
```

Erwartet: beide ohne Fehler.

- [ ] **Step 9: Die Seite tatsächlich ansehen**

```bash
cd frontend && npm run dev
```

Dann `http://localhost:5123/event/<EVENT_ID>/board` **in einem abgemeldeten Browserprofil oder privaten Fenster** öffnen. Das ist der eigentliche Nachweis: Die Seite muss ohne Anmeldung Inhalt zeigen und darf nicht auf `/login` umleiten. Zusätzlich das Fenster auf Telefonbreite (375 px) verkleinern und prüfen, dass die drei Spalten untereinander rutschen und nichts waagerecht scrollt.

Lässt sich das in der Umgebung nicht ausführen, ist es im Abschlussbericht ausdrücklich als ungeprüft zu vermerken.

- [ ] **Step 10: Committen**

```bash
git add frontend/src/components/event/info/ frontend/src/pages/event/AthleteBoardPage.tsx frontend/src/routes.tsx frontend/src/i18n/
git commit -m "Add the public athlete board page and its configuration"
```

---

## Abschluss

- [ ] **Alle Backend-Tests laufen lassen**

```bash
cd backend && ./mvnw test
```

Erwartet: BUILD SUCCESS.

- [ ] **Frontend bauen**

```bash
cd frontend && npm run build
```

Erwartet: Build läuft durch.

- [ ] **Bericht**

Festhalten, was geprüft wurde und was nicht — insbesondere, ob Task 3 Step 6 (Endpoint abgerufen) und Task 6 Step 9 (Seite abgemeldet im Browser gesehen) tatsächlich ausgeführt werden konnten.
