# Design: Lauf-Status statt „aktiv oder nicht"

**Stand:** 2026-08-07
**Status:** Entwurf, freigegeben — Umsetzung offen.
**Branch:** `claude/lauf-status-anzeige-24574a`
**Leitplanke des Auftraggebers:** *„Die bereits getestete Kette nicht anfassen, sondern rein die
Visualisierung verbessern."* Alles unten Beschriebene ist **Lesen und Anzeigen**. Kein neuer
Zustandsübergang, keine geänderte Aktivier-/Beenden-Logik, keine Änderung an
`ScheduleChain.decideNext` oder an `deriveMatchState`.

---

## 1. Problem

Ein Lauf hat im Betrieb sichtbar mehr Zustände, als die Oberflächen zeigen.

**Durchführungsseite** (`CompetitionExecutionRound.tsx:290`, Regattabüro) kennt genau einen:
die Checkbox „Aktuell laufend" plus einen farbigen Rahmen. Der Grund liegt im Backend —
`CompetitionMatchDto` trägt nur `currentlyRunning`, weder `finishedAt` noch `startedAt` noch
den Zeitplan-Slot. Ein beendeter, ein abgesagter und ein noch gar nicht angefasster Lauf sehen
dort identisch aus: „nicht aktiv". Ob ein Lauf seit acht Minuten überfällig ist oder ob vier von
sechs Booten gewertet sind, steht nirgends.

**Zeitplan** (`EventSchedule.tsx:74`) hat zwar eine Status-Spalte, zeigt für einen verknüpften
Lauf aber nur den *Slot*-Zustand `LINKED` („Verknüpft") — eine Aussage über den Plan, nicht über
den Lauf. Nur `matchFinishedAt` schlägt durch.

**Öffentliche Anzeigen** (Athleten-Anzeige, Kiosk) arbeiten mit Blöcken `running`/`upcoming`/
`results`. Abgesagte Läufe werden dort **still herausgefiltert**
(`EventInfoService.mergeWithPendingPlaceholders:248`). Für eine Besatzung, die am Steg auf ihren
Lauf wartet, ist ein spurlos verschwundener Lauf nicht von einem Anzeigefehler zu unterscheiden.

**Das Vokabular existiert bereits** — nur an einer einzigen Stelle: das Schiedsrichter-Dashboard
mit `LiveDashboardMatchState` (RUNNING, FINISHED, SKIPPED, AWAITING_FINISH, UPCOMING,
UNSCHEDULED), abgeleitet in `LiveDashboardLogic.deriveMatchState:68` und durch
`LiveDashboardLogicTest` festgenagelt. Die anderen drei Oberflächen erreichen es nicht.

**Nicht Teil des Problems:** Das Schiedsrichter-Dashboard selbst. Es zeigt den Status bereits
vollständig und bleibt unverändert.

## 2. Zielbild

Ein Status-Vokabular, vier Oberflächen, eine Ableitung.

| Chip | Bedingung | Farbe |
|---|---|---|
| Anstehend | `UPCOMING`, Startzeit noch nicht (deutlich) verstrichen | grau |
| Überfällig · *n* min | `UPCOMING`, Startzeit > 5 min her | rot |
| Läuft · *n* min | `RUNNING` | blau |
| Teilweise gewertet *n*/*m* | nicht aktiv, nicht beendet, 0 < gewertet < alle | orange |
| Wartet auf Beenden | `AWAITING_FINISH` | orange |
| Beendet | `FINISHED` (`finished_at` gesetzt) | grün |
| Abgesagt | `SKIPPED` | grau, durchgestrichen |
| Ungeplant | `UNSCHEDULED` | grau |

Dazu ein zweiter, leiser Chip **Wasser *n*/*m*** — nur auf der Durchführungsseite und nur solange
er etwas aussagt (Lauf steht an oder läuft und nicht alle Crews sind ausgecheckt).

„Boote auf dem Wasser" ist bewusst **kein** eigener Zustand geworden: es ist eine Eigenschaft des
Vorbereitungsstands, keine Phase des Laufs, und würde als Zustand mit „Läuft" konkurrieren.

### Verteilung auf die Oberflächen

- **Durchführungsseite:** Chip je Lauf-Karte + Wasser-Chip + Zählerleiste über der Runde
  („1 läuft · 1 offen · 3 beendet · 1 abgesagt").
- **Zeitplan:** derselbe Chip in der vorhandenen Status-Spalte, **sofern verknüpft**
  (`matchId` gesetzt). Programmpunkte und wartende Runden behalten ihren bisherigen Slot-Chip.
  Kein Wasser-Chip — die Liste umfasst die ganze Veranstaltung.
- **Öffentliche Anzeigen:** grob, vier Zustände — Anstehend · Läuft · Ergebnisse da · Abgesagt.
  Teilwertung und Wasserstand bleiben intern; Zuschauer würden ein Teilergebnis als Ergebnis lesen.

## 3. Modell

### 3.1 Keine neuen Enum-Werte

`LiveDashboardMatchState` bleibt **unverändert**. „Überfällig" und „Teilweise gewertet" kommen
nicht als neue Enum-Werte dazu, sondern als **zusätzliche Felder**. Der Grund ist die Leitplanke:
`selectForScope`, `matchControls`, `dashboardMatchState` und `LiveDashboardLogicTest` verzweigen
über diese Aufzählung. Ein neuer Wert darin würde still durch jedes `when`/`switch` in den
`else`-Zweig fallen und genau die getestete Kette verschieben.

Der Chip-Text entsteht stattdessen im Frontend aus `state` **plus** Qualifizierern:

```kotlin
data class MatchStatusDto(
    val state: MatchState,      // typealias auf LiveDashboardMatchState
    val startedAt: LocalDateTime?,
    val teamsTotal: Int,
    val teamsScored: Int,
    /** null = in dieser Ansicht nicht erhoben (Zeitplan, öffentliche Anzeigen). */
    val teamsOnWater: Int?,
)
```

`Teilweise gewertet` ist damit kein Zustand, sondern die Ablesung
`state != RUNNING && 0 < teamsScored < teamsTotal`. `Überfällig` ist
`state == UPCOMING && startTime + 5 min < jetzt`.

**Bewusst nicht getan:** `LiveDashboardMatchState` in `MatchState` umbenennen. Der Name ist
schief, sobald der Zeitplan ihn führt, aber die Umbenennung zöge `documentation.yaml`,
`types.gen.ts`, vier Frontend-Module, den i18n-Pfad `event.liveDashboard.state.*` und den
Dashboard-Test hinter sich her — Namenskosmetik auf getestetem Code, gegen die Leitplanke. Ein
Kotlin-`typealias MatchState` im gemeinsamen Modul gibt neuem Code den richtigen Namen, ohne die
Leitung anzufassen.

### 3.2 Eine Ableitung, drei Aufrufer

Neues Modul `app/matchStatus/`:

- `entity/MatchStatusDto.kt` — das DTO oben, plus `typealias MatchState`.
- `boundary/MatchStatusLogic.kt` — reine Funktionen, ohne Datenbank:
  - `scoredCount(teams)` — zählt über `LiveDashboardLogic.teamHasResult` (abgemeldet, Platz oder
    ausgeschieden gilt als gewertet — dieselbe Regel wie im Dashboard).
  - `roundCounters(statuses)` — die Zähler der Rundenleiste.

`deriveMatchState` bleibt, wo es ist. `MatchStatusLogic` ruft es auf. Die Abhängigkeit
matchStatus → liveDashboard ist der Preis dafür, die getestete Funktion nicht zu verschieben;
sie ist explizit dokumentiert.

**Überfällig und verstrichene Zeit rechnet das Frontend**, aus `startTime`/`startedAt` gegen die
Browseruhr. Beide Ansichten ticken bereits (`useNow` in `EventSchedule.tsx`), und so zählt der
Chip zwischen zwei Polls weiter, statt zu stehen. Nutzer sind intern und am selben Ort — die
Browseruhr genügt. Schwelle: `OVERDUE_GRACE_MINUTES = 5`; zwei Minuten Verzug sind Regattaalltag
und sollen nicht rot leuchten.

## 4. Datenwege

### 4.1 Durchführungsseite

`CompetitionMatchDto` bekommt `finishedAt`, `startedAt`, `skipped` und `status: MatchStatusDto`.

Die Daten kommen über die View `competition_match_with_teams` (`afterMigrate.sql:824`), die heute
nur `start_time` und `currently_running` führt:

- `started_at`, `finished_at` aus `competition_match` ergänzen (beide Spalten existieren seit
  `V202608061205__event_schedule_slot.sql`).
- `skipped` per `left join event_schedule_slot` auf den Slot des Setup-Matches, Bedingung
  `skipped_at is not null`.

Views liegen in `afterMigrate.sql` und werden bei jedem Flyway-Lauf neu erzeugt — **keine neue
Migration nötig**. `V202608062100` bleibt die höchste Versionsnummer.

Der Wasser-Chip braucht eine eigene, neue Abfrage in `CompetitionMatchRepo`: je Lauf-Team der
letzte `participant_tracking`-Scan seiner Crew, ausgewertet mit dem vorhandenen
`LiveDashboardLogic.teamOnWaterAt` (alle Crew-Mitglieder zuletzt `EXIT` → auf dem Wasser). Die
Abfrage läuft je Wettkampf, nicht je Veranstaltung, und wird nur für die Durchführungsseite
gestellt.

### 4.2 Zeitplan

`EventScheduleSlotDto` führt bereits `matchId`, `matchStartedAt`, `matchFinishedAt`,
`matchCurrentlyRunning`. Es fehlen `matchTeamsTotal`, `matchTeamsScored` und `matchSkipped`
(letzteres ist bereits über `slot.state == SKIPPED` ablesbar und wird **nicht** dupliziert).

Die Zählungen kommen als korrelierte Unterabfrage in der vorhandenen Slot-Abfrage
(`EventScheduleRepo.getSlots`) — eine Abfrage bleibt eine Abfrage.

`stateChipProps` in `EventSchedule.tsx:74` wird umgebaut: ist `matchId` gesetzt, entscheidet der
Lauf-Status; sonst bleibt alles wie bisher.

### 4.3 Öffentliche Anzeigen

Der eine Filter in `EventInfoService.mergeWithPendingPlaceholders:248`
(`real.filterNot { it.matchId in skippedMatchIds }`) wird zur Markierung:

- Abgesagte Läufe **bleiben in der Liste**, bekommen `cancelled = true` und werden dabei
  **ihrer Mannschaften entledigt** (`teams = emptyList()`). Auf der Anzeige steht nur noch
  Wettkampf · Runde · Lauf, die geplante Zeit und der Hinweis „Findet nicht statt".
- Sie stehen an ihrer geplanten Stelle im Block „nächste Läufe" — dort, wo die Besatzung ihren
  Lauf sucht.
- Die vorhandene Nachfrist (`isStillUpcoming`, 30 min) räumt sie von selbst wieder ab.

`AthleteBoardMatch` bekommt dafür genau ein Feld: `cancelled: Boolean = false`. Die anderen drei
öffentlichen Zustände ergeben sich unverändert aus den Blöcken `running`/`upcoming`/`results`.

Das gilt für **beide** öffentlichen Ansichten — Kiosk und Athleten-Anzeige laufen durch dieselbe
Funktion. Das ist gewollt: eine Regel, ein Ort.

## 5. Frontend

Neues Modul `frontend/src/components/event/match/matchStatusChip.ts` — reine Funktionen, ohne
Rendering, damit die Ableitung ohne DOM prüfbar bleibt (dasselbe Muster wie
`liveDashboard/common.ts`):

```ts
export type MatchChip = {labelKey: string; values?: object; color: ChipColor; strikeThrough?: boolean}
export const matchStatusChip = (status: MatchStatusDto, startTime: string | null, now: Date): MatchChip
export const waterChip = (status: MatchStatusDto): MatchChip | null
export const roundCounterChips = (statuses: MatchStatusDto[], ...): MatchChip[]
```

Verbraucher:

- `CompetitionExecutionRound.tsx` — Chip oben rechts auf der Karte, Wasser-Chip daneben,
  Zählerleiste über der Kartenliste. Checkbox und Rahmen bleiben, wie sie sind.
- `EventSchedule.tsx` — `stateChipProps` delegiert bei verknüpften Slots an `matchStatusChip`.
- Athleten-Anzeige/Kiosk — eigene, schlanke Darstellung für `cancelled`, kein gemeinsamer Chip:
  die öffentlichen Anzeigen haben ihre eigene Typografie und sollen nur vier Zustände kennen.

i18n: neuer Zweig `event.match.status.*` in allen drei Sprachdateien (de, en, da). Der vorhandene
Zweig `event.liveDashboard.state.*` bleibt unangetastet — das Dashboard wird nicht angefasst.

## 6. Fehlerfälle

- **Lauf ohne Mannschaften** (`teamsTotal == 0`): kein Teilwertungs-Chip, kein Wasser-Chip.
  `deriveMatchState` behandelt diesen Fall bereits (`teamResults.isNotEmpty()`), die neue
  Ableitung darf ihn nicht anders sehen.
- **Abgesagt und trotzdem aktiv:** `deriveMatchState` gibt hier `RUNNING` zurück — was passiert,
  schlägt den zurückgenommenen Plan. Der Chip folgt dieser Reihenfolge unverändert.
- **Startzeit fehlt** (`UNSCHEDULED`): niemals „Überfällig" — ohne Plan gibt es keinen Verzug.
- **Uhr des Browsers geht falsch:** betrifft nur „Überfällig" und die verstrichenen Minuten,
  beides Anzeige. Der Zustand selbst kommt vom Server.
- **`participant_tracking` leer** (Veranstaltung ohne Check-in): `teamsOnWater` ist dann für jeden
  Lauf 0. Der Wasser-Chip darf in diesem Fall nicht dauerhaft rot stehen — er entfällt, wenn
  **kein** Team der Runde je einen Scan hatte.

## 7. Tests

Neu, ohne Datenbank und ohne Rendering:

- `MatchStatusLogicTest` (Kotlin) — `scoredCount` inklusive Abmeldungen und Ausscheidungen,
  `roundCounters`.
- `matchStatusChip.test.ts` — jede Zeile der Tabelle aus Abschnitt 2, dazu die Fehlerfälle aus
  Abschnitt 6 (0 Mannschaften, fehlende Startzeit, abgesagt+aktiv, Wasser ohne Scans).

Bestehend und **unverändert lauffähig zu halten** — sie sind die Absicherung der Leitplanke:
`LiveDashboardLogicTest`, `liveDashboard/common.test.ts`, `timelineIndicator.test.ts`,
`EventScheduleLogicTest`, `ScheduleChainTest`.

## 8. Reihenfolge

1. Backend gemeinsames Modul (`MatchStatusDto`, `MatchStatusLogic`) + Test.
2. View `competition_match_with_teams` erweitern, `CompetitionMatchDto` + Conversions,
   `documentation.yaml`, `npm run generate`.
3. Zeitplan-Backend: Zählungen in `EventScheduleRepo.getSlots`, `EventScheduleSlotDto`.
4. Öffentliche Anzeigen: `cancelled` statt Filter.
5. Frontend `matchStatusChip.ts` + Test.
6. Frontend-Verbraucher: Durchführungsseite, Zeitplan, öffentliche Anzeigen, i18n (de/en/da).
7. Wasser-Chip: eigene Abfrage + Anzeige. **Zuletzt und abtrennbar** — es ist der einzige Teil
   mit neuer Abfragelast. Wird er teuer, fällt er ohne Folgen für den Rest weg.

## 9. Offen

- Abnahme am laufenden System steht aus (Seed `seed-foerde.sql`, Start über `launch.json`).
- Ob die Rundenleiste auch bei einer einzigen Runde mit einem Lauf erscheinen soll, entscheidet
  sich am Gerät — Vorschlag: erst ab zwei Läufen, sonst ist sie Rauschen.
