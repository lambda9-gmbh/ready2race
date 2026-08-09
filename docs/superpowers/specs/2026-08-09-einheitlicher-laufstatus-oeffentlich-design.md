# Der Laufstatus erreicht die öffentliche Ergebnisanzeige

Entwurf vom 09.08.2026. Branch `claude/ready2race-unified-run-status-9c32dd`, mündet in
`feature/crf-2026`.

## Warum

Ein Lauf hat sieben fachliche Zustände — anstehend, ohne Termin, in Vorbereitung, läuft,
Ergebnisse vollständig (wartet auf Beenden), beendet, abgesagt. Vier Oberflächen zeigen sie
inzwischen aus **einer** Ableitung
([`LiveDashboardLogic.deriveMatchState`](../../../backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/boundary/LiveDashboardLogic.kt)):
Durchführung, Schiedsrichter-Dashboard, Zeitplan und Athletenanzeige.

Die fünfte kennt sie nicht. Der Tab „Live" der öffentlichen Ergebnisanzeige
(`ResultsLiveMatches` → `GET /event/{id}/info/running-matches`) zeigt genau die Läufe mit
`activated_at is not null`, ohne jede Statusangabe, und lädt genau einmal.

Für einen Zuschauer am Ufer heißt das dreierlei:

1. Ein Lauf, der gleich dran ist, steht nirgends — der Tab ist leer, bis jemand im Büro
   aktiviert.
2. „In Vorbereitung" und „Läuft" sehen identisch aus. Die Karte trägt Wettkampf, Runde und
   geplante Zeit, sonst nichts.
3. Was beim Öffnen der Seite galt, gilt eine halbe Stunde später noch. Wer den Statuswechsel
   sehen will, muss neu laden.

## Nicht Gegenstand

- **Keine neue Statusableitung.** `deriveMatchState` bleibt die einzige Stelle, an der die
  Zweig-Reihenfolge festgelegt ist; `MatchStatusLogic` ruft sie auf. Dieser Entwurf fügt
  ausschließlich einen weiteren Leser hinzu.
- **Keine neuen Werte in `MatchState`.** Begründung unverändert in
  `2026-08-07-lauf-status-anzeige-design.md`, Abschnitt 3.1.
- **Kein Eingriff in die Ergebnisfreigabe.** `PublicResultsVisibility` und
  `AthleteBoardLogic.isPublicResult` bleiben Wort für Wort, wie sie sind.
- **Keine Umstellung der Athletenanzeige.** Sie liest bereits `AthleteBoardMatch.state` und
  zeigt ihn in ihrer eigenen Großschrift („In Vorbereitung" / „gestartet 14:32"). Ein
  MUI-Chip in 3-vw-Typografie wäre auf einem Steg-Bildschirm ein Fremdkörper; die
  Ableitung ist dort ohnehin schon die gemeinsame.
- **Kein Umbau von `useAthleteBoardData`.** Der Hook trägt Eigenheiten der Anzeige
  (servergesteuerter Takt, Abbruch nach 404, `serverTime`) und liegt auf dem Pfad, der am
  Renntag trägt. Der neue Takter steht daneben; das Zusammenlegen ist eine eigene Aufgabe.

---

## 1. Backend

### 1.1 Ein neuer öffentlicher Endpoint

`GET /event/{eventId}/info/live-matches?limit=` — im vorhandenen `rateLimit("publicInfo")`-Block
neben `upcoming-matches`, `latest-match-results` und `running-matches`, also ohne
`authenticate`.

Ein **neuer** Endpoint statt eines Schalters an `running-matches`: dieser Endpoint bedient auch
den Block `running` der Athletenanzeige (`EventInfoService.getAthleteBoard`). Anstehende Läufe
dort hineinzumischen würde die Blocktrennung der Anzeige zerstören, auf die ihre ganze
Darstellung aufbaut.

### 1.2 Das DTO

`eventInfo/entity/LiveMatchInfo.kt`:

```kotlin
data class LiveMatchInfo(
    val matchId: UUID,
    val competitionId: UUID,
    val competitionName: String,
    val categoryName: String?,
    val roundName: String?,
    val matchName: String?,
    /** Geplanter Start aus dem Zeitplan. */
    val startTime: LocalDateTime?,
    /** Der Zustand aus `MatchStatusLogic.matchStatus` — dieselbe Ableitung wie überall sonst. */
    val status: MatchStatusDto,
    val executionOrder: Int,
    /** Abgesagt: der Lauf bleibt an seiner Stelle stehen, [teams] ist dann leer. */
    val cancelled: Boolean = false,
    /** Platzhalter für eine noch nicht erzeugte Runde; [teams] ist dann leer. */
    val pendingRound: Boolean = false,
    /** Name eines Programmpunkts (FREE-Slot), nur bei erlaubten Pausen gefüllt. */
    val name: String? = null,
    val teams: List<RunningMatchTeamInfo> = emptyList(),
)
```

`teams` nutzt `RunningMatchTeamInfo` für beide Zweige. Für einen anstehenden Lauf bleiben
`currentPosition`, `timeString`, `penaltySeconds`, `penaltyNote` und `failed` leer — nicht weil
sie unterschlagen werden, sondern weil die Quelle sie gar nicht führt (siehe 1.4).

### 1.3 Die zwei Quellen

`EventInfoService.getLiveMatches(eventId, limit)` führt zwei bereits erprobte Abfragen zusammen:

| Zweig | Quelle | liefert |
|---|---|---|
| aktiviert | `CompetitionMatchRepo.getRunningMatches` | PREPARING, RUNNING — mit Teilergebnissen wie heute |
| anstehend | `EventInfoService.getUpcomingMatchesForBoard` (privat, inkl. `mergeWithPendingPlaceholders`) | UPCOMING, UNSCHEDULED, abgesagte markiert, wartende Runden und Programmpunkte als Platzhalter |

Der zweite Zweig bringt die 30-Minuten-Nachfrist, die Absage-Markierung und die
Pausen-Einstellung `showBreaksOnPublicBoards` unverändert mit. Das ist der eigentliche Grund,
ihn zu benutzen statt eine dritte Abfrage zu schreiben: eine Regel, ein Ort.

`status` entsteht in **beiden** Zweigen ausschließlich über
`MatchStatusLogic.matchStatus(...)`, das `deriveMatchState` aufruft. Für den anstehenden Zweig
mit `activatedAt = null`, `startedAt = null`, `finishedAt = null`, `skipped = cancelled` und
leeren Team-Ergebnissen; für den aktivierten Zweig mit den echten Zeitstempeln und den
Teilergebnissen der Boote.

### 1.4 Die Ergebnisfreigabe bleibt unangetastet — strukturell, nicht per Prüfung

`CompetitionMatchRepo.getUpcomingMatchesForBoard` schließt per SQL aus:

- `activated_at is not null` (deckt der andere Zweig ab),
- `finished_at is not null` — **beendete Läufe**,
- „es gibt kein Boot mehr ohne Platz, das nicht ausgeschieden und nicht abgemeldet ist" —
  **vollständig gewertete Läufe** (`AWAITING_FINISH`).

Und `CompetitionMatchTeamRepo.getTeamsForUpcomingMatch` liefert weder Platz noch Zeit.

Ein Lauf, den `PublicResultsVisibility` zurückhalten soll, kann in diesem Zweig damit gar nicht
erst entstehen. Der Schutz hängt nicht an einer zusätzlichen Bedingung, die jemand vergessen
könnte, sondern an der Auswahl selbst. Ein Test in `LiveMatchesLogicTest` nagelt fest, dass ein
Lauf im Zustand `FINISHED` oder `AWAITING_FINISH` nicht in die Liste gerät.

`getLatestMatchResults` mit seiner `visibility`-Bedingung bleibt unverändert die einzige Quelle
für veröffentlichte Ergebnisse.

### 1.5 Die Zusammenführung als reine Funktion

`eventInfo/boundary/LiveMatchesLogic.kt`:

```kotlin
fun merge(activated: List<LiveMatchInfo>, upcoming: List<LiveMatchInfo>, limit: Int): List<LiveMatchInfo>
```

- **Doppelte fallen weg, der aktivierte gewinnt.** Die zwei Abfragen laufen nacheinander;
  wird ein Lauf zwischen ihnen aktiviert, steht er in beiden Listen. Der aktivierte Eintrag
  trägt die frischere Aussage.
- **Reihenfolge:** erst die aktivierten (nach `startTime` aufsteigend, `null` zuletzt, bei
  Gleichstand nach `executionOrder`), dann die anstehenden nach derselben Regel. Wer die Seite
  öffnet, sucht zuerst, was gerade passiert.
- **Deckel:** `take(limit)` über das Ganze, nicht je Zweig — sonst verdrängen zwanzig
  anstehende Läufe die eine laufende.

Ohne Datenbank und ohne Uhr prüfbar; genau das ist der Zweck der Trennung.

---

## 2. Frontend

### 2.1 Derselbe Chip, nicht ein ähnlicher

Der Renderer, der heute privat in `CompetitionExecutionRound.tsx` steht („ein `MatchChip` als
MUI-Chip"), wandert nach `components/event/match/StatusChip.tsx` und wird von der
Durchführungsseite **und** der Ergebnisanzeige benutzt. Er übersetzt nur und malt; welcher Chip
es ist, entscheidet weiterhin ausschließlich `matchStatusChip.ts`.

`ResultsMatchCard` bekommt eine optionale `statusChip`-Angabe und zeigt sie oben rechts, neben
der geplanten Zeit. Die Karte bleibt sonst, wie sie ist — sie trägt auch die
Ergebnis-Ansicht (`LatestMatchResultInfo`), die keinen Statuschip bekommt.

Zeitplan und Schiedsrichter-Dashboard rendern ihre Chips innerhalb eigener Layouts weiter
selbst; sie lesen dieselbe `matchStatusChip`-Entscheidung, und ihr Umbau brächte nichts als
Bewegung auf getestetem Code.

### 2.2 Abgesagte Läufe und Platzhalter

- `cancelled`: Karte durchgestrichen und abgeblendet, Chip „Abgesagt", keine Mannschaften, nicht
  anklickbar — der Dialog hätte nichts zu zeigen.
- `pendingRound`: Hinweis „Aufstellung steht noch nicht fest" statt der Mannschaftsliste,
  ebenfalls nicht anklickbar.
- `name` (Programmpunkt): schlanke, neutrale Zeile ohne Wettkampfbezug, wie auf der
  Athletenanzeige.

### 2.3 Texte

`results.liveMatches` heißt heute „Laufende Partien" und „Momentan finden keine Partien statt."
Beides wird mit den anstehenden Läufen falsch, und „Partie" ist im Rudern das falsche Wort.
Neu (de): `title` „Läuft und steht an", `noMatches` „Zurzeit ist kein Lauf angesetzt.",
`stale` „Stand von {{time}}". en und da ziehen mit.

Die Statuslabels selbst kommen aus dem vorhandenen Zweig `event.match.status.*` — kein zweiter
Satz Wörter für dieselben Zustände.

---

## 3. Die Hintergrundaktualisierung

### 3.1 Warum ein eigenes Modul

Das Frontend hat kein jsdom und kein testing-library; `vite.config.ts` sammelt
`src/**/*.test.ts`, und alle vorhandenen Tests prüfen reine Funktionen. Ein React-Hook wäre
damit ungeprüft.

Deshalb: die Logik in `utils/polling.ts` als `createPoller` — pures TypeScript, kein React, mit
Vitest-Faketimers direkt prüfbar. Der Hook `utils/usePolledFetch.ts` ist nur noch Verdrahtung.
Dasselbe Muster wie `matchStatusChip.ts` neben `CompetitionExecutionRound.tsx`.

### 3.2 Was der Takter leistet

```ts
type PollerState<T> = {
    data: T | null          // letzter erfolgreicher Stand, bleibt bei Fehlern stehen
    lastUpdated: Date | null
    initialLoad: boolean    // vor dem ersten Abschluss
    failed: boolean         // letzter Versuch fehlgeschlagen
}
createPoller<T>({load, intervalMs, onState}): {start, stop, refreshNow, suspend, resume}
```

- **Kein Überlappen.** Höchstens ein Abruf ist unterwegs; der Timer für den nächsten startet
  erst, wenn der vorige abgeschlossen ist. Kein `setInterval` — ein hängender Abruf über einer
  langsamen Mobilfunkverbindung würde sonst eine Warteschlange aufbauen.
- **Ein `refreshNow` während eines laufenden Abrufs bricht diesen ab** (`AbortController`) und
  startet neu; der abgebrochene Abruf gilt nicht als Fehler und rührt weder Timer noch Zustand
  an.
- **Fehler behalten den Stand.** `data` bleibt stehen, `failed` wird gesetzt. Eine Seite, die
  nach einem Funkloch leer wird, ist der schlechteste Ausgang.
- **Antwort ohne Nutzdaten** (HTTP 500) zählt als Fehlversuch, nicht als leeres Ergebnis.
- **Getaktet wird nur im Vordergrund** (`suspend`/`resume`).

Takt: 15 Sekunden, dieselbe Größenordnung wie die Athletenanzeige.

### 3.3 Der Hook

`usePolledFetch` startet den Takter beim Einhängen, hält ihn beim Aushängen an und meldet:

- `visibilitychange`: versteckt → `suspend`, sichtbar → `resume` (mit sofortigem Abruf)
- `online`: `refreshNow` — nach einem Funkloch soll der Stand nicht bis zum nächsten Takt alt
  bleiben

`ResultsLiveMatches` zeigt den Throbber nur noch bei `initialLoad`. Danach bleiben die Karten
stehen und aktualisieren sich still; `failed && data != null` blendet die Zeile „Stand von hh:mm"
ein. Die Fehlermeldung über `feedback.error` entfällt — im Sekundentakt wäre sie eine
Lawine.

---

## 4. Fehlerfälle

- **Lauf ohne Mannschaften:** `teamsTotal == 0`, kein Teilwertungs-Chip. `deriveMatchState`
  behandelt das bereits über `teamResults.isNotEmpty()`.
- **Aktiviert und trotzdem abgesagt:** der aktivierte Zweig gewinnt beim Zusammenführen, der
  Chip zeigt „Läuft" — dieselbe Reihenfolge wie in `deriveMatchState`.
- **Startzeit fehlt:** UNSCHEDULED, niemals „Überfällig".
- **Uhr des Geräts geht falsch:** betrifft nur die verstrichenen Minuten auf dem Chip. Der
  Zustand kommt vom Server.
- **Veranstaltung ohne Zeitplan:** beide Zweige leer, `noMatches` statt einer leeren Liste.
- **Erster Abruf scheitert:** `initialLoad` ist vorbei, `data` ist null, `failed` gesetzt — die
  Seite sagt „konnte nicht geladen werden" statt „kein Lauf angesetzt". Der Unterschied ist der
  ganze Grund für das Feld.

---

## 5. Tests

**Backend** (ohne Datenbank):

- `MatchStatusLogicTest` erweitern: `matchStatus()` für alle sieben Zustände, jeweils aus den
  Rohwerten, die die beiden Zweige liefern.
- neu `LiveMatchesLogicTest`:
  - Reihenfolge (aktiviert vor anstehend, innerhalb nach Startzeit, `null` zuletzt),
  - Doppelte fallen weg und der aktivierte Eintrag gewinnt,
  - der Deckel greift über beide Zweige,
  - **Freigabe-Schutz:** ein Lauf im Zustand `FINISHED` oder `AWAITING_FINISH` erscheint nicht.

**Frontend:**

- neu `polling.test.ts` (Faketimers): Takt läuft; zwei Abrufe überlappen nie; ein Fehler behält
  `data` und setzt `failed`; nach `refreshNow` ist `failed` wieder falsch; `suspend` hält den
  Takt an, `resume` löst sofort einen Abruf aus; ein abgebrochener Abruf zählt nicht als Fehler.
- `matchStatusChip.test.ts` deckt die Chips bereits vollständig ab und bleibt unverändert
  lauffähig — er ist die Absicherung dafür, dass die fünfte Oberfläche nichts anderes zeigt.

**Checks:** `./mvnw test` (mit gesetztem `JAVA_HOME`), `npm run generate`, `npm run test`,
`npm run lint`, `npm run build`.

---

## 6. Reihenfolge

1. Backend: `LiveMatchInfo`, `LiveMatchesLogic`, `Conversions`, `EventInfoService.getLiveMatches`,
   Route, `documentation.yaml`.
2. Backend-Tests grün.
3. `npm run generate` — `types.gen.ts`/`sdk.gen.ts`.
4. Frontend: `StatusChip.tsx` herauslösen, Durchführungsseite darauf umstellen (unverändertes
   Verhalten).
5. Frontend: `polling.ts` + Test, `usePolledFetch.ts`.
6. Frontend: `ResultsLiveMatches` und `ResultsMatchCard`, Übersetzungen de/en/da.
7. `npm run test`, `npm run lint`, `npm run build`.

Schritt 4 und 5 sind voneinander unabhängig und können parallel laufen. Schritt 6 braucht
beide.

## 7. Offen

Die Abnahme am laufenden System steht aus (Seed `seed-foerde.sql`, Start über `launch.json`):
Lauf aktivieren und den Chip im Live-Tab von „Anstehend" über „In Vorbereitung" nach „Läuft"
wandern sehen, ohne die Seite neu zu laden.
