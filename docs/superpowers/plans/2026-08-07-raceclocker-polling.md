# Automatischer RaceClocker-Abruf — Umsetzungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ein Hintergrund-Job holt RaceClocker-Ergebnisse selbsttätig — aktive Läufe alle 5 s, bevorstehende einmal pro Minute, beendete nie — und aktiviert einen Lauf, sobald RaceClocker seinen Start meldet.

**Architecture:** Ein weiterer Job im bestehenden `Scheduler` (`Application.kt`) mit 1-s-Herzschlag, der je Veranstaltung anhand ihrer konfigurierten Takte entscheidet, ob abgerufen wird. Pro Takt wird jede benötigte RaceClocker-URL genau einmal geholt und die Antwort auf alle Läufe daran verteilt. Die Anwendungslogik wird aus dem bestehenden Endpunkt herausgeschnitten, damit Knopf und Automatik denselben Code benutzen.

**Tech Stack:** Kotlin 2.1 / Ktor 3.1 / KIO (tailwind) / jOOQ 3.19 gegen PostgreSQL 17 / Flyway; Frontend React 18 + MUI 6 + react-hook-form-mui, API-Client generiert per `openapi-ts` aus `documentation.yaml`.

**Spec:** `docs/superpowers/specs/2026-08-07-raceclocker-polling-design.md`

## Global Constraints

- **Kommentarsprache:** Deutsch mit echten Umlauten (ä, ö, ü, ß), passend zum umgebenden Code. Englische Kommentare nur dort, wo die Datei schon durchgängig englisch ist.
- **`JAVA_HOME` ist in dieser Shell nicht gesetzt.** Vor jedem Maven-Aufruf:
  ```bash
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH"
  ```
- **Der jOOQ-Codegen braucht eine laufende Build-Datenbank.** `./mvnw` migriert `jdbc:postgresql://localhost:7652/ready2race-build` (Dienst `build-db` aus `backend/docker-compose.yaml`) und generiert daraus die jOOQ-Klassen nach `target/generated-sources/jooq`. Dieser Container wird zwischen allen Worktrees geteilt; ihn niemals wipen.
- **Kein `Co-Authored-By`, keine Erwähnung von Claude oder KI** in Commit-Nachrichten.
- **Branch:** `claude/raceclocker-polling-53dac0` (bereits ausgecheckt). Nicht nach main mergen.
- **Testframework Backend:** `kotlin.test` (`@Test`, `assertEquals`, `assertTrue`, `assertNull`) mit JUnit 5 als Runner. Keine Datenbank-Tests im Projekt — Repositories werden nicht per Test abgedeckt, reine Logik dagegen immer.
- **Testframework Frontend:** `vitest` (`npm run test`), Tests liegen als `<name>.test.ts` neben der Datei.
- **`competition_match` wird überall über `competition_setup_match` identifiziert** — das ist die Spalte, die im Code `matchId` heißt. `competition_match` hat keine eigene `id`.
- **Untergrenze der Takte:** `RaceClockerPollLogic.MIN_INTERVAL_SECONDS = 2`.
- **Voreinstellungen:** Automatik aus, aktiver Takt 5 s, bevorstehender Takt 60 s, Vorlauf 15 min, Nachlauf 120 min.

---

## Dateiübersicht

**Neu (Backend):**

| Datei | Verantwortung |
|---|---|
| `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/boundary/RaceClockerPollLogic.kt` | Reine Entscheidungslogik: Fenster, Takt-Modus, Fälligkeit, Start-Erkennung, Fingerabdruck, Untergrenze |
| `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/boundary/RaceClockerPollService.kt` | Der Takt selbst: Kandidaten laden, Feeds holen, anwenden, Status schreiben |
| `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/control/RaceClockerPollRepo.kt` | Abfragen für Veranstaltungen und Kandidaten, Schreiben des Poll-Status |
| `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/entity/RaceClockerPollEvent.kt` | Veranstaltung mit ihren Takt-Einstellungen |
| `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/entity/RaceClockerPollCandidate.kt` | Ein beobachteter Lauf samt URL-Ziel |
| `backend/src/main/resources/db/migration/V202608071600__raceclocker_polling.sql` | Die acht neuen Spalten |
| `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/raceclocker/RaceClockerPollLogicTest.kt` | Tests zu `RaceClockerPollLogic` |
| `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/timingConfig/EventTimingConfigRequestTest.kt` | Tests zur Validierung der neuen Felder |

**Geändert (Backend):**

| Datei | Änderung |
|---|---|
| `backend/src/main/resources/db/migration/afterMigrate.sql` | Drei Spalten in die View `competition_match_with_teams` |
| `.../app/timingConfig/entity/EventTimingConfigDto.kt` und `EventTimingConfigRequest.kt` | Fünf neue Felder + Validierung |
| `.../app/timingConfig/boundary/TimingConfigService.kt` | Fünf Felder lesen und schreiben |
| `.../app/competitionExecution/boundary/CompetitionExecutionService.kt` | `applyRaceClockerRows` herausgelöst; Handeingabe pausiert die Automatik; `resumeRaceClockerAutoPull` |
| `.../app/competitionExecution/boundary/competitionExecution.kt` | Endpunkt zum Wiederaufnehmen |
| `.../app/competitionExecution/entity/CompetitionMatchDto.kt`, `CompetitionMatchWithTeams.kt`, `control/Conversions.kt` | Poll-Status durchreichen |
| `.../app/liveDashboard/entity/LiveDashboardDto.kt`, `control/LiveDashboardRepo.kt`, `boundary/LiveDashboardService.kt` | Poll-Status durchreichen |
| `backend/src/main/kotlin/de/lambda9/ready2race/backend/Application.kt` | Der neue Job |
| `backend/src/main/resources/openapi/documentation.yaml` | Neue Felder und der neue Endpunkt |

**Geändert (Frontend):**

| Datei | Änderung |
|---|---|
| `frontend/src/components/event/timing/eventTimingConfigForm.ts` (+ `.test.ts`) | Fünf Formularfelder |
| `frontend/src/components/event/timing/EventTimingConfig.tsx` | Die Eingabefelder |
| `frontend/src/components/event/competition/excecution/raceClockerPollStatus.ts` (+ `.test.ts`) | Poll-Status in Text übersetzen (neu) |
| `frontend/src/components/event/competition/excecution/CompetitionExecutionRound.tsx` | Statuszeile + „Automatik wieder aufnehmen" |
| `frontend/src/components/event/competition/excecution/CompetitionExecution.tsx` | Handler dafür |
| `frontend/src/components/event/liveDashboard/LiveDashboardMatchCard.tsx` | Statuszeile |
| `frontend/src/i18n/de/translations.json`, `en/…`, `da/…` | Neue Schlüssel |
| `frontend/src/api/types.gen.ts`, `sdk.gen.ts` | generiert, nicht von Hand |

---

## Task 1: Reine Poll-Logik

Die gesamte Entscheidungslogik des Jobs als testbare Funktionen, bevor irgendetwas davon an Datenbank oder HTTP hängt.

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/boundary/RaceClockerPollLogic.kt`
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/raceclocker/RaceClockerPollLogicTest.kt`

**Interfaces:**
- Consumes: `RaceClockerFeedRow` aus `de.lambda9.ready2race.backend.app.raceclocker.entity` (Felder `start: LocalTime?`, `hasResult: Boolean`, `rank: Int?`, `ids: List<UUID>`, `result: String?`, `penaltySeconds: Int?`, `penaltyNote: String?`).
- Produces:
  - `RaceClockerPollLogic.MIN_INTERVAL_SECONDS: Int` (= 2)
  - `RaceClockerPollLogic.PollMode` — `ACTIVE`, `UPCOMING`
  - `fun intervalSeconds(configured: Int): Int`
  - `fun isWatched(currentlyRunning: Boolean, startTime: LocalDateTime?, now: LocalDateTime, watchBeforeMinutes: Int, watchAfterMinutes: Int): Boolean`
  - `fun modeFor(anyRunning: Boolean): PollMode`
  - `fun isDue(lastPolledAt: LocalDateTime?, now: LocalDateTime, intervalSeconds: Int): Boolean`
  - `fun startDetected(rows: List<RaceClockerFeedRow>): Boolean`
  - `fun fingerprint(rows: List<RaceClockerFeedRow>): String`

- [ ] **Step 1: Den Test schreiben**

Datei `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/raceclocker/RaceClockerPollLogicTest.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.raceclocker

import de.lambda9.ready2race.backend.app.raceclocker.boundary.RaceClockerPollLogic
import de.lambda9.ready2race.backend.app.raceclocker.boundary.RaceClockerPollLogic.PollMode
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerFeedRow
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Die Entscheidungen des Abruf-Jobs, losgelöst von Datenbank und HTTP: wen er beobachtet, in
 * welchem Takt, wann der Takt fällig ist, wann ein Lauf als gestartet gilt und wann sich seit dem
 * letzten Abruf überhaupt etwas geändert hat.
 */
class RaceClockerPollLogicTest {

    private val now = LocalDateTime.of(2026, 8, 14, 10, 0)

    private fun row(
        id: UUID = UUID.randomUUID(),
        rank: Int? = 1,
        result: String? = null,
        start: LocalTime? = null,
        penaltySeconds: Int? = null,
        penaltyNote: String? = null,
    ) = RaceClockerFeedRow(
        name = "Testverein",
        rank = rank,
        bib = null,
        wave = "AF1 CM1x",
        ids = listOf(id),
        result = result,
        start = start,
        penaltySeconds = penaltySeconds,
        penaltyNote = penaltyNote,
    )

    // --- Fenster ---

    @Test
    fun aRunningMatchIsWatchedRegardlessOfItsPlannedTime() {
        assertTrue(
            RaceClockerPollLogic.isWatched(
                currentlyRunning = true,
                startTime = now.minusHours(6),
                now = now,
                watchBeforeMinutes = 15,
                watchAfterMinutes = 120,
            )
        )
    }

    @Test
    fun anUpcomingMatchIsWatchedInsideTheWindow() {
        assertTrue(
            RaceClockerPollLogic.isWatched(false, now.plusMinutes(10), now, 15, 120)
        )
        assertTrue(
            RaceClockerPollLogic.isWatched(false, now.minusMinutes(90), now, 15, 120)
        )
    }

    @Test
    fun theWindowBoundsAreInclusive() {
        assertTrue(RaceClockerPollLogic.isWatched(false, now.plusMinutes(15), now, 15, 120))
        assertTrue(RaceClockerPollLogic.isWatched(false, now.minusMinutes(120), now, 15, 120))
    }

    @Test
    fun outsideTheWindowNothingIsWatched() {
        assertFalse(RaceClockerPollLogic.isWatched(false, now.plusMinutes(16), now, 15, 120))
        assertFalse(RaceClockerPollLogic.isWatched(false, now.minusMinutes(121), now, 15, 120))
    }

    @Test
    fun withoutAPlannedStartTimeAnInactiveMatchIsNotWatched() {
        assertFalse(RaceClockerPollLogic.isWatched(false, null, now, 15, 120))
    }

    // --- Takt ---

    @Test
    fun oneRunningMatchPutsTheWholeEventIntoTheFastMode() {
        assertEquals(PollMode.ACTIVE, RaceClockerPollLogic.modeFor(anyRunning = true))
        assertEquals(PollMode.UPCOMING, RaceClockerPollLogic.modeFor(anyRunning = false))
    }

    @Test
    fun theConfiguredIntervalNeverFallsBelowTheFloor() {
        assertEquals(5, RaceClockerPollLogic.intervalSeconds(5))
        assertEquals(RaceClockerPollLogic.MIN_INTERVAL_SECONDS, RaceClockerPollLogic.intervalSeconds(1))
        assertEquals(RaceClockerPollLogic.MIN_INTERVAL_SECONDS, RaceClockerPollLogic.intervalSeconds(0))
        assertEquals(RaceClockerPollLogic.MIN_INTERVAL_SECONDS, RaceClockerPollLogic.intervalSeconds(-30))
    }

    // --- Fälligkeit ---

    @Test
    fun anEventThatWasNeverPolledIsDueImmediately() {
        assertTrue(RaceClockerPollLogic.isDue(null, now, 5))
    }

    @Test
    fun theIntervalMustHavePassed() {
        assertFalse(RaceClockerPollLogic.isDue(now.minusSeconds(4), now, 5))
        assertTrue(RaceClockerPollLogic.isDue(now.minusSeconds(5), now, 5))
        assertTrue(RaceClockerPollLogic.isDue(now.minusSeconds(30), now, 5))
    }

    // --- Start-Erkennung ---

    @Test
    fun aRecordedStartTimeCountsAsStarted() {
        assertTrue(RaceClockerPollLogic.startDetected(listOf(row(start = LocalTime.of(10, 3)))))
    }

    @Test
    fun aResultCountsAsStartedEvenWithoutAStartTime() {
        assertTrue(RaceClockerPollLogic.startDetected(listOf(row(result = "3:21.4"))))
        assertTrue(RaceClockerPollLogic.startDetected(listOf(row(result = "DNF"))))
    }

    @Test
    fun waitingRowsAreNotAStart() {
        assertFalse(RaceClockerPollLogic.startDetected(listOf(row(result = "Not started"))))
        assertFalse(RaceClockerPollLogic.startDetected(listOf(row(result = "In race..."))))
        assertFalse(RaceClockerPollLogic.startDetected(emptyList()))
    }

    @Test
    fun aBoatOnTheWaterIsAStartWhenItsStartWasTimed() {
        assertTrue(
            RaceClockerPollLogic.startDetected(
                listOf(row(result = "In race...", start = LocalTime.of(10, 3)))
            )
        )
    }

    // --- Fingerabdruck ---

    @Test
    fun unchangedRowsKeepTheirFingerprint() {
        val id = UUID.randomUUID()
        val a = listOf(row(id = id, result = "3:21.4", start = LocalTime.of(10, 3)))
        val b = listOf(row(id = id, result = "3:21.4", start = LocalTime.of(10, 3)))

        assertEquals(RaceClockerPollLogic.fingerprint(a), RaceClockerPollLogic.fingerprint(b))
    }

    @Test
    fun everyFieldThatIsWrittenChangesTheFingerprint() {
        val id = UUID.randomUUID()
        val base = listOf(row(id = id, result = "3:21.4", start = LocalTime.of(10, 3)))

        assertNotEquals(
            RaceClockerPollLogic.fingerprint(base),
            RaceClockerPollLogic.fingerprint(listOf(row(id = id, result = "3:22.0", start = LocalTime.of(10, 3)))),
        )
        assertNotEquals(
            RaceClockerPollLogic.fingerprint(base),
            RaceClockerPollLogic.fingerprint(listOf(row(id = id, rank = 2, result = "3:21.4", start = LocalTime.of(10, 3)))),
        )
        assertNotEquals(
            RaceClockerPollLogic.fingerprint(base),
            RaceClockerPollLogic.fingerprint(
                listOf(row(id = id, result = "3:21.4", start = LocalTime.of(10, 3), penaltySeconds = 10))
            ),
        )
        assertNotEquals(
            RaceClockerPollLogic.fingerprint(base),
            RaceClockerPollLogic.fingerprint(
                listOf(row(id = id, result = "3:21.4", start = LocalTime.of(10, 3), penaltyNote = "Boje"))
            ),
        )
    }

    @Test
    fun theOrderTheRowsArriveInDoesNotMatter() {
        val first = row(result = "3:21.4")
        val second = row(rank = 2, result = "3:25.0")

        assertEquals(
            RaceClockerPollLogic.fingerprint(listOf(first, second)),
            RaceClockerPollLogic.fingerprint(listOf(second, first)),
        )
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag prüfen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && cd backend && ./mvnw -q test -Dtest=RaceClockerPollLogicTest
```

Erwartet: Kompilierfehler, `Unresolved reference: RaceClockerPollLogic`.

- [ ] **Step 3: Die Logik schreiben**

Datei `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/boundary/RaceClockerPollLogic.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.raceclocker.boundary

import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerFeedRow
import java.time.LocalDateTime

/**
 * Die Entscheidungen des automatischen RaceClocker-Abrufs, bewusst ohne Datenbank- und HTTP-Bezug —
 * wie [de.lambda9.ready2race.backend.app.competitionExecution.boundary.RaceClockerAssignmentLogic]
 * und aus demselben Grund: Am Renntag zählt, dass diese Regeln stimmen, und sie lassen sich nur
 * dann ohne laufende Umgebung prüfen.
 */
object RaceClockerPollLogic {

    /**
     * Kein Takt unter zwei Sekunden. Die Takte sind pro Veranstaltung einstellbar, und eine
     * versehentlich eingetragene `1` würde die Regatta in Dauerfeuer gegen raceclocker.com
     * verwandeln. Die Grenze steht hier statt nur im Formular, damit sie auch greift, wenn der Wert
     * auf anderem Weg in die Datenbank kommt.
     */
    const val MIN_INTERVAL_SECONDS = 2

    enum class PollMode { ACTIVE, UPCOMING }

    fun intervalSeconds(configured: Int): Int = configured.coerceAtLeast(MIN_INTERVAL_SECONDS)

    /**
     * Ob dieser Lauf überhaupt beobachtet wird.
     *
     * Ein aktiver Lauf immer — er kann längst vor oder nach seinem Plan laufen, und was tatsächlich
     * passiert, schlägt den Plan. Ein noch nicht aktiver nur im Fenster um seine geplante Startzeit:
     * ohne diese Grenze würde eine Veranstaltung in drei Monaten jede Minute abgefragt.
     *
     * Beide Grenzen zählen einschließlich. Ohne geplante Startzeit gibt es kein Fenster, also auch
     * keine Beobachtung — solche Läufe aktiviert weiterhin jemand von Hand, und ab dann greift der
     * erste Zweig.
     */
    fun isWatched(
        currentlyRunning: Boolean,
        startTime: LocalDateTime?,
        now: LocalDateTime,
        watchBeforeMinutes: Int,
        watchAfterMinutes: Int,
    ): Boolean = when {
        currentlyRunning -> true
        startTime == null -> false
        else -> !now.isBefore(startTime.minusMinutes(watchBeforeMinutes.toLong())) &&
            !now.isAfter(startTime.plusMinutes(watchAfterMinutes.toLong()))
    }

    /**
     * Der Takt gilt für die ganze Veranstaltung, nicht je Lauf: Ein Abruf holt ohnehin das ganze
     * Rennen. Sobald ein einziger Lauf aktiv ist, lohnt sich der schnelle Takt für alle.
     */
    fun modeFor(anyRunning: Boolean): PollMode = if (anyRunning) PollMode.ACTIVE else PollMode.UPCOMING

    /** Noch nie abgerufen heißt sofort fällig — beim Start des Servers soll nicht erst gewartet werden. */
    fun isDue(lastPolledAt: LocalDateTime?, now: LocalDateTime, intervalSeconds: Int): Boolean =
        lastPolledAt == null || !now.isBefore(lastPolledAt.plusSeconds(intervalSeconds.toLong()))

    /**
     * Ob der Feed für diesen Lauf sagt, dass er losgegangen ist.
     *
     * Zwei Belege: eine gemessene Startzeit oder ein verwertbares Ergebnis. Die Startzeit ist der
     * übliche Fall; das Ergebnis fängt den ab, bei dem die Zeitnahme den Start nicht erfasst hat
     * (Nachtrag von Hand, Zeitfahren ohne Startstempel) — ein Boot mit Zeit ist unstrittig gefahren.
     *
     * Die Fortschritts-Texte von RaceClocker (`Not started`, `In race...`) belegen für sich nichts:
     * `Not started` steht direkt nach dem Startlisten-Import in jeder Zeile, und `In race...` setzt
     * RaceClocker mit dem Start — aber dann liegt auch eine Startzeit vor, die hier zählt. Der
     * Umweg über [RaceClockerFeedRow.hasResult] hält diese Unterscheidung an genau einer Stelle.
     */
    fun startDetected(rows: List<RaceClockerFeedRow>): Boolean =
        rows.any { it.start != null || it.hasResult }

    /**
     * Ein Kurzwert über alles, was aus diesen Zeilen in die Datenbank wandert. Ist er seit dem
     * letzten Abruf unverändert, schreibt der Job nichts — sonst sähe jeder aktive Lauf alle fünf
     * Sekunden „bearbeitet" aus, obwohl sich nichts getan hat.
     *
     * [RaceClockerFeedRow.name] steht bewusst nicht drin: Er wird nirgends übernommen. [rank] dagegen
     * schon, denn aus der Reihenfolge der Zeilen entstehen die Bahnen
     * ([RaceClockerFeedRow.lanesByRow]). Sortiert, weil die Reihenfolge innerhalb der Antwort keine
     * Aussage trägt — [rank] trägt sie.
     */
    fun fingerprint(rows: List<RaceClockerFeedRow>): String =
        rows.map { row ->
            listOf(
                row.ids.map { it.toString() }.sorted().joinToString("/"),
                row.rank?.toString() ?: "",
                row.result?.trim() ?: "",
                row.start?.toString() ?: "",
                row.penaltySeconds?.toString() ?: "",
                row.penaltyNote ?: "",
            ).joinToString("|")
        }.sorted().joinToString(";")
}
```

- [ ] **Step 4: Test laufen lassen und Erfolg prüfen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && cd backend && ./mvnw -q test -Dtest=RaceClockerPollLogicTest
```

Erwartet: `BUILD SUCCESS`, alle Tests grün.

- [ ] **Step 5: Committen**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/boundary/RaceClockerPollLogic.kt backend/src/test/kotlin/de/lambda9/ready2race/backend/app/raceclocker/RaceClockerPollLogicTest.kt && git commit -m "Decide when the RaceClocker poll runs and when it writes"
```

---

## Task 2: Schema — acht Spalten und die View

**Files:**
- Create: `backend/src/main/resources/db/migration/V202608071600__raceclocker_polling.sql`
- Modify: `backend/src/main/resources/db/migration/afterMigrate.sql` (View `competition_match_with_teams`, ab Zeile 821)

**Interfaces:**
- Produces: die jOOQ-Felder `EVENT.RACECLOCKER_AUTO_PULL`, `EVENT.RACECLOCKER_INTERVAL_ACTIVE_SECONDS`, `EVENT.RACECLOCKER_INTERVAL_UPCOMING_SECONDS`, `EVENT.RACECLOCKER_WATCH_BEFORE_MINUTES`, `EVENT.RACECLOCKER_WATCH_AFTER_MINUTES`, `COMPETITION_MATCH.RACECLOCKER_POLLED_AT`, `COMPETITION_MATCH.RACECLOCKER_POLL_ERROR`, `COMPETITION_MATCH.RACECLOCKER_AUTO_PAUSED_AT` sowie dieselben drei Felder auf `COMPETITION_MATCH_WITH_TEAMS`.

- [ ] **Step 1: Die Migration schreiben**

Datei `backend/src/main/resources/db/migration/V202608071600__raceclocker_polling.sql`:

```sql
set search_path to ready2race, pg_catalog, public;

-- Automatischer RaceClocker-Abruf (Entwurf 2026-08-07).
--
-- Die Takte stehen auf der Veranstaltung und nicht auf dem Wettkampf: Ein Abruf holt immer das
-- ganze RaceClocker-Rennen, und das Rennen wird pro Veranstaltung angelegt (siehe V202608062100).
-- Zwei Wettkämpfe am selben Rennen mit verschiedenen Takten wären nicht auflösbar.
alter table event
    add column raceclocker_auto_pull                 boolean not null default false,
    add column raceclocker_interval_active_seconds   int     not null default 5,
    add column raceclocker_interval_upcoming_seconds int     not null default 60,
    add column raceclocker_watch_before_minutes      int     not null default 15,
    add column raceclocker_watch_after_minutes       int     not null default 120;

-- Aus für Bestandsdaten: Eine Veranstaltung, die bisher von Hand nachgezogen wurde, soll nicht
-- durch eine Migration anfangen, sich selbst zu schreiben. Die Vorgaben der übrigen vier Spalten
-- gelten damit erst, wenn jemand die Automatik bewusst einschaltet.

alter table competition_match
    -- Wann zuletzt VERSUCHT wurde abzurufen, nicht wann zuletzt etwas geschrieben wurde: Am
    -- Renntag ist genau die Frage "läuft der Abruf überhaupt noch" die wichtige. Ein Lauf ohne
    -- Änderungen sieht sonst aus wie einer, dessen Abruf steht.
    add column raceclocker_polled_at      timestamp,
    -- Der ErrorCode des letzten Fehlschlags (z. B. RACECLOCKER_UNREACHABLE), null = in Ordnung.
    -- Als Code und nicht als Text, damit die Oberfläche ihn übersetzen kann statt eine englische
    -- Server-Meldung anzuzeigen.
    add column raceclocker_poll_error     text,
    -- Gesetzt, sobald jemand Ergebnisse von Hand einträgt oder eine Datei hochlädt. Die Automatik
    -- lässt den Lauf dann in Ruhe, bis er in der Oberfläche wieder freigegeben wird - das
    -- Regattabüro soll nicht gegen den Job anschreiben müssen.
    add column raceclocker_auto_paused_at timestamp;

-- Die beobachteten Läufe werden über Veranstaltung, finished_at und start_time gesucht; ein
-- eigener Index lohnt bei der Zeilenzahl eines Regattaprogramms nicht.
```

- [ ] **Step 2: Die View erweitern**

In `backend/src/main/resources/db/migration/afterMigrate.sql` die View `competition_match_with_teams` (beginnt bei `create view competition_match_with_teams as`) ersetzen durch:

```sql
create view competition_match_with_teams as
select cm.competition_setup_match,
       cm.start_time,
       cm.currently_running,
       cm.raceclocker_polled_at,
       cm.raceclocker_poll_error,
       cm.raceclocker_auto_paused_at,
       coalesce(array_agg(cmtwr) filter (where cmtwr.id is not null), '{}') as teams,
       cmtwr.mixed_team_term                                                as mixed_team_term
from competition_match cm
         left join competition_match_team_with_registration cmtwr
                   on cm.competition_setup_match = cmtwr.competition_match
group by cm.competition_setup_match, cmtwr.mixed_team_term
;
```

`group by` bleibt unverändert: `cm.competition_setup_match` ist der Primärschlüssel von `competition_match`, PostgreSQL erlaubt darüber alle weiteren Spalten derselben Zeile ohne Gruppierung.

- [ ] **Step 3: Migration und Codegen laufen lassen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && cd backend && docker compose up -d build-db && ./mvnw -q generate-sources
```

Erwartet: `BUILD SUCCESS`. Schlägt Flyway mit einem Validate-/Out-of-order-Fehler fehl, hat ein anderer Worktree die geteilte `build-db` mit anderen Migrationen belegt — dann eine Wegwerf-Datenbank benutzen und den geteilten Container in Ruhe lassen:

```bash
docker run -d --rm --name r2r-poll-db -e POSTGRES_USER=developer -e POSTGRES_PASSWORD=sql -e POSTGRES_DB=ready2race-build -p 7662:5432 postgres:17 && sleep 5 && cd backend && ./mvnw -q generate-sources -Ddatabase.url=jdbc:postgresql://localhost:7662/ready2race-build
```

- [ ] **Step 4: Die generierten Felder prüfen**

```bash
grep -c "RACECLOCKER_AUTO_PULL\|RACECLOCKER_POLLED_AT\|RACECLOCKER_AUTO_PAUSED_AT" backend/target/generated-sources/jooq/de/lambda9/ready2race/backend/database/generated/tables/Event.kt backend/target/generated-sources/jooq/de/lambda9/ready2race/backend/database/generated/tables/CompetitionMatch.kt backend/target/generated-sources/jooq/de/lambda9/ready2race/backend/database/generated/tables/CompetitionMatchWithTeams.kt
```

Erwartet: jede der drei Dateien meldet einen Treffer > 0.

- [ ] **Step 5: Committen**

```bash
git add backend/src/main/resources/db/migration/V202608071600__raceclocker_polling.sql backend/src/main/resources/db/migration/afterMigrate.sql && git commit -m "Add the columns the automatic RaceClocker poll needs"
```

---

## Task 3: Konfiguration im Backend

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/timingConfig/entity/EventTimingConfigDto.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/timingConfig/entity/EventTimingConfigRequest.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/timingConfig/boundary/TimingConfigService.kt`
- Modify: `backend/src/main/resources/openapi/documentation.yaml` (Schemata `EventTimingConfigDto` ab Zeile 12010 und `EventTimingConfigRequest` ab Zeile 12085)
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/timingConfig/EventTimingConfigRequestTest.kt`

**Interfaces:**
- Consumes: `RaceClockerPollLogic.MIN_INTERVAL_SECONDS` aus Task 1.
- Produces: `EventTimingConfigDto` und `EventTimingConfigRequest` mit den Feldern `autoPull: Boolean`, `intervalActiveSeconds: Int`, `intervalUpcomingSeconds: Int`, `watchBeforeMinutes: Int`, `watchAfterMinutes: Int`.

- [ ] **Step 1: Den Test schreiben**

Datei `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/timingConfig/EventTimingConfigRequestTest.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.timingConfig

import de.lambda9.ready2race.backend.app.timingConfig.entity.EventTimingConfigRequest
import de.lambda9.ready2race.backend.app.timingConfig.entity.TimingSystem
import de.lambda9.ready2race.backend.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Die Validierung der Abruf-Einstellungen. Sie soll den Tippfehler beim Bearbeiten abfangen — die
 * harte Untergrenze im Job (RaceClockerPollLogic.intervalSeconds) bleibt trotzdem bestehen, weil
 * Werte auch auf anderem Weg in die Datenbank kommen können.
 */
class EventTimingConfigRequestTest {

    private fun request(
        intervalActiveSeconds: Int = 5,
        intervalUpcomingSeconds: Int = 60,
        watchBeforeMinutes: Int = 15,
        watchAfterMinutes: Int = 120,
    ) = EventTimingConfigRequest(
        timingSystem = TimingSystem.RACECLOCKER,
        timeTrialResultsUrl = null,
        heatsResultsUrl = null,
        startlistConfigQualification = null,
        startlistConfigRounds = null,
        resultImportConfig = null,
        autoPull = true,
        intervalActiveSeconds = intervalActiveSeconds,
        intervalUpcomingSeconds = intervalUpcomingSeconds,
        watchBeforeMinutes = watchBeforeMinutes,
        watchAfterMinutes = watchAfterMinutes,
    )

    @Test
    fun theDefaultsAreValid() {
        assertEquals(ValidationResult.Valid, request().validate())
    }

    @Test
    fun anIntervalBelowTheFloorIsRejected() {
        assertTrue(request(intervalActiveSeconds = 1).validate() is ValidationResult.Invalid)
        assertTrue(request(intervalUpcomingSeconds = 0).validate() is ValidationResult.Invalid)
    }

    @Test
    fun negativeWindowsAreRejected() {
        assertTrue(request(watchBeforeMinutes = -1).validate() is ValidationResult.Invalid)
        assertTrue(request(watchAfterMinutes = -1).validate() is ValidationResult.Invalid)
    }

    @Test
    fun aWindowOfZeroMinutesIsAllowed() {
        assertEquals(ValidationResult.Valid, request(watchBeforeMinutes = 0, watchAfterMinutes = 0).validate())
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag prüfen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && cd backend && ./mvnw -q test -Dtest=EventTimingConfigRequestTest
```

Erwartet: Kompilierfehler — `EventTimingConfigRequest` kennt die fünf Parameter nicht.

- [ ] **Step 3: Request erweitern**

In `EventTimingConfigRequest.kt` die fünf Felder ergänzen und validieren. Der `data class`-Kopf und die `validate`-Methode werden zu:

```kotlin
data class EventTimingConfigRequest(
    val timingSystem: TimingSystem?,
    val timeTrialResultsUrl: String?,
    val heatsResultsUrl: String?,
    val startlistConfigQualification: UUID?,
    val startlistConfigRounds: UUID?,
    val resultImportConfig: UUID?,
    /**
     * Der automatische Abruf und seine Takte. Anders als die Felder darüber nicht optional: Sie
     * haben in der Datenbank eine Vorgabe (Migration V202608071600), und ein `null` hier hieße
     * "unverändert lassen" - eine Bedeutung, die das Formular nicht braucht und die beim Ausschalten
     * der Automatik gefährlich wäre.
     */
    val autoPull: Boolean,
    val intervalActiveSeconds: Int,
    val intervalUpcomingSeconds: Int,
    val watchBeforeMinutes: Int,
    val watchAfterMinutes: Int,
) : Validatable {

    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            validateUrl(timeTrialResultsUrl, "timeTrialResultsUrl"),
            validateUrl(heatsResultsUrl, "heatsResultsUrl"),
            validateInterval(intervalActiveSeconds, "intervalActiveSeconds"),
            validateInterval(intervalUpcomingSeconds, "intervalUpcomingSeconds"),
            validateMinutes(watchBeforeMinutes, "watchBeforeMinutes"),
            validateMinutes(watchAfterMinutes, "watchAfterMinutes"),
        )
```

Im `companion object` darunter, neben `validateUrl`:

```kotlin
        /**
         * Dieselbe Grenze, die der Job ohnehin erzwingt - hier nur, damit sie beim Speichern
         * sichtbar wird statt still zu greifen.
         */
        private fun validateInterval(value: Int, field: String): ValidationResult =
            if (value < RaceClockerPollLogic.MIN_INTERVAL_SECONDS) {
                ValidationResult.Invalid.Message { "$field must be at least ${RaceClockerPollLogic.MIN_INTERVAL_SECONDS} seconds" }
            } else {
                ValidationResult.Valid
            }

        /** Null Minuten sind erlaubt: "erst ab der geplanten Startzeit beobachten" ist eine Ansage. */
        private fun validateMinutes(value: Int, field: String): ValidationResult =
            if (value < 0) {
                ValidationResult.Invalid.Message { "$field must not be negative" }
            } else {
                ValidationResult.Valid
            }
```

Import ergänzen: `import de.lambda9.ready2race.backend.app.raceclocker.boundary.RaceClockerPollLogic`.

Im `example` am Ende des companion objects die fünf Werte ergänzen:

```kotlin
                autoPull = true,
                intervalActiveSeconds = 5,
                intervalUpcomingSeconds = 60,
                watchBeforeMinutes = 15,
                watchAfterMinutes = 120,
```

- [ ] **Step 4: Test laufen lassen und Erfolg prüfen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && cd backend && ./mvnw -q test -Dtest=EventTimingConfigRequestTest
```

Erwartet: alle vier Tests grün.

- [ ] **Step 5: Dto und Service erweitern**

In `EventTimingConfigDto.kt` dieselben fünf Felder ergänzen (`autoPull: Boolean`, `intervalActiveSeconds: Int`, `intervalUpcomingSeconds: Int`, `watchBeforeMinutes: Int`, `watchAfterMinutes: Int`), jeweils vor `deviatingCompetitions`.

In `TimingConfigService.getEventTimingConfig` das Dto füllen:

```kotlin
                    autoPull = event.raceclockerAutoPull,
                    intervalActiveSeconds = event.raceclockerIntervalActiveSeconds,
                    intervalUpcomingSeconds = event.raceclockerIntervalUpcomingSeconds,
                    watchBeforeMinutes = event.raceclockerWatchBeforeMinutes,
                    watchAfterMinutes = event.raceclockerWatchAfterMinutes,
```

In `TimingConfigService.updateEventTimingConfig` im `EventRepo.update`-Block schreiben:

```kotlin
            raceclockerAutoPull = request.autoPull
            raceclockerIntervalActiveSeconds = request.intervalActiveSeconds
            raceclockerIntervalUpcomingSeconds = request.intervalUpcomingSeconds
            raceclockerWatchBeforeMinutes = request.watchBeforeMinutes
            raceclockerWatchAfterMinutes = request.watchAfterMinutes
```

- [ ] **Step 6: OpenAPI ergänzen**

In `backend/src/main/resources/openapi/documentation.yaml` in **beiden** Schemata `EventTimingConfigDto` und `EventTimingConfigRequest` unter `properties` ergänzen (im Dto vor `deviatingCompetitions`):

```yaml
        autoPull:
          type: boolean
          description: Whether the background job pulls results for this event on its own.
        intervalActiveSeconds:
          type: integer
          description: Poll interval while at least one match of this event is running. Never goes below 2 seconds.
        intervalUpcomingSeconds:
          type: integer
          description: Poll interval while only upcoming matches are watched. Never goes below 2 seconds.
        watchBeforeMinutes:
          type: integer
          description: How long before its planned start an upcoming match is watched.
        watchAfterMinutes:
          type: integer
          description: How long after its planned start a match that is not active yet is still watched.
```

Und in beiden Schemata am Ende ein `required`-Block (im Dto zusätzlich zu den bestehenden Zeilen, falls vorhanden):

```yaml
      required:
        - autoPull
        - intervalActiveSeconds
        - intervalUpcomingSeconds
        - watchBeforeMinutes
        - watchAfterMinutes
```

- [ ] **Step 7: Übersetzen und committen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && cd backend && ./mvnw -q test-compile && ./mvnw -q test
```

Erwartet: `BUILD SUCCESS`, alle Tests grün.

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/app/timingConfig backend/src/test/kotlin/de/lambda9/ready2race/backend/app/timingConfig backend/src/main/resources/openapi/documentation.yaml && git commit -m "Configure the automatic pull per event"
```

---

## Task 4: Konfiguration im Formular

**Files:**
- Modify: `frontend/src/components/event/timing/eventTimingConfigForm.ts`
- Modify: `frontend/src/components/event/timing/eventTimingConfigForm.test.ts`
- Modify: `frontend/src/components/event/timing/EventTimingConfig.tsx`
- Modify: `frontend/src/i18n/de/translations.json`, `frontend/src/i18n/en/translations.json`, `frontend/src/i18n/da/translations.json`
- Generated: `frontend/src/api/types.gen.ts`, `frontend/src/api/sdk.gen.ts`

**Interfaces:**
- Consumes: `EventTimingConfigDto` / `EventTimingConfigRequest` aus Task 3, über `npm run generate` in `@api/types.gen.ts`.
- Produces: `EventTimingForm` mit den zusätzlichen Feldern `autoPull: boolean`, `intervalActiveSeconds: number`, `intervalUpcomingSeconds: number`, `watchBeforeMinutes: number`, `watchAfterMinutes: number`.

- [ ] **Step 1: Client neu generieren**

```bash
cd frontend && npm run generate
```

Erwartet: `types.gen.ts` enthält danach `autoPull`. Prüfen mit:

```bash
grep -n "autoPull" frontend/src/api/types.gen.ts | head
```

- [ ] **Step 2: Den Test schreiben**

In `frontend/src/components/event/timing/eventTimingConfigForm.test.ts` ergänzen (die bestehenden Tests bleiben; die Fixtures in ihnen brauchen die neuen Pflichtfelder — beim Anpassen `autoPull: false, intervalActiveSeconds: 5, intervalUpcomingSeconds: 60, watchBeforeMinutes: 15, watchAfterMinutes: 120` ergänzen):

```ts
describe('automatischer Abruf', () => {
    it('übernimmt die Abruf-Einstellungen aus dem Dto', () => {
        const form = mapDtoToEventTimingForm({
            timingSystem: 'RACECLOCKER',
            timeTrialResultsUrl: null,
            heatsResultsUrl: null,
            startlistConfigQualification: null,
            startlistConfigRounds: null,
            resultImportConfig: null,
            autoPull: true,
            intervalActiveSeconds: 3,
            intervalUpcomingSeconds: 90,
            watchBeforeMinutes: 20,
            watchAfterMinutes: 60,
            deviatingCompetitions: [],
        })

        expect(form.autoPull).toBe(true)
        expect(form.intervalActiveSeconds).toBe(3)
        expect(form.intervalUpcomingSeconds).toBe(90)
        expect(form.watchBeforeMinutes).toBe(20)
        expect(form.watchAfterMinutes).toBe(60)
    })

    it('reicht die Abruf-Einstellungen unverändert an den Request weiter', () => {
        const request = mapEventTimingFormToRequest({
            ...emptyEventTimingForm,
            timingSystem: 'RACECLOCKER',
            autoPull: true,
            intervalActiveSeconds: 3,
            intervalUpcomingSeconds: 90,
            watchBeforeMinutes: 20,
            watchAfterMinutes: 60,
        })

        expect(request.autoPull).toBe(true)
        expect(request.intervalActiveSeconds).toBe(3)
        expect(request.intervalUpcomingSeconds).toBe(90)
    })

    // Ohne RaceClocker gibt es keinen Feed, den man abrufen könnte - der Schalter darf dann nicht
    // still eingeschaltet gespeichert bleiben, sonst steht in der Datenbank eine Automatik, die
    // die Oberfläche gar nicht mehr anzeigt.
    it('schaltet den Abruf ab, wenn das System nicht RaceClocker ist', () => {
        const request = mapEventTimingFormToRequest({
            ...emptyEventTimingForm,
            timingSystem: 'WEBSCORER',
            autoPull: true,
        })

        expect(request.autoPull).toBe(false)
    })
})
```

- [ ] **Step 3: Test laufen lassen und Fehlschlag prüfen**

```bash
cd frontend && npm run test -- eventTimingConfigForm
```

Erwartet: FAIL — `autoPull` existiert im Formulartyp nicht.

- [ ] **Step 4: Das Formularmodell erweitern**

In `frontend/src/components/event/timing/eventTimingConfigForm.ts`:

```ts
export type EventTimingForm = {
    timingSystem: EventTimingFormSystem
    timeTrialResultsUrl: string
    heatsResultsUrl: string
    startlistConfigQualification: AutocompleteOption
    startlistConfigRounds: AutocompleteOption
    resultImportConfig: AutocompleteOption
    /**
     * Der automatische Abruf. Nur bei RaceClocker sichtbar und speicherbar — Webscorer hat keinen
     * Ergebnis-Feed, den ein Job abholen könnte.
     */
    autoPull: boolean
    intervalActiveSeconds: number
    intervalUpcomingSeconds: number
    watchBeforeMinutes: number
    watchAfterMinutes: number
}

export const emptyEventTimingForm: EventTimingForm = {
    timingSystem: 'NONE',
    timeTrialResultsUrl: '',
    heatsResultsUrl: '',
    startlistConfigQualification: null,
    startlistConfigRounds: null,
    resultImportConfig: null,
    autoPull: false,
    intervalActiveSeconds: 5,
    intervalUpcomingSeconds: 60,
    watchBeforeMinutes: 15,
    watchAfterMinutes: 120,
}
```

In `mapDtoToEventTimingForm` ergänzen:

```ts
    autoPull: dto.autoPull,
    intervalActiveSeconds: dto.intervalActiveSeconds,
    intervalUpcomingSeconds: dto.intervalUpcomingSeconds,
    watchBeforeMinutes: dto.watchBeforeMinutes,
    watchAfterMinutes: dto.watchAfterMinutes,
```

In `mapEventTimingFormToRequest` im Rückgabeobjekt ergänzen (`raceClocker` ist dort schon definiert):

```ts
        // Die Takte werden immer mitgeschickt: Sie haben in der Datenbank eine Vorgabe, und ein
        // Abschalten des Systems soll die eingestellten Werte nicht verlieren.
        autoPull: raceClocker && form.autoPull,
        intervalActiveSeconds: form.intervalActiveSeconds,
        intervalUpcomingSeconds: form.intervalUpcomingSeconds,
        watchBeforeMinutes: form.watchBeforeMinutes,
        watchAfterMinutes: form.watchAfterMinutes,
```

- [ ] **Step 5: Test laufen lassen und Erfolg prüfen**

```bash
cd frontend && npm run test -- eventTimingConfigForm
```

Erwartet: alle Tests grün.

- [ ] **Step 6: Die Eingabefelder ergänzen**

In `frontend/src/components/event/timing/EventTimingConfig.tsx` die Importe erweitern:

```ts
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import FormInputSwitch from '@components/form/input/FormInputSwitch.tsx'
```

Und innerhalb des `{timingSystem === 'RACECLOCKER' && (<Stack spacing={4}> …` -Blocks, hinter dem Feld `heatsResultsUrl`, einfügen:

```tsx
                            <Divider />
                            <FormInputSwitch
                                name={'autoPull'}
                                label={t('event.timing.autoPull.enabled')}
                                horizontal
                            />
                            <Typography variant={'body2'} color={'text.secondary'}>
                                <Trans i18nKey={'event.timing.autoPull.hint'} />
                            </Typography>
                            {autoPull && (
                                <Stack spacing={4}>
                                    <FormInputNumber
                                        name={'intervalActiveSeconds'}
                                        label={t('event.timing.autoPull.intervalActive')}
                                        min={2}
                                        integer
                                        transform={{
                                            output: value =>
                                                value.target.value !== ''
                                                    ? Number(value.target.value)
                                                    : null,
                                        }}
                                    />
                                    <FormInputNumber
                                        name={'intervalUpcomingSeconds'}
                                        label={t('event.timing.autoPull.intervalUpcoming')}
                                        min={2}
                                        integer
                                        transform={{
                                            output: value =>
                                                value.target.value !== ''
                                                    ? Number(value.target.value)
                                                    : null,
                                        }}
                                    />
                                    <FormInputNumber
                                        name={'watchBeforeMinutes'}
                                        label={t('event.timing.autoPull.watchBefore')}
                                        min={0}
                                        integer
                                        transform={{
                                            output: value =>
                                                value.target.value !== ''
                                                    ? Number(value.target.value)
                                                    : null,
                                        }}
                                    />
                                    <FormInputNumber
                                        name={'watchAfterMinutes'}
                                        label={t('event.timing.autoPull.watchAfter')}
                                        min={0}
                                        integer
                                        transform={{
                                            output: value =>
                                                value.target.value !== ''
                                                    ? Number(value.target.value)
                                                    : null,
                                        }}
                                    />
                                </Stack>
                            )}
```

Und neben dem bestehenden `timingSystem`-Watch ergänzen:

```ts
    const autoPull = useWatch({control: formContext.control, name: 'autoPull'})
```

- [ ] **Step 7: Übersetzungen ergänzen**

In `frontend/src/i18n/de/translations.json` unter `event.timing` einfügen:

```json
"autoPull": {
  "enabled": "Ergebnisse automatisch abrufen",
  "hint": "Läuft ein Lauf, holt der Server die Ergebnisse im schnellen Takt. Bevorstehende Läufe werden im langsamen Takt beobachtet, um ihren Start zu erkennen; beendete Läufe gar nicht.",
  "intervalActive": "Takt bei aktivem Lauf (Sekunden)",
  "intervalUpcoming": "Takt bei bevorstehendem Lauf (Sekunden)",
  "watchBefore": "Beobachten ab (Minuten vor der geplanten Startzeit)",
  "watchAfter": "Beobachten bis (Minuten nach der geplanten Startzeit)"
}
```

In `frontend/src/i18n/en/translations.json` an derselben Stelle:

```json
"autoPull": {
  "enabled": "Pull results automatically",
  "hint": "While a match is running, the server pulls its results at the fast interval. Upcoming matches are watched at the slow interval to detect their start; finished matches are not polled at all.",
  "intervalActive": "Interval while a match is running (seconds)",
  "intervalUpcoming": "Interval while a match is upcoming (seconds)",
  "watchBefore": "Start watching (minutes before the planned start)",
  "watchAfter": "Stop watching (minutes after the planned start)"
}
```

In `frontend/src/i18n/da/translations.json` an derselben Stelle:

```json
"autoPull": {
  "enabled": "Hent resultater automatisk",
  "hint": "Når et heat er i gang, henter serveren resultaterne i det hurtige interval. Kommende heats overvåges i det langsomme interval for at opdage deres start; afsluttede heats hentes ikke.",
  "intervalActive": "Interval ved igangværende heat (sekunder)",
  "intervalUpcoming": "Interval ved kommende heat (sekunder)",
  "watchBefore": "Overvåg fra (minutter før planlagt start)",
  "watchAfter": "Overvåg til (minutter efter planlagt start)"
}
```

- [ ] **Step 8: Übersetzen, Tests und Lint**

```bash
cd frontend && npx tsc -b && npm run test -- eventTimingConfigForm && npm run lint
```

Erwartet: keine Typfehler, Tests grün, Lint ohne Fehler.

- [ ] **Step 9: Committen**

```bash
git add frontend/src && git commit -m "Offer the automatic pull in the event's timing tab"
```

---

## Task 5: Anwendungslogik aus dem Endpunkt herauslösen

Kein Verhaltenswechsel. Danach benutzen Knopf und Job denselben Code.

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/boundary/CompetitionExecutionService.kt` (Funktion `updateMatchResultFromRaceClocker`, ab Zeile 886)

**Interfaces:**
- Consumes: `RaceClockerAssignmentLogic.assignFeedRows`, `RaceClockerFeedRow`, `RaceClockerMatchTarget`, `CompetitionMatchWithTeams`.
- Produces:
  ```kotlin
  fun applyRaceClockerRows(
      match: CompetitionMatchWithTeams,
      matchId: UUID,
      target: RaceClockerMatchTarget,
      rows: List<RaceClockerFeedRow>,
      userId: UUID,
  ): App<ServiceError, ApiResponse.NoData>
  ```
  Sichtbarkeit: `internal` reicht nicht — der Job liegt in einem anderen Paket, also `fun` (public) im `object CompetitionExecutionService`.

- [ ] **Step 1: Die bestehenden Tests als Netz laufen lassen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && cd backend && ./mvnw -q test
```

Erwartet: `BUILD SUCCESS`. Das ist der Ausgangszustand, gegen den das Refactoring gemessen wird.

- [ ] **Step 2: Die reine Anwendungslogik herausschneiden**

In `CompetitionExecutionService.kt` `updateMatchResultFromRaceClocker` durch diese beiden Funktionen ersetzen. Der gesamte Rumpf ab „Duplikate prüfen" wandert unverändert nach `applyRaceClockerRows`:

```kotlin
    /**
     * Pulls the results of a single match from RaceClocker's public results feed.
     *
     * Holt den Feed und reicht ihn an [applyRaceClockerRows] weiter. Die Trennung ist der Punkt: Der
     * automatische Abruf (`RaceClockerPollService`) holt denselben Feed einmal je Rennen für alle
     * Läufe gemeinsam und ruft dann dieselbe Anwendungslogik auf. Läge sie hier im Endpunkt, gäbe es
     * zwei Wege, Ergebnisse zu schreiben, und sie würden auseinanderlaufen.
     */
    suspend fun CallComprehensionScope.updateMatchResultFromRaceClocker(
        eventId: UUID,
        competitionId: UUID,
        matchId: UUID,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> {
        !EventService.checkIsChallengeEvent(eventId).onTrueFail { CompetitionExecutionError.IsChallengeEvent }

        val match = !checkUpdateMatchResult(competitionId, matchId, byeError = RaceClockerError.MatchIsBye)

        val target = !CompetitionMatchRepo.getForRaceClockerPull(matchId).orDie()
            .onNullFail { CompetitionExecutionError.MatchNotFound }

        val urls = target.candidateUrls
        if (urls.isEmpty()) return KIO.fail(RaceClockerError.UrlMissing)

        val teams = match.teams.filter { !it.deregistered }

        // The round type only decides which race to look into *first*. Is a round timed as a time trial
        // without being marked as a qualification round (or the other way around), the match is simply
        // found in the other race instead of failing with a misleading error.
        var rows: List<RaceClockerFeedRow> = emptyList()
        for (rawUrl in urls) {
            val url = !RaceClockerFeed.normalizeUrl(rawUrl)
            rows = !RaceClockerFeed.fetch(RaceClockerFeed.feedUrl(url))
            if (assignFeedRows(rows, teams, target.waveName).isNotEmpty()) break
        }

        return applyRaceClockerRows(match, matchId, target, rows, userId)
    }

    /**
     * Schreibt einen bereits geholten RaceClocker-Feed auf einen Lauf: Zuordnung, Duplikatprüfung,
     * `started_at`, Bahnen, Zeiten und Plätze.
     *
     * Ohne HTTP und ohne `CallComprehensionScope`, damit der Hintergrund-Job sie aufrufen kann. Was
     * hier fehlschlägt, ist derselbe Fehler wie beim Knopf — der Job schreibt ihn nur in eine Spalte,
     * statt ihn zu beantworten.
     */
    fun applyRaceClockerRows(
        match: CompetitionMatchWithTeams,
        matchId: UUID,
        target: RaceClockerMatchTarget,
        rows: List<RaceClockerFeedRow>,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        val teams = match.teams.filter { !it.deregistered }
        val rowsByTeam = assignFeedRows(rows, teams, target.waveName)

        if (rowsByTeam.isEmpty()) return@comprehension KIO.fail(RaceClockerError.MatchNotInFeed(target.candidateUrls))

        // RaceClocker only ever inserts, it never updates: importing the same start list twice leaves
        // duplicate crews behind. Picking one of them silently would be a coin flip, so we refuse and
        // let the user clean up there.
        val duplicates = rowsByTeam.filterValues { it.size > 1 }
        if (duplicates.isNotEmpty()) {
            return@comprehension KIO.fail(
                RaceClockerError.DuplicateTeams(target.waveName, duplicates.values.map { it.first().name })
            )
        }

        // Crews without a usable result are skipped rather than treated as an error, so the pull can
        // be repeated as the heat progresses. "Ohne Ergebnis" heißt hier: weder Zeit noch echte
        // Ausscheidung - RaceClocker schreibt in dieselbe Spalte auch Verlaufszustände (`Not started`,
        // `In race...`), und ein solcher Text als Ausscheidungsgrund würde ein noch fahrendes Boot als
        // ausgeschieden markieren (siehe [RaceClockerFeedRow.noResultReason]). Ein bloßes
        // `result != null` reicht dafür nicht.
        //
        // Übersprungene Zeilen bleiben in der DB unangetastet: eine vom Schiedsrichter von Hand
        // eingetragene Ausscheidung darf ein Nachziehen des Laufs nicht wieder löschen.
        val withResult = rowsByTeam.mapNotNull { (registrationId, matchedRows) ->
            matchedRows.single().takeIf { it.hasResult }?.let { registrationId to it }
        }
        if (withResult.isEmpty()) return@comprehension KIO.fail(RaceClockerError.NoResults(target.waveName))

        // Externe Zeitnahme ist Quelle der Wahrheit: die früheste von RaceClocker gemessene
        // Startzeit unter den zugeordneten Booten überschreibt started_at bedingungslos, auch wenn
        // dort schon ein manueller Stempel steht (z. B. von LiveDashboardService.markMatchStarted) -
        // gleiche Regel wie beim Penalty-Überschreiben oben.
        val earliestStart = RaceClockerFeedRow.earliestStart(rowsByTeam.values.flatten())
        if (earliestStart != null) {
            val raceDay = match.startTime?.toLocalDate() ?: LocalDate.now()
            !CompetitionMatchRepo.update(matchId) {
                startedAt = raceDay.atTime(earliestStart)
                updatedBy = userId
                updatedAt = LocalDateTime.now()
            }.orDie()
        }

        !prepareForNewPlaces(matchId)

        // Lanes are taken from every assigned row, not just the timed ones: a heat is usually pulled
        // while boats are still on the water, and numbering only the finishers would push the rest
        // out of their lanes.
        !applyLanesFromFeed(matchId, rowsByTeam.mapValues { (_, matchedRows) -> matchedRows.single() }, userId)

        val parsed = withResult.map { (registrationId, row) ->
            ParsedTeamResult(
                registrationId = registrationId,
                // Lanes were written above from the feed's list positions; leaving this null keeps
                // [applyParsedTeamResults] from renumbering them off the bib, which stays with a boat
                // when it is moved and therefore no longer describes where it starts.
                startNumber = null,
                // Places are derived from the times further down - RaceClocker's "Rank" is a list
                // position, not a finishing rank.
                place = null,
                time = row.time,
                noResultReason = row.noResultReason,
                penaltySeconds = row.penaltySeconds,
                penaltyNote = row.penaltyNote,
            )
        }

        applyParsedTeamResults(match, matchId, parsed, userId)
    }
```

Wichtig: Die alte Schleife brach ab, sobald eine URL Treffer lieferte, und `rowsByTeam` blieb dann gefüllt. Die neue Schleife bricht nach derselben Bedingung ab, behält aber die **Zeilen** statt der Zuordnung — `applyRaceClockerRows` ordnet danach erneut zu. Das ist ein zusätzlicher, billiger Durchlauf über eine Liste im Speicher und der Preis dafür, dass Endpunkt und Job dieselbe Funktion aufrufen.

`applyParsedTeamResults` und `applyLanesFromFeed` sind `private` und bleiben es — sie werden nur von hier aufgerufen.

- [ ] **Step 3: Übersetzen und Tests laufen lassen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && cd backend && ./mvnw -q test
```

Erwartet: `BUILD SUCCESS`, unverändert dieselben Tests grün wie in Step 1.

- [ ] **Step 4: Committen**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/boundary/CompetitionExecutionService.kt && git commit -m "Separate applying a RaceClocker feed from fetching it"
```

---

## Task 6: Abfragen für den Job

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/entity/RaceClockerPollEvent.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/entity/RaceClockerPollCandidate.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/control/RaceClockerPollRepo.kt`

**Interfaces:**
- Consumes: `RaceClockerMatchTarget` (`waveName`, `isQualification`, `timeTrialUrl`, `heatsUrl`, `candidateUrls`), `WaveName.format`.
- Produces:
  ```kotlin
  data class RaceClockerPollEvent(
      val eventId: UUID,
      val intervalActiveSeconds: Int,
      val intervalUpcomingSeconds: Int,
      val watchBeforeMinutes: Int,
      val watchAfterMinutes: Int,
  )

  data class RaceClockerPollCandidate(
      val matchId: UUID,
      val competitionId: UUID,
      val startTime: LocalDateTime?,
      val currentlyRunning: Boolean,
      val target: RaceClockerMatchTarget,
  )

  object RaceClockerPollRepo {
      fun getPollingEvents(): JIO<List<RaceClockerPollEvent>>
      fun getCandidates(eventId: UUID): JIO<List<RaceClockerPollCandidate>>
      fun recordPoll(matchId: UUID, at: LocalDateTime, errorCode: String?): JIO<Int>
  }
  ```

- [ ] **Step 1: Die beiden Entitäten anlegen**

`backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/entity/RaceClockerPollEvent.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.raceclocker.entity

import java.util.UUID

/**
 * Eine Veranstaltung, die ihre RaceClocker-Ergebnisse selbst abholen lässt, samt ihrer Takte.
 * Bewusst ohne die URLs: Welches Rennen ein Lauf braucht, entscheidet die Runde, nicht die
 * Veranstaltung - das steht je Lauf in [RaceClockerPollCandidate].
 */
data class RaceClockerPollEvent(
    val eventId: UUID,
    val intervalActiveSeconds: Int,
    val intervalUpcomingSeconds: Int,
    val watchBeforeMinutes: Int,
    val watchAfterMinutes: Int,
)
```

`backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/entity/RaceClockerPollCandidate.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.raceclocker.entity

import java.time.LocalDateTime
import java.util.UUID

/**
 * Ein Lauf, der für den automatischen Abruf überhaupt in Frage kommt. Ob er auch beobachtet wird,
 * entscheidet erst `RaceClockerPollLogic.isWatched` anhand von [startTime] und [currentlyRunning] -
 * das Zeitfenster steht in der Logik und nicht in der Abfrage, damit es prüfbar bleibt.
 *
 * [matchId] ist wie überall `competition_match.competition_setup_match`.
 */
data class RaceClockerPollCandidate(
    val matchId: UUID,
    val competitionId: UUID,
    val startTime: LocalDateTime?,
    val currentlyRunning: Boolean,
    val target: RaceClockerMatchTarget,
)
```

- [ ] **Step 2: Das Repository anlegen**

`backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/control/RaceClockerPollRepo.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.raceclocker.control

import de.lambda9.ready2race.backend.app.competitionExecution.entity.WaveName
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerMatchTarget
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerPollCandidate
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerPollEvent
import de.lambda9.ready2race.backend.app.timingConfig.entity.TimingSystem
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.time.LocalDateTime
import java.util.UUID

/**
 * Die Abfragen des automatischen RaceClocker-Abrufs.
 *
 * Der Herzschlag des Jobs läuft im Sekundentakt, deshalb ist [getPollingEvents] absichtlich winzig:
 * Sie liefert nur die Veranstaltungen mit eingeschalteter Automatik (im Normalfall keine oder eine)
 * und entscheidet damit, ob der teurere [getCandidates] überhaupt gebraucht wird.
 */
object RaceClockerPollRepo {

    fun getPollingEvents() = Jooq.query {
        select(
            EVENT.ID,
            EVENT.RACECLOCKER_INTERVAL_ACTIVE_SECONDS,
            EVENT.RACECLOCKER_INTERVAL_UPCOMING_SECONDS,
            EVENT.RACECLOCKER_WATCH_BEFORE_MINUTES,
            EVENT.RACECLOCKER_WATCH_AFTER_MINUTES,
        )
            .from(EVENT)
            .where(EVENT.RACECLOCKER_AUTO_PULL.isTrue)
            // Challenge-Veranstaltungen haben keine Läufe im Sinne der Durchführung; der manuelle
            // Pull weist sie mit IsChallengeEvent ab, der Job lädt sie gar nicht erst.
            .and(EVENT.CHALLENGE_EVENT.isFalse)
            .fetch {
                RaceClockerPollEvent(
                    // Im Schema not null; die Projektion verliert nur die Garantie.
                    eventId = it[EVENT.ID]!!,
                    intervalActiveSeconds = it[EVENT.RACECLOCKER_INTERVAL_ACTIVE_SECONDS]!!,
                    intervalUpcomingSeconds = it[EVENT.RACECLOCKER_INTERVAL_UPCOMING_SECONDS]!!,
                    watchBeforeMinutes = it[EVENT.RACECLOCKER_WATCH_BEFORE_MINUTES]!!,
                    watchAfterMinutes = it[EVENT.RACECLOCKER_WATCH_AFTER_MINUTES]!!,
                )
            }
    }

    /**
     * Die Läufe einer Veranstaltung, die der Job anfassen darf. Dieselbe Coalesce-Kette wie
     * [de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo.getForRaceClockerPull]
     * - Wettkampf-Wert vor Veranstaltungs-Voreinstellung.
     *
     * Ausgeschlossen sind hier nur die harten Fälle: beendet, pausiert, kein RaceClocker, keine URL,
     * Slot abgesagt. Das Zeitfenster fehlt bewusst - es hängt an `now` und gehört in die prüfbare
     * Logik, nicht in SQL.
     */
    fun getCandidates(eventId: UUID) = Jooq.query {
        val timingSystem = DSL.coalesce(COMPETITION.TIMING_SYSTEM, EVENT.TIMING_SYSTEM).`as`("timing_system")
        val timeTrialUrl = DSL.coalesce(COMPETITION.RACECLOCKER_TT_RESULTS_URL, EVENT.RACECLOCKER_TT_RESULTS_URL)
            .`as`("time_trial_url")
        val heatsUrl = DSL.coalesce(COMPETITION.RACECLOCKER_HEATS_RESULTS_URL, EVENT.RACECLOCKER_HEATS_RESULTS_URL)
            .`as`("heats_url")

        select(
            COMPETITION_MATCH.COMPETITION_SETUP_MATCH,
            COMPETITION_MATCH.START_TIME,
            COMPETITION_MATCH.CURRENTLY_RUNNING,
            COMPETITION_SETUP_MATCH.NAME,
            COMPETITION_SETUP_ROUND.IS_QUALIFICATION,
            COMPETITION.ID.`as`("competition_id"),
            timeTrialUrl,
            heatsUrl,
        )
            .from(COMPETITION_MATCH)
            .join(COMPETITION_SETUP_MATCH)
            .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .join(EVENT).on(COMPETITION.EVENT.eq(EVENT.ID))
            .where(COMPETITION.EVENT.eq(eventId))
            .and(COMPETITION_MATCH.FINISHED_AT.isNull)
            .and(COMPETITION_MATCH.RACECLOCKER_AUTO_PAUSED_AT.isNull)
            .and(timingSystem.eq(TimingSystem.RACECLOCKER.name))
            .and(DSL.or(timeTrialUrl.isNotNull, heatsUrl.isNotNull))
            // Ein abgesagter Slot bleibt abgesagt, auch wenn in RaceClocker jemand die Welle
            // startet. Die volle Zustandsableitung (EventScheduleLogic.deriveSlotState) ist hier
            // nicht nötig: Ihre beiden anderen Eingaben sind an dieser Stelle konstant - der Lauf
            // existiert, also ist die Runde erzeugt -, und damit bleibt genau `skipped_at` übrig.
            .andNotExists(
                DSL.selectOne()
                    .from(EVENT_SCHEDULE_SLOT)
                    .where(EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH.eq(COMPETITION_MATCH.COMPETITION_SETUP_MATCH))
                    .and(EVENT_SCHEDULE_SLOT.SKIPPED_AT.isNotNull)
            )
            .fetch {
                RaceClockerPollCandidate(
                    matchId = it[COMPETITION_MATCH.COMPETITION_SETUP_MATCH]!!,
                    competitionId = it["competition_id", UUID::class.java],
                    startTime = it[COMPETITION_MATCH.START_TIME],
                    currentlyRunning = it[COMPETITION_MATCH.CURRENTLY_RUNNING] == true,
                    target = RaceClockerMatchTarget(
                        waveName = WaveName.format(it[COMPETITION_SETUP_MATCH.NAME], it[COMPETITION_MATCH.START_TIME]),
                        isQualification = it[COMPETITION_SETUP_ROUND.IS_QUALIFICATION] == true,
                        timeTrialUrl = it[timeTrialUrl],
                        heatsUrl = it[heatsUrl],
                    ),
                )
            }
    }

    /**
     * Der Ausgang eines Abrufversuchs. Rührt `updated_at`/`updated_by` bewusst NICHT an: Der
     * Zeitstempel sagt "der Job war hier", nicht "am Lauf hat sich etwas geändert" - sonst sähe im
     * Änderungsprotokoll alle fünf Sekunden jeder aktive Lauf bearbeitet aus.
     */
    fun recordPoll(matchId: UUID, at: LocalDateTime, errorCode: String?) = Jooq.query {
        update(COMPETITION_MATCH)
            .set(COMPETITION_MATCH.RACECLOCKER_POLLED_AT, at)
            .set(COMPETITION_MATCH.RACECLOCKER_POLL_ERROR, errorCode)
            .where(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(matchId))
            .execute()
    }
}
```

- [ ] **Step 3: Übersetzen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && cd backend && ./mvnw -q compile
```

Erwartet: `BUILD SUCCESS`. Meldet der Compiler bei `it["competition_id", UUID::class.java]` einen Fehler, stattdessen `it.get("competition_id", UUID::class.java)` schreiben — dieselbe Form wie in `EventScheduleService.linkedMatchIdOrNull`.

- [ ] **Step 4: Committen**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker && git commit -m "Query which matches the automatic pull may touch"
```

---

## Task 7: Der Job

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/boundary/RaceClockerPollService.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/Application.kt`

**Interfaces:**
- Consumes: `RaceClockerPollLogic` (Task 1), `RaceClockerPollRepo` (Task 6), `CompetitionExecutionService.applyRaceClockerRows` (Task 5), `CompetitionSetupService.getSetupRoundsWithMatches`, `RaceClockerFeed.normalizeUrl` / `.feedUrl` / `.fetch`, `SYSTEM_USER`.
- Produces: `suspend fun RaceClockerPollService.pollTick(env: JEnv): App<Nothing, DynamicIntervalJobState>`

- [ ] **Step 1: Den Job-Dienst schreiben**

`backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/boundary/RaceClockerPollService.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.raceclocker.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.CompetitionSetupService
import de.lambda9.ready2race.backend.app.raceclocker.boundary.RaceClockerPollLogic.PollMode
import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerFeed
import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerPollRepo
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerFeedRow
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerPollCandidate
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerPollEvent
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.kio.CoroutineComprehensionScope
import de.lambda9.ready2race.backend.kio.comprehension
import de.lambda9.ready2race.backend.schedule.DynamicIntervalJobState
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.getOrNull
import de.lambda9.tailwind.core.extensions.kio.orDie
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Der automatische RaceClocker-Abruf (Entwurf 2026-08-07).
 *
 * [pollTick] ist ein Herzschlag, kein Abruf: Er läuft im Sekundentakt und entscheidet je
 * Veranstaltung, ob ihr eingestellter Takt fällig ist. Der Umweg ist nötig, weil die Takte pro
 * Veranstaltung einstellbar sind - ein fest verdrahteter 5-Sekunden-Job könnte einen auf 3 Sekunden
 * gestellten Takt nicht bedienen, und ein Job je Veranstaltung wäre eine Job-Verwaltung, die es
 * hier nicht braucht.
 *
 * Der Job beendet nie einen Lauf (Entscheidung vom 04.08.2026) und schreibt an bevorstehenden
 * Läufen nichts außer der Aktivierung.
 */
object RaceClockerPollService {

    private val logger = KotlinLogging.logger {}

    private data class EventState(val lastPolledAt: LocalDateTime, val mode: PollMode)

    /**
     * Wann eine Veranstaltung zuletzt abgerufen wurde und in welchem Takt sie dabei lief. Im
     * Speicher statt in der Datenbank: Nach einem Neustart wird einmal sofort abgerufen, was
     * harmlos ist - der Wert interessiert niemanden außerhalb dieses Jobs.
     */
    private val eventStates = ConcurrentHashMap<UUID, EventState>()

    /**
     * Der zuletzt geschriebene Stand je Lauf. Ist er unverändert, schreibt der Job nichts - sonst
     * sähe jeder aktive Lauf alle fünf Sekunden "bearbeitet" aus.
     */
    private val fingerprints = ConcurrentHashMap<UUID, String>()

    suspend fun pollTick(env: JEnv): App<Nothing, DynamicIntervalJobState> = coroutineScope {
        comprehension(env) {
            val events = !RaceClockerPollRepo.getPollingEvents().orDie()

            // Keine Veranstaltung mit Automatik: lange Pause. Sobald eine eingeschaltet ist, läuft
            // der Herzschlag im Sekundentakt - die Abfrage darüber ist genau dafür so klein.
            if (events.isEmpty()) {
                eventStates.clear()
                fingerprints.clear()
                return@comprehension KIO.ok(DynamicIntervalJobState.Empty)
            }

            val now = LocalDateTime.now()
            events.forEach { event ->
                val state = eventStates[event.eventId]
                val interval = RaceClockerPollLogic.intervalSeconds(
                    when (state?.mode) {
                        PollMode.ACTIVE -> event.intervalActiveSeconds
                        else -> event.intervalUpcomingSeconds
                    }
                )
                if (RaceClockerPollLogic.isDue(state?.lastPolledAt, now, interval)) {
                    pollEvent(event, now)
                }
            }

            KIO.ok(DynamicIntervalJobState.Processed)
        }
    }

    /**
     * Ein Abruf für eine Veranstaltung: beobachtete Läufe bestimmen, jede benötigte Adresse genau
     * einmal holen, die Antwort auf die Läufe verteilen.
     */
    private suspend fun CoroutineComprehensionScope<Nothing>.pollEvent(
        event: RaceClockerPollEvent,
        now: LocalDateTime,
    ) {
        val candidates = !RaceClockerPollRepo.getCandidates(event.eventId).orDie()
        val watched = candidates.filter {
            RaceClockerPollLogic.isWatched(
                currentlyRunning = it.currentlyRunning,
                startTime = it.startTime,
                now = now,
                watchBeforeMinutes = event.watchBeforeMinutes,
                watchAfterMinutes = event.watchAfterMinutes,
            )
        }

        eventStates[event.eventId] = EventState(now, RaceClockerPollLogic.modeFor(watched.any { it.currentlyRunning }))
        if (watched.isEmpty()) return

        // Ein Abruf liefert das ganze Rennen. Deshalb je Adresse genau einmal holen und die Antwort
        // teilen - bei einer Regatta sind das ein bis zwei Abrufe pro Takt, egal wie viele Läufe
        // gerade laufen.
        val feeds = watched.flatMap { it.target.candidateUrls }.distinct()
            .associateWith { fetchRows(it) }

        watched.forEach { candidate ->
            val outcome = pollMatch(candidate, feeds, now)
            !RaceClockerPollRepo.recordPoll(candidate.matchId, now, outcome).orDie()
        }
    }

    /**
     * Ein einzelner Lauf. Liefert den ErrorCode des Fehlschlags oder null.
     *
     * Ein Fehler bleibt hier: Ein Lauf mit doppelten Crews in RaceClocker darf die anderen Läufe
     * derselben Veranstaltung nicht mitreißen.
     */
    private fun CoroutineComprehensionScope<Nothing>.pollMatch(
        candidate: RaceClockerPollCandidate,
        feeds: Map<String, FeedResult>,
        now: LocalDateTime,
    ): String? {
        // Dieselbe Reihenfolge wie beim Knopf: Die Runde entscheidet, welches Rennen zuerst
        // versucht wird, das andere ist der Rückfall.
        val results = candidate.target.candidateUrls.mapNotNull { feeds[it] }
        val rows = results.firstNotNullOfOrNull { (it as? FeedResult.Rows)?.rows }
            ?: return (results.firstOrNull() as? FeedResult.Failed)?.errorCode

        val match = !CompetitionSetupService.getSetupRoundsWithMatches(candidate.competitionId)
            .map { rounds -> rounds.flatMap { it.matches }.find { it.competitionSetupMatch == candidate.matchId } }
            .orDie()
            ?: return null

        val teams = match.teams.filter { !it.deregistered }
        val assigned = CompetitionExecutionService.assignedRowsFor(rows, teams, candidate.target.waveName)

        // Eine Welle, die in RaceClocker noch nicht angelegt ist, ist vor dem Start der Normalfall
        // und keine Störung.
        if (assigned.isEmpty()) return null

        // Bevorstehender Lauf: nur hinsehen, nichts schreiben außer der Aktivierung. Ein
        // Umsortieren in RaceClocker vor dem Start schlägt erst durch, wenn der Lauf aktiv ist.
        if (!candidate.currentlyRunning) {
            if (RaceClockerPollLogic.startDetected(assigned)) {
                !CompetitionMatchRepo.update(candidate.matchId) {
                    currentlyRunning = true
                    if (startedAt == null) {
                        startedAt = now
                    }
                    updatedBy = SYSTEM_USER
                    updatedAt = now
                }.orDie()
                logger.info { "RaceClocker meldet den Start von Lauf ${candidate.matchId} - Lauf aktiviert." }
            }
            return null
        }

        // Unverändert seit dem letzten Abruf: nichts schreiben.
        val fingerprint = RaceClockerPollLogic.fingerprint(assigned)
        if (fingerprints[candidate.matchId] == fingerprint) return null

        val errorCode = CompetitionExecutionService
            .applyRaceClockerRows(match, candidate.matchId, candidate.target, rows, SYSTEM_USER)
            .fold(
                onSuccess = { null },
                onError = { it.respond().errorCode?.name },
            )
            .let { !KIO.ok(it) }

        if (errorCode == null) {
            fingerprints[candidate.matchId] = fingerprint
        }
        return errorCode
    }

    private sealed interface FeedResult {
        data class Rows(val rows: List<RaceClockerFeedRow>) : FeedResult
        data class Failed(val errorCode: String?) : FeedResult
    }

    /**
     * Holt einen Feed und fängt seinen Fehler ab, statt den Takt scheitern zu lassen. Die
     * Fehlermeldung wird auf ihren ErrorCode eingedampft, damit die Oberfläche sie übersetzen kann
     * (siehe `raceClockerErrorText` im Frontend).
     */
    private suspend fun fetchRows(rawUrl: String): FeedResult {
        val url = RaceClockerFeed.normalizeUrl(rawUrl).unsafeRunSync().getOrNull()
            ?: return FeedResult.Failed("RACECLOCKER_URL_INVALID")

        return RaceClockerFeed.fetch(RaceClockerFeed.feedUrl(url)).unsafeRunSync().fold(
            onSuccess = { FeedResult.Rows(it) },
            onError = { FeedResult.Failed(it.respond().errorCode?.name) },
            onDefect = {
                logger.warn(it) { "RaceClocker-Abruf von $rawUrl ist unerwartet gescheitert." }
                FeedResult.Failed("RACECLOCKER_UNREACHABLE")
            },
        )
    }
}
```

**Hinweise für die Umsetzung dieses Schritts:**

1. `CompetitionExecutionService.assignedRowsFor(rows, teams, waveName)` gibt es noch nicht. In `CompetitionExecutionService` ergänzen — eine dünne Hülle, damit der Job dieselbe Zuordnung sieht wie die Anwendung:

```kotlin
    /**
     * Welche Feed-Zeilen zu diesem Lauf gehören - flach, ohne Zuordnung zur Mannschaft. Der
     * automatische Abruf braucht das vor dem Schreiben: um zu erkennen, ob die Welle überhaupt im
     * Feed steht, ob sie gestartet ist und ob sich seit dem letzten Takt etwas geändert hat.
     */
    fun assignedRowsFor(
        rows: List<RaceClockerFeedRow>,
        teams: List<CompetitionMatchTeamWithRegistration>,
        waveName: String?,
    ): List<RaceClockerFeedRow> = assignFeedRows(rows, teams, waveName).values.flatten()
```

2. Der Ausdruck `.fold(onSuccess = …, onError = …).let { !KIO.ok(it) }` in `pollMatch` ist eine Krücke. `applyRaceClockerRows` liefert `App<ServiceError, ApiResponse.NoData>`; hier wird ein `String?` gebraucht. Sauberer Weg im Projekt-Stil:

```kotlin
        val errorCode = !CompetitionExecutionService
            .applyRaceClockerRows(match, candidate.matchId, candidate.target, rows, SYSTEM_USER)
            .map { null as String? }
            .recoverDefault { error -> KIO.ok(error.respond().errorCode?.name) }
```

`recoverDefault` wird in `Application.kt` genau so verwendet. Ergibt der Compiler hier Ärger mit den Typen, ist der Rückfall `.fold()` über das `Exit` aus `unsafeRunSync(env)` — dann muss `pollMatch` das `env` mitbekommen. Die erste Variante zuerst versuchen.

3. `getSetupRoundsWithMatches` liefert `App<ServiceError, List<CompetitionSetupRoundWithMatches>>`; sein `matches`-Feld enthält `CompetitionMatchWithTeams`. Der `?:`-Rückfall („Lauf nicht in der aktuellen Struktur") gibt `null` zurück — kein Fehler, der Job überspringt den Lauf still.

4. Der Import `de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerFeedRow` ist für `assignedRowsFor` in `CompetitionExecutionService` bereits vorhanden.

- [ ] **Step 2: Übersetzen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && cd backend && ./mvnw -q compile
```

Erwartet: `BUILD SUCCESS`. Typfehler an den in den Hinweisen genannten Stellen sind eingeplant — dort den beschriebenen Alternativweg nehmen, aber die Struktur des Ablaufs nicht ändern.

- [ ] **Step 3: Den Job anmelden**

In `backend/src/main/kotlin/de/lambda9/ready2race/backend/Application.kt` in `scheduleJobs`, hinter dem WebDAV-Import-Job, einfügen:

```kotlin
            // Herzschlag im Sekundentakt, der je Veranstaltung entscheidet, ob ihr eingestellter
            // Takt fällig ist (RaceClockerPollService). Ohne eine Veranstaltung mit eingeschalteter
            // Automatik meldet er Empty und schläft 30 s - der Normalzustand außerhalb einer Regatta.
            scheduleDynamic(
                "Pull RaceClocker results",
                emptyDelay = 30.seconds,
                processedDelay = 1.seconds,
                defectDelay = 30.seconds,
            ) {
                RaceClockerPollService.pollTick(env)
            }
```

Importe ergänzen: `import de.lambda9.ready2race.backend.app.raceclocker.boundary.RaceClockerPollService` und `import kotlin.time.Duration.Companion.seconds` (Letzteres ist bereits vorhanden).

- [ ] **Step 4: Übersetzen und alle Tests laufen lassen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && cd backend && ./mvnw -q test
```

Erwartet: `BUILD SUCCESS`, alle Tests grün.

- [ ] **Step 5: Committen**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend && git commit -m "Pull RaceClocker results on a schedule"
```

---

## Task 8: Handeingabe pausiert, Oberfläche gibt frei

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/boundary/CompetitionExecutionService.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/boundary/competitionExecution.kt`
- Modify: `backend/src/main/resources/openapi/documentation.yaml`

**Interfaces:**
- Produces:
  ```kotlin
  fun CompetitionExecutionService.resumeRaceClockerAutoPull(
      eventId: UUID,
      competitionId: UUID,
      matchId: UUID,
      userId: UUID,
  ): App<ServiceError, ApiResponse.NoData>
  ```
  sowie der Endpunkt `POST /event/{eventId}/competition/{competitionId}/competitionExecution/{competitionMatchId}/results/raceclocker/resume` mit `operationId: resumeRaceClockerAutoPull`.

- [ ] **Step 1: Pausieren beim Schreiben von Hand**

In `CompetitionExecutionService.kt` eine gemeinsame Hilfsfunktion neben `prepareForNewPlaces` anlegen:

```kotlin
    /**
     * Hält den automatischen RaceClocker-Abruf für diesen Lauf an.
     *
     * Wer von Hand einträgt oder eine Datei hochlädt, hat das letzte Wort: Der Job setzt bei jedem
     * Takt alle Plätze des Laufs zurück und schreibt nur die Boote wieder, die im Feed ein Ergebnis
     * haben - ein Handeintrag für ein Boot, das RaceClocker nicht kennt, wäre nach spätestens einem
     * Takt weg. Freigegeben wird der Lauf in der Oberfläche ([resumeRaceClockerAutoPull]).
     *
     * Der manuelle Pull pausiert bewusst NICHT: Er ist derselbe Weg wie die Automatik, nur von Hand
     * ausgelöst, und darf sie nicht abwürgen.
     */
    private fun pauseRaceClockerAutoPull(matchId: UUID): App<Nothing, Unit> = KIO.comprehension {
        !CompetitionMatchRepo.update(matchId) {
            if (raceclockerAutoPausedAt == null) {
                raceclockerAutoPausedAt = LocalDateTime.now()
            }
        }.orDie()

        unit
    }
```

In `updateMatchResult` (Formular) unmittelbar nach `!checkUpdateMatchResult(competitionId, matchId)` einfügen:

```kotlin
        !pauseRaceClockerAutoPull(matchId)
```

Dasselbe in `updateMatchResultByFile` (Datei-Upload) an der entsprechenden Stelle nach dessen `checkUpdateMatchResult`-Aufruf.

- [ ] **Step 2: Wiederaufnehmen im Service**

Neben `updateMatchResultFromRaceClocker` einfügen:

```kotlin
    /**
     * Gibt einen pausierten Lauf wieder für den automatischen Abruf frei. Löscht zugleich den
     * letzten Fehler - was beim nächsten Takt passiert, ist die Antwort, die jetzt zählt.
     */
    fun resumeRaceClockerAutoPull(
        eventId: UUID,
        competitionId: UUID,
        matchId: UUID,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        !EventService.checkIsChallengeEvent(eventId).onTrueFail { CompetitionExecutionError.IsChallengeEvent }
        !checkUpdateMatchResult(competitionId, matchId, byeError = RaceClockerError.MatchIsBye)

        !CompetitionMatchRepo.update(matchId) {
            raceclockerAutoPausedAt = null
            raceclockerPollError = null
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()

        noData
    }
```

- [ ] **Step 3: Den Endpunkt anlegen**

In `competitionExecution.kt` direkt hinter dem Block `post("/results/from-raceclocker") { … }`:

```kotlin
            post("/results/raceclocker/resume") {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val eventId = !pathParam("eventId", uuid)
                    val competitionId = !pathParam("competitionId", uuid)
                    val competitionMatchId = !pathParam("competitionMatchId", uuid)

                    CompetitionExecutionService.resumeRaceClockerAutoPull(
                        eventId = eventId,
                        competitionId = competitionId,
                        matchId = competitionMatchId,
                        userId = user.id!!,
                    )
                }
            }
```

- [ ] **Step 4: OpenAPI ergänzen**

In `documentation.yaml` hinter dem Pfad `…/results/from-raceclocker` (endet bei Zeile ~1715) einfügen:

```yaml
  /event/{eventId}/competition/{competitionId}/competitionExecution/{competitionMatchId}/results/raceclocker/resume:
    parameters:
      - $ref: '#/components/parameters/eventId'
      - $ref: '#/components/parameters/competitionId'
      - name: competitionMatchId
        in: path
        required: true
        schema:
          type: string
          format: uuid
    post:
      operationId: resumeRaceClockerAutoPull
      description: >-
        Releases a match back to the automatic RaceClocker pull. Entering results by hand or
        uploading a file pauses the pull for that match, so the office keeps the last word; this
        undoes that and clears the last poll error.
      responses:
        204:
          $ref: '#/components/responses/204'
        400:
          $ref: '#/components/responses/400'
        401:
          $ref: '#/components/responses/401'
        403:
          $ref: '#/components/responses/403'
        404:
          $ref: '#/components/responses/404'
        500:
          $ref: '#/components/responses/500'
```

- [ ] **Step 5: Übersetzen und Tests laufen lassen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && cd backend && ./mvnw -q test
```

Erwartet: `BUILD SUCCESS`, alle Tests grün.

- [ ] **Step 6: Committen**

```bash
git add backend/src && git commit -m "Let entering results by hand pause the automatic pull"
```

---

## Task 9: Anzeige im Durchführungs-Tab

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/entity/CompetitionMatchDto.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/entity/CompetitionMatchWithTeams.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/control/Conversions.kt`
- Modify: `backend/src/main/resources/openapi/documentation.yaml` (Schema `CompetitionMatchDto`)
- Create: `frontend/src/components/event/competition/excecution/raceClockerPollStatus.ts`
- Create: `frontend/src/components/event/competition/excecution/raceClockerPollStatus.test.ts`
- Modify: `frontend/src/components/event/competition/excecution/CompetitionExecutionRound.tsx`
- Modify: `frontend/src/components/event/competition/excecution/CompetitionExecution.tsx`
- Modify: `frontend/src/i18n/de/translations.json`, `en/…`, `da/…`

**Interfaces:**
- Consumes: die drei Spalten aus Task 2, die View-Erweiterung aus Task 2, `resumeRaceClockerAutoPull` aus Task 8, `raceClockerKeys` aus `executionError.ts`.
- Produces:
  - Backend: `CompetitionMatchDto.raceClockerPolledAt: LocalDateTime?`, `.raceClockerPollError: String?`, `.raceClockerAutoPausedAt: LocalDateTime?`
  - Frontend: `raceClockerPollStatus(match): {kind: 'ok' | 'error' | 'paused' | 'none', errorKey?: string}` in `raceClockerPollStatus.ts`

- [ ] **Step 1: Die Felder durchs Backend reichen**

In `CompetitionMatchWithTeams.kt` (wird aus der View gefüllt) ergänzen:

```kotlin
    val raceClockerPolledAt: LocalDateTime?,
    val raceClockerPollError: String?,
    val raceClockerAutoPausedAt: LocalDateTime?,
```

In `Conversions.kt` an beiden Stellen füllen — in `toCompetitionSetupRoundWithMatches` aus dem View-Record:

```kotlin
                raceClockerPolledAt = match.raceclockerPolledAt,
                raceClockerPollError = match.raceclockerPollError,
                raceClockerAutoPausedAt = match.raceclockerAutoPausedAt,
```

und im Dto-Aufbau (`CompetitionMatchDto`, um Zeile 70):

```kotlin
                    raceClockerPolledAt = match.first.raceClockerPolledAt,
                    raceClockerPollError = match.first.raceClockerPollError,
                    raceClockerAutoPausedAt = match.first.raceClockerAutoPausedAt,
```

In `CompetitionMatchDto.kt` dieselben drei Felder ergänzen.

In `documentation.yaml` im Schema `CompetitionMatchDto` unter `properties`:

```yaml
        raceClockerPolledAt:
          type: string
          format: date-time
          nullable: true
          description: When the automatic pull last tried this match - not when it last wrote something.
        raceClockerPollError:
          type: string
          nullable: true
          description: Error code of the last failed automatic pull, null when it is fine.
        raceClockerAutoPausedAt:
          type: string
          format: date-time
          nullable: true
          description: Set while the automatic pull leaves this match alone because results were entered by hand.
```

- [ ] **Step 2: Backend übersetzen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && cd backend && ./mvnw -q test
```

Erwartet: `BUILD SUCCESS`.

- [ ] **Step 3: Client generieren und den Frontend-Test schreiben**

```bash
cd frontend && npm run generate
```

Datei `frontend/src/components/event/competition/excecution/raceClockerPollStatus.test.ts`:

```ts
import {describe, expect, it} from 'vitest'
import {raceClockerPollStatus} from './raceClockerPollStatus.ts'

describe('raceClockerPollStatus', () => {
    it('meldet nichts, solange nie abgerufen wurde', () => {
        expect(
            raceClockerPollStatus({
                raceClockerPolledAt: null,
                raceClockerPollError: null,
                raceClockerAutoPausedAt: null,
            }).kind,
        ).toBe('none')
    })

    it('meldet den letzten erfolgreichen Abruf', () => {
        expect(
            raceClockerPollStatus({
                raceClockerPolledAt: '2026-08-14T10:03:12',
                raceClockerPollError: null,
                raceClockerAutoPausedAt: null,
            }).kind,
        ).toBe('ok')
    })

    it('meldet den letzten Fehler mit übersetzbarem Schlüssel', () => {
        const status = raceClockerPollStatus({
            raceClockerPolledAt: '2026-08-14T10:03:12',
            raceClockerPollError: 'RACECLOCKER_UNREACHABLE',
            raceClockerAutoPausedAt: null,
        })

        expect(status.kind).toBe('error')
        expect(status.errorKey).toBeDefined()
    })

    // Pausiert schlägt alles: Der letzte Fehler stammt aus der Zeit davor und ist keine Aussage
    // mehr über einen Lauf, den der Job gar nicht mehr anfasst.
    it('meldet Pause vor Fehler', () => {
        expect(
            raceClockerPollStatus({
                raceClockerPolledAt: '2026-08-14T10:03:12',
                raceClockerPollError: 'RACECLOCKER_UNREACHABLE',
                raceClockerAutoPausedAt: '2026-08-14T10:05:00',
            }).kind,
        ).toBe('paused')
    })

    it('meldet für einen unbekannten Fehlercode trotzdem einen Fehler', () => {
        const status = raceClockerPollStatus({
            raceClockerPolledAt: '2026-08-14T10:03:12',
            raceClockerPollError: 'SOMETHING_NEW',
            raceClockerAutoPausedAt: null,
        })

        expect(status.kind).toBe('error')
        expect(status.errorKey).toBeUndefined()
    })
})
```

- [ ] **Step 4: Test laufen lassen und Fehlschlag prüfen**

```bash
cd frontend && npm run test -- raceClockerPollStatus
```

Erwartet: FAIL — die Datei existiert nicht.

- [ ] **Step 5: Die Statuslogik schreiben**

Datei `frontend/src/components/event/competition/excecution/raceClockerPollStatus.ts`:

```ts
import {raceClockerKeys} from './executionError.ts'

export type RaceClockerPollStatus = {
    kind: 'none' | 'ok' | 'error' | 'paused'
    errorKey?: string
}

type PollFields = {
    raceClockerPolledAt?: string | null
    raceClockerPollError?: string | null
    raceClockerAutoPausedAt?: string | null
}

/**
 * Die Fehlercodes des automatischen Abrufs auf dieselben Texte wie beim Knopf — der Job scheitert
 * an denselben Dingen, es gibt keinen Grund, das zweimal zu formulieren.
 */
const errorKeyFor = (code: string): string | undefined => {
    switch (code) {
        case 'RACECLOCKER_URL_MISSING':
            return raceClockerKeys.urlMissing
        case 'RACECLOCKER_URL_INVALID':
            return raceClockerKeys.urlInvalid
        case 'RACECLOCKER_UNREACHABLE':
        case 'RACECLOCKER_MALFORMED_FEED':
            return raceClockerKeys.unreachable
        case 'RACECLOCKER_MATCH_NOT_IN_FEED':
            return raceClockerKeys.matchNotInFeed
        case 'RACECLOCKER_DUPLICATE_TEAMS':
            return raceClockerKeys.duplicateTeams
        case 'RACECLOCKER_NO_RESULTS':
            return raceClockerKeys.noResults
        case 'RACECLOCKER_MATCH_IS_BYE':
            return raceClockerKeys.matchIsBye
    }

    return undefined
}

/**
 * Was die Oberfläche über den automatischen Abruf dieses Laufs sagt.
 *
 * Die Reihenfolge ist die Aussage: „pausiert" schlägt alles, weil ein pausierter Lauf gar nicht
 * mehr abgerufen wird — ein Fehler von davor wäre dann eine Behauptung über etwas, das nicht mehr
 * stattfindet. „Nie abgerufen" ist kein Fehler: Vor dem ersten Takt und bei ausgeschalteter
 * Automatik ist genau das der richtige Zustand.
 */
export const raceClockerPollStatus = (match: PollFields): RaceClockerPollStatus => {
    if (match.raceClockerAutoPausedAt) {
        return {kind: 'paused'}
    }

    if (match.raceClockerPollError) {
        return {kind: 'error', errorKey: errorKeyFor(match.raceClockerPollError)}
    }

    if (match.raceClockerPolledAt) {
        return {kind: 'ok'}
    }

    return {kind: 'none'}
}
```

`raceClockerKeys` ist in `executionError.ts:46` bisher nicht exportiert — dort `const raceClockerKeys` zu `export const raceClockerKeys` ändern.

- [ ] **Step 6: Test laufen lassen und Erfolg prüfen**

```bash
cd frontend && npm run test -- raceClockerPollStatus
```

Erwartet: alle fünf Tests grün.

- [ ] **Step 7: Die Statuszeile einbauen**

In `CompetitionExecutionRound.tsx` die Prop-Liste um

```ts
    resumeRaceClockerAutoPull: (competitionMatchId: string) => Promise<void>
```

erweitern (neben `pullRaceClockerResults`) und im Rendern eines Laufs, direkt unter dem Ergebnis-Menü, einfügen:

```tsx
                                    {timingSystem === 'RACECLOCKER' &&
                                        (() => {
                                            const status = raceClockerPollStatus(match)
                                            if (status.kind === 'none') return null

                                            return (
                                                <Stack spacing={0.5}>
                                                    <Typography
                                                        variant={'caption'}
                                                        color={
                                                            status.kind === 'ok'
                                                                ? 'text.secondary'
                                                                : 'warning.main'
                                                        }>
                                                        {status.kind === 'paused'
                                                            ? t(
                                                                  'event.competition.execution.results.raceclocker.poll.paused',
                                                              )
                                                            : status.kind === 'error'
                                                              ? t(
                                                                    'event.competition.execution.results.raceclocker.poll.error',
                                                                    {
                                                                        reason: status.errorKey
                                                                            ? t(status.errorKey)
                                                                            : t(
                                                                                  'common.error.unexpected',
                                                                              ),
                                                                    },
                                                                )
                                                              : t(
                                                                    'event.competition.execution.results.raceclocker.poll.lastPolled',
                                                                    {
                                                                        time: format(
                                                                            new Date(
                                                                                match.raceClockerPolledAt!,
                                                                            ),
                                                                            'HH:mm:ss',
                                                                        ),
                                                                    },
                                                                )}
                                                    </Typography>
                                                    {status.kind === 'paused' && (
                                                        <LoadingButton
                                                            size={'small'}
                                                            variant={'text'}
                                                            pending={submitting}
                                                            onClick={() =>
                                                                props.resumeRaceClockerAutoPull(
                                                                    match.id,
                                                                )
                                                            }>
                                                            {t(
                                                                'event.competition.execution.results.raceclocker.poll.resume',
                                                            )}
                                                        </LoadingButton>
                                                    )}
                                                </Stack>
                                            )
                                        })()}
```

Importe ergänzen: `import {raceClockerPollStatus} from './raceClockerPollStatus.ts'` und `import {format} from 'date-fns'` (falls noch nicht vorhanden; die Datei benutzt bereits MUI-`Typography` und `Stack`).

- [ ] **Step 8: Den Handler ergänzen**

In `CompetitionExecution.tsx` neben `handlePullRaceClockerResults`:

```tsx
    const handleResumeRaceClockerAutoPull = async (competitionMatchId: string) => {
        setSubmitting(true)
        const {error} = await resumeRaceClockerAutoPull({
            path: {eventId, competitionId, competitionMatchId},
        })
        setSubmitting(false)

        if (error) {
            feedback.error(t('common.error.unexpected'))
        } else {
            feedback.success(
                t('event.competition.execution.results.raceclocker.poll.resumed'),
            )
            setReloadData(!reloadData)
        }
    }
```

`resumeRaceClockerAutoPull` aus `@api/sdk.gen.ts` importieren und die Prop an `CompetitionExecutionRound` durchreichen:

```tsx
                        resumeRaceClockerAutoPull={handleResumeRaceClockerAutoPull}
```

- [ ] **Step 9: Übersetzungen ergänzen**

In `frontend/src/i18n/de/translations.json` unter `event.competition.execution.results.raceclocker` einfügen:

```json
"poll": {
  "lastPolled": "Automatisch abgerufen: {{time}}",
  "error": "Automatischer Abruf klemmt: {{reason}}",
  "paused": "Automatischer Abruf pausiert — es wurde von Hand eingetragen.",
  "resume": "Automatik wieder aufnehmen",
  "resumed": "Der automatische Abruf ist wieder aktiv."
}
```

In `en/translations.json`:

```json
"poll": {
  "lastPolled": "Pulled automatically: {{time}}",
  "error": "Automatic pull is stuck: {{reason}}",
  "paused": "Automatic pull paused — results were entered by hand.",
  "resume": "Resume automatic pull",
  "resumed": "The automatic pull is active again."
}
```

In `da/translations.json`:

```json
"poll": {
  "lastPolled": "Hentet automatisk: {{time}}",
  "error": "Den automatiske hentning driller: {{reason}}",
  "paused": "Automatisk hentning sat på pause — resultater blev indtastet manuelt.",
  "resume": "Genoptag automatisk hentning",
  "resumed": "Den automatiske hentning er aktiv igen."
}
```

- [ ] **Step 10: Übersetzen, Tests, Lint**

```bash
cd frontend && npx tsc -b && npm run test && npm run lint
```

Erwartet: keine Typfehler, alle Tests grün, Lint sauber.

- [ ] **Step 11: Committen**

```bash
git add backend/src frontend/src && git commit -m "Show the automatic pull's state on each match"
```

---

## Task 10: Anzeige im Live-Dashboard

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/entity/LiveDashboardDto.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/control/LiveDashboardRepo.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/boundary/LiveDashboardService.kt`
- Modify: `backend/src/main/resources/openapi/documentation.yaml` (Schema `LiveDashboardMatchDto`)
- Modify: `frontend/src/components/event/liveDashboard/LiveDashboardMatchCard.tsx`

**Interfaces:**
- Consumes: `raceClockerPollStatus` aus Task 9 und dieselben i18n-Schlüssel.
- Produces: `LiveDashboardMatchDto.raceClockerPolledAt`, `.raceClockerPollError`, `.raceClockerAutoPausedAt`

- [ ] **Step 1: Die Felder durchreichen**

In `LiveDashboardRepo.getMatches` die drei Spalten in die Projektion aufnehmen:

```kotlin
            COMPETITION_MATCH.RACECLOCKER_POLLED_AT,
            COMPETITION_MATCH.RACECLOCKER_POLL_ERROR,
            COMPETITION_MATCH.RACECLOCKER_AUTO_PAUSED_AT,
```

In `LiveDashboardMatchDto` die drei Felder ergänzen (`raceClockerPolledAt: LocalDateTime?`, `raceClockerPollError: String?`, `raceClockerAutoPausedAt: LocalDateTime?`) und in `LiveDashboardService` an **beiden** Stellen füllen, an denen `LiveDashboardMatchDto` gebaut wird (um Zeile 156 und 170):

```kotlin
                            raceClockerPolledAt = match[COMPETITION_MATCH.RACECLOCKER_POLLED_AT],
                            raceClockerPollError = match[COMPETITION_MATCH.RACECLOCKER_POLL_ERROR],
                            raceClockerAutoPausedAt = match[COMPETITION_MATCH.RACECLOCKER_AUTO_PAUSED_AT],
```

In `documentation.yaml` im Schema `LiveDashboardMatchDto` dieselben drei Eigenschaften ergänzen wie in Task 9 Step 1.

- [ ] **Step 2: Backend übersetzen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && cd backend && ./mvnw -q test
```

Erwartet: `BUILD SUCCESS`.

- [ ] **Step 3: Client generieren und die Zeile einbauen**

```bash
cd frontend && npm run generate
```

In `LiveDashboardMatchCard.tsx` unterhalb der Kopfzeile des Laufs:

```tsx
            {(() => {
                const status = raceClockerPollStatus(match)
                if (status.kind === 'none' || status.kind === 'ok') return null

                return (
                    <Typography variant={'caption'} color={'warning.main'}>
                        {status.kind === 'paused'
                            ? t('event.competition.execution.results.raceclocker.poll.paused')
                            : t('event.competition.execution.results.raceclocker.poll.error', {
                                  reason: status.errorKey
                                      ? t(status.errorKey)
                                      : t('common.error.unexpected'),
                              })}
                    </Typography>
                )
            })()}
```

Der Erfolgsfall bleibt hier bewusst stumm: Das Live-Dashboard ist der Schiedsrichter-Blick auf die Läufe, und eine Zeile „zuletzt abgerufen" an jeder Karte wäre Rauschen. Was zählt, ist der Fall, in dem der Abruf **nicht** läuft.

Import: `import {raceClockerPollStatus} from '@components/event/competition/excecution/raceClockerPollStatus.ts'`.

- [ ] **Step 4: Übersetzen, Tests, Lint**

```bash
cd frontend && npx tsc -b && npm run test && npm run lint
```

Erwartet: keine Typfehler, Tests grün, Lint sauber.

- [ ] **Step 5: Committen**

```bash
git add backend/src frontend/src && git commit -m "Warn in the live dashboard when the automatic pull is stuck"
```

---

## Task 11: Rundgang am laufenden System

Die Datenbank- und Job-Anteile hat kein Unit-Test abgedeckt — das Projekt testet Repositories grundsätzlich nicht. Dieser Rundgang ist ihr Ersatz und darf nicht übersprungen werden.

**Files:** keine Änderungen; nur Verifikation.

- [ ] **Step 1: Backend und Frontend starten**

Ports pro Worktree wählen, damit kein fremder Server antwortet (siehe Global Constraints):

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && cd backend && docker compose up -d db && HTTP_PORT=8091 ./mvnw -q compile exec:java -Dexec.mainClass=de.lambda9.ready2race.backend.ApplicationKt
```

Vor dem Start prüfen, ob 8091 frei ist: `lsof -nP -iTCP:8091 -sTCP:LISTEN`.

- [ ] **Step 2: Prüfen, dass der Job ohne Automatik schläft**

Im Serverlog erscheint beim Start `Scheduling jobs ...`. Ohne eingeschaltete Automatik darf **keine** Last entstehen:

```bash
docker compose exec db psql -U developer -d ready2race -c "select count(*) from ready2race.event where raceclocker_auto_pull;"
```

Erwartet: `0`. Der Job meldet dann `Empty` und schläft 30 s.

- [ ] **Step 3: Automatik einschalten und einen bevorstehenden Lauf ins Fenster legen**

Im Zeitnahme-Tab der Veranstaltung „Ergebnisse automatisch abrufen" einschalten und speichern. Danach einen Lauf mit einer echten RaceClocker-Welle auf eine Startzeit gleich legen — Zeitzone beachten, Postgres läuft auf UTC, die App auf Europe/Berlin:

```bash
docker compose exec db psql -U developer -d ready2race -c "update ready2race.competition_match set start_time = (now() at time zone 'Europe/Berlin') + interval '5 minutes', currently_running = false, finished_at = null where competition_setup_match = '<matchId>';"
```

- [ ] **Step 4: Den langsamen Takt beobachten**

```bash
docker compose exec db psql -U developer -d ready2race -c "select competition_setup_match, raceclocker_polled_at, raceclocker_poll_error from ready2race.competition_match where raceclocker_polled_at is not null order by raceclocker_polled_at desc limit 5;"
```

Erwartet: `raceclocker_polled_at` rückt etwa einmal pro Minute vor. Zweimal im Abstand von ~30 s abfragen und die Differenz der Zeitstempel prüfen — sie muss bei rund 60 s liegen, nicht bei 5 s.

- [ ] **Step 5: Den Start erkennen lassen**

In RaceClocker die Welle starten (oder in der Testumgebung eine Startzeit setzen). Innerhalb einer Minute muss gelten:

```bash
docker compose exec db psql -U developer -d ready2race -c "select currently_running, started_at from ready2race.competition_match where competition_setup_match = '<matchId>';"
```

Erwartet: `currently_running = t`, `started_at` gesetzt. Der Lauf erscheint im Live-Tab des Dashboards.

- [ ] **Step 6: Den schnellen Takt und die Schreib-Sparsamkeit prüfen**

`raceclocker_polled_at` rückt jetzt etwa alle 5 s vor. Zugleich darf sich `updated_at` **nicht** mitbewegen, solange RaceClocker nichts Neues liefert:

```bash
docker compose exec db psql -U developer -d ready2race -c "select raceclocker_polled_at, updated_at from ready2race.competition_match where competition_setup_match = '<matchId>';"
```

Erwartet: über mehrere Abfragen hinweg wandert `raceclocker_polled_at`, `updated_at` steht still. Sobald in RaceClocker eine Zeit dazukommt, springt auch `updated_at` und die Zeit steht in der Oberfläche.

- [ ] **Step 7: Handeingabe und Wiederaufnehmen prüfen**

Für denselben Lauf im Durchführungs-Tab ein Ergebnis von Hand eintragen und speichern. Erwartet: `raceclocker_auto_paused_at` ist gesetzt, `raceclocker_polled_at` rückt nicht mehr vor, und die Oberfläche zeigt „Automatischer Abruf pausiert" samt Knopf. Nach Klick auf „Automatik wieder aufnehmen" rückt der Zeitstempel wieder vor.

- [ ] **Step 8: Beendete Läufe prüfen**

Den Lauf beenden. Erwartet: `raceclocker_polled_at` steht ab dann still — ein beendeter Lauf wird nie wieder abgerufen.

- [ ] **Step 9: Das Ergebnis festhalten**

Beobachtungen (gemessene Takte, aufgetretene Fehlercodes) in der Antwort an den Nutzer berichten. Keine Änderung, kein Commit.

---

## Selbstprüfung des Plans

**Abdeckung des Entwurfs:**

| Abschnitt des Entwurfs | Task |
|---|---|
| Der Job (Herzschlag, Empty/Processed) | 7 |
| Kandidaten laden | 6 |
| Fälligkeit je Veranstaltung | 1, 7 |
| Feed je URL genau einmal | 7 |
| Je Lauf zuordnen (kein Treffer / bevorstehend / aktiv) | 7 |
| Fehler je Lauf, Takt bricht nie ab | 6, 7 |
| Kein zweiter Code-Pfad | 5 |
| Schreib-Sparsamkeit (Fingerabdruck) | 1, 7 |
| Konfiguration (fünf Spalten, Untergrenze) | 2, 3, 4 |
| Handeingabe pausiert, Wiederaufnehmen | 8 |
| Sichtbarkeit je Lauf | 9, 10 |
| Was der Job nicht tut (beenden, bevorstehende schreiben, abgesagte, fremde Runden) | 6, 7 |
| Tests | 1, 3, 4, 9, 11 |
