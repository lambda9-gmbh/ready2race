# Freilos-Anzeige Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ein Freilos ist in Zeitplan, Schiedsrichter-Dashboard und Durchführungsübersicht als Freilos erkennbar, zeigt — wo belegbar — seine Ursache und seinen Quittierungsstand, ohne dass ein Knopf, ein Recht oder ein Statusübergang sich ändert.

**Architecture:** Eine neue reine Ableitung `MatchStatusLogic.deriveBye` im Backend beantwortet „ist das ein Freilos, und warum" nach exakt der Regel, die heute schon die Ergebniseingabe sperrt. Ihr Ergebnis reist als neues Feld `bye` im bestehenden `MatchStatusDto` (bzw. in `LiveDashboardMatchDto` und `EventScheduleSlotDto`) zu allen drei Ansichten. Im Frontend liest `matchStatusChip.ts` dieses eine Feld und gibt einen Freilos-Chip statt der ergebniserwartenden Chips aus; ein neues Modul `matchBye.ts` übersetzt die Ursache in einen Satz.

**Tech Stack:** Kotlin + jOOQ + tailwind-KIO (Backend), TypeSpec-freies OpenAPI-YAML als API-Vertrag, React 18 + MUI 6 + i18next (Frontend), JUnit/kotlin.test und Vitest.

## Global Constraints

- **Vollständige Spec:** `docs/superpowers/specs/2026-08-09-freilos-anzeige-design.md`. Bei Zweifeln gilt sie.
- **Keine Verhaltensänderung an Bedienung:** Knöpfe, Berechtigungen, `matchControls`, Aktivierung, Beenden, Absagen, `checkUpdateMatchResult` und `automaticFirstPlace` bleiben unangetastet.
- **Kein neuer `MatchState`.** Das Freilos ist ein Feld, kein Zustand — siehe die Warnung im KDoc von `MatchStatusDto`.
- **Freilos-Regel wortgleich zu heute:** Runde nicht `required` **und** genau eine Team-Zeile ist nicht `out`. Nicht verschärfen, nicht lockern.
- **Kein geratener Grund:** „Freilos wegen Abmeldung" nur bei vorhandenem `competition_deregistration`-Datensatz; der Freitext-Grund nur, wenn genau eine Zeile abgemeldet ist. Sonst der neutrale Satz.
- **Umlaute:** Deutsche Texte mit echten Umlauten (ä, ö, ü, ß), nie ae/oe/ue/ss.
- **Drei Sprachen:** Jeder neue Übersetzungsschlüssel muss in `frontend/src/i18n/de/translations.json`, `.../en/translations.json` und `.../da/translations.json` stehen.
- **Kommentarsprache:** Neue Kommentare und KDoc auf Deutsch, im Ton der umliegenden Datei (erklären *warum*, nicht *was*).
- **Commit-Nachrichten:** Deutsch oder Englisch im Stil der bestehenden Historie (kurze Aussagesätze). **Nie Claude, KI oder Co-Authored-By erwähnen.**

## Befehle (immer aus dem Repo-Wurzelverzeichnis heraus lesen, Pfade wie angegeben)

| Zweck | Befehl |
|---|---|
| Backend-Unittest | `cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -o test -Dtest=MatchStatusLogicTest` |
| Backend übersetzen | `cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -o -q compile` |
| Alle Backend-Tests | `cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -o test` |
| API-Typen erzeugen | `cd frontend && npm run generate` |
| Frontend-Tests | `cd frontend && npx vitest run` |
| Frontend-Typprüfung | `cd frontend && npx tsc -b` |
| Frontend-Lint | `cd frontend && npm run lint` |

`JAVA_HOME` fehlt in dieser Shell — die Zuweisung vor `./mvnw` ist Pflicht. Der Maven-Build lässt Flyway und den jOOQ-Codegen gegen die laufenden Postgres-Container (`backend-build-db-1` auf 7652, `backend-db-1` auf 7653) laufen; beide müssen stehen (`docker ps` prüfen, sonst `cd backend && docker compose up -d`).

## Dateien

**Backend, neu**
- `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/matchStatus/entity/MatchByeDto.kt` — `MatchByeCause`, `MatchByeDto`, `MatchByeTeam`. Nur Datentypen.
- `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/matchStatus/control/MatchByeRepo.kt` — eine Abfrage, die je Team-Zeile die Eingaben der Ableitung liefert.
- `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/matchStatus/boundary/MatchByeService.kt` — Zeilen → `Map<setupMatchId, MatchByeDto>`.

**Backend, geändert**
- `.../matchStatus/boundary/MatchStatusLogic.kt` — `deriveBye`, `matchStatus` reicht `bye` durch.
- `.../matchStatus/entity/MatchStatusDto.kt` — Feld `bye`.
- `.../competitionExecution/boundary/CompetitionExecutionService.kt` — `getProgress` holt die Freilose.
- `.../competitionExecution/control/Conversions.kt` — reicht sie in `MatchStatusLogic.matchStatus`.
- `.../liveDashboard/boundary/LiveDashboardService.kt`, `.../liveDashboard/entity/LiveDashboardDto.kt` — Feld `bye` an `LiveDashboardMatchDto`.
- `.../eventSchedule/boundary/EventScheduleService.kt`, `.../eventSchedule/entity/EventScheduleSlotDto.kt` — Feld `bye` am Slot.
- `backend/src/main/resources/openapi/documentation.yaml` — `MatchByeCause`, `MatchByeDto`, drei neue Felder.

**Backend, Tests**
- `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/matchStatus/MatchStatusLogicTest.kt` — erweitert.

**Frontend, neu**
- `frontend/src/components/event/match/matchBye.ts` + `matchBye.test.ts` — Erklärungstext.
- `frontend/src/components/event/competition/excecution/byeMatches.ts` + `byeMatches.test.ts` — die eine Aufteilung „Freilos-Panel / Lauf-Karten" für die Durchführungsseite.

**Frontend, geändert**
- `frontend/src/components/event/match/matchStatusChip.ts` + `matchStatusChip.test.ts` — Freilos-Chip, `arenaChip` schweigt, `slotMatchStatus` trägt `bye`.
- `frontend/src/components/event/liveDashboard/common.ts` + `common.test.ts` — neue reine Funktion `dashboardMatchStatus`.
- `frontend/src/components/event/schedule/EventSchedule.tsx` — Erklärungszeile.
- `frontend/src/components/event/liveDashboard/LiveDashboardMatchCard.tsx` — nutzt `dashboardMatchStatus`, Erklärungszeile.
- `frontend/src/components/event/competition/excecution/CompetitionExecutionRound.tsx` — Statusspalte im Freilos-Panel, Panel liest `byeMatches`.
- `frontend/src/components/event/competition/excecution/CompetitionExecution.tsx` — `matchesFiltered` wird `raceableMatches`.
- `frontend/src/i18n/{de,en,da}/translations.json` — neue Schlüssel.
- `frontend/src/api/types.gen.ts` — **generiert, nie von Hand bearbeiten.**

---

### Task 1: Die Freilos-Ableitung

Reine Funktion, kein Datenbankzugriff. Sie ist der einzige Ort, an dem steht, was ein Freilos ist und warum.

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/matchStatus/entity/MatchByeDto.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/matchStatus/entity/MatchStatusDto.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/matchStatus/boundary/MatchStatusLogic.kt`
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/matchStatus/MatchStatusLogicTest.kt`

**Interfaces:**
- Consumes: nichts.
- Produces:
  - `enum class MatchByeCause { DEREGISTRATION, NO_OPPONENT }`
  - `data class MatchByeDto(val cause: MatchByeCause, val teamName: String?, val reason: String?)`
  - `data class MatchByeTeam(val racing: Boolean, val name: String, val deregistered: Boolean, val deregistrationReason: String?)`
  - `MatchStatusLogic.deriveBye(roundRequired: Boolean, teams: List<MatchByeTeam>): MatchByeDto?`
  - `MatchStatusDto` mit zusätzlichem `val bye: MatchByeDto? = null` (letzter Parameter, mit Vorgabewert — bestehende Aufrufe bleiben gültig)
  - `MatchStatusLogic.matchStatus(...)` mit zusätzlichem letzten Parameter `bye: MatchByeDto? = null`

- [ ] **Step 1: Die Datentypen anlegen**

Neue Datei `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/matchStatus/entity/MatchByeDto.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.matchStatus.entity

/**
 * Warum ein Lauf ein Freilos ist - so weit es sich aus den Daten belegen lässt.
 *
 * [DEREGISTRATION] wird nur vergeben, wenn eine der nicht fahrenden Zeilen des Laufs einen
 * Abmelde-Datensatz trägt. `competition_deregistration` hat einen Unique-Index auf
 * `competition_registration`: eine Meldung ist entweder abgemeldet oder nicht, unabhängig davon, in
 * welcher Runde das geschah. Deshalb greift die Prüfung auch für eine Zeile, die nur als `out` aus
 * einer früheren Runde mitgeführt wird - und genau die ist im Betrieb der häufige Fall.
 *
 * [NO_OPPONENT] ist der neutrale Fallback für alles andere: es wurde von vornherein nur ein Boot in
 * diesen Lauf gesetzt, oder die Gegnerzeile ist ausgeschieden bzw. nicht weitergekommen. Ohne
 * Abmelde-Datensatz behauptet die Anzeige keine Abmeldung.
 */
enum class MatchByeCause { DEREGISTRATION, NO_OPPONENT }

/**
 * Das Freilos eines Laufs. Reine Anzeige: an der Lauf-Kette, an der Ergebnissperre und am
 * automatischen ersten Platz ändert dieser Datensatz nichts.
 */
data class MatchByeDto(
    val cause: MatchByeCause,
    /**
     * Die abgemeldeten Mannschaften, bei mehreren mit Komma verbunden - null bei
     * [MatchByeCause.NO_OPPONENT].
     */
    val teamName: String?,
    /**
     * Der gespeicherte Abmeldegrund - nur, wenn genau eine Zeile abgemeldet ist. Bei mehreren wäre
     * die Zuordnung Name -> Grund geraten, und geraten wird hier nichts.
     */
    val reason: String?,
)

/**
 * Eine Team-Zeile des Laufs, so weit die Freilos-Frage sie braucht. Bewusst ein eigener, minimaler
 * Typ statt eines der großen Team-DTOs - dasselbe Muster wie [MatchStatusTeam]: die Ableitung soll
 * ohne Datenbank und ohne Ansichtskontext prüfbar bleiben.
 */
data class MatchByeTeam(
    /** Fährt in diesem Lauf, ist also nicht als `out` aus einer früheren Runde mitgeführt. */
    val racing: Boolean,
    /** Anzeigename der Mannschaft - Verein, dahinter der Meldungsname, falls vorhanden. */
    val name: String,
    val deregistered: Boolean,
    val deregistrationReason: String?,
)
```

- [ ] **Step 2: Die fehlschlagenden Tests schreiben**

An `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/matchStatus/MatchStatusLogicTest.kt` **anhängen** (vor der schließenden Klammer der Klasse). Zusätzlich die Importe oben ergänzen:

```kotlin
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchByeCause
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchByeDto
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchByeTeam
```

Neuer Block:

```kotlin
    // --- deriveBye ---

    private fun racing(name: String = "RC Bergedorf") =
        MatchByeTeam(racing = true, name = name, deregistered = false, deregistrationReason = null)

    /** Aus der Vorrunde mitgeführt, aber nicht abgemeldet: ausgeschieden oder nicht weitergekommen. */
    private fun eliminated(name: String = "RV Hansa") =
        MatchByeTeam(racing = false, name = name, deregistered = false, deregistrationReason = null)

    private fun withdrawn(name: String = "RV Hansa", reason: String? = null) =
        MatchByeTeam(racing = false, name = name, deregistered = true, deregistrationReason = reason)

    @Test
    fun requiredRoundIsNeverABye() {
        assertNull(MatchStatusLogic.deriveBye(roundRequired = true, teams = listOf(racing())))
    }

    @Test
    fun twoRacingTeamsAreNoBye() {
        assertNull(MatchStatusLogic.deriveBye(false, listOf(racing("A"), racing("B"))))
    }

    @Test
    fun aMatchWithoutTeamsIsNoBye() {
        assertNull(MatchStatusLogic.deriveBye(false, emptyList()))
    }

    @Test
    fun aSingleSeededTeamIsAStructuralBye() {
        assertEquals(
            MatchByeDto(MatchByeCause.NO_OPPONENT, null, null),
            MatchStatusLogic.deriveBye(false, listOf(racing())),
        )
    }

    /** Ausgeschieden ist keine Abmeldung - ohne Datensatz wird auch keine behauptet. */
    @Test
    fun anEliminatedOpponentStaysNeutral() {
        assertEquals(
            MatchByeDto(MatchByeCause.NO_OPPONENT, null, null),
            MatchStatusLogic.deriveBye(false, listOf(racing(), eliminated())),
        )
    }

    @Test
    fun aWithdrawnOpponentNamesTeamAndReason() {
        assertEquals(
            MatchByeDto(MatchByeCause.DEREGISTRATION, "RV Hansa", "Krankheit"),
            MatchStatusLogic.deriveBye(false, listOf(racing(), withdrawn("RV Hansa", "Krankheit"))),
        )
    }

    @Test
    fun aWithdrawnOpponentWithoutAStoredReasonStillNamesTheTeam() {
        assertEquals(
            MatchByeDto(MatchByeCause.DEREGISTRATION, "RV Hansa", null),
            MatchStatusLogic.deriveBye(false, listOf(racing(), withdrawn("RV Hansa"))),
        )
    }

    /** Bei mehreren Abmeldungen wäre die Zuordnung Name -> Grund geraten. */
    @Test
    fun severalWithdrawalsDropTheReason() {
        assertEquals(
            MatchByeDto(MatchByeCause.DEREGISTRATION, "RV Hansa, RC Favorite", null),
            MatchStatusLogic.deriveBye(
                false,
                listOf(
                    racing(),
                    withdrawn("RV Hansa", "Krankheit"),
                    withdrawn("RC Favorite", "Materialschaden"),
                ),
            ),
        )
    }

    /** Eine Abmeldung neben einer Ausscheidung reicht für die Ursache - und für den Grund. */
    @Test
    fun aWithdrawalNextToAnEliminationStillCarriesTheReason() {
        assertEquals(
            MatchByeDto(MatchByeCause.DEREGISTRATION, "RV Hansa", "Krankheit"),
            MatchStatusLogic.deriveBye(
                false,
                listOf(racing(), eliminated("RG Wandsbek"), withdrawn("RV Hansa", "Krankheit")),
            ),
        )
    }

    @Test
    fun matchStatusCarriesTheBye() {
        val bye = MatchByeDto(MatchByeCause.NO_OPPONENT, null, null)
        val status = MatchStatusLogic.matchStatus(
            activatedAt = null,
            startTime = start,
            startedAt = null,
            finishedAt = null,
            skipped = false,
            teams = listOf(placed(1)),
            bye = bye,
        )
        assertEquals(bye, status.bye)
    }

    @Test
    fun matchStatusWithoutAByeSaysSo() {
        val status = MatchStatusLogic.matchStatus(
            activatedAt = null,
            startTime = start,
            startedAt = null,
            finishedAt = null,
            skipped = false,
            teams = listOf(open(), open()),
        )
        assertNull(status.bye)
    }
```

- [ ] **Step 3: Test laufen lassen und Fehlschlag bestätigen**

Run: `cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -o test -Dtest=MatchStatusLogicTest`
Expected: Übersetzungsfehler — `Unresolved reference: deriveBye` und `No value passed for parameter 'bye'` bzw. `Cannot find a parameter with this name: bye`.

- [ ] **Step 4: `MatchStatusDto` um das Feld erweitern**

In `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/matchStatus/entity/MatchStatusDto.kt` die `data class MatchStatusDto` um den letzten Parameter ergänzen:

```kotlin
data class MatchStatusDto(
    val state: MatchState,
    /** Tatsächlicher Start (`competition_match.started_at`) - null, solange niemand gestartet hat. */
    val startedAt: LocalDateTime?,
    val teamsTotal: Int,
    val teamsScored: Int,
    /** null = in dieser Ansicht nicht erhoben (Zeitplan, öffentliche Anzeigen). */
    val teamsInArena: Int? = null,
    /**
     * Gesetzt, wenn dieser Lauf ein Freilos ist - siehe [MatchStatusLogic.deriveBye]. Wie
     * "Überfällig" und "Teilweise gewertet" ist das eine Ablesung und ausdrücklich KEIN eigener
     * [MatchState]: ein neuer Wert in der Aufzählung fiele still in jedes `when`/`switch`, das
     * heute über sie verzweigt.
     */
    val bye: MatchByeDto? = null,
)
```

- [ ] **Step 5: `deriveBye` schreiben und in `matchStatus` durchreichen**

In `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/matchStatus/boundary/MatchStatusLogic.kt` die Importe ergänzen:

```kotlin
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchByeCause
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchByeDto
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchByeTeam
```

`matchStatus` bekommt den neuen Parameter und reicht ihn durch — die Signatur lautet danach:

```kotlin
    fun matchStatus(
        activatedAt: LocalDateTime?,
        startTime: LocalDateTime?,
        startedAt: LocalDateTime?,
        finishedAt: LocalDateTime?,
        skipped: Boolean,
        teams: List<MatchStatusTeam>,
        teamsInArena: Int? = null,
        bye: MatchByeDto? = null,
    ): MatchStatusDto {
```

und im `return MatchStatusDto(...)` als letztes Feld `bye = bye,`.

Danach die neue Ableitung anfügen (nach `matchStatus`, vor `teamsInArenaPerMatch`):

```kotlin
    /**
     * Ob dieser Lauf ein Freilos ist - und wenn ja, warum.
     *
     * Die Regel ist wortgleich zu der, die die Ergebniseingabe sperrt
     * (`CompetitionExecutionService.checkUpdateMatchResult`) und zu der, nach der die
     * Rundenerzeugung den automatischen ersten Platz vergibt (`automaticFirstPlace`): in einer
     * nicht verpflichtenden Runde fährt genau ein Boot. Sie wird hier weder verschärft noch
     * gelockert - dies ist reine Anzeige, und zwei Regeln für dieselbe Frage laufen früher oder
     * später auseinander.
     *
     * Über die Ursache entscheidet allein der Abmelde-Datensatz. Eine Zeile, die nur `out` ist,
     * kann ausgeschieden oder nicht weitergekommen sein - daraus eine Abmeldung zu machen, wäre
     * geraten. Der Freitext-Grund überlebt nur bei genau einer abgemeldeten Zeile: bei zweien wäre
     * die Zuordnung Name -> Grund ebenfalls geraten.
     */
    fun deriveBye(roundRequired: Boolean, teams: List<MatchByeTeam>): MatchByeDto? {
        if (roundRequired) return null
        if (teams.count { it.racing } != 1) return null

        val withdrawn = teams.filter { !it.racing && it.deregistered }
        if (withdrawn.isEmpty()) {
            return MatchByeDto(MatchByeCause.NO_OPPONENT, teamName = null, reason = null)
        }
        return MatchByeDto(
            cause = MatchByeCause.DEREGISTRATION,
            teamName = withdrawn.joinToString(", ") { it.name },
            reason = withdrawn.singleOrNull()?.deregistrationReason,
        )
    }
```

- [ ] **Step 6: Test laufen lassen und Erfolg bestätigen**

Run: `cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -o test -Dtest=MatchStatusLogicTest`
Expected: `BUILD SUCCESS`, keine Fehlschläge.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/app/matchStatus backend/src/test/kotlin/de/lambda9/ready2race/backend/app/matchStatus
git commit -m "Sag an einer Stelle, was ein Freilos ist und warum"
```

---

### Task 2: Die Freilose beschaffen und an die drei Ansichten verteilen

Die `out`-Zeilen — und damit der abgemeldete Gegner — fehlen in allen bestehenden Abfragewegen. Deshalb eine eigene, kleine Abfrage statt einer Erweiterung der großen: die Nutzlast des Schiedsrichter-Dashboards und damit sein ETag bleiben unberührt.

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/matchStatus/control/MatchByeRepo.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/matchStatus/boundary/MatchByeService.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/boundary/CompetitionExecutionService.kt` (in `getProgress`, ca. Zeile 330-379)
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/control/Conversions.kt` (`toCompetitionRoundDto`, ca. Zeile 49-135)
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/entity/LiveDashboardDto.kt` (`LiveDashboardMatchDto`, ca. Zeile 143-179)
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/boundary/LiveDashboardService.kt` (ca. Zeile 54 und `buildMatchDto`)
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventSchedule/entity/EventScheduleSlotDto.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventSchedule/boundary/EventScheduleService.kt` (`getSchedule`, ca. Zeile 36-93)

**Interfaces:**
- Consumes: `MatchStatusLogic.deriveBye`, `MatchByeDto`, `MatchByeTeam`, `MatchByeCause` aus Task 1; `MatchStatusLogic.matchStatus(..., bye = ...)`.
- Produces:
  - `MatchByeRepo.getByeInputs(eventId: UUID, competitionId: UUID? = null)` — jOOQ-Query, liefert `List<Record>` mit den Spalten `setup_match_id` (UUID), `round_required` (Boolean), `team_out` (Boolean), `club_name` (String?), `team_name` (String?), `deregistered` (Boolean), `deregistration_reason` (String?).
  - `MatchByeService.byeByMatch(eventId: UUID, competitionId: UUID? = null): App<Nothing, Map<UUID, MatchByeDto>>` — Schlüssel ist die Setup-Lauf-Id, Einträge ohne Freilos fehlen.
  - `LiveDashboardMatchDto` mit `val bye: MatchByeDto? = null` (direkt nach `state`; der Vorgabewert hält `LiveDashboardLogicTest` am Leben, das den Datensatz mit benannten Argumenten baut).
  - `EventScheduleSlotDto` mit `val bye: MatchByeDto?` (Pflichtparameter, am Ende — dort gibt es nur eine Bauststelle).
  - `CompetitionSetupRoundWithMatches.toCompetitionRoundDto(mixedTeamTerm, lastScanByParticipant, byeByMatch)` — dritter Parameter `byeByMatch: Map<UUID, MatchByeDto> = emptyMap()`.

- [ ] **Step 1: Die Abfrage schreiben**

Neue Datei `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/matchStatus/control/MatchByeRepo.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.matchStatus.control

import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.UUID

object MatchByeRepo {

    /**
     * Je Team-Zeile aller Läufe einer Veranstaltung die Angaben, aus denen
     * [de.lambda9.ready2race.backend.app.matchStatus.boundary.MatchStatusLogic.deriveBye] ein
     * Freilos ableitet.
     *
     * Eine eigene Abfrage und nicht eine Erweiterung der bestehenden Team-Abfragen: die filtern
     * allesamt `out`-Zeilen heraus (`CompetitionExecutionService.getProgress`,
     * `LiveDashboardRepo.getTeams`), und genau in denen steckt der abgemeldete Gegner. Die
     * Nutzlast des Schiedsrichter-Dashboards - und damit sein ETag - bleibt so unberührt.
     */
    fun getByeInputs(eventId: UUID, competitionId: UUID? = null) = Jooq.query {
        select(
            COMPETITION_MATCH_TEAM.COMPETITION_MATCH.`as`("setup_match_id"),
            COMPETITION_SETUP_ROUND.REQUIRED.`as`("round_required"),
            COMPETITION_MATCH_TEAM.OUT.`as`("team_out"),
            CLUB.NAME.`as`("club_name"),
            COMPETITION_REGISTRATION.NAME.`as`("team_name"),
            COMPETITION_DEREGISTRATION.COMPETITION_REGISTRATION.isNotNull.`as`("deregistered"),
            COMPETITION_DEREGISTRATION.REASON.`as`("deregistration_reason"),
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
            // Bewusst OHNE Rundenbedingung, anders als in LiveDashboardRepo.getTeams: die
            // Abmeldung ist je Meldung eindeutig (unique index auf competition_registration).
            // Genau deshalb trägt sie auch für eine Zeile, die als `out` aus einer früheren Runde
            // mitgeführt wird - und das ist der Fall, den "Freilos wegen Abmeldung" meint.
            .leftJoin(COMPETITION_DEREGISTRATION)
            .on(COMPETITION_DEREGISTRATION.COMPETITION_REGISTRATION.eq(COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION))
            .where(COMPETITION.EVENT.eq(eventId))
            .and(competitionId?.let { COMPETITION.ID.eq(it) } ?: DSL.noCondition())
            .fetch()
    }
}
```

- [ ] **Step 2: Übersetzen und die Spaltennamen bestätigen**

Run: `cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -o -q compile`
Expected: keine Ausgabe (Erfolg). Bei `Unresolved reference` auf eine Tabelle prüfen, ob der Import `...database.generated.tables.references.*` steht.

- [ ] **Step 3: Den Dienst schreiben, der Zeilen zu Freilosen macht**

Neue Datei `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/matchStatus/boundary/MatchByeService.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.matchStatus.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.matchStatus.control.MatchByeRepo
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchByeDto
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchByeTeam
import de.lambda9.tailwind.core.extensions.kio.orDie
import org.jooq.Record
import java.util.UUID

object MatchByeService {

    /**
     * Die Freilose einer Veranstaltung, nach Setup-Lauf. Läufe ohne Freilos fehlen in der Karte -
     * die Aufrufer fragen mit `byeByMatch[matchId]` und bekommen null.
     */
    fun byeByMatch(eventId: UUID, competitionId: UUID? = null): App<Nothing, Map<UUID, MatchByeDto>> =
        MatchByeRepo.getByeInputs(eventId, competitionId).orDie().map { rows -> group(rows) }

    /**
     * Der Anzeigename einer Mannschaft: Verein, dahinter der Meldungsname, falls es einen gibt -
     * dieselbe Zusammensetzung, die das Panel "Teams mit Freilos" auf der Durchführungsseite zeigt.
     */
    private fun teamName(record: Record): String = listOfNotNull(
        record.get("club_name", String::class.java),
        record.get("team_name", String::class.java),
    ).joinToString(" ")

    private fun group(rows: List<Record>): Map<UUID, MatchByeDto> =
        rows.groupBy { it.get("setup_match_id", UUID::class.java)!! }
            .mapNotNull { (matchId, matchRows) ->
                val bye = MatchStatusLogic.deriveBye(
                    roundRequired = matchRows.first().get("round_required", Boolean::class.java) == true,
                    teams = matchRows.map { row ->
                        MatchByeTeam(
                            racing = row.get("team_out", Boolean::class.java) != true,
                            name = teamName(row),
                            deregistered = row.get("deregistered", Boolean::class.java) == true,
                            deregistrationReason = row.get("deregistration_reason", String::class.java),
                        )
                    },
                )
                bye?.let { matchId to it }
            }
            .toMap()
}
```

- [ ] **Step 4: Die Durchführungsseite anschließen**

In `Conversions.kt` den Import ergänzen:

```kotlin
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchByeDto
```

Die Signatur von `toCompetitionRoundDto` um einen dritten Parameter erweitern (das KDoc darüber bleibt, ergänzt um einen Absatz):

```kotlin
/**
 * [byeByMatch] kommt von außen, weil `getProgress` die `out`-Zeilen bereits herausgefiltert hat,
 * bevor diese Umwandlung läuft - der abgemeldete Gegner ist hier also nicht mehr zu sehen.
 */
fun CompetitionSetupRoundWithMatches.toCompetitionRoundDto(
    mixedTeamTerm: String?,
    lastScanByParticipant: Map<UUID, Pair<String, LocalDateTime>> = emptyMap(),
    byeByMatch: Map<UUID, MatchByeDto> = emptyMap(),
) = run {
```

Im `MatchStatusLogic.matchStatus(...)`-Aufruf als letztes Argument ergänzen:

```kotlin
                            teamsInArena = teamsInArenaPerMatch[index],
                            bye = byeByMatch[match.second.id],
                        ),
```

In `CompetitionExecutionService.getProgress` den Import ergänzen:

```kotlin
import de.lambda9.ready2race.backend.app.matchStatus.boundary.MatchByeService
```

und direkt vor der `sortedRounds.filter { ... }.traverse { ... }`-Zeile:

```kotlin
            // Vor dem out-Filter unten geholt: die abgemeldete Gegnerzeile fällt dort heraus, und
            // genau sie trägt die Ursache des Freiloses.
            val byeByMatch = !MatchByeService.byeByMatch(eventId, competitionId)
```

und im `traverse` den Aufruf erweitern:

```kotlin
                    .toCompetitionRoundDto(event.mixedTeamTerm, lastScanByParticipant, byeByMatch)
```

- [ ] **Step 5: Das Schiedsrichter-Dashboard anschließen**

In `LiveDashboardDto.kt` den Import ergänzen:

```kotlin
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchByeDto
```

und `LiveDashboardMatchDto` direkt nach `state` erweitern:

```kotlin
    /**
     * Gesetzt, wenn dieser Lauf ein Freilos ist - siehe `MatchStatusLogic.deriveBye`. Kein eigener
     * [LiveDashboardMatchState]: das Freilos sagt etwas über den Lauf, nicht über seinen Fortschritt,
     * und ein aktivierter Lauf bleibt RUNNING.
     */
    val bye: MatchByeDto? = null,
```

Der Vorgabewert ist kein Schmuck: `LiveDashboardLogicTest.match(...)` baut diesen Datensatz mit benannten Argumenten, und ohne ihn müsste der Test für ein Feld nachgezogen werden, das ihn nichts angeht.

In `LiveDashboardService.kt` den Import ergänzen:

```kotlin
import de.lambda9.ready2race.backend.app.matchStatus.boundary.MatchByeService
```

direkt hinter `val slotRecords = !EventScheduleRepo.getSlots(eventId).orDie()`:

```kotlin
            val byeByMatch = !MatchByeService.byeByMatch(eventId)
```

und in `buildMatchDto`, im `LiveDashboardMatchDto(...)` direkt nach `state = ...`:

```kotlin
                        bye = byeByMatch[matchId],
```

- [ ] **Step 6: Den Zeitplan anschließen**

In `EventScheduleSlotDto.kt` den Import ergänzen:

```kotlin
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchByeDto
```

und `EventScheduleSlotDto` am Ende erweitern:

```kotlin
    /**
     * Gesetzt, wenn der verknüpfte Lauf ein Freilos ist - siehe `MatchStatusLogic.deriveBye`. Null
     * für freie Slots und für Slots ohne erzeugten Lauf: dort gibt es keine Mannschaften, aus denen
     * sich etwas ableiten ließe.
     */
    val bye: MatchByeDto?,
```

In `EventScheduleService.getSchedule` den Import ergänzen:

```kotlin
import de.lambda9.ready2race.backend.app.matchStatus.boundary.MatchByeService
```

hinter `val chainProgressionMode = !EventRepo.getChainProgressionMode(eventId).orDie()`:

```kotlin
            val byeByMatch = !MatchByeService.byeByMatch(eventId)
```

und im `EventScheduleSlotDto(...)` als letztes Feld:

```kotlin
                    bye = r[EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH]?.let { byeByMatch[it] },
```

- [ ] **Step 7: Übersetzen und die vorhandenen Tests laufen lassen**

Run: `cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -o test`
Expected: `BUILD SUCCESS`. Schlägt ein Aufruf von `toCompetitionRoundDto` oder ein Konstruktoraufruf von `LiveDashboardMatchDto`/`EventScheduleSlotDto` fehl (fehlender Parameter `bye`), diese Stelle mit `bye = null` bzw. dem passenden Kartenzugriff nachziehen und erneut laufen lassen.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/kotlin
git commit -m "Reiche das Freilos an Zeitplan, Board und Durchfuehrung durch"
```

---

### Task 3: Der API-Vertrag und die erzeugten Typen

**Files:**
- Modify: `backend/src/main/resources/openapi/documentation.yaml`
- Modify (generiert): `frontend/src/api/types.gen.ts`

**Interfaces:**
- Consumes: die Kotlin-DTOs aus Task 1 und 2.
- Produces: TypeScript-Typen `MatchByeCause`, `MatchByeDto`; `MatchStatusDto.bye`, `LiveDashboardMatchDto.bye`, `EventScheduleSlotDto.bye`.

- [ ] **Step 1: Die neuen Schemas eintragen**

In `backend/src/main/resources/openapi/documentation.yaml` **direkt vor** `    MatchStatusDto:` einfügen (Einrückung: vier Leerzeichen für den Schema-Namen):

```yaml
    MatchByeCause:
      type: string
      description: >-
        Why a match is a bye. DEREGISTRATION is only used when one of the non-racing rows of the
        match carries a deregistration record - competition_deregistration is unique per
        registration, so it also applies to a row carried over as OUT from an earlier round.
        NO_OPPONENT is the neutral fallback for everything else (only one boat seeded, or the
        opponent row was eliminated): without a record no withdrawal is claimed.
      enum:
        - DEREGISTRATION
        - NO_OPPONENT

    MatchByeDto:
      type: object
      description: >-
        The bye of a match. Display only - it changes nothing about the chain, the result lock or
        the automatic first place.
      required:
        - cause
      properties:
        cause:
          $ref: '#/components/schemas/MatchByeCause'
        teamName:
          type: string
          nullable: true
          description: The withdrawn teams, comma separated when there are several - null for NO_OPPONENT.
        reason:
          type: string
          nullable: true
          description: >-
            The stored withdrawal reason - only when exactly one row is deregistered, because with
            several the mapping name -> reason would be a guess.
```

- [ ] **Step 2: Die drei Felder eintragen**

In `MatchStatusDto` unter `properties:` hinter `teamsInArena` anfügen:

```yaml
        bye:
          allOf:
            - $ref: '#/components/schemas/MatchByeDto'
          nullable: true
          description: >-
            Set when this match is a bye. Like "overdue" and "partially scored" this is a reading,
            not a state of its own.
```

In `LiveDashboardMatchDto` unter `properties:` hinter `state` anfügen:

```yaml
        bye:
          allOf:
            - $ref: '#/components/schemas/MatchByeDto'
          nullable: true
          description: Set when this match is a bye - the referee dashboard shows the reason below the match.
```

In `EventScheduleSlotDto` unter `properties:` hinter `matchTeamsScored` anfügen:

```yaml
        bye:
          allOf:
            - $ref: '#/components/schemas/MatchByeDto'
          nullable: true
          description: Set when the linked match is a bye - null for free slots and slots whose round is not materialized yet.
```

- [ ] **Step 3: Typen erzeugen**

Run: `cd frontend && npm run generate`
Expected: läuft durch, `frontend/src/api/types.gen.ts` wird neu geschrieben.

- [ ] **Step 4: Das Ergebnis prüfen**

Run: `grep -n "MatchByeDto\|MatchByeCause\|bye" frontend/src/api/types.gen.ts | head -20`
Expected: `export type MatchByeCause = 'DEREGISTRATION' | 'NO_OPPONENT'`, ein `export type MatchByeDto = {...}` mit `cause`, `teamName?`, `reason?`, und je ein `bye?:`-Feld in `MatchStatusDto`, `LiveDashboardMatchDto` und `EventScheduleSlotDto`.

**Auf den genauen Typ des Feldes achten.** Erzeugt der Generator `bye?: MatchByeDto` (ohne `| null`), scheitern die späteren Zuweisungen `bye: slot.bye` und `bye: match.bye` in Task 5 und 6, weil dort `null` ankommen kann. Tritt das ein, in `documentation.yaml` bei allen drei Feldern das `nullable: true` durch ein zusätzliches `- type: 'null'` im `allOf` ersetzen — oder, falls der Generator das nicht mitmacht, an den beiden Zuweisungsstellen `?? undefined` anhängen. Nicht die generierte Datei von Hand nachbessern: sie wird beim nächsten Lauf überschrieben.

Run: `cd frontend && npx tsc -b`
Expected: keine Fehler (die neuen Felder sind optional, bestehender Code bleibt gültig).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/openapi/documentation.yaml frontend/src/api
git commit -m "Nimm das Freilos in den API-Vertrag auf"
```

---

### Task 4: Chip und Erklärungstext im Frontend

Das Herzstück: hier entscheidet sich, dass ein Freilos nicht mehr wie ein ergebnisoffener Lauf aussieht.

**Files:**
- Modify: `frontend/src/components/event/match/matchStatusChip.ts`
- Modify: `frontend/src/components/event/match/matchStatusChip.test.ts`
- Create: `frontend/src/components/event/match/matchBye.ts`
- Create: `frontend/src/components/event/match/matchBye.test.ts`
- Modify: `frontend/src/i18n/de/translations.json`, `frontend/src/i18n/en/translations.json`, `frontend/src/i18n/da/translations.json`

**Interfaces:**
- Consumes: `MatchStatusDto.bye`, `MatchByeDto`, `EventScheduleSlotDto.bye` aus Task 3.
- Produces:
  - `matchStatusChip(status, startTime, now)` gibt bei `status.bye != null` einen Freilos-Chip zurück.
  - `arenaChip(status)` gibt bei `status.bye != null` `null` zurück.
  - `slotMatchStatus(slot)` trägt `bye` in das erzeugte `MatchStatusDto`.
  - `byeExplanation(bye: MatchByeDto | null | undefined): ByeExplanation | null` mit `type ByeExplanation = {key: string; values?: Record<string, string>}` aus `matchBye.ts`.

- [ ] **Step 1: Die fehlschlagenden Tests für den Chip schreiben**

In `frontend/src/components/event/match/matchStatusChip.test.ts` den Import oben erweitern:

```typescript
import {EventScheduleSlotDto, MatchByeDto, MatchStatusDto} from '@api/types.gen.ts'
```

und einen neuen `describe`-Block **am Ende der Datei** anfügen:

```typescript
const structuralBye: MatchByeDto = {cause: 'NO_OPPONENT'}
const withdrawalBye: MatchByeDto = {
    cause: 'DEREGISTRATION',
    teamName: 'RV Hansa',
    reason: 'Krankheit',
}

describe('matchStatusChip beim Freilos', () => {
    it('sagt „offen", solange niemand quittiert hat', () => {
        const chip = matchStatusChip(
            status({state: 'AWAITING_FINISH', bye: structuralBye}),
            minutesAgo(30),
            NOW,
        )
        expect(chip).toEqual({labelKey: 'event.match.status.bye.open', color: 'info'})
    })

    /** „Überfällig" würde ein Ergebnis einfordern, auf das niemand wartet. */
    it('wird nie überfällig', () => {
        const chip = matchStatusChip(
            status({state: 'UPCOMING', bye: structuralBye}),
            minutesAgo(OVERDUE_GRACE_MINUTES + 60),
            NOW,
        )
        expect(chip.labelKey).toBe('event.match.status.bye.open')
    })

    /** Ebenso wenig „Teilweise gewertet": zu werten gibt es hier nichts. */
    it('wird nie teilweise gewertet', () => {
        const chip = matchStatusChip(
            status({state: 'UPCOMING', teamsTotal: 2, teamsScored: 1, bye: withdrawalBye}),
            minutesAgo(1),
            NOW,
        )
        expect(chip.labelKey).toBe('event.match.status.bye.open')
    })

    it('sagt „quittiert", sobald der Lauf beendet ist', () => {
        const chip = matchStatusChip(
            status({state: 'FINISHED', bye: structuralBye}),
            minutesAgo(30),
            NOW,
        )
        expect(chip).toEqual({labelKey: 'event.match.status.bye.acknowledged', color: 'success'})
    })

    it('sagt „entfallen" und streicht durch, wenn der Slot abgesagt ist', () => {
        const chip = matchStatusChip(
            status({state: 'SKIPPED', bye: structuralBye}),
            minutesAgo(30),
            NOW,
        )
        expect(chip).toEqual({
            labelKey: 'event.match.status.bye.cancelled',
            color: 'default',
            strikeThrough: true,
        })
    })

    /** Was tatsächlich passiert, schlägt weiterhin alles: aktiviert heißt aktiviert. */
    it('tritt hinter einen aktivierten Lauf zurück', () => {
        expect(
            matchStatusChip(status({state: 'PREPARING', bye: structuralBye}), minutesAgo(1), NOW)
                .labelKey,
        ).toBe('event.match.status.preparing')
        expect(
            matchStatusChip(
                status({state: 'RUNNING', startedAt: minutesAgo(4), bye: structuralBye}),
                minutesAgo(5),
                NOW,
            ).labelKey,
        ).toBe('event.match.status.running')
    })

    it('lässt den Arena-Chip schweigen', () => {
        expect(
            arenaChip(
                status({state: 'UPCOMING', teamsTotal: 1, teamsInArena: 0, bye: structuralBye}),
            ),
        ).toBeNull()
    })
})
```

- [ ] **Step 2: Die fehlschlagenden Tests für den Erklärungstext schreiben**

Neue Datei `frontend/src/components/event/match/matchBye.test.ts`:

```typescript
import {describe, expect, it} from 'vitest'
import {byeExplanation} from './matchBye.ts'

describe('byeExplanation', () => {
    it('schweigt ohne Freilos', () => {
        expect(byeExplanation(null)).toBeNull()
        expect(byeExplanation(undefined)).toBeNull()
    })

    it('nennt bei einer Abmeldung Mannschaft und Grund', () => {
        expect(
            byeExplanation({cause: 'DEREGISTRATION', teamName: 'RV Hansa', reason: 'Krankheit'}),
        ).toEqual({
            key: 'event.match.bye.deregistrationWithReason',
            values: {team: 'RV Hansa', reason: 'Krankheit'},
        })
    })

    it('nennt die Mannschaft auch ohne gespeicherten Grund', () => {
        expect(byeExplanation({cause: 'DEREGISTRATION', teamName: 'RV Hansa'})).toEqual({
            key: 'event.match.bye.deregistration',
            values: {team: 'RV Hansa'},
        })
    })

    it('bleibt beim neutralen Satz, wenn kein Gegner benannt ist', () => {
        expect(byeExplanation({cause: 'NO_OPPONENT'})).toEqual({
            key: 'event.match.bye.noOpponent',
        })
    })

    /** Eine Abmeldung ohne Namen ist keine belegte Ursache - dann lieber der neutrale Satz. */
    it('fällt ohne Mannschaftsnamen auf den neutralen Satz zurück', () => {
        expect(byeExplanation({cause: 'DEREGISTRATION', reason: 'Krankheit'})).toEqual({
            key: 'event.match.bye.noOpponent',
        })
    })
})
```

- [ ] **Step 3: Tests laufen lassen und Fehlschlag bestätigen**

Run: `cd frontend && npx vitest run src/components/event/match`
Expected: `matchBye.test.ts` scheitert mit „Failed to load .../matchBye.ts"; die Freilos-Fälle in `matchStatusChip.test.ts` scheitern mit `expected 'event.match.status.upcoming' to be 'event.match.status.bye.open'` und dergleichen.

- [ ] **Step 4: Den Erklärungstext schreiben**

Neue Datei `frontend/src/components/event/match/matchBye.ts`:

```typescript
import {MatchByeDto} from '@api/types.gen.ts'

/** Ein Satz als Datensatz — die aufrufende Komponente übersetzt und malt, wie bei [MatchChip]. */
export type ByeExplanation = {key: string; values?: Record<string, string>}

/**
 * Der Satz, der unter einem Freilos steht — die Übersetzung der Ursache in etwas, das am Steg
 * jemand lesen kann.
 *
 * Ohne Mannschaftsnamen fällt auch eine als Abmeldung gemeldete Ursache auf den neutralen Satz
 * zurück: „Freilos wegen Abmeldung —" ohne Namen behauptet eine Ursache und liefert sie nicht.
 * Dasselbe Prinzip wie im Backend, wo der Freitext-Grund bei mehreren Abmeldungen entfällt.
 */
export const byeExplanation = (bye: MatchByeDto | null | undefined): ByeExplanation | null => {
    if (!bye) return null
    if (bye.cause === 'DEREGISTRATION' && bye.teamName) {
        return bye.reason
            ? {
                  key: 'event.match.bye.deregistrationWithReason',
                  values: {team: bye.teamName, reason: bye.reason},
              }
            : {key: 'event.match.bye.deregistration', values: {team: bye.teamName}}
    }
    return {key: 'event.match.bye.noOpponent'}
}
```

- [ ] **Step 5: Den Chip erweitern**

In `frontend/src/components/event/match/matchStatusChip.ts`:

(a) Vor `matchStatusChip` einfügen:

```typescript
/**
 * Der Chip eines Freiloses. Er ersetzt „Anstehend", „Überfällig" und „Teilweise gewertet" — genau
 * die drei Chips, die ein Ergebnis erwarten lassen, auf das hier niemand wartet. Was er stattdessen
 * sagt, ist die Frage, die am Steg offen ist: muss das noch quittiert werden?
 *
 * „Quittiert" ist bewusst kein eigener Vorgang, sondern der bestehende Beenden-Klick
 * (`finished_at`); „entfallen" ist die abgesagte Runde. An beidem ändert sich nichts.
 */
const byeChip = (status: MatchStatusDto): MatchChip => {
    if (status.state === 'FINISHED') {
        return {labelKey: 'event.match.status.bye.acknowledged', color: 'success'}
    }
    if (status.state === 'SKIPPED') {
        return {labelKey: 'event.match.status.bye.cancelled', color: 'default', strikeThrough: true}
    }
    return {labelKey: 'event.match.status.bye.open', color: 'info'}
}
```

(b) In `matchStatusChip` direkt **nach** dem `RUNNING`-Zweig und **vor** dem `FINISHED`-Zweig einfügen:

```typescript
    // Erst hier, nicht weiter oben: Was tatsächlich passiert, schlägt weiterhin alles. Ein Freilos,
    // das jemand aktiviert hat, zeigt „In Vorbereitung"/„Läuft" — die Anzeige behauptet nicht, es
    // passiere nichts, während in der Arena etwas passiert.
    if (status.bye) {
        return byeChip(status)
    }
```

(c) In `arenaChip` direkt nach `if (inArena == null) return null` einfügen:

```typescript
    // Ein Boot, das nicht fährt, muss auch nicht draußen sein — „Arena 0/1" wäre hier reines
    // Rauschen.
    if (status.bye) return null
```

(d) In `slotMatchStatus` das zurückgegebene Objekt um das Feld ergänzen:

```typescript
    return {
        state,
        startedAt: slot.matchStartedAt ?? undefined,
        teamsTotal: slot.matchTeamsTotal,
        teamsScored: slot.matchTeamsScored,
        bye: slot.bye,
    }
```

- [ ] **Step 6: Die Übersetzungen ergänzen**

In `frontend/src/i18n/de/translations.json` unter `event.match.status` (neben `counter`) einfügen:

```json
          "bye": {
            "open": "Freilos · offen",
            "acknowledged": "Freilos · quittiert",
            "cancelled": "Freilos · entfallen"
          },
```

und unter `event.match` (neben `status`):

```json
        "bye": {
          "deregistration": "Freilos wegen Abmeldung — {{team}}",
          "deregistrationWithReason": "Freilos wegen Abmeldung — {{team}} ({{reason}})",
          "noOpponent": "Freilos – kein Gegner in dieser Runde"
        },
```

Dieselben Schlüssel in `frontend/src/i18n/en/translations.json`:

```json
          "bye": {
            "open": "Bye · open",
            "acknowledged": "Bye · acknowledged",
            "cancelled": "Bye · cancelled"
          },
```
```json
        "bye": {
          "deregistration": "Bye after withdrawal — {{team}}",
          "deregistrationWithReason": "Bye after withdrawal — {{team}} ({{reason}})",
          "noOpponent": "Bye – no opponent in this round"
        },
```

und in `frontend/src/i18n/da/translations.json`:

```json
          "bye": {
            "open": "Oversidder · åben",
            "acknowledged": "Oversidder · kvitteret",
            "cancelled": "Oversidder · aflyst"
          },
```
```json
        "bye": {
          "deregistration": "Oversidder efter afmelding — {{team}}",
          "deregistrationWithReason": "Oversidder efter afmelding — {{team}} ({{reason}})",
          "noOpponent": "Oversidder – ingen modstander i denne runde"
        },
```

Die genaue Einrückung der jeweiligen Datei übernehmen. Prüfen, dass jede Datei gültiges JSON bleibt:

Run: `cd frontend && node -e "['de','en','da'].forEach(l => JSON.parse(require('fs').readFileSync('src/i18n/'+l+'/translations.json','utf8')) && console.log(l, 'ok'))"`
Expected: `de ok`, `en ok`, `da ok`.

- [ ] **Step 7: Tests laufen lassen und Erfolg bestätigen**

Run: `cd frontend && npx vitest run src/components/event/match`
Expected: alle Tests grün, einschließlich der bestehenden Chip-Tests ohne `bye`.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/components/event/match frontend/src/i18n
git commit -m "Nenne ein Freilos beim Namen statt Ergebnisse zu erwarten"
```

---

### Task 5: Der Zeitplan zeigt die Erklärung

**Files:**
- Modify: `frontend/src/components/event/schedule/EventSchedule.tsx` (Slot-Zeile, ca. Zeile 463-520)
- Modify: `frontend/src/components/event/match/matchStatusChip.test.ts`

**Interfaces:**
- Consumes: `byeExplanation` aus Task 4, `slotMatchStatus` mit `bye`.
- Produces: nichts für spätere Tasks.

- [ ] **Step 1: Den fehlschlagenden Test schreiben**

In `frontend/src/components/event/match/matchStatusChip.test.ts` in den bestehenden `describe('slotMatchStatus', ...)`-Block (falls es keinen gibt: am Ende der Datei als eigener Block) anfügen:

```typescript
describe('slotMatchStatus beim Freilos', () => {
    const slot = (overrides: Partial<EventScheduleSlotDto>): EventScheduleSlotDto =>
        ({
            id: 'slot-1',
            startTime: minutesAgo(10),
            state: 'LINKED',
            matchId: 'match-1',
            matchTeamsTotal: 1,
            matchTeamsScored: 1,
            ...overrides,
        }) as EventScheduleSlotDto

    it('trägt das Freilos in den Zeitplan-Status', () => {
        const status = slotMatchStatus(slot({bye: {cause: 'NO_OPPONENT'}}))
        expect(status?.bye).toEqual({cause: 'NO_OPPONENT'})
        expect(matchStatusChip(status!, slot({}).startTime, NOW).labelKey).toBe(
            'event.match.status.bye.open',
        )
    })

    it('lässt einen Slot ohne verknüpften Lauf unberührt', () => {
        expect(slotMatchStatus(slot({matchId: undefined}))).toBeNull()
    })
})
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `cd frontend && npx vitest run src/components/event/match/matchStatusChip.test.ts`
Expected: FAIL — `expected undefined to deeply equal { cause: 'NO_OPPONENT' }`, falls Task 4 Schritt 5(d) nicht griff. Ist der Test schon grün (weil 5(d) bereits umgesetzt ist), direkt zu Schritt 3.

- [ ] **Step 3: Die Erklärungszeile in die Slot-Zeile einbauen**

In `frontend/src/components/event/schedule/EventSchedule.tsx`:

(a) Import ergänzen (neben dem bestehenden `matchStatusChip`-Import):

```typescript
import {byeExplanation} from '@components/event/match/matchBye.ts'
```

(b) In der Map über die Slots die Erklärung einmal berechnen — die Zeile

```typescript
                                {section.slots.map(slot => {
                                    const chip = stateChipProps(slot, now, t)
                                    return (
```

wird zu

```typescript
                                {section.slots.map(slot => {
                                    const chip = stateChipProps(slot, now, t)
                                    // Der Schlüssel steht erst zur Laufzeit fest, deshalb die
                                    // gelockerte Signatur — dasselbe Muster wie in stateChipProps.
                                    const translate = t as (
                                        key: string,
                                        values?: Record<string, string>,
                                    ) => string
                                    const bye = byeExplanation(slot.bye)
                                    return (
```

(c) In der Namensspalte, unmittelbar **nach** dem schließenden `</Stack>` der Zeile mit `slotLabel(...)` und **vor** dem schließenden `</TableCell>`, einfügen:

```tsx
                                                {bye && (
                                                    <Typography
                                                        variant={'caption'}
                                                        display={'block'}
                                                        sx={{color: 'text.secondary'}}>
                                                        {translate(bye.key, bye.values)}
                                                    </Typography>
                                                )}
```

(d) Sicherstellen, dass `Typography` aus `@mui/material` importiert ist; falls nicht, dem bestehenden Import hinzufügen.

- [ ] **Step 4: Tests, Typprüfung und Lint laufen lassen**

Run: `cd frontend && npx vitest run src/components/event/match/matchStatusChip.test.ts`
Expected: PASS.

Run: `cd frontend && npx tsc -b`
Expected: keine Fehler.

Run: `cd frontend && npm run lint`
Expected: keine neuen Fehler.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/event/schedule frontend/src/components/event/match
git commit -m "Sag im Zeitplan, warum ein Lauf ein Freilos ist"
```

---

### Task 6: Das Schiedsrichter-Dashboard zeigt Chip und Erklärung

Die Karte baut ihr `MatchStatusDto` heute inline im JSX — dort ist es nicht prüfbar. Es wandert als reine Funktion nach `liveDashboard/common.ts`.

**Files:**
- Modify: `frontend/src/components/event/liveDashboard/common.ts`
- Modify: `frontend/src/components/event/liveDashboard/common.test.ts`
- Modify: `frontend/src/components/event/liveDashboard/LiveDashboardMatchCard.tsx` (ca. Zeile 95-235)

**Interfaces:**
- Consumes: `byeExplanation` aus Task 4, `LiveDashboardMatchDto.bye` aus Task 3.
- Produces: `dashboardMatchStatus(match: LiveDashboardMatchDto): MatchStatusDto` aus `liveDashboard/common.ts`.

- [ ] **Step 1: Den fehlschlagenden Test schreiben**

In `frontend/src/components/event/liveDashboard/common.test.ts` den Import um `dashboardMatchStatus` erweitern und am Ende der Datei anfügen:

```typescript
describe('dashboardMatchStatus', () => {
    const match = (overrides: Partial<LiveDashboardMatchDto>): LiveDashboardMatchDto =>
        ({
            matchId: 'm-1',
            state: 'UPCOMING',
            competitionId: 'c-1',
            competitionName: 'Coastal Mixed 4x+',
            executionOrder: 1,
            teams: [],
            ...overrides,
        }) as LiveDashboardMatchDto

    it('zählt die gewerteten Boote nach derselben Regel wie das Backend', () => {
        const status = dashboardMatchStatus(
            match({
                teams: [
                    {place: 1} as never,
                    {failed: true} as never,
                    {deregistered: true} as never,
                    {} as never,
                ],
            }),
        )
        expect(status.teamsTotal).toBe(4)
        expect(status.teamsScored).toBe(3)
    })

    it('trägt das Freilos in den Dashboard-Status', () => {
        const status = dashboardMatchStatus(
            match({state: 'AWAITING_FINISH', bye: {cause: 'DEREGISTRATION', teamName: 'RV Hansa'}}),
        )
        expect(status.bye).toEqual({cause: 'DEREGISTRATION', teamName: 'RV Hansa'})
    })

    it('lässt einen gewöhnlichen Lauf ohne Freilos', () => {
        expect(dashboardMatchStatus(match({})).bye).toBeUndefined()
    })
})
```

Falls `LiveDashboardMatchDto` in `common.test.ts` noch nicht importiert ist, den Import aus `@api/types.gen.ts` ergänzen.

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `cd frontend && npx vitest run src/components/event/liveDashboard/common.test.ts`
Expected: FAIL — `dashboardMatchStatus is not a function` bzw. ein Importfehler.

- [ ] **Step 3: `dashboardMatchStatus` schreiben**

In `frontend/src/components/event/liveDashboard/common.ts` den Import erweitern:

```typescript
import {
    EffectiveSeverity,
    LiveDashboardCrewMemberDto,
    LiveDashboardMatchDto,
    LiveDashboardTeamDto,
    MatchStatusDto,
    PendingSlotDto,
} from '@api/types.gen.ts'
```

und **hinter** `teamHasResult` (die Funktion muss vorher definiert sein) anfügen:

```typescript
/**
 * Der Zustand eines Dashboard-Laufs als [MatchStatusDto] — dieselbe Form, die Durchführung und
 * Zeitplan lesen. Stand bis hierher inline im JSX der Karte und war damit nicht prüfbar; die
 * Ableitung selbst bleibt unverändert.
 *
 * `teamsScored` zählt nach derselben Regel wie `MatchStatusLogic.scoredCount` im Backend (Platz,
 * ausgeschieden oder abgemeldet), damit „Teilweise gewertet" hier nichts anderes sagt als dort.
 */
export const dashboardMatchStatus = (match: LiveDashboardMatchDto): MatchStatusDto => ({
    state: match.state,
    startedAt: match.startedAt ?? undefined,
    teamsTotal: match.teams.length,
    teamsScored: match.teams.filter(teamHasResult).length,
    bye: match.bye,
})
```

- [ ] **Step 4: Test laufen lassen und Erfolg bestätigen**

Run: `cd frontend && npx vitest run src/components/event/liveDashboard/common.test.ts`
Expected: PASS.

- [ ] **Step 5: Die Karte umstellen und die Erklärung einbauen**

In `frontend/src/components/event/liveDashboard/LiveDashboardMatchCard.tsx`:

(a) Den Import um `dashboardMatchStatus` erweitern (im bestehenden Import aus `./common.ts` bzw. `@components/event/liveDashboard/common.ts`) und ergänzen:

```typescript
import {byeExplanation} from '@components/event/match/matchBye.ts'
```

(b) Den inline gebauten Status ersetzen — aus

```typescript
    const status: MatchStatusDto = {
        state: match.state,
        startedAt: match.startedAt ?? undefined,
        teamsTotal: match.teams.length,
        teamsScored: match.teams.filter(teamHasResult).length,
    }
```

wird

```typescript
    const status = dashboardMatchStatus(match)
```

Das darüberstehende KDoc bleibt, ergänzt um den Satz: „Die Zusammensetzung selbst liegt in `common.ts`, damit sie ohne Rendering prüfbar bleibt." Werden `MatchStatusDto` oder `teamHasResult` danach nicht mehr gebraucht, ihre Importe entfernen (der Lint-Lauf meldet das).

(c) Direkt **nach** der `<Typography>` mit `[competitionLabel(...), match.categoryName, match.roundName]` und **vor** dem `<Box sx={{justifySelf: 'end'}}>` mit dem Statusfeld einfügen:

```tsx
                    {byeExplanation(match.bye) && (
                        <Box sx={{gridColumn: '1 / -1'}}>
                            <Typography variant="caption" sx={{color: 'grey.700'}}>
                                {translate(
                                    byeExplanation(match.bye)!.key,
                                    byeExplanation(match.bye)!.values,
                                )}
                            </Typography>
                        </Box>
                    )}
```

`gridColumn: '1 / -1'` ist nötig, weil die Kopfzeile ein zweispaltiges Grid ist — ohne die Angabe rutschte der Satz in die rechte Spalte unter den Statuschip.

- [ ] **Step 6: Tests, Typprüfung und Lint laufen lassen**

Run: `cd frontend && npx vitest run src/components/event/liveDashboard`
Expected: PASS.

Run: `cd frontend && npx tsc -b`
Expected: keine Fehler.

Run: `cd frontend && npm run lint`
Expected: keine neuen Fehler (insbesondere keine ungenutzten Importe).

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/event/liveDashboard
git commit -m "Zeig dem Schiedsrichter, dass und warum ein Lauf ein Freilos ist"
```

---

### Task 7: Die Durchführungsübersicht bekommt den Statuschip

Die Freilos-Darstellung besteht dort bereits (Panel „Teams mit Freilos"). Sie bekommt genau eine Ergänzung: den Laufstatus. Gleichzeitig wandert die Frage „ist das ein Freilos" auf das zentrale Feld, damit es nicht zwei Regeln gibt.

**Files:**
- Create: `frontend/src/components/event/competition/excecution/byeMatches.ts`
- Create: `frontend/src/components/event/competition/excecution/byeMatches.test.ts`
- Modify: `frontend/src/components/event/competition/excecution/CompetitionExecutionRound.tsx` (Panel ca. Zeile 260-319)
- Modify: `frontend/src/components/event/competition/excecution/CompetitionExecution.tsx` (`matchesFiltered`, Zeile 207-211, sowie die Aufrufe Zeile 214, 632, 815)
- Modify: `frontend/src/i18n/de/translations.json`, `frontend/src/i18n/en/translations.json`, `frontend/src/i18n/da/translations.json`

**Interfaces:**
- Consumes: `CompetitionMatchDto.status.bye` aus Task 3, `matchStatusChip` aus Task 4.
- Produces:
  - `byeMatches(round: CompetitionRoundDto): CompetitionMatchDto[]`
  - `raceableMatches(round: CompetitionRoundDto): CompetitionMatchDto[]`

- [ ] **Step 1: Den fehlschlagenden Test schreiben**

Neue Datei `frontend/src/components/event/competition/excecution/byeMatches.test.ts`:

```typescript
import {describe, expect, it} from 'vitest'
import {CompetitionMatchDto, CompetitionRoundDto, MatchByeDto} from '@api/types.gen.ts'
import {byeMatches, raceableMatches} from './byeMatches.ts'

const match = (
    id: string,
    overrides: {bye?: MatchByeDto; teams?: number; weighting?: number; order?: number} = {},
): CompetitionMatchDto =>
    ({
        id,
        teams: Array.from({length: overrides.teams ?? 2}, () => ({}) as never),
        weighting: overrides.weighting ?? 1,
        executionOrder: overrides.order ?? 1,
        skipped: false,
        status: {
            state: 'UPCOMING',
            teamsTotal: overrides.teams ?? 2,
            teamsScored: 0,
            bye: overrides.bye,
        },
    }) as CompetitionMatchDto

const round = (matches: CompetitionMatchDto[]): CompetitionRoundDto =>
    ({setupRoundId: 'r-1', name: 'Viertelfinale', matches, required: false, substitutions: []}) as CompetitionRoundDto

describe('byeMatches', () => {
    it('nimmt genau die Läufe mit Freilos, nach Gewichtung sortiert', () => {
        const bye: MatchByeDto = {cause: 'NO_OPPONENT'}
        const result = byeMatches(
            round([
                match('a', {bye, teams: 1, weighting: 3}),
                match('b'),
                match('c', {bye, teams: 1, weighting: 1}),
            ]),
        )
        expect(result.map(m => m.id)).toEqual(['c', 'a'])
    })

    it('bleibt leer, wenn es keins gibt', () => {
        expect(byeMatches(round([match('a'), match('b')]))).toEqual([])
    })
})

describe('raceableMatches', () => {
    it('lässt Freilose weg und sortiert nach Startreihenfolge', () => {
        const bye: MatchByeDto = {cause: 'NO_OPPONENT'}
        const result = raceableMatches(
            round([
                match('a', {order: 2}),
                match('b', {bye, teams: 1}),
                match('c', {order: 1}),
            ]),
        )
        expect(result.map(m => m.id)).toEqual(['c', 'a'])
    })

    it('lässt Läufe ohne Mannschaften weg', () => {
        expect(raceableMatches(round([match('a', {teams: 0})]))).toEqual([])
    })

    /** Panel und Kartenliste teilen die Runde vollständig und überschneidungsfrei auf. */
    it('teilt die Runde vollständig auf', () => {
        const bye: MatchByeDto = {cause: 'NO_OPPONENT'}
        const r = round([match('a'), match('b', {bye, teams: 1}), match('c')])
        expect([...byeMatches(r), ...raceableMatches(r)].map(m => m.id).sort()).toEqual([
            'a',
            'b',
            'c',
        ])
    })
})
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `cd frontend && npx vitest run src/components/event/competition/excecution/byeMatches.test.ts`
Expected: FAIL — „Failed to load .../byeMatches.ts".

- [ ] **Step 3: Die Aufteilung schreiben**

Neue Datei `frontend/src/components/event/competition/excecution/byeMatches.ts`:

```typescript
import {CompetitionMatchDto, CompetitionRoundDto} from '@api/types.gen.ts'

/**
 * Die Aufteilung einer Runde in „gehört ins Freilos-Panel" und „bekommt eine Lauf-Karte".
 *
 * Beide fragen dasselbe Feld: `status.bye`, das der Server aus derselben Regel ableitet, nach der
 * er die Ergebniseingabe sperrt (`MatchStatusLogic.deriveBye`). Bis hierher stand die Regel ein
 * zweites Mal im Frontend als `teams.length === 1` — dieselbe Menge Läufe, aber eine zweite
 * Wahrheit, die auseinanderlaufen konnte.
 */
export const byeMatches = (round: CompetitionRoundDto): CompetitionMatchDto[] =>
    round.matches.filter(match => match.status.bye != null).sort((a, b) => a.weighting - b.weighting)

/**
 * Die Läufe, die als Karte erscheinen: alles, was kein Freilos ist und Mannschaften hat. Ein Lauf
 * ohne Mannschaften ist eine leere Hülle aus dem Turnierbaum und hat auf der Seite nichts verloren.
 */
export const raceableMatches = (round: CompetitionRoundDto): CompetitionMatchDto[] =>
    round.matches
        .filter(match => match.teams.length > 0 && match.status.bye == null)
        .sort((a, b) => a.executionOrder - b.executionOrder)
```

- [ ] **Step 4: Test laufen lassen und Erfolg bestätigen**

Run: `cd frontend && npx vitest run src/components/event/competition/excecution/byeMatches.test.ts`
Expected: PASS.

- [ ] **Step 5: Die Übersetzung für die Spaltenüberschrift ergänzen**

In `frontend/src/i18n/de/translations.json` unter `event.competition.execution.match` ergänzen: `"status": "Status"`.
In `frontend/src/i18n/en/translations.json` an derselben Stelle: `"status": "Status"`.
In `frontend/src/i18n/da/translations.json` an derselben Stelle: `"status": "Status"`.

Run: `cd frontend && node -e "['de','en','da'].forEach(l => JSON.parse(require('fs').readFileSync('src/i18n/'+l+'/translations.json','utf8')) && console.log(l, 'ok'))"`
Expected: `de ok`, `en ok`, `da ok`.

- [ ] **Step 6: Das Panel umstellen und die Statusspalte einbauen**

In `frontend/src/components/event/competition/excecution/CompetitionExecutionRound.tsx`:

(a) Import ergänzen:

```typescript
import {byeMatches} from '@components/event/competition/excecution/byeMatches.ts'
```

(b) Nach `const counterChips = roundCounterChips(...)` einfügen:

```typescript
    // Die Freilose der Runde — dieselbe Frage wie überall sonst, gestellt an `status.bye`.
    const byes = byeMatches(round)
```

(c) Den gesamten Block von

```tsx
                    {!round.required &&
                        round.matches.filter(match => match.teams.length === 1).length > 0 && (
```

bis zum schließenden `)}` des `<Accordion>` ersetzen durch:

```tsx
                    {byes.length > 0 && (
                        <Accordion
                            expanded={props.accordionsExpanded?.[0] ?? false}
                            onChange={handleAccordionExpandedChange(0)}>
                            <AccordionSummary
                                expandIcon={<ExpandMoreIcon />}
                                aria-expanded={true}
                                aria-controls={`round-${roundIndex}-${round.name}-panel-teams-with-bye-content`}
                                id={`round-${roundIndex}-${round.name}-panel-teams-with-bye-header`}>
                                <Typography component="span">
                                    {t('event.competition.execution.teamsWithBye')} ({byes.length})
                                </Typography>
                            </AccordionSummary>
                            <AccordionDetails>
                                <TableContainer>
                                    <Table>
                                        <TableHead>
                                            <TableRow>
                                                <TableCell width="15%">
                                                    {t(
                                                        'event.competition.setup.match.outcome.outcome',
                                                    )}
                                                </TableCell>
                                                <TableCell width="60%">
                                                    {t('event.competition.execution.match.team')}
                                                </TableCell>
                                                {/* Der Zustand des Laufs, aus derselben Ableitung
                                                    wie auf jeder Lauf-Karte: ohne ihn war hier
                                                    nicht zu sehen, ob das Freilos noch zu
                                                    quittieren ist. */}
                                                <TableCell width="25%">
                                                    {t('event.competition.execution.match.status')}
                                                </TableCell>
                                            </TableRow>
                                        </TableHead>
                                        <TableBody>
                                            {byes.map(match => (
                                                <TableRow key={match.id}>
                                                    <TableCell width="15%">
                                                        {match.weighting}
                                                    </TableCell>
                                                    <TableCell width="60%">
                                                        {match.teams[0]
                                                            ? match.teams[0].clubName +
                                                              (match.teams[0].name
                                                                  ? ` ${match.teams[0].name}`
                                                                  : '')
                                                            : ''}
                                                    </TableCell>
                                                    <TableCell width="25%">
                                                        <StatusChip
                                                            chip={matchStatusChip(
                                                                match.status,
                                                                match.startTime,
                                                                now,
                                                            )}
                                                        />
                                                    </TableCell>
                                                </TableRow>
                                            ))}
                                        </TableBody>
                                    </Table>
                                </TableContainer>
                            </AccordionDetails>
                        </Accordion>
                    )}
```

Der `match.teams[0]`-Schutz ist neu und nötig: die zentrale Regel zählt nur die fahrenden Zeilen, ein Freilos hat also immer genau eine — aber ein Zugriff ohne Prüfung wäre eine Absturzstelle, falls der Server je etwas anderes liefert.

- [ ] **Step 7: Die Kartenliste auf dieselbe Regel umstellen**

In `frontend/src/components/event/competition/excecution/CompetitionExecution.tsx`:

(a) Import ergänzen:

```typescript
import {raceableMatches} from '@components/event/competition/excecution/byeMatches.ts'
```

(b) Die Funktion `matchesFiltered` (Zeile 207-211) **löschen**:

```typescript
    const matchesFiltered = (round: CompetitionRoundDto): CompetitionMatchDto[] => {
        return round.matches
            .filter(match => match.teams.length > 0 && (match.teams.length > 1 || round.required))
            .sort((a, b) => a.executionOrder - b.executionOrder)
    }
```

(c) Alle drei Aufrufstellen `matchesFiltered(` durch `raceableMatches(` ersetzen (Zeile ~214 `currentRoundMatches`, Zeile ~632 im `openEditMatchDialog`, Zeile ~815 die Prop `filteredMatches`).

Run: `grep -n "matchesFiltered" frontend/src/components/event/competition/excecution/CompetitionExecution.tsx`
Expected: keine Treffer.

(d) Werden `CompetitionMatchDto` oder `CompetitionRoundDto` danach nicht mehr importiert gebraucht, meldet der Lint-Lauf das — dann entfernen.

- [ ] **Step 8: Tests, Typprüfung und Lint laufen lassen**

Run: `cd frontend && npx vitest run`
Expected: alle Tests grün.

Run: `cd frontend && npx tsc -b`
Expected: keine Fehler.

Run: `cd frontend && npm run lint`
Expected: keine neuen Fehler.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/components/event/competition/excecution frontend/src/i18n
git commit -m "Zeig am Freilos-Panel, ob noch quittiert werden muss"
```

---

### Task 8: Gesamtlauf und Abschluss

**Files:** keine Änderungen erwartet; entstehen doch welche (z. B. eine übersehene Aufrufstelle), gehören sie in diesen Commit.

**Interfaces:**
- Consumes: alles aus Task 1-7.
- Produces: nichts.

- [ ] **Step 1: Alle Backend-Tests**

Run: `cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -o test`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0`.

- [ ] **Step 2: Alle Frontend-Tests**

Run: `cd frontend && npx vitest run`
Expected: alle Dateien grün, keine übersprungenen Tests.

- [ ] **Step 3: Frontend-Build**

Run: `cd frontend && npm run build`
Expected: erfolgreicher Build.

- [ ] **Step 4: Lint**

Run: `cd frontend && npm run lint`
Expected: keine Fehler.

- [ ] **Step 5: Den Arbeitsbaum prüfen**

Run: `git status --short`
Expected: leer. Ist etwas übrig (etwa eine neu erzeugte `types.gen.ts`), prüfen, ob es dazugehört, und committen:

```bash
git add -A
git commit -m "Zieh die letzten Stellen der Freilos-Anzeige nach"
```

- [ ] **Step 6: Die Änderung im Überblick durchsehen**

Run: `git diff main --stat`
Expected: Änderungen ausschließlich in `backend/src/main/kotlin/.../matchStatus`, `.../competitionExecution`, `.../liveDashboard`, `.../eventSchedule`, `backend/src/main/resources/openapi/documentation.yaml`, `backend/src/test/.../MatchStatusLogicTest.kt`, `frontend/src/api/types.gen.ts`, `frontend/src/components/event/{match,schedule,liveDashboard,competition/excecution}`, `frontend/src/i18n/*` und `docs/superpowers/`.

Ausdrücklich **nicht** verändert sein dürfen: `matchControls` in `liveDashboard/common.ts`, `checkUpdateMatchResult` und `automaticFirstPlace` in `CompetitionExecutionService.kt`, `timelineIndicator.ts`, `roundCounterChips` in `matchStatusChip.ts` und alle Berechtigungsprüfungen. Run: `git diff main -- frontend/src/components/event/schedule/timelineIndicator.ts` — Expected: leer.
