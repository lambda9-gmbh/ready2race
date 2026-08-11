# Athletenanzeige auf 27″ — Implementierungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die öffentliche Athletenanzeige zeigt auf einem 27″-Bildschirm im 16:9-Vollbild alle relevanten Läufe ohne vertikales Scrollen, in drei bis vier gleich breiten Spalten, und nennt die Startnummer beim Namen.

**Architecture:** Die Bühne wird ein CSS-Grid über die volle Fensterhöhe: Kopfzeile `auto`, Bühne `1fr`, darin gleich breite Spalten (`grid-auto-columns: 1fr`), je Spalte eine Karte über die volle Höhe, darin die Bootszeilen als `repeat(n, minmax(0, 1fr))`. Überlauf ist damit baulich unmöglich — es gibt kein `overflow: auto` mehr. Zwei reine Funktionen entscheiden, was auf die Bühne kommt (`selectBoardCards`) und wie groß der Text dabei wird (`densityScale`); beide sind ohne Browser prüfbar. Parallel wird das Feld, das bisher `lane` heißt und „Bahn" anzeigt, auf dem ganzen Pfad in `startNumber` umbenannt.

**Tech Stack:** Kotlin/Ktor + jOOQ (Backend), TypeSpec/OpenAPI → `@hey-api/openapi-ts` (API-Typen), React 18 + MUI 5 + TanStack Router + i18next (Frontend), Vitest (Frontend-Tests), JUnit/kotlin.test (Backend-Tests).

**Spec:** `docs/superpowers/specs/2026-08-09-athletenanzeige-27zoll-design.md`

## Global Constraints

- Arbeitsverzeichnis ist der Worktree `/Users/thomas/Developer/privat/ready2race/.claude/worktrees/ready2race-athlete-display-27in-ad983c`, Branch `claude/ready2race-athlete-display-27in-ad983c`. Alle Pfade in diesem Plan sind relativ dazu.
- **Kommentare und Commit-Nachrichten auf Deutsch mit echten Umlauten** (ä, ö, ü, ß), passend zum Bestand in `athleteBoard/`. Kein Hinweis auf Claude oder KI in Commits.
- **Höchstens zwei Läufe in der Arena** kommen auf die Bühne (`MAX_RUNNING_CARDS = 2`), plus genau ein „nächster Lauf" und genau ein „letztes Ergebnis". Spaltenzahl also immer 3 oder 4.
- **Die drei Statusspalten stehen immer**, auch leer — dann mit ihrer neutralen Zeile.
- **Kein `overflow: auto`** und kein Scrollen ab dem Umbruchpunkt `lg`. Unterhalb `lg` bleibt die heutige gestapelte, scrollbare Darstellung erhalten.
- **Keine Rotation, keine automatische Seitenumschaltung, keine Bedienelemente.**
- Die Crew-Zeile bleibt in den Karten „Aktuell" und „Nächster", aber **einzeilig mit Auslassungspunkten**.
- **Unverändert bleiben:** `useAthleteBoardData.ts`, `useServerClock.ts`, `AthleteBoardPenaltyNote.tsx`, das gesamte Backend außer den in Task 1 genannten Dateien, sowie das Konfigurationsmodell (`running`-Vorgabe bleibt 3 — die Kappung auf 2 passiert allein im Frontend).
- Nach jeder Task müssen `npm run lint`, `npx tsc -b` und `npm run test` im Verzeichnis `frontend/` fehlerfrei laufen.

---

## Dateiübersicht

| Datei | Rolle | Task |
| --- | --- | --- |
| `backend/src/main/kotlin/.../eventInfo/entity/AthleteBoardDto.kt` | ändern: `lane` → `startNumber` | 1 |
| `backend/src/main/kotlin/.../eventInfo/control/Conversions.kt` | ändern: drei Zuweisungen | 1 |
| `backend/src/main/resources/openapi/documentation.yaml` | ändern: zwei Schemata | 1 |
| `frontend/src/api/*.gen.ts` | erzeugt über `npm run generate` | 1 |
| `frontend/src/i18n/{de,en,da}/translations.json` | Schlüssel `lane` → `startNumber`, neu `moreRunning_*` | 1, 3 |
| `frontend/src/components/event/info/athleteBoard/common.ts` | neu: `scaled()` | 2 |
| `frontend/src/components/event/info/athleteBoard/boardLayout.ts` | **neu**: `selectBoardCards`, `densityScale`, `maxBoats` | 2 |
| `frontend/src/components/event/info/athleteBoard/boardLayout.test.ts` | **neu**: Tests dazu | 2 |
| `frontend/src/components/event/info/athleteBoard/AthleteBoardColumnCard.tsx` | **neu**: Spaltenrahmen mit Überschrift | 3 |
| `frontend/src/components/event/info/views/AthleteBoardView.tsx` | ändern: Bühne als Vollhöhen-Raster | 3 |
| `frontend/src/pages/event/AthleteBoardPage.tsx` | ändern: `100dvh` | 3 |
| `frontend/src/components/event/info/athleteBoard/AthleteBoardMatchCard.tsx` | ändern: rahmenlos, Zeilenraster, einzeilige Crew | 4 |
| `frontend/src/components/event/info/athleteBoard/AthleteBoardResultCard.tsx` | ändern: rahmenlos, Zeilenraster, „Nr." statt „Bahn" | 5 |

---

## Task 1: Die Startnummer beim Namen nennen

Das Feld heißt im Datenmodell `competition_match_team.start_number` und in der ganzen übrigen Anwendung „Startnummer". Nur die Athletenanzeige nennt es `lane` / „Bahn". Diese Task beseitigt den Doppelnamen auf dem ganzen Pfad. Rein mechanisch, keine Verhaltensänderung außer der Beschriftung.

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/entity/AthleteBoardDto.kt:63-64` und `:110-111`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/control/Conversions.kt` (drei Stellen: `lane = startNumber` ×2, `lane = it.startNumber` ×1)
- Modify: `backend/src/main/resources/openapi/documentation.yaml` (Schemata `AthleteBoardTeam` ab Zeile 13277, `AthleteBoardResultTeam` ab Zeile 13382)
- Modify (erzeugt): `frontend/src/api/types.gen.ts`
- Modify: `frontend/src/components/event/info/athleteBoard/AthleteBoardResultCard.tsx:79,92,102`
- Modify: `frontend/src/components/event/info/athleteBoard/AthleteBoardMatchCard.tsx:231,244`
- Modify: `frontend/src/i18n/de/translations.json`, `frontend/src/i18n/en/translations.json`, `frontend/src/i18n/da/translations.json`

**Interfaces:**
- Consumes: nichts.
- Produces: `AthleteBoardTeam.startNumber?: number | null`, `AthleteBoardResultTeam.startNumber: number` in `@api/types.gen`. Der i18n-Schlüssel `event.info.athleteBoard.startNumber` liefert die Kurzform „Nr.". Alle folgenden Tasks verwenden ausschließlich diese Namen.

- [ ] **Step 1: Kotlin-DTO umbenennen**

In `AthleteBoardDto.kt`, im `data class AthleteBoardTeam`:

```kotlin
    /** Startposition im Lauf, aus `competition_match_team.start_number`. Die Anzeige nannte sie
     *  bis zum 09.08.2026 „Bahn"; eine davon unabhängige Bahnnummer gibt es im Datenmodell nicht. */
    val startNumber: Int?,
```

Und im `data class AthleteBoardResultTeam`:

```kotlin
    val startNumber: Int,
```

- [ ] **Step 2: Conversions anpassen**

In `Conversions.kt` die drei Zuweisungen ändern — in `RunningMatchTeamInfo.toAthleteBoardTeam()` und `UpcomingMatchTeamInfo.toAthleteBoardTeam()` jeweils:

```kotlin
    startNumber = startNumber,
```

und im `AthleteBoardResultTeam(...)`-Konstruktoraufruf:

```kotlin
            startNumber = it.startNumber,
```

- [ ] **Step 3: Backend übersetzen und Tests laufen lassen**

```bash
cd backend && ./mvnw -q -o test
```

Erwartet: BUILD SUCCESS, `AthleteBoardLogicTest` grün. Falls `JAVA_HOME` fehlt, vorher setzen:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

- [ ] **Step 4: OpenAPI-Beschreibung anpassen**

In `backend/src/main/resources/openapi/documentation.yaml` im Schema `AthleteBoardTeam` die Eigenschaft umbenennen (Einrückung beibehalten):

```yaml
        startNumber:
          type: integer
          nullable: true
          description: "starting position in the match, from competition_match_team.start_number"
```

Und im Schema `AthleteBoardResultTeam` — dort steht `lane` zusätzlich unter `required`:

```yaml
      required:
        - startNumber
        - failed
        - deregistered
      properties:
        place:
          type: integer
          nullable: true
        startNumber:
          type: integer
```

Kontrolle, dass kein `lane` übrig ist:

```bash
grep -n "lane" backend/src/main/resources/openapi/documentation.yaml
```

Erwartet: keine Ausgabe.

- [ ] **Step 5: API-Typen neu erzeugen**

```bash
cd frontend && npm run generate
git diff --stat src/api
```

Erwartet: nur `src/api/types.gen.ts` geändert. Falls weitere Dateien auftauchen oder der Diff über die beiden Felder hinausgeht, mit `git diff src/api` prüfen und Fremdänderungen mit `git checkout --` zurücknehmen.

- [ ] **Step 6: Übersetzungsschlüssel umbenennen**

In allen drei Dateien den Schlüssel `event.info.athleteBoard.lane` durch `startNumber` ersetzen (die Sortierung an Ort und Stelle beibehalten):

- `frontend/src/i18n/de/translations.json`: `"startNumber": "Nr."`
- `frontend/src/i18n/en/translations.json`: `"startNumber": "No."`
- `frontend/src/i18n/da/translations.json`: `"startNumber": "Nr."`

- [ ] **Step 7: Verwendungsstellen im Frontend nachziehen**

In `AthleteBoardResultCard.tsx`:

```tsx
                            key={`${result.matchId}-${team.startNumber}-${index}`}
```

```tsx
                                {team.place ?? '–'}
```

(unverändert — nur zur Orientierung, die Zeile darunter ändert sich:)

```tsx
                                <Typography
                                    sx={{fontSize: 'clamp(0.7rem, 1.1vw, 0.95rem)'}}
                                    color="text.secondary">
                                    {t('event.info.athleteBoard.startNumber')} {team.startNumber}
                                </Typography>
```

Und in der Sortierfunktion oben in derselben Datei:

```tsx
        if (a.place == null && b.place == null) return a.startNumber - b.startNumber
```

In `AthleteBoardMatchCard.tsx`:

```tsx
                                key={`${match.matchId}-${team.startNumber ?? index}`}
```

```tsx
                                    {team.startNumber ?? '–'}
```

- [ ] **Step 8: Checks laufen lassen**

```bash
cd frontend && npx tsc -b && npm run lint && npm run test
```

Erwartet: alle drei ohne Fehler; kein Treffer mehr für `lane`:

```bash
grep -rn "lane" frontend/src/components/event/info frontend/src/i18n
```

Erwartet: keine Ausgabe.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "Nenne die Startnummer auf der Athletenanzeige beim Namen"
```

---

## Task 2: Auswahl und Dichte als reine Funktionen

Die beiden Entscheidungen, die das neue Layout trägt, entstehen zuerst — ohne React, ohne Browser, mit Tests voran.

**Files:**
- Create: `frontend/src/components/event/info/athleteBoard/boardLayout.ts`
- Create: `frontend/src/components/event/info/athleteBoard/boardLayout.test.ts`
- Modify: `frontend/src/components/event/info/athleteBoard/common.ts` (Ergänzung `scaled`)

**Interfaces:**
- Consumes: `AthleteBoardDto`, `AthleteBoardMatch`, `AthleteBoardResult` aus `@api/types.gen`.
- Produces:
  - `MAX_RUNNING_CARDS: number` (= 2)
  - `type BoardCardKind = 'running' | 'upcoming' | 'result'`
  - `interface BoardCard {kind: BoardCardKind; key: string; match: AthleteBoardMatch | null; result: AthleteBoardResult | null}`
  - `interface BoardLayout {cards: BoardCard[]; hiddenRunning: number}`
  - `selectBoardCards(data: AthleteBoardDto | null): BoardLayout`
  - `maxBoats(cards: BoardCard[]): number`
  - `densityScale(boats: number, columns: number): number`
  - `MIN_DENSITY_SCALE: number` (= 0.55)
  - aus `common.ts`: `scaled(min: string, preferred: string, max: string): string`

- [ ] **Step 1: Den fehlschlagenden Test schreiben**

Neue Datei `frontend/src/components/event/info/athleteBoard/boardLayout.test.ts`:

```ts
import {describe, expect, test} from 'vitest'
import {AthleteBoardDto, AthleteBoardMatch, AthleteBoardResult} from '@api/types.gen'
import {
    MAX_RUNNING_CARDS,
    MIN_DENSITY_SCALE,
    densityScale,
    maxBoats,
    selectBoardCards,
} from './boardLayout'

const match = (id: string, boats = 4): AthleteBoardMatch =>
    ({
        matchId: id,
        competitionName: 'CM 4x+',
        categoryName: null,
        roundName: 'Vorlauf',
        matchName: null,
        startTime: null,
        state: 'RUNNING',
        startState: 'UNSCHEDULED',
        teams: Array.from({length: boats}, (_, i) => ({
            startNumber: i + 1,
            participants: [],
            failed: false,
        })),
    }) as unknown as AthleteBoardMatch

const result = (id: string, boats = 4): AthleteBoardResult =>
    ({
        matchId: id,
        competitionName: 'CM 4x+',
        categoryName: null,
        roundName: 'Vorlauf',
        matchName: null,
        startTime: null,
        actualStartTime: null,
        teams: Array.from({length: boats}, (_, i) => ({
            place: i + 1,
            startNumber: i + 1,
            failed: false,
            deregistered: false,
        })),
    }) as unknown as AthleteBoardResult

const board = (partial: Partial<AthleteBoardDto>): AthleteBoardDto =>
    ({
        eventName: 'Förde-Regatta',
        serverTime: '2026-08-09T14:32:00',
        refreshIntervalSeconds: 15,
        showCountdown: true,
        running: [],
        upcoming: [],
        results: [],
        ...partial,
    }) as unknown as AthleteBoardDto

describe('selectBoardCards', () => {
    // Ein fest montierter Bildschirm soll seine Struktur nicht wechseln, nur weil gerade
    // nichts fährt: die drei Statusspalten stehen auch leer.
    test('ohne Daten stehen drei leere Statusspalten', () => {
        const {cards, hiddenRunning} = selectBoardCards(null)
        expect(cards.map(c => c.kind)).toEqual(['running', 'upcoming', 'result'])
        expect(cards.every(c => c.match === null && c.result === null)).toBe(true)
        expect(hiddenRunning).toBe(0)
    })

    test('ein Lauf je Status ergibt drei Spalten', () => {
        const {cards} = selectBoardCards(
            board({running: [match('r1')], upcoming: [match('u1')], results: [result('e1')]}),
        )
        expect(cards.map(c => c.kind)).toEqual(['running', 'upcoming', 'result'])
        expect(cards[0].match?.matchId).toBe('r1')
        expect(cards[1].match?.matchId).toBe('u1')
        expect(cards[2].result?.matchId).toBe('e1')
    })

    test('zwei Läufe in der Arena ergeben vier gleichwertige Spalten', () => {
        const {cards, hiddenRunning} = selectBoardCards(
            board({running: [match('r1'), match('r2')], upcoming: [match('u1')], results: [result('e1')]}),
        )
        expect(cards.map(c => c.kind)).toEqual(['running', 'running', 'upcoming', 'result'])
        expect(hiddenRunning).toBe(0)
    })

    // Ein verschwundener Lauf ist von einem Anzeigefehler nicht zu unterscheiden — deshalb
    // wird die Kappung gemeldet statt verschwiegen.
    test('mehr als zwei Läufe in der Arena werden gekappt und gemeldet', () => {
        const {cards, hiddenRunning} = selectBoardCards(
            board({running: [match('r1'), match('r2'), match('r3')]}),
        )
        expect(cards.filter(c => c.kind === 'running')).toHaveLength(MAX_RUNNING_CARDS)
        expect(cards.map(c => c.match?.matchId)).toEqual(['r1', 'r2', undefined, undefined])
        expect(hiddenRunning).toBe(1)
    })

    test('nur der nächste Lauf: die übrigen Spalten bleiben leer stehen', () => {
        const {cards} = selectBoardCards(board({upcoming: [match('u1')]}))
        expect(cards).toHaveLength(3)
        expect(cards[0].match).toBeNull()
        expect(cards[1].match?.matchId).toBe('u1')
        expect(cards[2].result).toBeNull()
    })

    test('nur der erste kommende Lauf und das erste Ergebnis kommen auf die Bühne', () => {
        const {cards} = selectBoardCards(
            board({upcoming: [match('u1'), match('u2')], results: [result('e1'), result('e2')]}),
        )
        expect(cards[1].match?.matchId).toBe('u1')
        expect(cards[2].result?.matchId).toBe('e1')
    })

    test('jede Spalte hat einen stabilen, eindeutigen Schlüssel', () => {
        const {cards} = selectBoardCards(
            board({running: [match('r1'), match('r2')], upcoming: [match('u1')], results: [result('e1')]}),
        )
        expect(new Set(cards.map(c => c.key)).size).toBe(cards.length)
    })
})

describe('maxBoats', () => {
    test('nimmt das vollste Boot-Feld über alle Spalten', () => {
        const {cards} = selectBoardCards(
            board({running: [match('r1', 3)], upcoming: [match('u1', 7)], results: [result('e1', 5)]}),
        )
        expect(maxBoats(cards)).toBe(7)
    })

    test('leere Bühne ergibt null Boote', () => {
        expect(maxBoats(selectBoardCards(null).cards)).toBe(0)
    })
})

describe('densityScale', () => {
    test('kleines Feld in drei Spalten bleibt in voller Größe', () => {
        expect(densityScale(4, 3)).toBe(1)
    })

    test('mehr Boote verkleinern nie stärker als die Untergrenze', () => {
        expect(densityScale(40, 4)).toBe(MIN_DENSITY_SCALE)
    })

    test('monoton fallend in der Bootszahl', () => {
        for (let boats = 1; boats < 20; boats++) {
            expect(densityScale(boats + 1, 3)).toBeLessThanOrEqual(densityScale(boats, 3))
        }
    })

    test('monoton fallend in der Spaltenzahl', () => {
        expect(densityScale(8, 4)).toBeLessThanOrEqual(densityScale(8, 3))
    })

    test('bleibt immer zwischen Unter- und Obergrenze', () => {
        for (let boats = 0; boats < 30; boats++) {
            for (let columns = 3; columns <= 4; columns++) {
                const scale = densityScale(boats, columns)
                expect(scale).toBeGreaterThanOrEqual(MIN_DENSITY_SCALE)
                expect(scale).toBeLessThanOrEqual(1)
            }
        }
    })
})
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

```bash
cd frontend && npx vitest run src/components/event/info/athleteBoard/boardLayout.test.ts
```

Erwartet: FAIL mit `Failed to resolve import "./boardLayout"`.

- [ ] **Step 3: `boardLayout.ts` schreiben**

Neue Datei `frontend/src/components/event/info/athleteBoard/boardLayout.ts`:

```ts
import {AthleteBoardDto, AthleteBoardMatch, AthleteBoardResult} from '@api/types.gen'

/**
 * Wie viele Läufe in der Arena gleichzeitig auf die Bühne kommen.
 *
 * Auf einer Regatta sind höchstens zwei Läufe gleichzeitig relevant. Eine Anzeige, die auf
 * beliebig viele auslegt, zahlt dafür mit Enge, ohne den Nutzen je zu sehen. Was darüber
 * hinausgeht, verschwindet nicht stumm, sondern wird über [BoardLayout.hiddenRunning] gemeldet.
 */
export const MAX_RUNNING_CARDS = 2

export type BoardCardKind = 'running' | 'upcoming' | 'result'

/**
 * Eine Spalte der Bühne. Genau eines von [match] und [result] ist gefüllt — oder keines, dann
 * steht die Statusspalte leer da und zeigt ihre neutrale Zeile.
 */
export interface BoardCard {
    kind: BoardCardKind
    /** React-Schlüssel; für eine leere Statusspalte der Status selbst. */
    key: string
    match: AthleteBoardMatch | null
    result: AthleteBoardResult | null
}

export interface BoardLayout {
    cards: BoardCard[]
    /** Läufe in der Arena, für die kein Platz war. */
    hiddenRunning: number
}

/**
 * Was auf die Bühne kommt, in fester Reihenfolge: die Läufe in der Arena (höchstens zwei), der
 * nächste Lauf, das letzte Ergebnis.
 *
 * Die drei Statusspalten stehen immer, auch leer. Ein fest montierter Bildschirm soll seine
 * Struktur nicht wechseln, nur weil gerade nichts fährt — wer täglich davorsteht, findet seine
 * Spalte über die Position, nicht über die Überschrift.
 */
export const selectBoardCards = (data: AthleteBoardDto | null): BoardLayout => {
    const running = data?.running ?? []
    const shownRunning = running.slice(0, MAX_RUNNING_CARDS)

    const runningCards: BoardCard[] =
        shownRunning.length > 0
            ? shownRunning.map(match => ({
                  kind: 'running' as const,
                  key: match.matchId,
                  match,
                  result: null,
              }))
            : [{kind: 'running' as const, key: 'running-empty', match: null, result: null}]

    const upcoming = data?.upcoming?.[0] ?? null
    const latest = data?.results?.[0] ?? null

    return {
        cards: [
            ...runningCards,
            {
                kind: 'upcoming',
                key: upcoming?.matchId ?? 'upcoming-empty',
                match: upcoming,
                result: null,
            },
            {
                kind: 'result',
                key: latest?.matchId ?? 'result-empty',
                match: null,
                result: latest,
            },
        ],
        hiddenRunning: running.length - shownRunning.length,
    }
}

const boatsInCard = (card: BoardCard): number =>
    card.match?.teams.length ?? card.result?.teams.length ?? 0

/** Das vollste Boot-Feld auf der Bühne — es bestimmt, wie eng es überall wird. */
export const maxBoats = (cards: BoardCard[]): number =>
    cards.reduce((max, card) => Math.max(max, boatsInCard(card)), 0)

/** Unterhalb dieser Größe wäre der Text aus fünf Metern nicht mehr zu lesen. */
export const MIN_DENSITY_SCALE = 0.55

/** Bis zu so vielen Booten und so vielen Spalten bleibt die Anzeige in voller Größe. */
const BOATS_AT_FULL_SIZE = 4
const COLUMNS_AT_FULL_SIZE = 3

const BOAT_STEP = 0.06
const COLUMN_STEP = 0.09

/**
 * Der Faktor, mit dem alle Schriftgrößen der Bühne multipliziert werden.
 *
 * Das Layout kann baulich nicht überlaufen — die Bootszeilen teilen sich als `1fr` die Höhe, die
 * da ist. Diese Funktion entscheidet nur, wie groß der Text dabei bleibt, damit ein volles Feld
 * nicht in einem Kartenrahmen erdrückt wird. Bewusst ohne Messung im Browser: ein Bildschirm, der
 * tagelang unbeaufsichtigt läuft, soll bei einer Größenänderung nichts neu entscheiden müssen.
 */
export const densityScale = (boats: number, columns: number): number => {
    const forBoats = BOAT_STEP * Math.max(0, boats - BOATS_AT_FULL_SIZE)
    const forColumns = COLUMN_STEP * Math.max(0, columns - COLUMNS_AT_FULL_SIZE)
    return Math.min(1, Math.max(MIN_DENSITY_SCALE, 1 - forBoats - forColumns))
}
```

- [ ] **Step 4: Test laufen lassen und Erfolg bestätigen**

```bash
cd frontend && npx vitest run src/components/event/info/athleteBoard/boardLayout.test.ts
```

Erwartet: PASS, alle Tests grün.

- [ ] **Step 5: `scaled()` in `common.ts` ergänzen**

Am Ende von `frontend/src/components/event/info/athleteBoard/common.ts` anfügen:

```ts
/**
 * Eine Größe, die mit der Dichte der Bühne mitskaliert.
 *
 * `--ab-scale` setzt die Bühne einmal aus [densityScale]; jede Schriftgröße und jeder Abstand der
 * Karten hängt daran. Der Vorgabewert 1 hält die Karten auch außerhalb der Bühne benutzbar
 * (Kiosk-Rotation, künftige Einzelansichten).
 */
export const scaled = (min: string, preferred: string, max: string): string =>
    `calc(var(--ab-scale, 1) * clamp(${min}, ${preferred}, ${max}))`
```

- [ ] **Step 6: Kleinen Test für `scaled` ergänzen**

Ans Ende von `frontend/src/components/event/info/athleteBoard/common.test.ts`, und `scaled` in den Import in Zeile 3 aufnehmen:

```ts
describe('scaled', () => {
    test('haengt die Groesse an die Dichte der Buehne', () => {
        expect(scaled('1rem', '2vw', '3rem')).toBe(
            'calc(var(--ab-scale, 1) * clamp(1rem, 2vw, 3rem))',
        )
    })
})
```

- [ ] **Step 7: Checks laufen lassen**

```bash
cd frontend && npx tsc -b && npm run lint && npm run test
```

Erwartet: alle ohne Fehler.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/components/event/info/athleteBoard
git commit -m "Entscheide Spaltenauswahl und Schriftdichte der Athletenanzeige in reinen Funktionen"
```

---

## Task 3: Die Bühne über die volle Höhe

**Files:**
- Create: `frontend/src/components/event/info/athleteBoard/AthleteBoardColumnCard.tsx`
- Modify: `frontend/src/components/event/info/views/AthleteBoardView.tsx` (Zeilen 73–181 werden ersetzt)
- Modify: `frontend/src/pages/event/AthleteBoardPage.tsx:14`
- Modify: `frontend/src/i18n/{de,en,da}/translations.json` (neu: `moreRunning_one`, `moreRunning_other`)

**Interfaces:**
- Consumes: `selectBoardCards`, `maxBoats`, `densityScale` aus `./boardLayout`; `scaled` aus `./common`.
- Produces: `AthleteBoardColumnCard` mit den Eigenschaften `{title: string; emptyText?: string; children?: ReactNode}`. Tasks 4 und 5 liefern ihre Inhalte als Kinder dieses Rahmens: **genau zwei Elemente** — Kopfblock und Bootsliste —, weil der Rahmen sie in ein `auto 1fr`-Raster stellt.

- [ ] **Step 1: Übersetzungen ergänzen**

In allen drei `translations.json` unter `event.info.athleteBoard` einfügen:

- `de`: `"moreRunning_one": "+{{count}} weiterer Lauf in der Arena"`, `"moreRunning_other": "+{{count}} weitere Läufe in der Arena"`
- `en`: `"moreRunning_one": "+{{count}} more race in the arena"`, `"moreRunning_other": "+{{count}} more races in the arena"`
- `da`: `"moreRunning_one": "+{{count}} løb mere i arenaen"`, `"moreRunning_other": "+{{count}} løb mere i arenaen"`

- [ ] **Step 2: Den Spaltenrahmen schreiben**

Neue Datei `frontend/src/components/event/info/athleteBoard/AthleteBoardColumnCard.tsx`:

```tsx
import {ReactNode} from 'react'
import {Box, Card, Typography} from '@mui/material'
import {scaled} from './common'

interface AthleteBoardColumnCardProps {
    /** Status-Überschrift der Spalte, z.B. "Aktueller Lauf". */
    title: string
    /** Steht anstelle des Inhalts, solange die Spalte nichts zu zeigen hat. */
    emptyText: string
    /**
     * Genau zwei Elemente: Kopfblock und Bootsliste. Der Rahmen stellt sie in ein
     * `auto 1fr`-Raster, damit die Liste den ganzen Rest der Höhe bekommt und ihre Zeilen sich
     * darin teilen können.
     */
    children?: ReactNode
}

/**
 * Der Rahmen einer Spalte auf der Bühne: volle Höhe, Statusüberschrift oben, Inhalt darunter.
 *
 * Die Überschrift sitzt bewusst im Rahmen und nicht im Inhalt — die Statusspalten stehen auch
 * leer, und ein fest montierter Bildschirm soll seine Struktur nicht wechseln, nur weil gerade
 * nichts fährt.
 */
const AthleteBoardColumnCard = ({title, emptyText, children}: AthleteBoardColumnCardProps) => (
    <Card
        variant="outlined"
        sx={{
            height: '100%',
            minHeight: 0,
            overflow: 'hidden',
            display: 'grid',
            gridTemplateRows: 'auto 1fr',
            rowGap: scaled('0.25rem', '0.4vw', '0.6rem'),
            p: scaled('0.5rem', '0.9vw', '1.25rem'),
        }}>
        <Typography
            sx={{
                fontSize: scaled('0.75rem', '1vw', '1.3rem'),
                fontWeight: 700,
                textTransform: 'uppercase',
                letterSpacing: '0.04em',
                lineHeight: 1.2,
            }}
            color="text.secondary">
            {title}
        </Typography>
        <Box
            sx={{
                minHeight: 0,
                display: 'grid',
                gridTemplateRows: 'auto minmax(0, 1fr)',
                rowGap: scaled('0.35rem', '0.6vw', '0.9rem'),
            }}>
            {children ?? (
                <Typography
                    sx={{fontSize: scaled('0.85rem', '1.2vw', '1.3rem')}}
                    color="text.secondary">
                    {emptyText}
                </Typography>
            )}
        </Box>
    </Card>
)

export default AthleteBoardColumnCard
```

- [ ] **Step 3: Die Trägerseite auf `100dvh` stellen**

In `frontend/src/pages/event/AthleteBoardPage.tsx` Zeile 14 ersetzen:

```tsx
        <Box sx={{height: '100dvh', overflow: 'hidden'}}>
```

`100dvh` statt `100vh`, damit auf einem Gerät mit ein-/ausblendenden Leisten nicht ein Stück Bühne unter den Rand rutscht.

- [ ] **Step 4: Die Ansicht auf die Bühne umbauen**

In `frontend/src/components/event/info/views/AthleteBoardView.tsx` den Import-Block ersetzen:

```tsx
import {Box, CircularProgress, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useAthleteBoardData} from '../athleteBoard/useAthleteBoardData'
import {useServerClock} from '../athleteBoard/useServerClock'
import AthleteBoardColumnCard from '../athleteBoard/AthleteBoardColumnCard'
import AthleteBoardMatchCard from '../athleteBoard/AthleteBoardMatchCard'
import AthleteBoardResultCard from '../athleteBoard/AthleteBoardResultCard'
import {densityScale, maxBoats, selectBoardCards} from '../athleteBoard/boardLayout'
import {scaled} from '../athleteBoard/common'
```

(Der bisherige `ReactNode`-Import und der `Card`-Import entfallen.)

Dann alles ab `const column = (` (Zeile 73) bis zum schließenden `)` des `return` (Zeile 180) durch Folgendes ersetzen:

```tsx
    const layout = selectBoardCards(data)
    const scale = densityScale(maxBoats(layout.cards), layout.cards.length)

    const titleFor = (kind: string) =>
        kind === 'running'
            ? t('event.info.athleteBoard.running')
            : kind === 'upcoming'
              ? t('event.info.athleteBoard.upcoming')
              : t('event.info.athleteBoard.results')

    const emptyTextFor = (kind: string) =>
        kind === 'running'
            ? t('event.info.athleteBoard.noRunning')
            : kind === 'upcoming'
              ? t('event.info.athleteBoard.noUpcoming')
              : t('event.info.athleteBoard.noResults')

    return (
        <Box
            sx={{
                height: '100%',
                minHeight: 0,
                display: 'grid',
                gridTemplateRows: 'auto minmax(0, 1fr)',
                rowGap: 'clamp(0.4rem, 0.9vh, 1rem)',
                p: 'clamp(0.5rem, 1vw, 1.5rem)',
                // Ab lg gilt das Scroll-Verbot: die Bühne passt sich der Höhe an, statt
                // überzulaufen. Darunter bleibt die gestapelte Darstellung von früher.
                overflow: {xs: 'auto', lg: 'hidden'},
                '--ab-scale': scale,
                // Höhe der Overlay-Knöpfe (top: 16 + Knopfhöhe) plus Luft
                ...(controlsOverlayed && {pt: '4rem'}),
            }}>
            <Stack
                direction="row"
                justifyContent="space-between"
                alignItems="baseline"
                gap={2}>
                <Typography
                    sx={{fontSize: 'clamp(1rem, 1.8vw, 2rem)', fontWeight: 800}}
                    noWrap>
                    {data?.eventName ?? ''}
                </Typography>
                <Stack alignItems="flex-end" sx={{flexShrink: 0}}>
                    <Typography sx={{fontSize: 'clamp(1rem, 1.8vw, 2rem)', fontWeight: 800}}>
                        {now.toLocaleTimeString(undefined, {hour: '2-digit', minute: '2-digit'})}
                    </Typography>
                    {asOfTime && (
                        <Typography
                            sx={{fontSize: 'clamp(0.65rem, 1vw, 0.85rem)'}}
                            color={stale ? 'warning.main' : 'text.secondary'}>
                            {t('event.info.athleteBoard.asOf', {
                                time: asOfTime.toLocaleTimeString(undefined, {
                                    hour: '2-digit',
                                    minute: '2-digit',
                                }),
                            })}
                            {stale ? ` — ${t('event.info.athleteBoard.stale')}` : ''}
                            {/* Ein gekappter Lauf verschwindet nicht stumm: von einem
                                Anzeigefehler wäre das nicht zu unterscheiden. */}
                            {layout.hiddenRunning > 0
                                ? ` — ${t('event.info.athleteBoard.moreRunning', {count: layout.hiddenRunning})}`
                                : ''}
                        </Typography>
                    )}
                </Stack>
            </Stack>

            {/* Gleich breite Spalten, keine dominante: bei zwei Läufen in der Arena stehen sie
                gleichwertig nebeneinander. Unterhalb lg stapeln sie wie bisher. */}
            <Box
                sx={{
                    minHeight: 0,
                    display: 'grid',
                    gap: scaled('0.4rem', '0.7vw', '1rem'),
                    gridAutoFlow: {xs: 'row', lg: 'column'},
                    gridAutoColumns: {lg: 'minmax(0, 1fr)'},
                }}>
                {layout.cards.map(card => (
                    <AthleteBoardColumnCard
                        key={card.key}
                        title={titleFor(card.kind)}
                        emptyText={emptyTextFor(card.kind)}>
                        {card.match ? (
                            <AthleteBoardMatchCard
                                match={card.match}
                                now={now}
                                variant={card.kind === 'running' ? 'running' : 'upcoming'}
                                showCountdown={data?.showCountdown ?? true}
                            />
                        ) : card.result ? (
                            <AthleteBoardResultCard result={card.result} />
                        ) : undefined}
                    </AthleteBoardColumnCard>
                ))}
            </Box>
        </Box>
    )
}
```

- [ ] **Step 5: Übersetzen — Fehler in den Karten sind an dieser Stelle erwartet**

```bash
cd frontend && npx tsc -b
```

Erwartet: erfolgreich. `AthleteBoardMatchCard` und `AthleteBoardResultCard` haben noch ihren eigenen `<Card>`-Rahmen; die Darstellung ist damit vorübergehend doppelt gerahmt. Das räumen Tasks 4 und 5 auf. Wenn `tsc` hier scheitert, liegt es an einem Tippfehler im obigen Block, nicht an den Karten.

- [ ] **Step 6: Lint und Tests**

```bash
cd frontend && npm run lint && npm run test
```

Erwartet: ohne Fehler.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "Stelle die Athletenanzeige auf eine Buehne ueber die volle Fensterhoehe"
```

---

## Task 4: Die Lauf-Karte ohne eigenen Rahmen

**Files:**
- Modify: `frontend/src/components/event/info/athleteBoard/AthleteBoardMatchCard.tsx` (vollständige Neufassung des `return`-Teils, Zeilen 128–295)

**Interfaces:**
- Consumes: `scaled` aus `./common`, `AthleteBoardColumnCard` als Elternteil (liefert das `auto 1fr`-Raster).
- Produces: `AthleteBoardMatchCard` gibt jetzt ein Fragment mit **genau zwei** Elementen zurück (Kopfblock, Bootsliste) statt einer `<Card>`. Die Eigenschaften (`match`, `now`, `variant`, `showCountdown`) bleiben unverändert.

- [ ] **Step 1: Imports anpassen**

`Card` und `CardContent` aus dem MUI-Import entfernen, `scaled` aus `./common` aufnehmen:

```tsx
import {Box, Chip, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {AthleteBoardMatch} from '@api/types.gen'
import AthleteBoardPenaltyNote from './AthleteBoardPenaltyNote'
import AthleteBoardTeamLabel from './AthleteBoardTeamLabel'
import {
    COUNTDOWN_MAX_SECONDS,
    formatClockTime,
    formatRemaining,
    formatShortDate,
    isSameDay,
    scaled,
} from './common'
```

- [ ] **Step 2: Die Schriftgrößen in `renderRunningStart` und `renderTiming` an die Dichte hängen**

Alle `fontSize: 'clamp(a, b, c)'` in diesen beiden Funktionen (Zeilen 54–126) durch `fontSize: scaled('a', 'b', 'c')` ersetzen, mit denselben drei Werten. Betroffen sind sechs Stellen:

- `'clamp(0.75rem, 1.3vw, 1rem)'` → `scaled('0.75rem', '1.3vw', '1rem')` (viermal: „in Vorbereitung", „gestartet", „erwartet", „in …")
- `'clamp(0.8rem, 1.4vw, 1.1rem)'` → `scaled('0.8rem', '1.4vw', '1.1rem')` („Zeit offen")
- `'clamp(0.7rem, 1.2vw, 0.95rem)'` → `scaled('0.7rem', '1.2vw', '0.95rem')` (Datum)
- `'clamp(1.1rem, 2.4vw, 2rem)'` → `scaled('1.1rem', '2.4vw', '2rem')` (Startzeit)

- [ ] **Step 3: Den abgesagten Lauf ohne eigenen Rahmen zeichnen**

Den `if (match.cancelled)`-Block (Zeilen 133–176) ersetzen:

```tsx
    // Abgesagter Lauf: Er bleibt an seiner geplanten Stelle stehen, statt spurlos zu verschwinden —
    // für eine Besatzung am Steg ist ein verschwundener Lauf nicht von einem Anzeigefehler zu
    // unterscheiden. Gezeigt wird nur noch, worum es ging und wann es hätte sein sollen.
    if (match.cancelled) {
        return (
            <>
                <Stack
                    direction="row"
                    justifyContent="space-between"
                    alignItems="flex-start"
                    gap={1}
                    sx={{opacity: 0.6}}>
                    <Box sx={{minWidth: 0}}>
                        <Typography
                            sx={{
                                fontSize: scaled('1rem', '1.8vw', '1.6rem'),
                                fontWeight: 700,
                                textDecoration: 'line-through',
                            }}
                            color="text.secondary">
                            {[match.competitionName, match.roundName, match.matchName]
                                .filter(Boolean)
                                .join(' · ')}
                        </Typography>
                        <Typography
                            sx={{fontSize: scaled('0.95rem', '1.6vw', '1.4rem'), fontWeight: 600}}
                            color="text.secondary">
                            {t('event.match.status.doesNotTakePlace')}
                        </Typography>
                    </Box>
                    {match.startTime && (
                        <Typography
                            sx={{
                                fontSize: scaled('1.1rem', '2.4vw', '2rem'),
                                fontWeight: 700,
                                lineHeight: 1.1,
                                textDecoration: 'line-through',
                            }}
                            color="text.secondary">
                            {formatClockTime(match.startTime)}
                        </Typography>
                    )}
                </Stack>
                <Box />
            </>
        )
    }
```

Das leere `<Box />` besetzt die zweite Rasterzeile des Rahmens, damit der Kopfblock oben bleibt.

- [ ] **Step 4: Kopfblock und Bootsliste als Fragment zurückgeben**

Den verbleibenden `return`-Block (Zeilen 178–292) ersetzen:

```tsx
    const boats = match.teams.length
    // Nur im laufenden Lauf steht rechts eine Zeit. Im Block "Nächster Lauf" liefert der Server
    // Platz, Zeit und Strafe ohnehin nie — die Spalte entfällt dort strukturell, statt leer
    // mitzulaufen und Breite zu verbrauchen.
    const showLiveResult = variant === 'running'

    return (
        <>
            <Stack direction="row" justifyContent="space-between" alignItems="flex-start" gap={1}>
                <Box sx={{minWidth: 0}}>
                    {/* Programmpunkt (FREE-Slot, z.B. Mittagspause): schlanke, neutrale
                        Darstellung ohne Wettkampf-/Team-Bezug und ohne Interaktion. */}
                    {match.name ? (
                        <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap">
                            <Chip label={t('event.info.freeSlot')} size="small" variant="outlined" />
                            <Typography
                                sx={{fontSize: scaled('1rem', '1.8vw', '1.6rem'), fontWeight: 700}}
                                color="text.secondary">
                                {match.name}
                            </Typography>
                        </Stack>
                    ) : (
                        <>
                            <Typography
                                sx={{fontSize: scaled('1rem', '1.8vw', '1.6rem'), fontWeight: 700}}>
                                {match.competitionName}
                            </Typography>
                            <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap">
                                {match.roundName && (
                                    <Typography
                                        sx={{fontSize: scaled('0.75rem', '1.2vw', '1rem')}}
                                        color="text.secondary">
                                        {match.roundName}
                                    </Typography>
                                )}
                                {match.matchName && match.matchName !== match.roundName && (
                                    <Chip label={match.matchName} size="small" variant="outlined" />
                                )}
                                {match.categoryName && (
                                    <Chip
                                        label={match.categoryName}
                                        size="small"
                                        color="primary"
                                        variant="outlined"
                                    />
                                )}
                            </Stack>
                        </>
                    )}
                </Box>
                {renderTiming()}
            </Stack>

            {match.name ? (
                <Box />
            ) : match.pendingRound ? (
                <Typography
                    sx={{fontSize: scaled('0.95rem', '1.6vw', '1.4rem')}}
                    color="text.secondary"
                    fontStyle="italic">
                    {t('event.info.pendingRound')}
                </Typography>
            ) : (
                // Die Bootszeilen teilen sich die verbleibende Höhe zu gleichen Teilen. Damit kann
                // die Karte nicht überlaufen, ganz gleich wie voll das Feld ist — an die Stelle
                // eines Scrollbalkens tritt die kleinere Schrift aus densityScale().
                <Box
                    sx={{
                        minHeight: 0,
                        display: 'grid',
                        gridTemplateRows: `repeat(${boats}, minmax(0, 1fr))`,
                    }}>
                    {match.teams.map((team, index) => (
                        <Stack
                            key={`${match.matchId}-${team.startNumber ?? index}`}
                            direction="row"
                            alignItems="center"
                            gap={1.5}
                            sx={{
                                minWidth: 0,
                                minHeight: 0,
                                overflow: 'hidden',
                                borderTop: index > 0 ? '1px solid' : 'none',
                                borderColor: 'divider',
                            }}>
                            <Typography
                                sx={{
                                    fontSize: scaled('1.4rem', '2.8vw', '2.8rem'),
                                    fontWeight: 800,
                                    lineHeight: 1,
                                    minWidth: '1.8em',
                                    textAlign: 'center',
                                    flexShrink: 0,
                                }}>
                                {team.startNumber ?? '–'}
                            </Typography>
                            <Box sx={{flex: 1, minWidth: 0}}>
                                <AthleteBoardTeamLabel team={team} />
                                {team.participants.length > 0 && (
                                    // Einzeilig mit Auslassungspunkten: erst dadurch hat eine
                                    // Bootszeile eine berechenbare Höhe. Mit umbrechender Crew
                                    // hinge die Kartenhöhe an der Länge der Nachnamen.
                                    <Typography
                                        noWrap
                                        sx={{fontSize: scaled('0.7rem', '1.1vw', '0.95rem')}}
                                        color="text.secondary">
                                        {team.participants
                                            .map(p => (p.role ? `${p.name} (${p.role})` : p.name))
                                            .join(', ')}
                                    </Typography>
                                )}
                            </Box>
                            {/* Teilergebnis: sobald die Zeitnahme dieses Boot gewertet hat,
                                steht die Zeit hier — der Lauf läuft dabei weiter, bis die
                                Organisation ihn beendet, und eine später ergänzte Zeitstrafe
                                ändert die Zeile beim nächsten Abruf noch. */}
                            {showLiveResult && (team.failed || team.timeString) && (
                                <Stack alignItems="flex-end" sx={{flexShrink: 0, maxWidth: '45%'}}>
                                    <Typography
                                        sx={{
                                            fontSize: scaled('0.9rem', '1.5vw', '1.3rem'),
                                            fontWeight: 600,
                                            textAlign: 'right',
                                        }}
                                        color={team.failed ? 'text.secondary' : 'text.primary'}>
                                        {team.failed
                                            ? (team.failedReason ??
                                              t('event.info.athleteBoard.failed'))
                                            : `${team.place != null ? `${team.place}. ` : ''}${team.timeString}`}
                                    </Typography>
                                    <AthleteBoardPenaltyNote
                                        penaltySeconds={team.penaltySeconds}
                                        penaltyNote={team.penaltyNote}
                                    />
                                </Stack>
                            )}
                        </Stack>
                    ))}
                </Box>
            )}
        </>
    )
```

Die Trennlinien wandern vom `divider`-Prop des `Stack` in einen `borderTop` je Zeile: Ein `Stack` mit `divider` schiebt zusätzliche Elemente ins Raster und würde die `repeat(n, …)`-Zeilen verschieben.

- [ ] **Step 5: Die zweizeilige Grenze für den Mannschaftsnamen setzen**

In `frontend/src/components/event/info/athleteBoard/AthleteBoardTeamLabel.tsx` die `Typography` in Zeile 31 ersetzen:

```tsx
        <Typography
            sx={{
                fontSize: scaled('0.95rem', '1.6vw', '1.4rem'),
                fontWeight: 600,
                // Höchstens zwei Zeilen, danach Auslassungspunkte: ein sehr langer
                // Renngemeinschafts-Name darf die Bootszeile nicht aufblähen.
                display: '-webkit-box',
                WebkitLineClamp: 2,
                WebkitBoxOrient: 'vertical',
                overflow: 'hidden',
            }}
            color={color}>
```

und den Import in Zeile 3 auf `import {TeamWithClubs, scaled, teamLabel} from './common'` erweitern.

- [ ] **Step 6: Checks laufen lassen**

```bash
cd frontend && npx tsc -b && npm run lint && npm run test
```

Erwartet: alle ohne Fehler.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/event/info/athleteBoard
git commit -m "Fuege die Lauf-Karte in die Buehne ein, statt sie eigenstaendig zu rahmen"
```

---

## Task 5: Die Ergebnis-Karte ohne eigenen Rahmen

**Files:**
- Modify: `frontend/src/components/event/info/athleteBoard/AthleteBoardResultCard.tsx` (vollständige Neufassung ab Zeile 26)

**Interfaces:**
- Consumes: `scaled` aus `./common`, `AthleteBoardColumnCard` als Elternteil.
- Produces: `AthleteBoardResultCard` gibt ein Fragment mit genau zwei Elementen zurück. Eigenschaft `result` unverändert.

- [ ] **Step 1: Imports anpassen**

```tsx
import {Box, Chip, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {AthleteBoardResult} from '@api/types.gen'
import AthleteBoardPenaltyNote from './AthleteBoardPenaltyNote'
import AthleteBoardTeamLabel from './AthleteBoardTeamLabel'
import {formatClockTime, scaled} from './common'
```

- [ ] **Step 2: Den `return`-Block ersetzen**

Alles ab `return (` (Zeile 26) bis zum Ende der Komponente ersetzen; die Sortierung darüber bleibt unverändert:

```tsx
    const boats = teams.length

    return (
        <>
            <Stack direction="row" justifyContent="space-between" alignItems="flex-start" gap={1}>
                <Box sx={{minWidth: 0}}>
                    <Typography sx={{fontSize: scaled('1rem', '1.8vw', '1.6rem'), fontWeight: 700}}>
                        {result.competitionName}
                    </Typography>
                    <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap">
                        {result.roundName && (
                            <Typography
                                sx={{fontSize: scaled('0.75rem', '1.2vw', '1rem')}}
                                color="text.secondary">
                                {result.roundName}
                            </Typography>
                        )}
                        {result.matchName && result.matchName !== result.roundName && (
                            <Chip label={result.matchName} size="small" variant="outlined" />
                        )}
                    </Stack>
                </Box>
                {/* Geplanter Start groß, darunter der tatsächliche — so ist eine Verschiebung
                    im Ergebnis noch nachvollziehbar, ohne den Zeitplan zu verstecken. */}
                {result.startTime && (
                    <Stack alignItems="flex-end" sx={{flexShrink: 0}}>
                        <Typography
                            sx={{
                                fontSize: scaled('1.1rem', '2.4vw', '2rem'),
                                fontWeight: 700,
                                lineHeight: 1.1,
                            }}>
                            {formatClockTime(result.startTime)}
                        </Typography>
                        {result.actualStartTime && (
                            <Typography
                                sx={{fontSize: scaled('0.75rem', '1.3vw', '1rem')}}
                                color="text.secondary">
                                {t('event.info.athleteBoard.startedAt', {
                                    time: formatClockTime(result.actualStartTime),
                                })}
                            </Typography>
                        )}
                    </Stack>
                )}
            </Stack>

            <Box
                sx={{
                    minHeight: 0,
                    display: 'grid',
                    gridTemplateRows: `repeat(${boats}, minmax(0, 1fr))`,
                }}>
                {teams.map((team, index) => (
                    <Stack
                        key={`${result.matchId}-${team.startNumber}-${index}`}
                        direction="row"
                        alignItems="center"
                        gap={1.5}
                        sx={{
                            minWidth: 0,
                            minHeight: 0,
                            overflow: 'hidden',
                            borderTop: index > 0 ? '1px solid' : 'none',
                            borderColor: 'divider',
                        }}>
                        {/* Die große Zahl ist der Platz, nicht die Startnummer: an dieser Stelle
                            erwartet eine Besatzung das Ergebnis. Die Startnummer steht klein
                            darunter, damit die Zeile dem Boot zuzuordnen bleibt. */}
                        <Typography
                            sx={{
                                fontSize: scaled('1.4rem', '2.8vw', '2.6rem'),
                                fontWeight: 800,
                                lineHeight: 1,
                                minWidth: '1.8em',
                                textAlign: 'center',
                                flexShrink: 0,
                            }}>
                            {team.place ?? '–'}
                        </Typography>
                        <Box sx={{flex: 1, minWidth: 0}}>
                            <AthleteBoardTeamLabel
                                team={team}
                                color={team.deregistered ? 'text.secondary' : 'text.primary'}
                            />
                            <Typography
                                noWrap
                                sx={{fontSize: scaled('0.7rem', '1.1vw', '0.95rem')}}
                                color="text.secondary">
                                {t('event.info.athleteBoard.startNumber')} {team.startNumber}
                            </Typography>
                        </Box>
                        {/* Ein langer DNF-Grund darf den Vereinsnamen nicht überlagern:
                            rechts bündig in der eigenen Hälfte umbrechen. */}
                        <Stack alignItems="flex-end" sx={{flexShrink: 0, maxWidth: '45%'}}>
                            <Typography
                                sx={{
                                    fontSize: scaled('0.9rem', '1.5vw', '1.3rem'),
                                    fontWeight: 600,
                                    textAlign: 'right',
                                }}
                                color={
                                    team.failed || team.deregistered
                                        ? 'text.secondary'
                                        : 'text.primary'
                                }>
                                {team.deregistered
                                    ? [
                                          t('event.info.athleteBoard.deregistered'),
                                          team.deregisteredReason,
                                      ]
                                          .filter(Boolean)
                                          .join(' · ')
                                    : team.failed
                                      ? (team.failedReason ?? t('event.info.athleteBoard.failed'))
                                      : (team.timeString ?? '')}
                            </Typography>
                            {!team.deregistered && (
                                <AthleteBoardPenaltyNote
                                    penaltySeconds={team.penaltySeconds}
                                    penaltyNote={team.penaltyNote}
                                />
                            )}
                        </Stack>
                    </Stack>
                ))}
            </Box>
        </>
    )
```

- [ ] **Step 3: Checks laufen lassen**

```bash
cd frontend && npx tsc -b && npm run lint && npm run test
```

Erwartet: alle ohne Fehler.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/event/info/athleteBoard
git commit -m "Fuege die Ergebnis-Karte in die Buehne ein und stelle die Startnummer klein daneben"
```

---

## Task 6: Abnahme am laufenden System

Die Darstellung selbst ist im Repository nicht automatisiert prüfbar (es gibt keine Rendering-Tests). Diese Task nimmt sie von Hand ab und behebt, was dabei auffällt.

**Files:**
- Modify: je nach Befund die Dateien aus Tasks 3–5.
- Create: keine.

**Interfaces:**
- Consumes: alles Vorherige.
- Produces: keine neuen Schnittstellen.

- [ ] **Step 1: Alle Checks vollständig laufen lassen**

```bash
cd frontend && npx tsc -b && npm run lint && npm run test
```

```bash
cd backend && ./mvnw -q -o test
```

Erwartet: alles grün. Ergebnisse notieren (Anzahl Tests je Lauf).

- [ ] **Step 2: Die Anwendung starten**

Datenbank und Backend nach dem Muster aus `.claude/launch.json` bzw. `backend/docker-compose.yaml` starten, dann:

```bash
cd frontend && npm run dev
```

Läuft auf Port 5123. Kollidiert der Port oder Port 8080 mit einem anderen Worktree, den Backend-Port über `HTTP_PORT` umbiegen. Ist bereits ein Dev-Server aktiv, prüfen, ob er zu diesem Worktree gehört.

- [ ] **Step 3: Die Bühne bei 2560×1440 im Vollbild durchsehen**

Seite: `/board/<eventId>`. Fensterbreite 2560, Höhe 1440 (im Browser-Werkzeug einstellbar). Für jeden Fall prüfen: **kein vertikaler Scrollbalken**, alle Spalten gleich breit, Text aus der Entfernung lesbar.

| Fall | Wie herstellen | Erwartung |
| --- | --- | --- |
| Ein Lauf je Status | Seed-Zustand | Drei Spalten, je ein Drittel |
| Zwei parallele Läufe | Zweiten Lauf in der Durchführung aktivieren | Vier gleich breite Spalten |
| In Vorbereitung | Lauf aktivieren, nicht starten | „in Vorbereitung" im Kopf der Karte |
| Laufender Lauf mit Zeiten und Strafen | Einzelne Boote werten, Zeitstrafe eintragen | Platz, Zeit und „inkl. … s Zeitstrafe" rechts in der Zeile |
| Abgeschlossener Lauf | Lauf beenden | Ergebnis-Spalte: große Zahl ist der Platz, darunter „Nr. n" |
| Lange Mannschaftsnamen | Renngemeinschaft mit langen Vereinsnamen | Höchstens zwei Zeilen, danach Auslassungspunkte; die Zeile bläht sich nicht auf |
| Volles Feld | Lauf mit sechs bis acht Booten | Schrift kleiner, kein Überlauf |
| Fehlende Daten | Backend anhalten | Letzter Stand bleibt stehen, „Stand …" wird nach zwei Intervallen zur Warnung |
| Unbekannte Veranstaltung | `/board/00000000-0000-0000-0000-000000000000` | Neutrale Meldung, kein Weiterleiten zur Anmeldung |
| Drei Läufe in der Arena | Dritten Lauf aktivieren | Zwei Karten, Kopfzeile trägt „+1 weiterer Lauf in der Arena" |

- [ ] **Step 4: Die Kiosk-Einbindung gegenprüfen**

`/event/<eventId>/info` mit der Athleten-Anzeige als aktiver View öffnen. Erwartung: dieselbe Bühne, die Overlay-Knöpfe verdecken die Uhr nicht (`controlsOverlayed`).

- [ ] **Step 5: Unterhalb `lg` gegenprüfen**

Fensterbreite auf 500 px stellen. Erwartung: Spalten stapeln, die Seite scrollt wieder — die Mobilansicht ist nicht Teil dieses Vorhabens, darf aber nicht kaputt sein.

- [ ] **Step 6: Gefundene Fehler beheben**

Für jeden Befund: kleinste Änderung, danach `npx tsc -b && npm run lint && npm run test`, dann eigener Commit mit deutscher Nachricht, die den behobenen Fall benennt.

- [ ] **Step 7: Abschluss-Commit**

Falls Schritt 6 nichts ergeben hat, entfällt dieser Schritt. Sonst:

```bash
git add -A
git commit -m "<was in der Abnahme auffiel>"
```

---

## Selbstprüfung des Plans

- **Spec-Abdeckung.** Entscheidung 1 (Umbenennung) → Task 1. Auswahl und Priorität → Task 2 + 3. Kein Scrollen durch Bauart → Task 3 (Bühne), 4 und 5 (Kartenraster). Inhalt je Karte → Task 4 (aktuell/nächster, Live-Spalte nur bei `running`) und Task 5 (Platz groß, Startnummer klein). Kopfzeile samt Überlauf-Hinweis → Task 3. Unverändertes → in den Global Constraints festgehalten. Tests → Task 2, Checks und Sichtprüfung → Task 6. Alle Sonderfälle der Spec-Tabelle stehen in der Prüftabelle in Task 6.
- **Namenskonsistenz.** `startNumber` (nicht `lane`) ab Task 1 durchgehend; `selectBoardCards`/`maxBoats`/`densityScale`/`scaled` in Tasks 3–5 genau so benannt wie in Task 2 definiert; `BoardCard.kind`-Werte `'running' | 'upcoming' | 'result'` decken sich mit den Verzweigungen in Task 3.
- **Offene Punkte.** Keine.
