# Stream-Overlay-Kachel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ein neuer Board-Kachel-Typ `STREAM`, der eine vollflächige Chroma-Key-Seite mit
Lower-Third im r2r-Design rendert — für Livestream-Overlays per OBS-Browser-Quelle.

**Architecture:** Der Kachel-Typ hängt sich an die vorhandene Slot-Maschinerie
(`BoardLogic.resolveOffset`: Offset 0 = zuletzt gestarteter laufender Lauf, −1 = jüngstes
Ergebnis, +1 = nächster anstehender Lauf). Es entsteht KEIN neuer Datenpfad: `dataNeeds`
meldet je nach Modus die Offsets {0, −1, +1} an, der Server liefert sie als `slots` wie für
MATCH-Kacheln. Die Auswahl (AUTO-Rückfall) ist eine reine Frontend-Funktion.

**Tech Stack:** Kotlin/Ktor Backend (Validierung + dataNeeds), handgepflegtes OpenAPI-YAML +
hey-api-Generator, React/TS/MUI Frontend, vitest, Testcontainers-freie Logiktests (JUnit).

**Spec:** `docs/superpowers/specs/2026-08-13-stream-overlay-kachel-design.md`

## Global Constraints

- Worktree: `/Users/thomas/Developer/privat/ready2race/.claude/worktrees/stream-overlay`, Branch `claude/stream-overlay`.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home` vor jedem Maven-Aufruf.
- Backend-Builds/Tests IMMER mit `-Ddatabase.url=jdbc:postgresql://localhost:7652/r2r-stream-overlay-build`
  (eigene Build-DB; einmalig anlegen, siehe Task 6).
- Commits englisch-imperativ, ≤ 50 Zeichen Betreff, KEINE AI-Attribution.
- Chroma-Regel: im Overlay-Panel keine Halbtransparenz, keine weichen Schatten, kein Blur.
- Deutscher Fließtext in Kommentaren mit echten Umlauten.
- Nichts pushen; am Ende nach `feature/crf-2026` mergen (Task 7).

---

### Task 1: Backend — Elementtyp STREAM, Modus-Enum, Validierung

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/entity/BoardConfig.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/entity/BoardDtos.kt`
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/eventInfo/BoardRequestValidationTest.kt`

**Interfaces:**
- Produces: `BoardElementType.STREAM`, `enum class StreamOverlayMode { AUTO, RUNNING, RESULTS, UPCOMING }`,
  Felder `BoardElement.streamMode: StreamOverlayMode?` (null = AUTO) — `useShortNames`,
  `backgroundColor` existieren bereits und werden mitbenutzt.
- Validierungsregeln: STREAM muss die einzige Kachel des Boards sein (wie MATCH_DETAIL);
  `streamMode` ist nur an STREAM-Elementen erlaubt.

- [ ] **Step 1: Failing Tests schreiben** — in `BoardRequestValidationTest.kt` ergänzen
  (bestehende Testmuster der Datei übernehmen; `boardRequest(...)`-Helfer der Datei nutzen,
  falls vorhanden, sonst analog zu den MATCH_DETAIL-Fällen aufbauen):

```kotlin
@Test
fun `STREAM ist nur als einzige Kachel gueltig`() {
    val invalid = BoardRequest(
        name = "Stream",
        config = BoardConfig(
            columns = 2,
            tiles = listOf(
                BoardTile(elements = listOf(BoardElement(type = BoardElementType.STREAM))),
                BoardTile(elements = listOf(BoardElement(type = BoardElementType.CLOCK))),
            ),
        ),
    )
    assertTrue(invalid.validate() is ValidationResult.Invalid)

    val valid = BoardRequest(
        name = "Stream",
        config = BoardConfig(
            columns = 1,
            tiles = listOf(
                BoardTile(elements = listOf(BoardElement(type = BoardElementType.STREAM))),
            ),
        ),
    )
    assertEquals(ValidationResult.Valid, valid.validate())
}

@Test
fun `streamMode ist nur an STREAM-Elementen erlaubt`() {
    val invalid = BoardRequest(
        name = "Uhr",
        config = BoardConfig(
            columns = 1,
            tiles = listOf(
                BoardTile(
                    elements = listOf(
                        BoardElement(type = BoardElementType.CLOCK, streamMode = StreamOverlayMode.AUTO)
                    )
                ),
            ),
        ),
    )
    assertTrue(invalid.validate() is ValidationResult.Invalid)
}
```

- [ ] **Step 2: Tests laufen lassen — müssen ROT sein (Kompilierfehler zählt als rot)**

```bash
cd /Users/thomas/Developer/privat/ready2race/.claude/worktrees/stream-overlay/backend && \
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test \
  -Dtest='BoardRequestValidationTest' \
  -Ddatabase.url=jdbc:postgresql://localhost:7652/r2r-stream-overlay-build 2>&1 | tail -5
```
Expected: Kompilierfehler (`Unresolved reference: STREAM` / `StreamOverlayMode`).

- [ ] **Step 3: Implementierung in `BoardConfig.kt`**

Enum erweitern (Kommentar an der bestehenden Zeile fortschreiben):

```kotlin
enum class BoardElementType { MATCH, MATCH_DETAIL, MATCH_LIST, CLOCK, TEXT, AWARD_CEREMONY, DELAY, STREAM }

/**
 * Inhalt der Stream-Overlay-Kachel: AUTO = laufender Lauf, sonst letztes Ergebnis (der
 * Rückfall, mit dem der Stream fast immer eine sinnvolle Einblendung hat); die übrigen
 * Modi zeigen genau eine Quelle und sonst nichts — für Streamer, die sich je Quelle ein
 * eigenes Board bauen. Fehlt das Feld, gilt AUTO.
 */
enum class StreamOverlayMode { AUTO, RUNNING, RESULTS, UPCOMING }
```

An `BoardElement` (nach `scheduleMode`, vor `useShortNames`):

```kotlin
    // STREAM: was das Livestream-Overlay einblendet — siehe StreamOverlayMode.
    val streamMode: StreamOverlayMode? = null,
```

- [ ] **Step 4: Validierung in `BoardDtos.kt`** — im `when (element.type)` einen Zweig
  ergänzen UND die Quer-Regel für `streamMode` neben die bestehende `scheduleMode`-Regel
  stellen (vor dem `when`, bei den typunabhängigen Prüfungen):

```kotlin
                if (element.streamMode != null && element.type != BoardElementType.STREAM) {
                    errors += "$at: streamMode requires type STREAM"
                }
```

Im `when`:

```kotlin
                    BoardElementType.STREAM -> {
                        // Wie die Sprecher-Kachel: vollflächig, duldet keine Nachbarkacheln.
                        if (config.tiles.size > 1) {
                            errors += "$at: STREAM must be the only tile of the board"
                        }
                    }
```

- [ ] **Step 5: Tests laufen lassen — GRÜN**

Gleicher Befehl wie Step 2. Expected: `Tests run: <n>, Failures: 0`.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/entity/BoardConfig.kt \
        backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/entity/BoardDtos.kt \
        backend/src/test/kotlin/de/lambda9/ready2race/backend/app/eventInfo/BoardRequestValidationTest.kt
git commit -m "Add STREAM board element type with overlay mode"
```

---

### Task 2: Backend — dataNeeds liefert die Slots des Stream-Boards

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/boundary/BoardLogic.kt:67-113` (`dataNeeds`)
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/eventInfo/BoardLogicTest.kt`

**Interfaces:**
- Consumes: `BoardElementType.STREAM`, `StreamOverlayMode` aus Task 1.
- Produces: `dataNeeds` zählt für STREAM-Elemente Offsets in die Slot-Menge:
  AUTO → {0, −1}, RUNNING → {0}, RESULTS → {−1}, UPCOMING → {1}. Mehr braucht der
  Server nicht zu liefern; `resolveOffset` bleibt unangetastet.

- [ ] **Step 1: Failing Test in `BoardLogicTest.kt`** (Baumuster der Datei übernehmen):

```kotlin
@Test
fun `dataNeeds meldet die Slots des Stream-Overlays an`() {
    fun config(mode: StreamOverlayMode?) = BoardConfig(
        columns = 1,
        tiles = listOf(
            BoardTile(elements = listOf(BoardElement(type = BoardElementType.STREAM, streamMode = mode)))
        ),
    )
    // AUTO (auch als null): laufender Lauf + Ergebnis-Rückfall.
    assertEquals(setOf(0, -1), BoardLogic.dataNeeds(config(null)).offsets)
    assertEquals(setOf(0, -1), BoardLogic.dataNeeds(config(StreamOverlayMode.AUTO)).offsets)
    assertEquals(setOf(0), BoardLogic.dataNeeds(config(StreamOverlayMode.RUNNING)).offsets)
    assertEquals(setOf(-1), BoardLogic.dataNeeds(config(StreamOverlayMode.RESULTS)).offsets)
    assertEquals(setOf(1), BoardLogic.dataNeeds(config(StreamOverlayMode.UPCOMING)).offsets)
}
```

- [ ] **Step 2: Test rot**

```bash
cd /Users/thomas/Developer/privat/ready2race/.claude/worktrees/stream-overlay/backend && \
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test \
  -Dtest='BoardLogicTest' \
  -Ddatabase.url=jdbc:postgresql://localhost:7652/r2r-stream-overlay-build 2>&1 | tail -5
```
Expected: FAIL (`expected: <[0, -1]> but was: <[]>`).

- [ ] **Step 3: Implementierung in `dataNeeds`** — nach der bestehenden `offsets`-Berechnung:

```kotlin
        // Das Stream-Overlay hängt sich an dieselbe Timeline: Offset 0 ist der zuletzt
        // gestartete laufende Lauf (genau die Regel „bei mehreren gewinnt der zuletzt
        // gestartete" aus der Spec), −1 das jüngste Ergebnis, +1 der nächste anstehende.
        val streamOffsets = elements
            .filter { it.type == BoardElementType.STREAM }
            .flatMap {
                when (it.streamMode ?: StreamOverlayMode.AUTO) {
                    StreamOverlayMode.AUTO -> listOf(0, -1)
                    StreamOverlayMode.RUNNING -> listOf(0)
                    StreamOverlayMode.RESULTS -> listOf(-1)
                    StreamOverlayMode.UPCOMING -> listOf(1)
                }
            }
            .toSet()
```

Dann `offsets` durch `offsets + streamOffsets` ersetzen — konkret: die lokale Variable
umbenennen und zusammenführen, sodass `maxNegative`/`maxPositive` und das
`BoardDataNeeds(offsets = …)`-Feld die VEREINIGTE Menge sehen.

- [ ] **Step 4: Test grün** — Befehl aus Step 2, Expected: `Failures: 0`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/boundary/BoardLogic.kt \
        backend/src/test/kotlin/de/lambda9/ready2race/backend/app/eventInfo/BoardLogicTest.kt
git commit -m "Feed stream overlay slots through board data needs"
```

---

### Task 3: OpenAPI + generierter Client

**Files:**
- Modify: `backend/src/main/resources/openapi/documentation.yaml` (Suchanker: `BoardElement:` Schema, Enum des Feldes `type`, Nachbarfeld `scheduleMode`)
- Generated: `frontend/src/api/types.gen.ts`, `frontend/src/api/sdk.gen.ts` (nur via Generator anfassen)

**Interfaces:**
- Produces: TS-Typen `BoardElement['type']` enthält `'STREAM'`; `streamMode?: 'AUTO' | 'RUNNING' | 'RESULTS' | 'UPCOMING'`.

- [ ] **Step 1: YAML erweitern.** Im `BoardElement`-Schema: beim `type`-Enum den Wert
  `- STREAM` ergänzen; neben `scheduleMode` das neue Feld:

```yaml
        streamMode:
          type: string
          nullable: true
          description: "STREAM only: what the livestream overlay shows; missing means AUTO (running match, falling back to the latest result)"
          enum:
            - AUTO
            - RUNNING
            - RESULTS
            - UPCOMING
```

- [ ] **Step 2: Client generieren und Diff prüfen**

```bash
cd /Users/thomas/Developer/privat/ready2race/.claude/worktrees/stream-overlay/frontend && npm install --no-audit --no-fund && npm run generate && git diff --stat src/api/
```
Expected: `types.gen.ts` ändert sich (STREAM + streamMode), sonst nichts Überraschendes.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/openapi/documentation.yaml frontend/src/api/
git commit -m "Document the STREAM board element in the API schema"
```

---

### Task 4: Frontend — Auswahllogik als reine Funktion (TDD)

**Files:**
- Create: `frontend/src/components/event/board/streamOverlay.ts`
- Test: `frontend/src/components/event/board/streamOverlay.test.ts`

**Interfaces:**
- Consumes: generierte Typen `BoardViewDto`, `BoardElement`, `AthleteBoardMatch`, `AthleteBoardResult`.
- Produces (Task 5 verlässt sich darauf):

```ts
export type StreamOverlayContent =
    | {kind: 'running'; match: AthleteBoardMatch}
    | {kind: 'result'; result: AthleteBoardResult}
    | {kind: 'upcoming'; match: AthleteBoardMatch}
    | null

export const streamOverlayContent = (
    slots: BoardViewDto['slots'],
    mode: BoardElement['streamMode'],
): StreamOverlayContent

/** Chroma-Voreinstellung des Stream-Overlays — reines Grün. */
export const STREAM_DEFAULT_BACKGROUND = '#00FF00'
```

Regeln: `slotAt(offset)` sucht in `slots` nach `offset`. AUTO (auch `null`/`undefined`):
`slot(0).match` als running, sonst `slot(-1).result` als result, sonst `null`.
RUNNING: nur `slot(0).match`. RESULTS: nur `slot(-1).result`. UPCOMING: nur `slot(1).match`.

- [ ] **Step 1: Failing Tests schreiben** — `streamOverlay.test.ts`:

```ts
import {describe, expect, it} from 'vitest'
import {streamOverlayContent} from './streamOverlay.ts'
import {AthleteBoardMatch, AthleteBoardResult, BoardMatchSlotDto} from '@api/types.gen.ts'

const match = (name: string) => ({matchName: name}) as unknown as AthleteBoardMatch
const result = (name: string) => ({matchName: name}) as unknown as AthleteBoardResult
const slot = (offset: number, m?: AthleteBoardMatch, r?: AthleteBoardResult): BoardMatchSlotDto =>
    ({offset, match: m ?? null, result: r ?? null}) as BoardMatchSlotDto

describe('streamOverlayContent', () => {
    it('AUTO zeigt den laufenden Lauf, wenn einer läuft', () => {
        const content = streamOverlayContent([slot(0, match('VF1')), slot(-1, undefined, result('ZF'))], 'AUTO')
        expect(content).toMatchObject({kind: 'running'})
    })

    it('AUTO fällt ohne laufenden Lauf auf das jüngste Ergebnis zurück', () => {
        const content = streamOverlayContent([slot(0), slot(-1, undefined, result('ZF'))], undefined)
        expect(content).toMatchObject({kind: 'result'})
    })

    it('AUTO ohne beides bleibt leer', () => {
        expect(streamOverlayContent([slot(0), slot(-1)], 'AUTO')).toBeNull()
    })

    it('RUNNING zeigt kein Ergebnis als Rückfall', () => {
        expect(streamOverlayContent([slot(0), slot(-1, undefined, result('ZF'))], 'RUNNING')).toBeNull()
    })

    it('RESULTS zeigt nur das Ergebnis', () => {
        const content = streamOverlayContent([slot(-1, undefined, result('ZF'))], 'RESULTS')
        expect(content).toMatchObject({kind: 'result'})
    })

    it('UPCOMING zeigt den nächsten anstehenden Lauf', () => {
        const content = streamOverlayContent([slot(1, match('HF2'))], 'UPCOMING')
        expect(content).toMatchObject({kind: 'upcoming'})
    })

    it('ein Ergebnis im Slot −1 wird im AUTO-Modus nicht als laufend ausgegeben', () => {
        const content = streamOverlayContent([slot(0), slot(-1, match('noch laufender älterer Lauf'))], 'AUTO')
        // Slot −1 kann laut resolveOffset auch einen FRÜHER gestarteten, noch laufenden
        // Lauf tragen — der ist kein Ergebnis und wird im AUTO-Rückfall übersprungen.
        expect(content).toBeNull()
    })
})
```

- [ ] **Step 2: Rot**

```bash
cd /Users/thomas/Developer/privat/ready2race/.claude/worktrees/stream-overlay/frontend && npx vitest run src/components/event/board/streamOverlay.test.ts 2>&1 | tail -5
```
Expected: FAIL (Modul existiert nicht).

- [ ] **Step 3: Implementierung** — `streamOverlay.ts`:

```ts
import {AthleteBoardMatch, AthleteBoardResult, BoardElement, BoardViewDto} from '@api/types.gen.ts'

/**
 * Was das Livestream-Overlay einblendet. Genau EIN Lauf oder nichts — ein Lower-Third
 * mit zwei Läufen gibt es nicht (Spec: bei mehreren laufenden gewinnt der zuletzt
 * gestartete, und das erledigt bereits die Slot-Maschinerie des Servers mit Offset 0).
 */
export type StreamOverlayContent =
    | {kind: 'running'; match: AthleteBoardMatch}
    | {kind: 'result'; result: AthleteBoardResult}
    | {kind: 'upcoming'; match: AthleteBoardMatch}
    | null

/** Chroma-Voreinstellung des Stream-Overlays — reines Grün. */
export const STREAM_DEFAULT_BACKGROUND = '#00FF00'

const slotAt = (slots: BoardViewDto['slots'], offset: number) =>
    slots.find(slot => slot.offset === offset)

export const streamOverlayContent = (
    slots: BoardViewDto['slots'],
    mode: BoardElement['streamMode'],
): StreamOverlayContent => {
    const running = slotAt(slots, 0)?.match
    const latestResult = slotAt(slots, -1)?.result
    const upcoming = slotAt(slots, 1)?.match
    switch (mode ?? 'AUTO') {
        case 'RUNNING':
            return running ? {kind: 'running', match: running} : null
        case 'RESULTS':
            return latestResult ? {kind: 'result', result: latestResult} : null
        case 'UPCOMING':
            return upcoming ? {kind: 'upcoming', match: upcoming} : null
        default:
            return running
                ? {kind: 'running', match: running}
                : latestResult
                  ? {kind: 'result', result: latestResult}
                  : null
    }
}
```

- [ ] **Step 4: Grün** — Befehl aus Step 2, Expected: alle Tests PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/event/board/streamOverlay.ts frontend/src/components/event/board/streamOverlay.test.ts
git commit -m "Add pure content selection for the stream overlay"
```

---

### Task 5: Frontend — Overlay-Komponente + vollflächiges Rendern

**Files:**
- Create: `frontend/src/components/event/board/BoardStreamOverlayElement.tsx`
- Modify: `frontend/src/components/event/board/BoardElementView.tsx` (Dispatch: `case 'STREAM'`)
- Modify: `frontend/src/components/event/board/BoardRenderer.tsx` (Früh-Zweig: Board mit STREAM-Element rendert NUR das Overlay, randlos, ohne Kopfzeile und ohne Raster)
- Modify: `frontend/src/components/event/board/boardView.ts` (Helfer `hasStreamOverlay(tiles)` analog `hasMatchDetail`)

**Interfaces:**
- Consumes: `streamOverlayContent`, `STREAM_DEFAULT_BACKGROUND` (Task 4); Team-Felder
  `startNumber`, `clubsShort`, `clubsFull`, `teamName`, `place`, `timeString`,
  `penaltySeconds`, `penaltyNote`, `failed`, `failedReason`, `laps` (existieren an
  `AthleteBoardTeam`/Result-Teams seit der Laps-Runde).
- Produces: `<BoardStreamOverlayElement view={BoardViewDto} element={BoardElement} />`.

- [ ] **Step 1: `hasStreamOverlay` in `boardView.ts`** — direkt neben `hasMatchDetail`:

```ts
/** Board mit Stream-Overlay: rendert vollflächig in Key-Farbe, ohne Raster und Kopfzeile. */
export const hasStreamOverlay = (tiles: BoardTile[]): boolean =>
    tiles.some(tile => tile.elements.some(element => element.type === 'STREAM'))
```

- [ ] **Step 2: Komponente `BoardStreamOverlayElement.tsx`.** Kernpunkte (vollständiger
  Aufbau, Werte aus dem Theme über `useTheme()`):

```tsx
import {Box, Stack, Typography, useTheme} from '@mui/material'
import {BoardElement, BoardViewDto} from '@api/types.gen.ts'
import {streamOverlayContent, STREAM_DEFAULT_BACKGROUND} from './streamOverlay.ts'
import {useTranslation} from 'react-i18next'

type Props = {
    view: BoardViewDto
    element: BoardElement
}

/**
 * Das Livestream-Overlay: vollflächige Key-Farbe, unten ein Lower-Third im r2r-Design.
 *
 * Chroma-Regeln: Das Panel ist VOLLSTÄNDIG deckend — keine Halbtransparenz, keine weichen
 * Schatten, kein Blur. Halbtransparente Pixel mischen sich mit der Key-Farbe und erzeugen
 * Farbsäume, sobald der Streamer die Farbe herausfiltert. Harte Kanten, Rundung ist okay.
 */
const BoardStreamOverlayElement = ({view, element}: Props) => {
    const {t} = useTranslation()
    const theme = useTheme()
    const content = streamOverlayContent(view.slots, element.streamMode)
    const keyColor = element.backgroundColor ?? STREAM_DEFAULT_BACKGROUND

    // Leerzustand: reine Key-Farbe — das Overlay verschwindet im Stream von selbst.
    if (content === null) {
        return <Box sx={{position: 'fixed', inset: 0, backgroundColor: keyColor}} />
    }

    const teams = /* je nach content.kind: match.teams bzw. result.teams,
                     sortiert nach place (Ergebnis) bzw. startNumber (laufend/anstehend) */
    const clubOf = (team) => (element.useShortNames === false ? team.clubsFull : team.clubsShort)

    return (
        <Box sx={{position: 'fixed', inset: 0, backgroundColor: keyColor, display: 'flex', alignItems: 'flex-end'}}>
            <Box sx={{
                m: 3, width: 1, maxHeight: '38vh', overflow: 'hidden',
                borderRadius: 2,
                backgroundColor: theme.palette.text.primary,   // dunkles, DECKENDES Panel
                color: theme.palette.background.paper,
            }}>
                {/* Kopfzeile: Zustand + Wettkampf + Runde/Lauf + Startzeit */}
                {/* Akzentband links in theme.palette.primary.main */}
                {/* Je Boot eine Zeile: Startnummer (Monospace-Ziffern), Verein, Zeit/Platz */}
                {/* Zeitstrafe: warning-Farbton, Text "…s · {penaltyNote}" */}
                {/* Rundenzeiten: eigene Zeile, tabularNums, kleiner */}
            </Box>
        </Box>
    )
}
export default BoardStreamOverlayElement
```

  Der Kommentarblock in der Mitte ist im echten Code auszuprogrammieren:
  - Kopfzeile: `Typography variant h4` fett — bei `running`: Chip-artiges deckendes Label
    `t('event.boards.stream.running')`; bei `result`: `t('event.boards.stream.result')`;
    bei `upcoming`: `t('event.boards.stream.upcoming')` + Startzeit (`format(new Date(match.startTime), t('format.time'))`).
  - Wettkampfname aus `match.competitionName ?? result.competitionName` (+ `roundName`/`matchName`,
    Laufname weglassen, wenn identisch mit Rundenname — gleiche Regel wie ResultsMatchCard).
  - Bootszeilen: `Stack` mit `Typography` in `h5`-Größe, `fontVariantNumeric: 'tabular-nums'`
    für Startnummer/Zeit; Platz vorangestellt bei Ergebnis (`1.`), `failedLabel` für DNF
    (`@utils/matchResultStatus.ts`).
  - Zeitstrafe: `theme.palette.warning.light` als Textfarbe auf dem dunklen Panel.
  - Rundenzeiten (`team.laps`): eine Sekundärzeile `Runde 1 12:34.5 · Runde 2 …` in
    `body2`, nur wenn `laps` nicht leer.

- [ ] **Step 3: Dispatch + Vollflächig-Zweig.** In `BoardElementView.tsx` den
  `case 'STREAM': return <BoardStreamOverlayElement …/>` ergänzen (Props wie die
  Nachbarn). In `BoardRenderer.tsx` VOR dem Raster:

```tsx
    // Ein Stream-Board ist kein Raster: nur das Overlay, randlos, ohne Kopfzeile —
    // dieselbe Heilungs-Idee wie beim Sprecher-Board (Fehlkonfiguration beim Lesen).
    if (hasStreamOverlay(view.config.tiles)) {
        const tile = view.config.tiles.find(t => t.elements.some(e => e.type === 'STREAM'))!
        const element = tile.elements.find(e => e.type === 'STREAM')!
        return <BoardStreamOverlayElement view={view} element={element} />
    }
```

- [ ] **Step 4: Build prüfen**

```bash
cd /Users/thomas/Developer/privat/ready2race/.claude/worktrees/stream-overlay/frontend && npx tsc --noEmit && npm run build 2>&1 | tail -2
```
Expected: kein Typfehler, Build sauber.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/event/board/
git commit -m "Render the stream overlay board full-page"
```

---

### Task 6: Editor, i18n, Browser-Verifikation

**Files:**
- Modify: `frontend/src/components/event/board/BoardEditor.tsx` (`elementForType`, Typ-Auswahl-MenuItem, Options-Formular)
- Modify: `frontend/src/i18n/de/translations.json`, `…/en/…`, `…/da/…` (unter `event.boards.…`)

**Interfaces:**
- Consumes: alles aus Task 1–5.

- [ ] **Step 1: Editor.** In `elementForType`:

```ts
        case 'STREAM':
            // Stream-Overlay: Grün als Key-Farbe voreingestellt, Kurzform an, Modus AUTO.
            return {type, streamMode: 'AUTO', useShortNames: true, backgroundColor: '#00FF00'}
```

  Typ-Auswahl: `<MenuItem value="STREAM">` mit derselben Sichtbarkeitsregel wie
  MATCH_DETAIL (`config.tiles.length === 1 || element.type === 'STREAM'`), Label
  `t('event.boards.element.type.stream')`. Options-Formular (analog zum
  MATCH_DETAIL-Block bei Zeile ~355): Dropdown für `streamMode` (vier Werte, Labels
  `event.boards.stream.mode.auto|running|results|upcoming`) und Schalter
  „Vereins-Langform" (`useShortNames === false`).

- [ ] **Step 2: i18n.** In allen drei Sprachen unter `event.boards`:

```json
"stream": {
    "running": "Läuft",
    "result": "Ergebnis",
    "upcoming": "Als Nächstes",
    "mode": {
        "label": "Inhalt",
        "auto": "Automatisch (läuft, sonst letztes Ergebnis)",
        "running": "Nur laufender Lauf",
        "results": "Nur letztes Ergebnis",
        "upcoming": "Nächster anstehender Lauf"
    },
    "longClubNames": "Vereins-Langform"
}
```
  (en: Running/Result/Up next/Content/Automatic (running, else latest result)/…;
  da: I gang/Resultat/Næste/Indhold/Automatisk (…)/…. Unter `event.boards.element.type`:
  `"stream": "Livestream-Overlay"` / `"Livestream overlay"` / `"Livestream-overlay"`.)
  JSON-Bearbeitung formaterhaltend (2 Spaces, `ensure_ascii=False`).

- [ ] **Step 3: Alle Frontend-Tests + Build**

```bash
cd /Users/thomas/Developer/privat/ready2race/.claude/worktrees/stream-overlay/frontend && npm run test 2>&1 | grep -E "Tests |failed" && npm run build 2>&1 | tail -1
```
Expected: alle Tests grün (Stand vor diesem Feature: 1045), Build sauber.

- [ ] **Step 4: Backend bauen + DB aufsetzen + Seed**

```bash
docker exec backend-build-db-1 psql -U developer -d postgres -c 'CREATE DATABASE "r2r-stream-overlay-build"' || true
cd /Users/thomas/Developer/privat/ready2race/.claude/worktrees/stream-overlay/backend && docker compose up -d && \
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw -q package -DskipTests \
  -Ddatabase.url=jdbc:postgresql://localhost:7652/r2r-stream-overlay-build
```

  Backend einmal über `preview_start` (`stream-overlay-backend`) starten, damit Flyway die
  Dev-DB `r2r_stream_overlay` anlegt, dann den schema-gehobenen Seed einspielen
  (liegt im Scratchpad dieser Session; falls der Task „Prod-Seed auf neues Schema heben"
  inzwischen gemergt ist, stattdessen `docs/seeds/seed-prod-crf-2026.sql`):

```bash
docker exec -i backend-db-1 psql -U developer -d r2r_stream_overlay -v ON_ERROR_STOP=1 \
  < /private/tmp/claude-501/-Users-thomas-Developer-privat-ready2race/31cebdc0-9c6f-4f2d-9f2c-be08a395d62f/scratchpad/seed-prod-crf-2026-schema-neu.sql
```

  Testzustände fabrizieren (laufend + Ergebnis mit Strafe und Rundenzeiten), SQL-Muster aus
  der mobile-ergebnisse-Session übernehmen: fünf Läufe beenden (Zeiten via `timecode`-Zeilen,
  Plätze, eine 10-s-Strafe mit Vermerk), einen Lauf auf laufend setzen; zusätzlich für einen
  laufenden Lauf `competition_match_team_lap`-Zeilen anlegen, damit Rundenzeiten sichtbar sind.

- [ ] **Step 5: Board anlegen + verifizieren.** Ohne Admin-Zugang (`.env` gesperrt) das
  Board direkt per SQL anlegen (Tabelle `board`, Spalte `config` als JSON):

```sql
insert into ready2race.board (id, event, name, config, created_at, updated_at)
values (gen_random_uuid(), '9d27f0ee-6622-48d5-82a4-1975baa18d13', 'Stream-Overlay',
        '{"columns":1,"refreshIntervalSeconds":3,"tiles":[{"rotationIntervalSeconds":10,"colSpan":1,"rowSpan":1,"elements":[{"type":"STREAM","streamMode":"AUTO","useShortNames":true,"backgroundColor":"#00FF00"}]}]}',
        now(), now());
```
  (Spaltenliste vor dem Einfügen gegen `information_schema.columns` prüfen — `created_by`
  ist nullable oder per vorhandenem Admin-User zu füllen.)

  Dann `preview_start` (`stream-overlay-frontend`), Browser auf
  `/board/9d27f0ee-6622-48d5-82a4-1975baa18d13/<boardId>`, Viewport 1280×800, prüfen:
  1. Laufender Lauf → Lower-Third mit Kopfzeile „Läuft", Bootszeilen, Rundenzeiten, Strafe.
  2. Laufenden Lauf beenden (SQL: `started_at` nullen bzw. `finished_at` setzen) → AUTO
     fällt auf „Ergebnis" zurück.
  3. Alle Aktivität nullen → reine Grünfläche (Screenshot).
  4. `streamMode` auf `UPCOMING` umschreiben (SQL-Update der config) → „Als Nächstes" + Startzeit.
  Screenshots je Zustand über den Screenshot-Befehl des Browser-Panels festhalten.

- [ ] **Step 6: Kompletter Backend-Testlauf**

```bash
cd /Users/thomas/Developer/privat/ready2race/.claude/worktrees/stream-overlay/backend && \
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test \
  -Ddatabase.url=jdbc:postgresql://localhost:7652/r2r-stream-overlay-build 2>&1 | grep -E "Tests run: [0-9]+, Failures" | tail -1
```
Expected: `Failures: 0, Errors: 0` (Stand vor diesem Feature: 1011 Tests).

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/event/board/BoardEditor.tsx frontend/src/i18n/
git commit -m "Let the board editor configure stream overlays"
```

---

### Task 7: Merge in den Sammelbranch

- [ ] **Step 1:** Im Hauptcheckout (`/Users/thomas/Developer/privat/ready2race`, Branch
  `feature/crf-2026`) — vorher prüfen, dass er sauber ist (`git status --short`):

```bash
cd /Users/thomas/Developer/privat/ready2race && git merge claude/stream-overlay --no-edit
```

- [ ] **Step 2:** KEIN Push. Abschlussbericht an Thomas: Board-Anlage im Editor zeigen
  (Typ „Livestream-Overlay"), OBS-Hinweis (Browser-Quelle mit Board-URL, Chroma-Key auf
  Grün, Auflösung frei — die Seite skaliert), Screenshots der vier Zustände.
