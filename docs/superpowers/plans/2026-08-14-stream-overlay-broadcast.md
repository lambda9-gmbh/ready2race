# Stream-Overlay Broadcast-Runde Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die Ausbaustufe der Stream-Overlay-Kachel: zentrierte TV-Panels, einstellbare
Boot-Darstellung, zehntelgenaue Laufuhr, Rundenband, Nächste-Läufe-Liste und
Ski-Zeitnahme-Bewegung.

**Architecture:** Aufbau auf der bestehenden STREAM-Kachel (Branch `claude/stream-overlay`).
Backend liefert drei neue Bausteine additiv (Crew-Bedarf, Lap-Eintreffzeit, größere
Upcoming-Liste); die gesamte Darstellung inkl. Uhr und Animation bleibt Client-Sache.
Uhr-Sync über `BoardViewDto.serverTime`; Animationen ausschließlich Transforms (FLIP).

**Tech Stack:** Kotlin/Ktor, OpenAPI-YAML + hey-api, React/TS/MUI, vitest.

**Spec:** `docs/superpowers/specs/2026-08-13-stream-overlay-kachel-design.md`, Abschnitt
„Ausbaustufe" — die Spec ist der Vertrag, dieser Plan die Arbeitsteilung.

## Global Constraints

- Worktree `/Users/thomas/Developer/privat/ready2race/.claude/worktrees/stream-overlay`, Branch `claude/stream-overlay`.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`; Maven immer mit `-Ddatabase.url=jdbc:postgresql://localhost:7652/r2r-stream-overlay-build`.
- Commits englisch-imperativ ≤ 50 Zeichen, KEINE AI-Attribution; deutsche Kommentare mit Umlauten.
- Chroma-Regeln: deckende Flächen, Fades nur als Text-Opacity auf Panels, Bewegung nur per Transform.
- Tabellenziffern für alle tickenden/wechselnden Werte.
- Kein Push; Merge nach `feature/crf-2026` erst nach Abschluss-Review.

---

### Task 8: Backend — streamCrew, neue Modi, Lap-Eintreffzeit, dataNeeds

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/entity/BoardConfig.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/entity/BoardDtos.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/entity/AthleteBoardDto.kt` (MatchTeamLapDto + matchTeamLapDto-Fabrik)
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/boundary/BoardLogic.kt` (dataNeeds)
- Modify: alle Aufrufer der Lap-Konvertierung (Suchanker: `matchTeamLapDto(` — Eintreffzeit `created_at` durchreichen; Repository-Abfrage der Laps um `CREATED_AT` ergänzen, Suchanker `CompetitionMatchTeamLapRepo`)
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/eventInfo/BoardRequestValidationTest.kt`, `BoardLogicTest.kt`

**Interfaces:**
- Produces: `enum class StreamCrewDisplay { CLUBS_FIRST, PARTICIPANTS_FIRST, CLUBS_ONLY }`;
  `BoardElement.streamCrew: StreamCrewDisplay?` (null = CLUBS_FIRST, nur an STREAM erlaubt);
  `StreamOverlayMode` um `LAPS` und `UPCOMING_LIST` erweitert;
  `MatchTeamLapDto.recordedAt: LocalDateTime?` (additiv, nullable);
  dataNeeds: LAPS → offsets {0}; UPCOMING_LIST → offsets {1} UND `upcomingLimit ≥ 5`;
  `crewDetails = true`, wenn irgendein STREAM-Element `streamCrew != CLUBS_ONLY` wirkt
  (auch bei fehlendem Feld, da Default CLUBS_FIRST Personen zeigt);
  `advancement = true`, wenn ein STREAM-Element `showAdvancement == true` trägt.

- [ ] **Step 1: Failing Tests.** In `BoardLogicTest.kt` ergänzen:

```kotlin
@Test
fun `dataNeeds der neuen Stream-Modi und der Boot-Darstellung`() {
    fun config(mode: StreamOverlayMode?, crew: StreamCrewDisplay? = null, advancement: Boolean? = null) = BoardConfig(
        columns = 1,
        tiles = listOf(
            BoardTile(elements = listOf(
                BoardElement(type = BoardElementType.STREAM, streamMode = mode, streamCrew = crew, showAdvancement = advancement)
            ))
        ),
    )
    assertEquals(setOf(0), BoardLogic.dataNeeds(config(StreamOverlayMode.LAPS)).offsets)
    assertEquals(setOf(1), BoardLogic.dataNeeds(config(StreamOverlayMode.UPCOMING_LIST)).offsets)
    assertTrue(BoardLogic.dataNeeds(config(StreamOverlayMode.UPCOMING_LIST)).upcomingLimit >= 5)
    // Personen sind per Default sichtbar (CLUBS_FIRST) - Crew-Details werden angefordert.
    assertTrue(BoardLogic.dataNeeds(config(StreamOverlayMode.AUTO)).crewDetails)
    assertTrue(BoardLogic.dataNeeds(config(StreamOverlayMode.AUTO, StreamCrewDisplay.PARTICIPANTS_FIRST)).crewDetails)
    // Nur-Vereine spart die Crew-Abfrage.
    assertFalse(BoardLogic.dataNeeds(config(StreamOverlayMode.AUTO, StreamCrewDisplay.CLUBS_ONLY)).crewDetails)
    assertTrue(BoardLogic.dataNeeds(config(StreamOverlayMode.AUTO, advancement = true)).advancement)
}
```

In `BoardRequestValidationTest.kt`:

```kotlin
@Test
fun `streamCrew ist nur an STREAM-Elementen erlaubt`() {
    val invalid = BoardRequest(
        name = "Uhr",
        config = BoardConfig(
            columns = 1,
            tiles = listOf(BoardTile(elements = listOf(
                BoardElement(type = BoardElementType.CLOCK, streamCrew = StreamCrewDisplay.CLUBS_ONLY)
            ))),
        ),
    )
    assertNotEquals(ValidationResult.Valid, invalid.validate())
}
```

- [ ] **Step 2: Rot** — `./mvnw test -Dtest='BoardLogicTest,BoardRequestValidationTest' -Ddatabase.url=…` → Kompilierfehler.

- [ ] **Step 3: Implementierung.**
  - `BoardConfig.kt`: `StreamOverlayMode` um `LAPS, UPCOMING_LIST` erweitern (Doku-Kommentar
    fortschreiben); neues Enum + Feld:

```kotlin
/**
 * Boot-Darstellung des Stream-Overlays: Vereine prominent mit kleiner Personenzeile
 * (Voreinstellung), Personen prominent mit kleinem Verein, oder nur Vereine. Fehlt das
 * Feld, gilt CLUBS_FIRST.
 */
enum class StreamCrewDisplay { CLUBS_FIRST, PARTICIPANTS_FIRST, CLUBS_ONLY }
```

    Feld `val streamCrew: StreamCrewDisplay? = null` direkt unter `streamMode`.
  - `BoardDtos.kt`: Quer-Regel wie bei `streamMode`: `streamCrew` nur an STREAM.
  - `AthleteBoardDto.kt`: `MatchTeamLapDto` um `val recordedAt: LocalDateTime? = null`
    erweitern; die Fabrik `matchTeamLapDto(name, lapMillis)` bekommt einen dritten Parameter
    `recordedAt: LocalDateTime?` (mit Default null, damit Aufrufer ohne Zeit weiterbauen);
    Lap-Abfrage (`CompetitionMatchTeamLapRepo`) liefert `CREATED_AT` mit, alle Aufrufer
    reichen sie durch (Suchanker `matchTeamLapDto(`; die Zahl der Stellen ist klein).
  - `BoardLogic.dataNeeds`: `streamOffsets`-when um `LAPS -> listOf(0)` und
    `UPCOMING_LIST -> listOf(1)` erweitern; nach der Offsets-Vereinigung:

```kotlin
        val streamElements = elements.filter { it.type == BoardElementType.STREAM }
        val streamUpcomingList = streamElements.any {
            (it.streamMode ?: StreamOverlayMode.AUTO) == StreamOverlayMode.UPCOMING_LIST
        }
        val streamCrew = streamElements.any {
            (it.streamCrew ?: StreamCrewDisplay.CLUBS_FIRST) != StreamCrewDisplay.CLUBS_ONLY
        }
        val streamAdvancement = streamElements.any { it.showAdvancement == true }
```

    `upcomingLimit = maxOf(…, if (streamUpcomingList) 5 else 0)`, `crewDetails = … || streamCrew`
    (nur wenn überhaupt STREAM-Elemente existieren — `streamElements.isNotEmpty() && …`),
    `advancement = … || streamAdvancement`.

- [ ] **Step 4: Grün** — gleicher Befehl; danach volle Suite `./mvnw test -Ddatabase.url=…` → 0 Failures.

- [ ] **Step 5: Commit** — `git add backend/ && git commit -m "Extend stream tile data needs for broadcast"`

---

### Task 9: OpenAPI + Client

**Files:**
- Modify: `backend/src/main/resources/openapi/documentation.yaml`
- Generated: `frontend/src/api/*.gen.ts` (nur via `npm run generate`)

- [ ] **Step 1:** Im `BoardElement`-Schema: `streamMode`-Enum um `- LAPS` und `- UPCOMING_LIST`
  ergänzen (Description fortschreiben: LAPS = Rundenband, UPCOMING_LIST = nächste fünf Läufe);
  neues Feld daneben:

```yaml
        streamCrew:
          type: string
          nullable: true
          description: "STREAM only: crew emphasis - clubs first (default when missing), participants first, or clubs only"
          enum:
            - CLUBS_FIRST
            - PARTICIPANTS_FIRST
            - CLUBS_ONLY
```

  Im `MatchTeamLapDto`-Schema (Suchanker `MatchTeamLapDto:`): Feld

```yaml
        recordedAt:
          type: string
          format: date-time
          nullable: true
          description: "when the lap time arrived; carries the ordering of the stream lap band"
```

- [ ] **Step 2:** `npm install --no-audit --no-fund && npm run generate`; prüfen, dass
  `types.gen.ts` `LAPS`, `UPCOMING_LIST`, `streamCrew`, `recordedAt` enthält.
- [ ] **Step 3: Commit** — `git add backend/src/main/resources/openapi frontend/src/api && git commit -m "Document broadcast stream fields in the API"`

---

### Task 10: Frontend-Logik — Inhalte, Uhr, Rundenband (TDD)

**Files:**
- Modify: `frontend/src/components/event/board/streamOverlay.ts`
- Create: `frontend/src/components/event/board/streamClock.ts`
- Test: `frontend/src/components/event/board/streamOverlay.test.ts`, `streamClock.test.ts`

**Interfaces (Task 11 verlässt sich exakt darauf):**

```ts
// streamOverlay.ts — Erweiterungen:
export type StreamOverlayContent =
    | {kind: 'running'; match: AthleteBoardMatch}
    | {kind: 'result'; result: AthleteBoardResult}
    | {kind: 'upcoming'; match: AthleteBoardMatch}
    | {kind: 'upcomingList'; matches: AthleteBoardMatch[]}   // NEU: UPCOMING_LIST
    | {kind: 'laps'; match: AthleteBoardMatch; laps: StreamLapEntry[]} // NEU: LAPS
    | null

export type StreamLapEntry = {
    startNumber: number
    club: string           // bereits nach useShortNames aufgelöst? NEIN - roh: clubsShort/clubsFull entscheidet Task 11
    clubsShort: string | null
    clubsFull: string | null
    lapName: string
    timeString: string
    recordedAt: string | null
}

// Signatur erweitert: UPCOMING_LIST braucht die upcoming-LISTE, nicht nur Slots.
export const streamOverlayContent = (
    view: Pick<BoardViewDto, 'slots' | 'lists'>,
    mode: BoardElement['streamMode'],
): StreamOverlayContent

// Die letzten drei eingetroffenen Runden des Laufs, neueste zuerst; Runden ohne
// recordedAt gelten als älteste (stabil nach Rundenname dahinter).
export const lastLaps = (match: AthleteBoardMatch, limit?: number): StreamLapEntry[]

// streamClock.ts:
export type StreamClockState =
    | {phase: 'hidden'}
    | {phase: 'running'; elapsedMs: number}
    | {phase: 'frozen'; elapsedMs: number}

/**
 * Reine Zustandsfunktion der Laufuhr: actualStartTime + Serverzeitversatz -> Phase.
 * clockOffsetMs = clientNow - serverTime (beim Eintreffen der Antwort gemessen).
 * frozen, sobald alle Boote timeString tragen oder failed sind (und mind. eines existiert).
 */
export const streamClockState = (
    match: AthleteBoardMatch | null,
    clientNowMs: number,
    clockOffsetMs: number,
): StreamClockState

/** 'm:ss.z' bzw. ab einer Stunde 'h:mm:ss.z' - Zehntel, Tabellenziffern-tauglich. */
export const formatElapsed = (elapsedMs: number): string
```

  UPCOMING_LIST-Quelle: `view.lists.find(l => l.mode === 'UPCOMING')?.matches ?? []`, auf 5
  gekürzt — dataNeeds garantiert die Befüllung, weil UPCOMING_LIST als listLimits-freier
  Modus über `upcomingLimit` läuft: deshalb liefert der Server die Liste NUR im
  `slots`-Umfeld… **Achtung, prüfen:** `BoardService` baut `lists` nur für MATCH_LIST-Elemente
  (`needs.listLimits`). Falls `lists` für STREAM leer bleibt, stattdessen in `dataNeeds`
  `listLimits[UPCOMING] = max(bisher, 5)` für UPCOMING_LIST-Elemente setzen (kleine
  Rückänderung in Task 8 — der Implementierer dieses Tasks prüft `BoardService.getBoardView`
  und passt Task-8-Code ggf. mit an, mit Test).

- [ ] **Step 1: Failing Tests** — `streamOverlay.test.ts` erweitern (bestehende Fälle auf die
  neue Signatur heben: `streamOverlayContent({slots, lists: []}, mode)`), neue Fälle:
  upcomingList liefert die ersten 5 der UPCOMING-Liste; laps liefert die letzten 3 nach
  recordedAt (neueste zuerst), Runden ohne recordedAt hinten; leere Laps → content null.
  `streamClock.test.ts`: hidden ohne actualStartTime; running mit korrektem elapsedMs
  (inkl. Versatz-Korrektur: serverTime 2 s hinter clientNow → elapsed rechnet mit
  Serverzeit); frozen wenn alle Boote gewertet/failed; formatElapsed('83_450') = '1:23.4',
  Stunde: 3_723_400 → '1:02:03.4'.
- [ ] **Step 2: Rot**, **Step 3: Implementierung**, **Step 4: Grün** (`npx vitest run src/components/event/board/`).
- [ ] **Step 5: Commit** — `"Add stream clock and broadcast content selection"`

---

### Task 11: Frontend-Darstellung — Panels, Uhr, Band, Bewegung

**Files:**
- Modify: `frontend/src/components/event/board/BoardStreamOverlayElement.tsx`
- Create: `frontend/src/components/event/board/FlipList.tsx`

**Interfaces:**
- Consumes: alles aus Task 10; `useBoardViewData` liefert die View samt `serverTime` (der
  Versatz wird beim Eintreffen jeder Antwort neu gemessen: `Date.now() - Date.parse(serverTime)`).
- Produces: `<FlipList items keyOf render/>` — generische Liste, die Positionswechsel per
  FLIP animiert (Transform, ~350 ms ease-out) und neue Einträge per translateY-Slide-in.

- [ ] **Step 1: FlipList.** Eigenständige, wiederverwendbare Komponente (~60 Zeilen):
  `useLayoutEffect` misst `getBoundingClientRect` je Key, vergleicht mit der letzten Messung
  (Ref-Map), setzt bei Verschiebung `transform: translateY(delta)` ohne Transition und hebt
  es im nächsten Frame mit Transition auf. Neue Keys starten mit `translateY(24px)` →
  Identität. KEINE Opacity auf Zeilenebene (Chroma). Deutsche Kommentare.
- [ ] **Step 2: Layout-Split in `BoardStreamOverlayElement.tsx`.**
  - `running` → Lower-Third (bestehend), aber: Zeilen via FlipList; Sortierung „gewertete
    nach Platz oben, ungewertete nach Startnummer darunter"; Kopf bekommt die Laufuhr
    (rechtsbündig, `h3`-Größe, tabular-nums, Text-Fade in/out per Opacity-Transition auf
    dem Panel; 100-ms-Tick via `setInterval`, Zustand aus `streamClockState`, Freeze-Wert
    beibehalten, nach 5 s `hidden`-Übergang mit Fade-out und erst nach Ende des Fades
    entfernen).
  - `result`/`upcoming` → zentriertes Panel: `position: fixed; inset: 0; display: grid;
    place-items: center`, Panel max. 70 % Breite/Höhe, Kopf groß (h3), Zustand als
    Akzent-Label, bei `upcoming` mit `showCountdown !== false`: Startzeit + Countdown
    („in 4:32 min", tabular). `showAdvancement`: Zeile mit der Weiterkommens-Regel aus
    den Match-Feldern (Suchanker `advancing` im `AthleteBoardMatch` — Felder existieren,
    nur bei angefordertem needs gefüllt). Zeilen gestaffelt (FlipList reicht: Panel
    erscheint als Ganzes per translateY-Slide-in, Zeilen sliden nach).
  - `upcomingList` → zentriertes Panel „Nächste Läufe": je Zeile Startzeit (tabular),
    Wettkampf (+ Kurzform-Regel), Runde/Lauf, dedupe Runde==Lauf.
  - `laps` → Bauchband: schmale, volle Breite unten (~10 vh), drei Einträge nebeneinander,
    neueste links und mit Akzentrand, Einzug neuer Einträge per translateX-FLIP.
  - Boot-Darstellung nach `element.streamCrew` (Default CLUBS_FIRST): prominent/sekundär
    bzw. Personen weglassen; Personen = `team.participants` als „Vorname Nachname"-Liste,
    mit mittlerem Punkt getrennt.
- [ ] **Step 3: Verifikation** — `npx tsc --noEmit`, `npm run build`, `npx vitest run
  src/components/event/board/` alle grün.
- [ ] **Step 4: Commit** — `"Render broadcast panels with motion for streams"`

---

### Task 12: Editor, i18n, Browser-Verifikation

**Files:**
- Modify: `frontend/src/components/event/board/BoardEditor.tsx`
- Modify: `frontend/src/i18n/{de,en,da}/translations.json`

- [ ] **Step 1: Editor.** streamMode-Dropdown um „Rundenanzeige" (`LAPS`) und „Nächste Läufe"
  (`UPCOMING_LIST`) erweitern; neues Dropdown „Boot-Darstellung" (`streamCrew`, drei Werte,
  Default-Anzeige CLUBS_FIRST); Schalter „Uhrzeit/Countdown" (`showCountdown`, Default an)
  und „Weiterkommens-Regel" (`showAdvancement`, Default aus) im STREAM-Optionsblock.
  `elementForType('STREAM')` ergänzt `showCountdown: true`.
- [ ] **Step 2: i18n** de/en/da unter `event.boards.stream`: `mode.laps`, `mode.upcomingList`,
  `crew.label` („Boot-Darstellung"), `crew.clubsFirst` („Vereine prominent"),
  `crew.participantsFirst` („Personen prominent"), `crew.clubsOnly` („Nur Vereine"),
  `showCountdown` („Uhrzeit/Countdown"), `upcomingListTitle` („Nächste Läufe"),
  `inMinutes` („in {{time}}") — plus englische/dänische Entsprechungen. Formaterhaltend.
- [ ] **Step 3: Suiten** — Frontend-Tests + Build; Backend-Suite einmal komplett.
- [ ] **Step 4: Browser-Verifikation** (Setup wie in Task 6, DB/Seed existieren im Worktree
  bereits): Zustände belegen mit Screenshots: laufend mit tickender Uhr (zwei Screenshots
  im Abstand ≥ 1 s: Uhrwert MUSS sich um den Abstand bewegen), Umsortier-Animation
  (Zeit per SQL nachtragen, Reihenfolge wechselt), zentriertes Ergebnis-Panel, Als-Nächstes
  mit Countdown, Nächste-Läufe-Liste, Rundenband (Lap per SQL einfügen → schiebt herein),
  Leerzustand. Board-Config per SQL-Update auf die jeweiligen Modi stellen.
- [ ] **Step 5: Commit** — `"Let the editor tune broadcast stream options"`

---

### Task 13: Abschluss-Review + Merge

- [ ] Abschluss-Review über den Branch-Diff seit dem letzten Merge (`731fcb5c`-Basis:
  `git merge-base feature/crf-2026 HEAD`).
- [ ] Findings fixen (EIN Fixer für alle), Re-Review.
- [ ] Merge in `feature/crf-2026` im Hauptcheckout, volle Frontend-Suite dort. KEIN Push.
