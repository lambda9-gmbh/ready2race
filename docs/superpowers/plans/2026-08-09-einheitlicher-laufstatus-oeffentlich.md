# Einheitlicher Laufstatus in der öffentlichen Ergebnisanzeige — Implementierungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Der Tab „Live" der öffentlichen Ergebnisanzeige zeigt aktivierte **und** anstehende Läufe, jeden mit demselben Statuschip wie Durchführung, Zeitplan und Schiedsrichter-Dashboard, und aktualisiert sich ohne Neuladen.

**Architecture:** Ein neuer öffentlicher Endpoint `GET /event/{eventId}/info/live-matches` führt zwei bereits vorhandene Abfragen zusammen (aktivierte Läufe + anstehende Läufe der Athleten-Anzeige) und hängt an jeden Lauf ein `MatchStatusDto`, das ausschließlich aus `MatchStatusLogic.matchStatus` → `LiveDashboardLogic.deriveMatchState` entsteht. Im Frontend liest die Ergebnisanzeige denselben `matchStatusChip` wie die anderen Oberflächen und lädt über einen neuen, in reinem TypeScript geschriebenen und deshalb prüfbaren Takter nach.

**Tech Stack:** Kotlin/Ktor/jOOQ (Backend), Kotlin `kotlin.test` (Backend-Tests), React 18 + MUI 6 + TypeScript (Frontend), Vitest 4 im Node-Umfeld (Frontend-Tests, **kein jsdom, kein testing-library**), `@hey-api/openapi-ts` für den generierten API-Client.

**Spec:** `docs/superpowers/specs/2026-08-09-einheitlicher-laufstatus-oeffentlich-design.md`

## Global Constraints

- **Genau eine Statusableitung.** `LiveDashboardLogic.deriveMatchState` ist die einzige Stelle, die die Zweig-Reihenfolge festlegt. Neuer Code ruft sie über `MatchStatusLogic.matchStatus` auf und leitet **nie** selbst aus `activatedAt`/`startedAt`/`finishedAt` ab.
- **Keine neuen Werte in `LiveDashboardMatchState`.** Die Aufzählung ist und bleibt `PREPARING, RUNNING, FINISHED, SKIPPED, AWAITING_FINISH, UPCOMING, UNSCHEDULED`.
- **Die Ergebnisfreigabe wird nicht angefasst.** `PublicResultsVisibility`, `AthleteBoardLogic.isPublicResult`, `CompetitionMatchRepo.getMatchResults` und die View `competition_having_results` bleiben unverändert.
- **Keine Änderung an `EventInfoService.getRunningMatches`, `getAthleteBoard`, `getUpcomingMatchesForBoard`, `mergeWithPendingPlaceholders`** — sie tragen die Athleten-Anzeige am Renntag. Neuer Code *benutzt* sie, ändert sie nicht.
- **Keine Änderung an `useAthleteBoardData.ts`.**
- **Kommentare und KDoc auf Deutsch mit echten Umlauten** (ä, ö, ü, ß), Bezeichner auf Englisch — so wie die umgebenden Dateien. Ausnahme: `documentation.yaml` ist durchgehend englisch.
- **Commit-Nachrichten** im Stil der Historie: ein Satz, was fachlich passiert, ohne Präfix wie `feat:`. Kein Hinweis auf Claude oder KI.
- **Keine Migration.** Es kommt keine neue Datei unter `backend/src/main/resources/db/migration/`.
- **Frontend-Tests dürfen nichts rendern.** `vite.config.ts` sammelt `src/**/*.test.ts` (nicht `.tsx`) und läuft ohne DOM-Umgebung. Alles Prüfbare gehört in reine `.ts`-Module.

---

## Task 1: Alle sieben Zustände in `MatchStatusLogicTest`

Der vorhandene Test prüft `scoredCount` und einige Zustände. Der Auftrag verlangt einen Test pro Zustand — und dieser Test ist die Absicherung dafür, dass die fünfte Oberfläche nichts anderes zeigt als die vier vorhandenen.

**Files:**
- Modify: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/matchStatus/MatchStatusLogicTest.kt`

**Interfaces:**
- Consumes: `MatchStatusLogic.matchStatus(activatedAt, startTime, startedAt, finishedAt, skipped, teams, teamsInArena)` → `MatchStatusDto(state, startedAt, teamsTotal, teamsScored, teamsInArena)`; `MatchStatusTeam(place, failed, deregistered)`; `MatchState` (Alias auf `LiveDashboardMatchState`).
- Produces: nichts (reiner Test).

- [ ] **Step 1: Vorhandenen Test lesen**

Lies `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/matchStatus/MatchStatusLogicTest.kt` vollständig. Die Hilfsfunktionen `open()`, `placed(n)`, `failed()`, `deregistered()` und das Feld `start` sind bereits vorhanden und werden wiederverwendet. Prüfe, welche der sieben Zustände schon geprüft sind, und **ergänze nur, was fehlt** — kein zweiter Test für dieselbe Aussage.

- [ ] **Step 2: Den Zustandsblock schreiben**

Füge in der Sektion `// --- matchStatus ---` die fehlenden Tests ein. Vollständiger Block (lasse weg, was wortgleich schon dasteht):

```kotlin
    /**
     * Ein Block je Zustand: das ist die Liste, gegen die jede Oberflaeche geprueft wird. Faellt hier
     * ein Zweig um, zeigen Durchfuehrung, Zeitplan, Dashboard, Athleten-Anzeige UND die oeffentliche
     * Ergebnisanzeige gemeinsam etwas Falsches - genau deshalb steht die Ableitung an einem Ort.
     */
    @Test
    fun upcomingIsTheDefaultForAScheduledMatch() {
        val status = MatchStatusLogic.matchStatus(
            activatedAt = null,
            startTime = start,
            startedAt = null,
            finishedAt = null,
            skipped = false,
            teams = listOf(open(), open()),
        )
        assertEquals(MatchState.UPCOMING, status.state)
        assertEquals(2, status.teamsTotal)
        assertEquals(0, status.teamsScored)
    }

    /** Ohne geplante Zeit gibt es keinen Termin - und damit auch nie einen Verzug. */
    @Test
    fun withoutAStartTimeTheMatchIsUnscheduled() {
        val status = MatchStatusLogic.matchStatus(
            activatedAt = null,
            startTime = null,
            startedAt = null,
            finishedAt = null,
            skipped = false,
            teams = listOf(open()),
        )
        assertEquals(MatchState.UNSCHEDULED, status.state)
    }

    /** Aktiviert, aber ohne Ist-Start: der Lauf ist an den Start gerufen und liegt noch am Steg. */
    @Test
    fun activatedWithoutARealStartIsPreparing() {
        val status = MatchStatusLogic.matchStatus(
            activatedAt = start.minusMinutes(3),
            startTime = start,
            startedAt = null,
            finishedAt = null,
            skipped = false,
            teams = listOf(open(), open()),
        )
        assertEquals(MatchState.PREPARING, status.state)
        assertNull(status.startedAt)
    }

    @Test
    fun activatedAndStartedIsRunning() {
        val status = MatchStatusLogic.matchStatus(
            activatedAt = start.minusMinutes(3),
            startTime = start,
            startedAt = start.plusMinutes(1),
            finishedAt = null,
            skipped = false,
            teams = listOf(open(), open()),
        )
        assertEquals(MatchState.RUNNING, status.state)
        assertEquals(start.plusMinutes(1), status.startedAt)
    }

    /**
     * Nicht aktiv, nicht beendet, aber jedes Boot gewertet: auf den Beenden-Klick wartet die Kette.
     * Der Lauf darf deshalb nicht unter "beendet" verschwinden.
     */
    @Test
    fun allTeamsScoredButNotFinishedAwaitsFinish() {
        val status = MatchStatusLogic.matchStatus(
            activatedAt = null,
            startTime = start,
            startedAt = start,
            finishedAt = null,
            skipped = false,
            teams = listOf(placed(1), failed(), deregistered()),
        )
        assertEquals(MatchState.AWAITING_FINISH, status.state)
        assertEquals(3, status.teamsScored)
    }

    /** FINISHED heisst ausschliesslich "jemand hat beendet" (finished_at gesetzt). */
    @Test
    fun finishedAtMakesTheMatchFinished() {
        val status = MatchStatusLogic.matchStatus(
            activatedAt = null,
            startTime = start,
            startedAt = start,
            finishedAt = start.plusMinutes(8),
            skipped = false,
            teams = listOf(placed(1), placed(2)),
        )
        assertEquals(MatchState.FINISHED, status.state)
    }

    @Test
    fun aSkippedMatchIsCancelled() {
        val status = MatchStatusLogic.matchStatus(
            activatedAt = null,
            startTime = start,
            startedAt = null,
            finishedAt = null,
            skipped = true,
            teams = listOf(open(), open()),
        )
        assertEquals(MatchState.SKIPPED, status.state)
    }

    /**
     * Was tatsaechlich passiert, schlaegt den zurueckgenommenen Plan: ein abgesagter, aber
     * aktivierter Lauf zeigt weiter seinen Aktivierungszustand statt zu behaupten, es passiere
     * nichts, waehrend Boote in der Arena sind.
     */
    @Test
    fun activationBeatsCancellation() {
        val status = MatchStatusLogic.matchStatus(
            activatedAt = start,
            startTime = start,
            startedAt = start,
            finishedAt = null,
            skipped = true,
            teams = listOf(open()),
        )
        assertEquals(MatchState.RUNNING, status.state)
    }

    /** Ein Lauf ohne Mannschaften erreicht niemals AWAITING_FINISH. */
    @Test
    fun aMatchWithoutTeamsStaysUpcoming() {
        val status = MatchStatusLogic.matchStatus(
            activatedAt = null,
            startTime = start,
            startedAt = null,
            finishedAt = null,
            skipped = false,
            teams = emptyList(),
        )
        assertEquals(MatchState.UPCOMING, status.state)
        assertEquals(0, status.teamsTotal)
    }
```

- [ ] **Step 3: Test laufen lassen**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21) && cd backend && ../mvnw -q test -Dtest=MatchStatusLogicTest
```

Falls `java_home -v 21` fehlschlägt: `/usr/libexec/java_home -V` listet die vorhandenen JDKs, nimm das höchste ≥ 17.
Erwartet: alle Tests grün (`BUILD SUCCESS`). Sie prüfen vorhandenes Verhalten und dürfen nicht fehlschlagen — schlägt einer fehl, ist der Test falsch geschrieben, nicht die Ableitung.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/kotlin/de/lambda9/ready2race/backend/app/matchStatus/MatchStatusLogicTest.kt
git commit -m "Jeden der sieben Laufzustaende einzeln festnageln"
```

---

## Task 2: `LiveMatchInfo` und `LiveMatchesLogic.merge`

Das DTO der neuen Antwort und die reine Funktion, die die zwei Quellen zusammenführt. Noch ohne Service, ohne Route — nur Datentyp und Logik, damit der Test ohne Datenbank läuft.

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/entity/LiveMatchInfo.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/boundary/LiveMatchesLogic.kt`
- Create: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/eventInfo/LiveMatchesLogicTest.kt`

**Interfaces:**
- Consumes: `MatchStatusDto` aus `de.lambda9.ready2race.backend.app.matchStatus.entity`, `RunningMatchTeamInfo` aus `de.lambda9.ready2race.backend.app.eventInfo.entity`.
- Produces:
  - `data class LiveMatchInfo(matchId: UUID, competitionId: UUID?, competitionName: String, categoryName: String?, roundName: String?, matchName: String?, startTime: LocalDateTime?, status: MatchStatusDto, executionOrder: Int, cancelled: Boolean = false, pendingRound: Boolean = false, name: String? = null, teams: List<RunningMatchTeamInfo> = emptyList())`
  - `LiveMatchesLogic.merge(activated: List<LiveMatchInfo>, upcoming: List<LiveMatchInfo>, limit: Int): List<LiveMatchInfo>`

- [ ] **Step 1: Das DTO anlegen**

`backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/entity/LiveMatchInfo.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.eventInfo.entity

import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchStatusDto
import java.time.LocalDateTime
import java.util.UUID

/**
 * Ein Lauf im Tab „Live" der oeffentlichen Ergebnisanzeige: aktiviert ODER anstehend, jeder mit
 * seinem Zustand.
 *
 * Bis zum 09.08.2026 fuehrte dieser Tab ausschliesslich aktivierte Laeufe und zeigte keinen
 * Zustand - „In Vorbereitung" und „Laeuft" sahen dort identisch aus, und ein Lauf, der gleich dran
 * ist, stand gar nicht erst da.
 *
 * [status] ist die einzige Zustandsangabe dieses DTOs. Es gibt bewusst keine Felder `activatedAt`
 * oder `finishedAt` daneben: die Anzeige soll nichts selbst ableiten koennen, sondern lesen, was
 * [de.lambda9.ready2race.backend.app.matchStatus.boundary.MatchStatusLogic.matchStatus] entschieden
 * hat.
 */
data class LiveMatchInfo(
    val matchId: UUID,
    /** Null bei einem Programmpunkt (FREE-Platzhalter, siehe [name]) - der haengt an keinem Wettkampf. */
    val competitionId: UUID?,
    val competitionName: String,
    val categoryName: String?,
    val roundName: String?,
    val matchName: String?,
    /** Geplanter Start aus dem Zeitplan. */
    val startTime: LocalDateTime?,
    val status: MatchStatusDto,
    val executionOrder: Int,
    /**
     * Der Lauf ist abgesagt („Findet nicht statt"). Er bleibt trotzdem in der Liste stehen: ein
     * spurlos verschwundener Lauf ist fuer einen Zuschauer nicht von einem Anzeigefehler zu
     * unterscheiden. [teams] ist dann immer leer.
     */
    val cancelled: Boolean = false,
    /** Platzhalter fuer eine noch nicht erzeugte Runde; [teams] ist dann immer leer. */
    val pendingRound: Boolean = false,
    /** Name eines Programmpunkts (FREE-Slot wie „Mittagspause"), sonst null. */
    val name: String? = null,
    val teams: List<RunningMatchTeamInfo> = emptyList(),
)
```

- [ ] **Step 2: Den Test schreiben (er schlägt fehl, die Logik fehlt noch)**

`backend/src/test/kotlin/de/lambda9/ready2race/backend/app/eventInfo/LiveMatchesLogicTest.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.eventInfo

import de.lambda9.ready2race.backend.app.eventInfo.boundary.LiveMatchesLogic
import de.lambda9.ready2race.backend.app.eventInfo.entity.LiveMatchInfo
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchState
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchStatusDto
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiveMatchesLogicTest {

    private val noon: LocalDateTime = LocalDateTime.of(2026, 8, 14, 12, 0)

    private fun match(
        state: MatchState,
        startTime: LocalDateTime?,
        executionOrder: Int = 0,
        id: UUID = UUID.randomUUID(),
    ) = LiveMatchInfo(
        matchId = id,
        competitionId = UUID.randomUUID(),
        competitionName = "Männer Vierer",
        categoryName = null,
        roundName = "Vorlauf",
        matchName = "Lauf 1",
        startTime = startTime,
        status = MatchStatusDto(
            state = state,
            startedAt = if (state == MatchState.RUNNING) startTime else null,
            teamsTotal = 2,
            teamsScored = 0,
        ),
        executionOrder = executionOrder,
    )

    /** Wer die Seite oeffnet, sucht zuerst, was gerade passiert. */
    @Test
    fun activatedMatchesComeFirst() {
        val running = match(MatchState.RUNNING, noon.plusHours(2))
        val upcoming = match(MatchState.UPCOMING, noon)

        val merged = LiveMatchesLogic.merge(listOf(running), listOf(upcoming), limit = 10)

        assertEquals(listOf(running.matchId, upcoming.matchId), merged.map { it.matchId })
    }

    @Test
    fun withinAGroupTheEarlierStartWins() {
        val late = match(MatchState.UPCOMING, noon.plusMinutes(30))
        val early = match(MatchState.UPCOMING, noon)

        val merged = LiveMatchesLogic.merge(emptyList(), listOf(late, early), limit = 10)

        assertEquals(listOf(early.matchId, late.matchId), merged.map { it.matchId })
    }

    /** Ein Lauf ohne Termin steht am Ende seiner Gruppe, nicht am Anfang. */
    @Test
    fun matchesWithoutAStartTimeGoLast() {
        val unscheduled = match(MatchState.UNSCHEDULED, null)
        val scheduled = match(MatchState.UPCOMING, noon.plusHours(3))

        val merged = LiveMatchesLogic.merge(emptyList(), listOf(unscheduled, scheduled), limit = 10)

        assertEquals(listOf(scheduled.matchId, unscheduled.matchId), merged.map { it.matchId })
    }

    @Test
    fun sameStartTimeIsOrderedByExecutionOrder() {
        val second = match(MatchState.UPCOMING, noon, executionOrder = 2)
        val first = match(MatchState.UPCOMING, noon, executionOrder = 1)

        val merged = LiveMatchesLogic.merge(emptyList(), listOf(second, first), limit = 10)

        assertEquals(listOf(first.matchId, second.matchId), merged.map { it.matchId })
    }

    /**
     * Die zwei Abfragen laufen nacheinander. Wird ein Lauf dazwischen aktiviert, steht er in beiden
     * Listen - der aktivierte Eintrag traegt die frischere Aussage.
     */
    @Test
    fun aMatchInBothListsAppearsOnceAndActivatedWins() {
        val id = UUID.randomUUID()
        val activated = match(MatchState.PREPARING, noon, id = id)
        val stale = match(MatchState.UPCOMING, noon, id = id)

        val merged = LiveMatchesLogic.merge(listOf(activated), listOf(stale), limit = 10)

        assertEquals(1, merged.size)
        assertEquals(MatchState.PREPARING, merged.single().status.state)
    }

    /** Der Deckel gilt ueber beide Zweige - sonst verdraengen anstehende Laeufe den laufenden. */
    @Test
    fun theLimitAppliesToBothBranchesTogether() {
        val running = match(MatchState.RUNNING, noon)
        val upcoming = (1..5).map { match(MatchState.UPCOMING, noon.plusMinutes(it * 10L)) }

        val merged = LiveMatchesLogic.merge(listOf(running), upcoming, limit = 3)

        assertEquals(3, merged.size)
        assertEquals(running.matchId, merged.first().matchId)
    }

    @Test
    fun aLimitOfZeroOrLessYieldsNothing() {
        val running = match(MatchState.RUNNING, noon)

        assertTrue(LiveMatchesLogic.merge(listOf(running), emptyList(), limit = 0).isEmpty())
        assertTrue(LiveMatchesLogic.merge(listOf(running), emptyList(), limit = -1).isEmpty())
    }

    /**
     * Der Schutz der Ergebnisfreigabe. Ein beendeter oder vollstaendig gewerteter Lauf gehoert
     * ausschliesslich zu `/latest-match-results`, wo `PublicResultsVisibility` entscheidet, ob er
     * gezeigt werden darf. Die beiden Abfragen hinter [LiveMatchesLogic.merge] koennen ihn per SQL
     * gar nicht erst liefern (`CompetitionMatchRepo.getUpcomingMatchesForBoard` schliesst
     * `finished_at is not null` und „alle Boote gewertet" aus) - kommt er trotzdem an, ist das ein
     * Fehler, und die Zusammenfuehrung laesst ihn nicht durch.
     */
    @Test
    fun finishedAndAwaitingFinishMatchesNeverReachTheLiveList() {
        val finished = match(MatchState.FINISHED, noon)
        val awaiting = match(MatchState.AWAITING_FINISH, noon)
        val upcoming = match(MatchState.UPCOMING, noon.plusMinutes(20))

        val merged = LiveMatchesLogic.merge(
            activated = listOf(finished),
            upcoming = listOf(awaiting, upcoming),
            limit = 10,
        )

        assertEquals(listOf(upcoming.matchId), merged.map { it.matchId })
    }

    /** Abgesagte Laeufe bleiben stehen - sie sind die Antwort auf „wo ist mein Lauf?". */
    @Test
    fun cancelledMatchesStayInTheList() {
        val cancelled = match(MatchState.SKIPPED, noon).copy(cancelled = true)

        val merged = LiveMatchesLogic.merge(emptyList(), listOf(cancelled), limit = 10)

        assertEquals(listOf(cancelled.matchId), merged.map { it.matchId })
    }
}
```

- [ ] **Step 3: Test laufen lassen und Fehlschlag bestätigen**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21) && cd backend && ../mvnw -q test -Dtest=LiveMatchesLogicTest
```

Erwartet: Übersetzungsfehler `Unresolved reference: LiveMatchesLogic`.

- [ ] **Step 4: Die Logik schreiben**

`backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/boundary/LiveMatchesLogic.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.eventInfo.boundary

import de.lambda9.ready2race.backend.app.eventInfo.entity.LiveMatchInfo
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchState
import java.time.LocalDateTime

/**
 * Reine Funktionen fuer den Tab „Live" der oeffentlichen Ergebnisanzeige - ohne Datenbank und ohne
 * Uhr, damit die Reihenfolge und der Schutz der Ergebnisfreigabe ohne laufenden Server pruefbar
 * bleiben.
 */
object LiveMatchesLogic {

    /**
     * Zustaende, die in der oeffentlichen Live-Liste nichts zu suchen haben.
     *
     * Ein beendeter ([MatchState.FINISHED]) und ein vollstaendig gewerteter, aber nicht beendeter
     * Lauf ([MatchState.AWAITING_FINISH]) sind ERGEBNISSE. Ob sie oeffentlich sichtbar sind,
     * entscheidet allein `Event.publicResultsVisibility` ueber `/latest-match-results` - bis zum
     * Beenden kann noch eine Zeitstrafe kommen.
     *
     * Die beiden Abfragen hinter [merge] koennen solche Laeufe per SQL gar nicht liefern
     * (`CompetitionMatchRepo.getUpcomingMatchesForBoard` schliesst `finished_at is not null` und
     * „kein Boot mehr ohne Ergebnis" aus). Dieser Filter ist deshalb kein Arbeitsschritt, sondern
     * ein Riegel: Aendert jemand eine der Abfragen, faellt die Freigaberegel nicht still um,
     * sondern der Lauf verschwindet aus der Live-Liste.
     */
    private val notLive = setOf(MatchState.FINISHED, MatchState.AWAITING_FINISH)

    /**
     * Innerhalb einer Gruppe zaehlt die geplante Zeit; ein Lauf ohne Termin steht ans Ende, weil er
     * ueber seine Reihenfolge nichts aussagt. Bei gleicher Zeit entscheidet die Startfolge des
     * Wettkampfs.
     */
    private val byStartTime: Comparator<LiveMatchInfo> =
        compareBy<LiveMatchInfo, LocalDateTime?>(nullsLast()) { it.startTime }
            .thenBy { it.executionOrder }

    /**
     * Fuehrt die aktivierten und die anstehenden Laeufe zu einer Liste zusammen.
     *
     * [activated] steht vorn: wer die Seite oeffnet, sucht zuerst, was gerade passiert. Steht ein
     * Lauf in beiden Listen - die zwei Abfragen laufen nacheinander, dazwischen kann jemand
     * aktivieren -, gewinnt der aktivierte Eintrag; er traegt die frischere Aussage.
     *
     * [limit] deckelt das GESAMTE Ergebnis und nicht jeden Zweig fuer sich. Andernfalls
     * verdraengten zwanzig anstehende Laeufe den einen, der gerade faehrt.
     */
    fun merge(
        activated: List<LiveMatchInfo>,
        upcoming: List<LiveMatchInfo>,
        limit: Int,
    ): List<LiveMatchInfo> {
        val live = { matches: List<LiveMatchInfo> ->
            matches.filterNot { it.status.state in notLive }.sortedWith(byStartTime)
        }
        val front = live(activated)
        val frontIds = front.map { it.matchId }.toSet()
        val back = live(upcoming).filterNot { it.matchId in frontIds }
        return (front + back).take(limit.coerceAtLeast(0))
    }
}
```

- [ ] **Step 5: Test laufen lassen**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21) && cd backend && ../mvnw -q test -Dtest=LiveMatchesLogicTest
```

Erwartet: `BUILD SUCCESS`, alle neun Tests grün.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/entity/LiveMatchInfo.kt backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/boundary/LiveMatchesLogic.kt backend/src/test/kotlin/de/lambda9/ready2race/backend/app/eventInfo/LiveMatchesLogicTest.kt
git commit -m "Laufende und anstehende Laeufe zu einer Live-Liste zusammenfuehren"
```

---

## Task 3: Endpoint `GET /event/{eventId}/info/live-matches`

Die Verdrahtung: zwei vorhandene Abfragen, die Abbildung auf `LiveMatchInfo` mit `MatchStatusLogic` und die Route.

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/control/Conversions.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/boundary/EventInfoService.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/boundary/eventInfo.kt`
- Modify: `backend/src/main/resources/openapi/documentation.yaml`

**Interfaces:**
- Consumes: `LiveMatchInfo`, `LiveMatchesLogic.merge` (Task 2); `MatchStatusLogic.matchStatus`, `MatchStatusTeam`; die vorhandenen privaten `EventInfoService.getRunningMatches(eventId, limit, clubShortNames)` und `EventInfoService.getUpcomingMatchesForBoard(eventId, limit, clubShortNames)`, beide `App<Nothing, ApiResponse.ListDto<…>>`.
- Produces: `EventInfoService.getLiveMatches(eventId: UUID, limit: Int): App<Nothing, ApiResponse.ListDto<LiveMatchInfo>>`; SDK-Operation `getLiveMatches` mit `query: {limit}` (in Task 4 generiert).

- [ ] **Step 1: Die Abbildungen in `Conversions.kt` ergänzen**

Ergänze die Importe am Kopf der Datei:

```kotlin
import de.lambda9.ready2race.backend.app.eventInfo.entity.LiveMatchInfo
import de.lambda9.ready2race.backend.app.matchStatus.boundary.MatchStatusLogic
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchStatusTeam
```

Hänge ans Ende der Datei an:

```kotlin
/**
 * Ein anstehendes Boot hat noch kein Ergebnis - die Quellabfrage
 * (`CompetitionMatchTeamRepo.getTeamsForUpcomingMatch`) fragt Platz und Zeit gar nicht erst ab.
 * Die Ergebnisfelder bleiben deshalb leer, statt aus einer zweiten Abfrage gefuellt zu werden:
 * genau daran haengt, dass die Live-Liste keine Ergebnisse veroeffentlichen kann, die
 * `PublicResultsVisibility` zurueckhalten soll.
 */
fun UpcomingMatchTeamInfo.toRunningMatchTeamInfo() = RunningMatchTeamInfo(
    teamId = teamId,
    teamName = teamName,
    teamNumber = teamNumber,
    startNumber = startNumber,
    clubName = clubName,
    clubsShort = clubsShort,
    clubsFull = clubsFull,
    currentScore = null,
    currentPosition = null,
    timeString = null,
    penaltySeconds = null,
    penaltyNote = null,
    failed = false,
    failedReason = null,
    participants = participants,
)

/**
 * Ein aktivierter Lauf fuer die oeffentliche Live-Liste. `finishedAt` und `skipped` sind hier
 * immer aus dem Spiel: die Quellabfrage fuehrt ausschliesslich Laeufe mit `activated_at`, und
 * Beenden nimmt die Aktivierung zurueck. Der Zustand entsteht trotzdem ueber
 * [MatchStatusLogic.matchStatus] statt aus einem `if` - es gibt genau eine Ableitung.
 */
fun RunningMatchInfo.toLiveMatchInfo() = LiveMatchInfo(
    matchId = matchId,
    competitionId = competitionId,
    competitionName = competitionName,
    categoryName = categoryName,
    roundName = roundName,
    matchName = matchName,
    startTime = startTime,
    status = MatchStatusLogic.matchStatus(
        activatedAt = activatedAt,
        startTime = startTime,
        startedAt = startedAt,
        finishedAt = null,
        skipped = false,
        teams = teams.map {
            MatchStatusTeam(place = it.currentPosition, failed = it.failed, deregistered = false)
        },
    ),
    executionOrder = executionOrder,
    teams = teams,
)

/**
 * Ein anstehender Lauf fuer die oeffentliche Live-Liste. Die Quellabfrage schliesst aktivierte,
 * beendete und vollstaendig gewertete Laeufe aus; die Ableitung entscheidet damit nur noch
 * zwischen abgesagt, ungeplant und anstehend. Sie steht trotzdem hier und nicht als eigenes `if`,
 * damit die Anzeige dieselbe Aufzaehlung liest wie jede andere Oberflaeche.
 *
 * Alle Boote gehen als „noch offen" in die Ableitung - fuer einen anstehenden Lauf liegt kein
 * Ergebnis vor, und die Quellabfrage koennte auch keins liefern.
 */
fun UpcomingCompetitionMatchInfo.toLiveMatchInfo() = LiveMatchInfo(
    matchId = matchId,
    competitionId = competitionId,
    competitionName = competitionName,
    categoryName = categoryName,
    roundName = roundName,
    matchName = matchName,
    startTime = scheduledStartTime,
    status = MatchStatusLogic.matchStatus(
        activatedAt = null,
        startTime = scheduledStartTime,
        startedAt = null,
        finishedAt = null,
        skipped = cancelled,
        teams = teams.map { MatchStatusTeam(place = null, failed = false, deregistered = false) },
    ),
    executionOrder = executionOrder,
    cancelled = cancelled,
    pendingRound = pendingRound,
    name = name,
    teams = teams.map { it.toRunningMatchTeamInfo() },
)
```

- [ ] **Step 2: `getLiveMatches` im Service**

Ergänze die Importe in `EventInfoService.kt`:

```kotlin
import de.lambda9.ready2race.backend.app.eventInfo.control.toLiveMatchInfo
```

(`de.lambda9.ready2race.backend.app.eventInfo.entity.*` ist bereits importiert und deckt `LiveMatchInfo` ab.)

Füge die Funktion direkt hinter dem vorhandenen privaten `getRunningMatches` ein (also vor `getAthleteBoard`):

```kotlin
    /**
     * Der Tab „Live" der oeffentlichen Ergebnisanzeige: was gerade laeuft UND was als naechstes
     * dran ist, jeder Lauf mit seinem Zustand.
     *
     * Zwei bereits erprobte Quellen, keine dritte Abfrage:
     * - [getRunningMatches] liefert die aktivierten Laeufe (PREPARING, RUNNING) samt
     *   Teilergebnissen - unveraendert das, was dieser Tab schon immer zeigte.
     * - [getUpcomingMatchesForBoard] liefert die anstehenden (UPCOMING, UNSCHEDULED) und bringt
     *   die 30-Minuten-Nachfrist, die Absage-Markierung, die wartenden Runden und die Einstellung
     *   `showBreaksOnPublicBoards` unveraendert mit. Eine Regel, ein Ort.
     *
     * Warum ein eigener Endpoint und kein Schalter an `/running-matches`: der bedient auch den
     * Block `running` der Athleten-Anzeige. Anstehende Laeufe dort hineinzumischen zerstoerte die
     * Blocktrennung, auf der ihre ganze Darstellung aufbaut.
     *
     * Die Ergebnisfreigabe bleibt unberuehrt, und zwar ohne zusaetzliche Pruefung: die zweite
     * Abfrage schliesst beendete und vollstaendig gewertete Laeufe per SQL aus und liefert
     * Mannschaften ohne Platz und ohne Zeit. Ein Lauf, den `PublicResultsVisibility` zurueckhalten
     * soll, kann hier gar nicht entstehen (Riegel und Begruendung in [LiveMatchesLogic.merge]).
     *
     * Beide Quellen bekommen [limit] einzeln; gedeckelt wird erst nach dem Zusammenfuehren.
     */
    fun getLiveMatches(
        eventId: UUID,
        limit: Int,
    ): App<Nothing, ApiResponse.ListDto<LiveMatchInfo>> = KIO.comprehension {
        // Einmal je Abruf, nicht je Mannschaft - beide Bloecke loesen zusammen leicht hundert
        // Vereinsnamen auf, und dieser Endpoint laeuft im Viertelminutentakt.
        val clubShortNames = !clubShortNames()

        val activated = !getRunningMatches(eventId, limit, clubShortNames)
        val upcoming = !getUpcomingMatchesForBoard(eventId, limit, clubShortNames)

        KIO.ok(
            ApiResponse.ListDto(
                LiveMatchesLogic.merge(
                    activated = activated.data.map { it.toLiveMatchInfo() },
                    upcoming = upcoming.data.map { it.toLiveMatchInfo() },
                    limit = limit,
                )
            )
        )
    }
```

- [ ] **Step 3: Die Route**

In `eventInfo.kt`, direkt hinter dem Block `get("/running-matches") { … }` und vor `get("/athlete-board")`:

```kotlin
            // Der Tab "Live" der oeffentlichen Ergebnisanzeige: aktivierte UND anstehende Laeufe,
            // jeder mit seinem Zustand. Oeffentlich wie die Endpoints darueber.
            get("/live-matches") {
                call.respondComprehension {
                    val eventId = !pathParam("eventId", uuid)
                    val limit = !queryParam("limit", { it.toIntOrNull() ?: 10 })

                    EventInfoService.getLiveMatches(eventId, limit)
                }
            }
```

- [ ] **Step 4: `documentation.yaml` — der Pfad**

Füge in `backend/src/main/resources/openapi/documentation.yaml` direkt nach dem Block `/event/{eventId}/info/running-matches:` (er endet mit `500: $ref: '#/components/responses/500'`) und vor `/event/{eventId}/info/athlete-board:` ein:

```yaml
  /event/{eventId}/info/live-matches:
    parameters:
      - $ref: '#/components/parameters/eventId'
    get:
      operationId: getLiveMatches
      description: >-
        The "live" tab of the public results page: activated matches (PREPARING, RUNNING) together
        with the upcoming ones, each carrying its derived state. Finished matches and matches whose
        boats are all scored are deliberately absent - those are results and are governed by
        Event.publicResultsVisibility through /latest-match-results.
      parameters:
        - name: limit
          in: query
          required: true
          schema:
            type: integer
            default: 10
            minimum: 1
            maximum: 100
      responses:
        200:
          description: Live matches retrieved successfully
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/LiveMatchInfo'
        404:
          $ref: '#/components/responses/404'
        500:
          $ref: '#/components/responses/500'
```

- [ ] **Step 5: `documentation.yaml` — das Schema**

Füge unter `components: schemas:` direkt vor `RunningMatchInfo:` ein (gleiche Einrückung, vier Leerzeichen):

```yaml
    LiveMatchInfo:
      type: object
      description: >-
        A match in the "live" tab of the public results page - either activated or upcoming. The
        state is carried by `status` alone; there is deliberately no `activatedAt` or `finishedAt`
        next to it, so the display cannot derive a second truth of its own.
      required:
        - matchId
        - competitionName
        - status
        - executionOrder
        - teams
      properties:
        matchId:
          type: string
          format: uuid
        competitionId:
          type: string
          format: uuid
          nullable: true
          description: Null for a programme placeholder (FREE slot), which belongs to no competition.
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
        status:
          $ref: '#/components/schemas/MatchStatusDto'
        executionOrder:
          type: integer
        cancelled:
          type: boolean
          default: false
          description: >-
            The match does not take place. It stays in the list on purpose - a match that vanishes
            without a trace is indistinguishable from a display error. `teams` is empty then.
        pendingRound:
          type: boolean
          default: false
          description: Placeholder for a round that has not been created yet; `teams` is empty then.
        name:
          type: string
          nullable: true
          description: Name of a programme item (FREE slot such as "lunch break"), null for real matches.
        teams:
          type: array
          items:
            $ref: '#/components/schemas/RunningMatchTeamInfo'
```

- [ ] **Step 6: Backend übersetzen und alle Tests laufen lassen**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21) && cd backend && ../mvnw -q test
```

Erwartet: `BUILD SUCCESS`. Schlägt die Übersetzung fehl, fehlt meist ein Import in `Conversions.kt` oder `EventInfoService.kt` (`LiveMatchesLogic` liegt in `…eventInfo.boundary` und ist damit im selben Paket wie `EventInfoService` — kein Import nötig).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo backend/src/main/resources/openapi/documentation.yaml
git commit -m "Die oeffentliche Live-Liste ueber einen eigenen Endpoint ausliefern"
```

---

## Task 4: Den API-Client neu erzeugen

**Files:**
- Modify: `frontend/src/api/types.gen.ts` (generiert)
- Modify: `frontend/src/api/sdk.gen.ts` (generiert)

**Interfaces:**
- Consumes: `documentation.yaml` aus Task 3.
- Produces: TypeScript-Typ `LiveMatchInfo` und SDK-Funktion `getLiveMatches({signal, path: {eventId}, query: {limit}})` für die Tasks 8.

- [ ] **Step 1: Abhängigkeiten installieren**

```bash
cd frontend && npm ci
```

Schlägt `npm ci` mangels passender Lock-Datei fehl, nimm `npm install`. Der Ordner `node_modules` ist nicht eingecheckt und fehlt in einer frischen Arbeitskopie.

- [ ] **Step 2: Generieren**

```bash
cd frontend && npm run generate
```

- [ ] **Step 3: Ergebnis prüfen**

```bash
cd frontend && grep -n "LiveMatchInfo" src/api/types.gen.ts | head -5 && grep -n "getLiveMatches" src/api/sdk.gen.ts | head -5
```

Erwartet: je mindestens ein Treffer. Prüfe außerdem mit `git diff --stat src/api`, dass **nur** die beiden generierten Dateien angefasst wurden und der Diff neben `LiveMatchInfo`/`getLiveMatches` nichts Unerwartetes enthält.

- [ ] **Step 4: Commit**

```bash
cd frontend && git add src/api/types.gen.ts src/api/sdk.gen.ts
git commit -m "Den API-Client um die Live-Liste erweitern"
```

---

## Task 5: `StatusChip` und `useNow` herauslösen

Der Chip-Renderer und die Minutenuhr stehen heute privat in `CompetitionExecutionRound.tsx`. Die Ergebnisanzeige soll denselben benutzen, nicht einen ähnlichen. Diese Aufgabe ändert **kein Verhalten**.

**Files:**
- Create: `frontend/src/components/event/match/StatusChip.tsx`
- Create: `frontend/src/components/event/match/useNow.ts`
- Modify: `frontend/src/components/event/competition/excecution/CompetitionExecutionRound.tsx`

**Interfaces:**
- Consumes: `MatchChip` aus `@components/event/match/matchStatusChip.ts`.
- Produces:
  - `StatusChip` (Default-Export aus `@components/event/match/StatusChip.tsx`), Props `{chip: MatchChip | null}` — rendert `null`, wenn `chip` null ist.
  - `useNow` (benannter Export aus `@components/event/match/useNow.ts`), Signatur `(intervalMs?: number) => Date`, Voreinstellung 30000.

- [ ] **Step 1: `useNow.ts` anlegen**

```ts
import {useEffect, useState} from 'react'

/**
 * Uhr für die verstrichenen Minuten auf den Status-Chips („Läuft · 4 min", „Überfällig · 8 min").
 *
 * Ohne eigene Uhr stünde die Zahl auf dem Chip still und behauptete nach einer Viertelstunde noch
 * immer „Läuft · 1 min" — auch dort, wo die Ansicht im Hintergrund nachlädt: der Chip zählt so
 * zwischen zwei Abrufen weiter. 30 Sekunden reichen für eine Minutenangabe.
 */
export const useNow = (intervalMs = 30_000): Date => {
    const [now, setNow] = useState(() => new Date())
    useEffect(() => {
        const id = window.setInterval(() => setNow(new Date()), intervalMs)
        return () => window.clearInterval(id)
    }, [intervalMs])
    return now
}
```

- [ ] **Step 2: `StatusChip.tsx` anlegen**

```tsx
import {Chip} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {MatchChip} from '@components/event/match/matchStatusChip.ts'

/**
 * Ein [MatchChip] als MUI-Chip. Welcher Chip es ist, entscheidet ausschließlich
 * `matchStatusChip.ts` — hier wird nur noch übersetzt und gemalt.
 *
 * Bewusst geteilt zwischen Durchführungsseite und öffentlicher Ergebnisanzeige: derselbe Zustand
 * soll nicht nur dasselbe Wort, sondern auch dieselbe Farbe und dieselbe Form haben.
 */
const StatusChip = ({chip}: {chip: MatchChip | null}) => {
    const {t} = useTranslation()
    // Der Schlüssel steht erst zur Laufzeit fest, deshalb die gelockerte Signatur - dasselbe
    // Muster wie `stateChipProps` in EventSchedule.tsx.
    const translate = t as (key: string, values?: Record<string, string | number>) => string
    // null heißt "dieser Chip sagt hier nichts aus" (z.B. der Arena-Chip ohne erhobene
    // Check-in-Daten) - dann gar nichts zeigen, statt eine leere Hülle.
    if (!chip) return null
    return (
        <Chip
            size={'small'}
            label={translate(chip.labelKey, chip.values)}
            color={chip.color}
            sx={chip.strikeThrough ? {textDecoration: 'line-through'} : undefined}
        />
    )
}

export default StatusChip
```

- [ ] **Step 3: `CompetitionExecutionRound.tsx` umstellen**

Lösche dort die lokalen Definitionen `const useNow = …` (samt ihres KDoc-Blocks) und `const StatusChip = …` (samt KDoc) vollständig. Ergänze bei den Importen:

```ts
import StatusChip from '@components/event/match/StatusChip.tsx'
import {useNow} from '@components/event/match/useNow.ts'
```

Der vorhandene Import-Block aus `matchStatusChip.ts` verliert dabei `MatchChip`, falls dieser Typ danach in der Datei nicht mehr vorkommt — prüfe das mit `grep -n "MatchChip" frontend/src/components/event/competition/excecution/CompetitionExecutionRound.tsx` und entferne ihn dann aus der Import-Liste. Alle Aufrufstellen (`<StatusChip chip={…} />`, `const now = useNow()`) bleiben unverändert.

- [ ] **Step 4: Übersetzen und prüfen**

```bash
cd frontend && npx tsc -b && npm run lint
```

Erwartet: keine Ausgabe von `tsc`, keine Fehler von ESLint. Warnungen aus unberührten Dateien sind hinzunehmen; neue Fehler in den drei angefassten Dateien nicht.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/event/match/StatusChip.tsx frontend/src/components/event/match/useNow.ts frontend/src/components/event/competition/excecution/CompetitionExecutionRound.tsx
git commit -m "Den Status-Chip aus der Durchfuehrungsseite herausloesen"
```

---

## Task 6: Der Takter `polling.ts`

Reines TypeScript, kein React — nur so ist die Hintergrundaktualisierung in diesem Projekt prüfbar (Vitest läuft ohne DOM, `vite.config.ts` sammelt ausschließlich `src/**/*.test.ts`).

**Files:**
- Create: `frontend/src/utils/polling.ts`
- Create: `frontend/src/utils/polling.test.ts`

**Interfaces:**
- Consumes: nichts aus dem Projekt.
- Produces:
  - `type PollerState<T> = {data: T | null; lastUpdated: Date | null; initialLoad: boolean; failed: boolean}`
  - `const initialPollerState: <T>() => PollerState<T>`
  - `createPoller<T>(options: {load: (signal: AbortSignal) => Promise<T | null>; intervalMs: number; onState: (state: PollerState<T>) => void; now?: () => Date}): {start(): void; stop(): void; refreshNow(): void; suspend(): void; resume(): void}`
  - Vertrag von `load`: liefert die Daten bei Erfolg, `null` für „Antwort ohne Nutzdaten" (gilt als Fehlversuch); wirft bei Netzfehlern; ein `AbortError` zählt nicht als Fehlversuch.

- [ ] **Step 1: Den Test schreiben**

`frontend/src/utils/polling.test.ts`:

```ts
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {PollerState, createPoller} from './polling'

const INTERVAL = 15_000

/** Ein Versprechen, das der Test von Hand einlöst - so ist jeder Abruf einzeln steuerbar. */
const deferred = <T,>() => {
    let resolve!: (value: T) => void
    let reject!: (reason: unknown) => void
    const promise = new Promise<T>((res, rej) => {
        resolve = res
        reject = rej
    })
    return {promise, resolve, reject}
}

/** Sammelt die Zustände und den Abruf-Verlauf eines Takters. */
const harness = () => {
    const pending: {resolve: (value: string | null) => void; reject: (reason: unknown) => void; signal: AbortSignal}[] = []
    const states: PollerState<string>[] = []
    const poller = createPoller<string>({
        intervalMs: INTERVAL,
        onState: state => states.push(state),
        load: signal => {
            const d = deferred<string | null>()
            pending.push({resolve: d.resolve, reject: d.reject, signal})
            return d.promise
        },
    })
    const last = () => states[states.length - 1]
    return {poller, pending, states, last}
}

/** Lässt die Microtask-Warteschlange leerlaufen, ohne die Uhr zu bewegen. */
const flush = async () => {
    await Promise.resolve()
    await Promise.resolve()
    await Promise.resolve()
}

describe('createPoller', () => {
    beforeEach(() => {
        vi.useFakeTimers()
    })
    afterEach(() => {
        vi.useRealTimers()
    })

    it('lädt sofort beim Start und meldet die Daten', async () => {
        const {poller, pending, last} = harness()
        poller.start()
        expect(pending).toHaveLength(1)

        pending[0].resolve('erster Stand')
        await flush()

        expect(last().data).toBe('erster Stand')
        expect(last().initialLoad).toBe(false)
        expect(last().failed).toBe(false)
        expect(last().lastUpdated).not.toBeNull()
        poller.stop()
    })

    it('taktet nach dem Intervall weiter', async () => {
        const {poller, pending} = harness()
        poller.start()
        pending[0].resolve('a')
        await flush()

        await vi.advanceTimersByTimeAsync(INTERVAL)

        expect(pending).toHaveLength(2)
        poller.stop()
    })

    /** Der eigentliche Grund gegen setInterval: ein hängender Abruf darf keine Schlange bilden. */
    it('startet keinen zweiten Abruf, solange der erste läuft', async () => {
        const {poller, pending} = harness()
        poller.start()

        await vi.advanceTimersByTimeAsync(INTERVAL * 5)

        expect(pending).toHaveLength(1)
        poller.stop()
    })

    it('behält bei einem Fehler den letzten guten Stand', async () => {
        const {poller, pending, last} = harness()
        poller.start()
        pending[0].resolve('guter Stand')
        await flush()

        await vi.advanceTimersByTimeAsync(INTERVAL)
        pending[1].reject(new Error('kein Netz'))
        await flush()

        expect(last().data).toBe('guter Stand')
        expect(last().failed).toBe(true)
        poller.stop()
    })

    it('wertet eine Antwort ohne Nutzdaten als Fehlversuch', async () => {
        const {poller, pending, last} = harness()
        poller.start()
        pending[0].resolve(null)
        await flush()

        expect(last().data).toBeNull()
        expect(last().initialLoad).toBe(false)
        expect(last().failed).toBe(true)
        poller.stop()
    })

    it('taktet nach einem Fehler weiter und erholt sich', async () => {
        const {poller, pending, last} = harness()
        poller.start()
        pending[0].reject(new Error('kein Netz'))
        await flush()

        await vi.advanceTimersByTimeAsync(INTERVAL)
        expect(pending).toHaveLength(2)
        pending[1].resolve('wieder da')
        await flush()

        expect(last().data).toBe('wieder da')
        expect(last().failed).toBe(false)
        poller.stop()
    })

    it('bricht bei refreshNow den laufenden Abruf ab, ohne ihn als Fehler zu werten', async () => {
        const {poller, pending, last} = harness()
        poller.start()

        poller.refreshNow()
        expect(pending).toHaveLength(2)
        expect(pending[0].signal.aborted).toBe(true)

        // Der abgebrochene Abruf meldet sich verspätet mit einem AbortError.
        pending[0].reject(Object.assign(new Error('aborted'), {name: 'AbortError'}))
        await flush()
        expect(last().failed).toBe(false)

        pending[1].resolve('frisch')
        await flush()
        expect(last().data).toBe('frisch')
        poller.stop()
    })

    it('taktet im Hintergrund nicht und lädt beim Zurückkehren sofort', async () => {
        const {poller, pending} = harness()
        poller.start()
        pending[0].resolve('a')
        await flush()

        poller.suspend()
        await vi.advanceTimersByTimeAsync(INTERVAL * 3)
        expect(pending).toHaveLength(1)

        poller.resume()
        expect(pending).toHaveLength(2)
        poller.stop()
    })

    it('taktet nach stop nicht mehr', async () => {
        const {poller, pending} = harness()
        poller.start()
        pending[0].resolve('a')
        await flush()

        poller.stop()
        await vi.advanceTimersByTimeAsync(INTERVAL * 3)

        expect(pending).toHaveLength(1)
    })
})
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

```bash
cd frontend && npx vitest run src/utils/polling.test.ts
```

Erwartet: Fehlschlag, `Failed to resolve import "./polling"`.

- [ ] **Step 3: Den Takter schreiben**

`frontend/src/utils/polling.ts`:

```ts
/**
 * Der Stand einer Hintergrundaktualisierung.
 *
 * [data] ist der letzte ERFOLGREICHE Stand und bleibt bei einem Fehlversuch stehen — eine Seite,
 * die nach einem Funkloch leer wird, ist der schlechteste Ausgang. [failed] trägt dann die
 * Stand-von-Warnung.
 *
 * [initialLoad] unterscheidet „noch nie etwas gewusst" von „geladen, aber leer". Ohne dieses Feld
 * behauptete eine Anzeige nach einem gescheiterten ersten Abruf, es sei kein Lauf angesetzt.
 */
export type PollerState<T> = {
    data: T | null
    lastUpdated: Date | null
    initialLoad: boolean
    failed: boolean
}

export const initialPollerState = <T,>(): PollerState<T> => ({
    data: null,
    lastUpdated: null,
    initialLoad: true,
    failed: false,
})

export type PollerOptions<T> = {
    /**
     * Ein Abruf. Liefert die Daten bei Erfolg und `null` für eine Antwort ohne Nutzdaten (z.B.
     * HTTP 500) — die zählt als Fehlversuch statt als leeres Ergebnis. Ein Netzabbruch darf
     * werfen; ein `AbortError` gilt nicht als Fehlversuch, sondern als abgelöster Abruf.
     */
    load: (signal: AbortSignal) => Promise<T | null>
    intervalMs: number
    onState: (state: PollerState<T>) => void
    /** Nur für Tests: die Uhr für [PollerState.lastUpdated]. */
    now?: () => Date
}

export type Poller = {
    start: () => void
    stop: () => void
    /** Sofort neu laden; bricht einen laufenden Abruf ab. */
    refreshNow: () => void
    /** In den Hintergrund: der Takt hält an, ein laufender Abruf darf zu Ende gehen. */
    suspend: () => void
    /** Zurück in den Vordergrund: sofort ein Abruf, danach wieder im Takt. */
    resume: () => void
}

/**
 * Ein Takter, der im Hintergrund nachlädt.
 *
 * Bewusst ohne React: das Frontend prüft mit Vitest im Node-Umfeld, ohne DOM und ohne
 * Rendering-Bibliothek. Stünde diese Logik in einem Hook, wäre sie ungeprüft. `usePolledFetch`
 * ist deshalb nur noch Verdrahtung.
 *
 * Drei Eigenschaften sind hier wichtiger als Kürze:
 *
 * - **Kein Überlappen.** Höchstens ein Abruf ist unterwegs, und der Timer für den nächsten startet
 *   erst, wenn der vorige abgeschlossen ist. Mit `setInterval` würde ein hängender Abruf über einer
 *   langsamen Mobilfunkverbindung eine Warteschlange aufbauen, die nie wieder abfließt.
 * - **Fehler behalten den Stand.** Siehe [PollerState].
 * - **Im Hintergrund wird nicht geladen**, beim Zurückkehren sofort einmal.
 */
export const createPoller = <T,>({
    load,
    intervalMs,
    onState,
    now = () => new Date(),
}: PollerOptions<T>): Poller => {
    let state = initialPollerState<T>()
    let timer: ReturnType<typeof setTimeout> | null = null
    // Der Abruf, der gerade gilt. Ein älterer, abgelöster Abruf erkennt sich daran, dass hier
    // nicht mehr sein eigener Controller steht - er rührt dann weder Zustand noch Timer an.
    let current: AbortController | null = null
    let started = false
    let awake = true

    const emit = (patch: Partial<PollerState<T>>) => {
        state = {...state, ...patch}
        onState(state)
    }

    const clearTimer = () => {
        if (timer !== null) {
            clearTimeout(timer)
            timer = null
        }
    }

    const schedule = () => {
        clearTimer()
        if (!started || !awake) return
        timer = setTimeout(() => {
            timer = null
            void run()
        }, intervalMs)
    }

    const run = async () => {
        current?.abort()
        const own = new AbortController()
        current = own
        try {
            const data = await load(own.signal)
            if (current !== own) return
            if (data === null) {
                emit({initialLoad: false, failed: true})
            } else {
                emit({data, lastUpdated: now(), initialLoad: false, failed: false})
            }
        } catch (error) {
            if (current !== own) return
            // Abgelöst oder gestoppt - kein echter Fehler, und der Nachfolger hat bereits
            // übernommen.
            if (error instanceof Error && error.name === 'AbortError') return
            emit({initialLoad: false, failed: true})
        } finally {
            // Wurde dieser Abruf zwischenzeitlich abgelöst, überlässt er Timer und Zustand
            // vollständig dem neueren.
            if (current === own) {
                current = null
                schedule()
            }
        }
    }

    const restart = () => {
        clearTimer()
        void run()
    }

    return {
        start: () => {
            if (started) return
            started = true
            void run()
        },
        stop: () => {
            started = false
            clearTimer()
            current?.abort()
            current = null
        },
        refreshNow: () => {
            if (!started || !awake) return
            restart()
        },
        suspend: () => {
            awake = false
            clearTimer()
        },
        resume: () => {
            if (awake) return
            awake = true
            if (started) restart()
        },
    }
}
```

- [ ] **Step 4: Test laufen lassen**

```bash
cd frontend && npx vitest run src/utils/polling.test.ts
```

Erwartet: 9 Tests grün.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/utils/polling.ts frontend/src/utils/polling.test.ts
git commit -m "Einen Takter bauen, der im Hintergrund nachlaedt ohne sich zu ueberholen"
```

---

## Task 7: Der Hook `usePolledFetch`

**Files:**
- Create: `frontend/src/utils/usePolledFetch.ts`

**Interfaces:**
- Consumes: `createPoller`, `PollerState`, `initialPollerState` aus `@utils/polling.ts` (Task 6).
- Produces: `usePolledFetch<T>(load: (signal: AbortSignal) => Promise<T | null>, intervalMs: number, deps: unknown[]): PollerState<T>`.

- [ ] **Step 1: Den Hook schreiben**

`frontend/src/utils/usePolledFetch.ts`:

```ts
import {useEffect, useRef, useState} from 'react'
import {PollerState, createPoller, initialPollerState} from '@utils/polling.ts'

/**
 * Lädt im Hintergrund nach, ohne die Seite neu zu laden.
 *
 * Der Hook ist absichtlich nur Verdrahtung: was der Takt leistet — kein Überlappen, letzter guter
 * Stand bei Fehlern, Pause im Hintergrund — steht in [createPoller] und ist dort ohne DOM geprüft.
 * Hier kommen nur die beiden Ereignisse des Browsers dazu:
 *
 * - `visibilitychange`: im Hintergrund wird nicht getaktet; beim Zurückkehren sofort einmal.
 * - `online`: nach einem Funkloch soll der Stand nicht bis zum nächsten Takt alt bleiben.
 *
 * [load] darf sich bei jedem Rendern ändern (Pfeilfunktion im Aufrufer); der Takt wird deshalb
 * über [deps] gesteuert und nicht über die Identität der Funktion — sonst startete er bei jedem
 * Rendern neu.
 */
export const usePolledFetch = <T,>(
    load: (signal: AbortSignal) => Promise<T | null>,
    intervalMs: number,
    deps: unknown[],
): PollerState<T> => {
    const [state, setState] = useState<PollerState<T>>(initialPollerState<T>)

    const loadRef = useRef(load)
    loadRef.current = load

    useEffect(() => {
        // Neue Abhängigkeiten heißen: andere Daten. Der alte Stand gehörte zu einer anderen
        // Veranstaltung und darf nicht stehen bleiben.
        setState(initialPollerState<T>())

        const poller = createPoller<T>({
            load: signal => loadRef.current(signal),
            intervalMs,
            onState: setState,
        })
        poller.start()

        const onVisibilityChange = () => {
            if (document.hidden) {
                poller.suspend()
            } else {
                poller.resume()
            }
        }
        const onOnline = () => poller.refreshNow()

        document.addEventListener('visibilitychange', onVisibilityChange)
        window.addEventListener('online', onOnline)
        return () => {
            document.removeEventListener('visibilitychange', onVisibilityChange)
            window.removeEventListener('online', onOnline)
            poller.stop()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [intervalMs, ...deps])

    return state
}
```

- [ ] **Step 2: Übersetzen und prüfen**

```bash
cd frontend && npx tsc -b && npm run lint
```

Erwartet: keine Fehler. Meldet ESLint trotz der Ausnahmezeile etwas zu `react-hooks/exhaustive-deps`, prüfe, ob die Zeile direkt über dem Abhängigkeits-Array steht.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/utils/usePolledFetch.ts
git commit -m "Den Takter an React anschliessen"
```

---

## Task 8: Der Tab „Live" liest den Status und aktualisiert sich

**Files:**
- Modify: `frontend/src/components/results/ResultsMatchCard.tsx`
- Modify: `frontend/src/components/results/ResultsLiveMatches.tsx`
- Modify: `frontend/src/i18n/de/translations.json`
- Modify: `frontend/src/i18n/en/translations.json`
- Modify: `frontend/src/i18n/da/translations.json`

**Interfaces:**
- Consumes: `LiveMatchInfo`, `getLiveMatches` (Task 4); `StatusChip`, `useNow` (Task 5); `usePolledFetch` (Task 7); `matchStatusChip` aus `@components/event/match/matchStatusChip.ts`.
- Produces: nichts für spätere Tasks.

- [ ] **Step 1: Übersetzungen ergänzen**

In `frontend/src/i18n/de/translations.json` den Zweig `results.liveMatches` ersetzen durch:

```json
      "liveMatches": {
        "liveMatches": "Läuft und steht an",
        "noMatches": "Zurzeit ist kein Lauf angesetzt.",
        "loadFailed": "Die Läufe konnten nicht geladen werden.",
        "stale": "Stand von {{time}}",
        "pendingRound": "Aufstellung steht noch nicht fest"
      }
```

In `frontend/src/i18n/en/translations.json`:

```json
      "liveMatches": {
        "liveMatches": "Running and upcoming",
        "noMatches": "No match is scheduled right now.",
        "loadFailed": "The matches could not be loaded.",
        "stale": "As of {{time}}",
        "pendingRound": "Line-up not decided yet"
      }
```

In `frontend/src/i18n/da/translations.json`:

```json
      "liveMatches": {
        "liveMatches": "I gang og kommende",
        "noMatches": "Der er ingen løb planlagt lige nu.",
        "loadFailed": "Løbene kunne ikke indlæses.",
        "stale": "Opdateret {{time}}",
        "pendingRound": "Holdopstillingen er endnu ikke fastlagt"
      }
```

Die Einrückung richtet sich nach der Datei — schau nach, wie tief `results` dort liegt, und passe sie an. Der Schlüssel `liveMatches.liveMatches` behält seinen Namen, weil `ResultsLiveMatches.tsx` ihn bereits benutzt.

Prüfe danach, dass alle drei Dateien gültiges JSON sind:

```bash
cd frontend && for l in de en da; do python3 -m json.tool "src/i18n/$l/translations.json" > /dev/null && echo "$l ok"; done
```

- [ ] **Step 2: `ResultsMatchCard.tsx` um Chip und Sperre erweitern**

Ersetze den Kopf der Datei (Importe, Typen) und den Rumpf so:

```tsx
import {LatestMatchResultInfo, LiveMatchInfo, RunningMatchInfo} from '@api/types.gen.ts'
import {Box, Card, CardActionArea, CardContent, Chip, Typography} from '@mui/material'
import {format} from 'date-fns'
import {useTranslation} from 'react-i18next'
import {MatchChip} from '@components/event/match/matchStatusChip.ts'
import StatusChip from '@components/event/match/StatusChip.tsx'

export type ResultsMatchInfo = LatestMatchResultInfo | RunningMatchInfo | LiveMatchInfo

type Props<M extends ResultsMatchInfo> = {
    match: M
    selectMatch: (match: M) => void
    competition?: {
        competitionName: string
        competitionCategory?: string
    }
    /** Der Zustand des Laufs, wo die Ansicht ihn kennt. Null oder fehlend heißt: kein Chip. */
    statusChip?: MatchChip | null
    /**
     * Ein Lauf, hinter dem kein Dialog steht: abgesagt, wartende Runde, Programmpunkt. Die Karte
     * bleibt sichtbar, verliert aber ihre Klickfläche — ein Dialog hätte dort nichts zu zeigen.
     */
    disabled?: boolean
}

const ResultsMatchCard = <M extends ResultsMatchInfo>({
    match,
    selectMatch,
    competition,
    statusChip,
    disabled = false,
}: Props<M>) => {
    const {t} = useTranslation()

    const content = (
        <CardContent>
            <Box
                sx={{
                    display: 'flex',
                    justifyContent: 'space-between',
                }}>
                <Box>
                    {competition && (
                        <Chip
                            variant={'outlined'}
                            color={'primary'}
                            sx={{mb: 1}}
                            label={
                                <Typography fontWeight={'bold'} variant={'body2'}>
                                    {competition.competitionName +
                                        (competition.competitionCategory
                                            ? ` (${competition.competitionCategory})`
                                            : '')}
                                </Typography>
                            }
                        />
                    )}
                    <Typography>{match.roundName}</Typography>
                    <Box>
                        {match.matchName && (
                            <Typography variant={'h6'}>{match.matchName}</Typography>
                        )}
                    </Box>
                </Box>
                <Box
                    sx={{
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'flex-end',
                        gap: 0.5,
                    }}>
                    <StatusChip chip={statusChip ?? null} />
                    {match.startTime && (
                        <Typography>
                            {format(new Date(match.startTime), t('format.datetime'))}
                        </Typography>
                    )}
                </Box>
            </Box>
        </CardContent>
    )

    return (
        <Card sx={{flex: 1, width: 1, ...(disabled && {opacity: 0.6})}} key={match.matchId}>
            {disabled ? (
                content
            ) : (
                <CardActionArea onClick={() => selectMatch(match)}>{content}</CardActionArea>
            )}
        </Card>
    )
}

export default ResultsMatchCard
```

- [ ] **Step 3: `ResultsLiveMatches.tsx` neu schreiben**

```tsx
import {useTranslation} from 'react-i18next'
import {Alert, Stack, Typography} from '@mui/material'
import Throbber from '@components/Throbber.tsx'
import ResultsMatchCard from '@components/results/ResultsMatchCard.tsx'
import {useState} from 'react'
import {LiveMatchInfo} from '@api/types.gen.ts'
import {getLiveMatches} from '@api/sdk.gen.ts'
import ResultsMatchDialog from '@components/results/ResultsMatchDialog.tsx'
import {matchStatusChip} from '@components/event/match/matchStatusChip.ts'
import {useNow} from '@components/event/match/useNow.ts'
import {usePolledFetch} from '@utils/usePolledFetch.ts'
import {format} from 'date-fns'

type Props = {
    eventId: string
}

const MATCHES_LIMIT = 100
/**
 * Dieselbe Größenordnung wie die Athleten-Anzeige. Der Endpoint sitzt hinter dem
 * `publicInfo`-Rate-Limit, und ein Zustandswechsel darf am Ufer ruhig eine Viertelminute brauchen.
 */
const REFRESH_MS = 15_000

/**
 * Der Tab „Live" der öffentlichen Ergebnisanzeige: was gerade läuft UND was als nächstes dran ist.
 *
 * Bis zum 09.08.2026 zeigte er ausschließlich aktivierte Läufe, ohne Zustand und ohne
 * Nachladen — ein Lauf, der gleich dran war, stand nirgends, „In Vorbereitung" und „Läuft" sahen
 * gleich aus, und wer den Wechsel sehen wollte, musste die Seite neu laden.
 *
 * Der Zustand kommt fertig vom Server (`match.status`) und wird hier ausschließlich durch
 * `matchStatusChip` in einen Chip übersetzt — dieselbe Entscheidung, dieselben Wörter und dieselben
 * Farben wie auf der Durchführungsseite, im Zeitplan und im Schiedsrichter-Dashboard.
 */
const ResultsLiveMatches = ({eventId}: Props) => {
    const {t} = useTranslation()
    // Eigene Uhr für die verstrichenen Minuten auf dem Chip: so zählt „Läuft · 4 min" zwischen
    // zwei Abrufen weiter, statt eine Viertelminute lang stillzustehen.
    const now = useNow()

    const {data, lastUpdated, initialLoad, failed} = usePolledFetch<LiveMatchInfo[]>(
        async signal => {
            const {data} = await getLiveMatches({
                signal,
                path: {eventId},
                query: {limit: MATCHES_LIMIT},
            })
            return data ?? null
        },
        REFRESH_MS,
        [eventId],
    )

    const [dialogOpen, setDialogOpen] = useState(false)
    const [matchSelected, setMatchSelected] = useState<LiveMatchInfo | null>(null)
    const onClickMatch = (match: LiveMatchInfo) => {
        setDialogOpen(true)
        setMatchSelected(match)
    }
    const closeDialog = () => {
        setDialogOpen(false)
        setMatchSelected(null)
    }

    return (
        <>
            <Stack spacing={2} sx={{alignItems: 'center', p: 2}}>
                {initialLoad ? (
                    <Throbber />
                ) : data === null ? (
                    // Vor dem ersten Erfolg gescheitert: „konnte nicht geladen werden" ist etwas
                    // anderes als „kein Lauf angesetzt", und der Unterschied ist der ganze Grund
                    // für `initialLoad`.
                    <Alert severity={'warning'} sx={{width: 1}}>
                        {t('results.liveMatches.loadFailed')}
                    </Alert>
                ) : (
                    <>
                        {/* Der letzte gute Stand bleibt stehen, wenn ein Abruf scheitert - eine
                            leere Seite nach einem Funkloch wäre der schlechteste Ausgang. Die
                            Zeile sagt, wie alt das Gezeigte ist. */}
                        {failed && lastUpdated && (
                            <Typography variant={'body2'} color={'text.secondary'}>
                                {t('results.liveMatches.stale', {
                                    time: format(lastUpdated, t('format.time')),
                                })}
                            </Typography>
                        )}
                        {data.length === 0 ? (
                            <Alert severity={'info'} sx={{width: 1}}>
                                {t('results.liveMatches.noMatches')}
                            </Alert>
                        ) : (
                            data.map(match => (
                                <ResultsMatchCard
                                    match={match}
                                    selectMatch={onClickMatch}
                                    key={match.matchId}
                                    statusChip={matchStatusChip(
                                        match.status,
                                        match.startTime,
                                        now,
                                    )}
                                    // Abgesagt, wartende Runde oder Programmpunkt: hinter der
                                    // Karte steht keine Aufstellung, die ein Dialog zeigen könnte.
                                    disabled={
                                        match.cancelled === true ||
                                        match.pendingRound === true ||
                                        match.name != null
                                    }
                                    competition={{
                                        competitionName: match.name ?? match.competitionName,
                                        competitionCategory: match.categoryName ?? undefined,
                                    }}
                                />
                            ))
                        )}
                    </>
                )}
            </Stack>
            <ResultsMatchDialog
                match={matchSelected}
                dialogOpen={dialogOpen}
                closeDialog={closeDialog}
            />
        </>
    )
}
export default ResultsLiveMatches
```

Die Sortierung entfällt hier: sie passiert im Backend (`LiveMatchesLogic.merge`), damit Reihenfolge und Deckel zusammenpassen.

- [ ] **Step 4: Prüfen, dass `format.time` existiert**

```bash
cd frontend && python3 -c "
import json
d = json.load(open('src/i18n/de/translations.json'))
print(d['format'])
"
```

Erwartet: ein Objekt mit einem Schlüssel `time`. Fehlt er, nimm stattdessen `t('format.datetime')` im Aufruf in Step 3 und lasse die Übersetzungsdateien in Ruhe.

- [ ] **Step 5: Übersetzen, prüfen, bauen**

```bash
cd frontend && npx tsc -b && npm run lint && npm run test
```

Erwartet: keine Übersetzungsfehler, keine neuen Lint-Fehler, alle Vitest-Tests grün.

Häufige Stolperstelle: `ResultsMatchDialog` ist über `ResultsMatchInfo` typisiert und unterscheidet mit `'executionOrder' in match` (Startnummern-Sortierung) und `'deregistered' in team` (Ergebnis-Mannschaft). `LiveMatchInfo` trägt `executionOrder`, seine Mannschaften tragen kein `deregistered` — der Dialog verhält sich damit für Live-Läufe genau wie bisher für `RunningMatchInfo`. Ändere daran nichts.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/results/ResultsLiveMatches.tsx frontend/src/components/results/ResultsMatchCard.tsx frontend/src/i18n
git commit -m "Den oeffentlichen Live-Tab anstehende Laeufe und ihren Zustand zeigen lassen"
```

---

## Task 9: Gesamtprüfung

**Files:** keine.

**Interfaces:**
- Consumes: alles aus den Tasks 1–8.
- Produces: nichts.

- [ ] **Step 1: Backend vollständig**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21) && cd backend && ../mvnw -q test
```

Erwartet: `BUILD SUCCESS`. Besonders im Blick: `LiveDashboardLogicTest`, `MatchStatusLogicTest`, `EventScheduleLogicTest`, `ScheduleChainTest` — sie sichern die Kette, an der nichts geändert wurde.

- [ ] **Step 2: Frontend vollständig**

```bash
cd frontend && npm run test && npm run lint && npm run build
```

Erwartet: alle Tests grün, keine Lint-Fehler, erfolgreicher Produktionsbau.

- [ ] **Step 3: Nachweis, dass nur eine Statusableitung existiert**

```bash
cd /Users/thomas/Developer/privat/ready2race/.claude/worktrees/ready2race-unified-run-status-9c32dd
grep -rn "deriveMatchState" backend/src/main --include=*.kt
```

Erwartet: die Definition in `LiveDashboardLogic.kt` und ausschließlich Aufrufe in `MatchStatusLogic.kt`, `LiveDashboardService.kt` und `eventInfo/control/Conversions.kt`. Taucht anderswo eine eigene Ableitung aus `activatedAt`/`startedAt`/`finishedAt` auf, ist sie ein Fehler dieses Plans und gehört ersetzt.

- [ ] **Step 4: Nichts Ungewolltes im Diff**

```bash
git status --short && git log --oneline main..HEAD
```

Erwartet: sauberer Arbeitsbaum, acht bis neun Commits, keine Datei unter `backend/src/main/resources/db/migration/`.

---

## Ausstehend nach diesem Plan

Die Abnahme am laufenden System (Seed `seed-foerde.sql`, Start über `.claude/launch.json`): einen Lauf aktivieren und im Tab „Live" der öffentlichen Ergebnisanzeige den Chip von „Anstehend" über „In Vorbereitung" nach „Läuft" wandern sehen, ohne die Seite neu zu laden. Das steht bewusst außerhalb des Plans — es braucht eine laufende Datenbank und ein Augenpaar.
