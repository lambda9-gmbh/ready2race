# Folgerunden automatisch erzeugen

Entwurf vom 09.08.2026. Branch `claude/ready2race-auto-follow-rounds-7ab377`.

## Problem

Nach jeder abgeschlossenen Runde muss heute jemand im Durchführungs-Tab „Nächste Runde
erstellen" drücken. Am Renntag ist das die Stelle, an der die Regatta wartet: Die Boote der
nächsten Runde stehen erst im System, wenn das Regattabüro den Knopf gefunden hat — und der
Zeitstrahl kann die Läufe erst dann an den Start rufen, weil es sie vorher nicht gibt
(`EventScheduleSlotState.WAITING`).

Die Rechnung selbst ist fertig und getestet. Es fehlt nur der Auslöser.

## Kernbefund: keine Formatunterscheidung nötig

`CompetitionExecutionService.createNewRound` ist bereits formatunabhängig. Es liest die Kette aus
`competition_setup_round` (`next_round`, `required`, `is_qualification`) und verteilt die
Mannschaften über `competition_setup_participant.seed`. K.-o.-Baum, Vorrunde → Zwischenrunde →
Finale, Qualifikation mit Freilosen und die Einzelrunde ohne Folgerunde sind allesamt nur
Konfigurationen derselben Kette; die Schleife in `createNewRound` erzeugt sogar mehrere Runden
hintereinander, wenn eine nicht-erforderliche Runde durchgereicht wird.

Die Automatik entscheidet deshalb ausschließlich **wann** gerufen wird, nie **wie** gerechnet
wird. „Alle Formate abdecken" heißt in diesem Entwurf: die Abschluss-Erkennung muss für jede
Kettenform stimmen — nicht, dass es je Format einen Zweig gäbe.

## Datenmodell

Eine Migration, vier Spalten.

| Spalte | Zweck |
| --- | --- |
| `event.auto_create_following_rounds boolean not null default false` | Veranstaltungseinstellung. Aus als Vorgabe, wie `chain_progression_mode` |
| `competition.auto_create_following_rounds boolean` (nullable) | Übersteuerung des Wettkampfs. `null` = erben, `true` = ausdrücklich an, `false` = ausdrücklich aus |
| `competition_setup_round.materialized_at timestamp` | Merkt, dass diese Runde schon einmal gesetzt war |
| `competition_match.pairings_recalculated_at timestamp` | Der Vermerk am Lauf |

Die Übersteuerung folgt dem vorhandenen Muster: `competition.timing_system` überschreibt
`event.timing_system`, gelesen mit `coalesce`. Ein nullable Boolean trägt genau die geforderten
drei Zustände, ohne eine neue Aufzählung einzuführen.

`competition_setup_round.materialized_at` steht bewusst auf der Setup-Runde und nicht auf der
Runde selbst: Es muss das **Löschen** der Runde überleben, denn genau daran hängt die
Unterscheidung zwischen Erst-Erzeugung und Wiedererzeugung nach einer Korrektur.
`deleteCurrentRound` räumt es deshalb nicht ab.

`competition_match.pairings_recalculated_at` ist die exakte Entsprechung zu
`raceclocker_auto_paused_at`: ein Zeitstempel am Lauf, den eine Ansicht als Vermerk zeigt.

## Abschluss-Erkennung

Reine Funktion in `AutoRoundProgressionLogic`, ohne Datenbank, über
`CompetitionSetupRoundWithMatches` — der Typ trägt bereits alles Nötige (`finishedAt`, `skipped`,
`required`, Mannschaften mit `place`/`failed`/`out`/`deregistered`).

Eine Runde ist abgeschlossen, wenn sie Läufe hat **und** jeder Lauf erledigt ist **und** alle
Plätze gesetzt sind.

Erledigt ist ein Lauf, wenn eine der drei Aussagen zutrifft:

- Er ist ein **Freilos** (`!round.required && teams.size == 1`). Ein Freilos wird nie gefahren und
  nie beendet; sein Platz 1 steht seit der Erzeugung.
- Sein Zeitstrahl-Slot ist **abgesagt** (`skipped`).
- Er ist **beendet** (`finishedAt != null`).

„Beendet" heißt ausschließlich: jemand hat den Lauf aktiv beendet. Das ist die Entscheidung C1 aus
dem Bestand — `AWAITING_FINISH` („alle Boote gewertet, aber niemand hat beendet") zählt bewusst
nicht, weil bis zum Beenden-Klick noch eine Zeitstrafe kommen kann.

„Alle Plätze gesetzt" ist wörtlich die Bedingung aus `checkRoundCreation`: je Lauf enthalten die
Plätze die Folge `1..n` über alle Mannschaften ohne `deregistered`, `failed` oder `out`. DNF,
Disqualifikation und Nichtantritt sind `failed = true` und damit gewertet; abgemeldete Boote
zählen ebenfalls als gewertet. Beides ist bestehende Regel (`LiveDashboardLogic.teamHasResult`),
sie wird benutzt und nicht nachgebaut.

Zweite reine Funktion, ebenfalls mit Test:

```
effectiveAutoCreate(eventDefault: Boolean, competitionOverride: Boolean?) =
    competitionOverride ?: eventDefault
```

Eine Zeile — als benannte Funktion, damit die drei Zustände nicht an jeder Lesestelle neu
ausgedacht werden.

## Auslöser

`AutoRoundProgressionService.progressIfRoundComplete(eventId, competitionId, userId)`.

Abbruchreihenfolge:

1. Einstellung ausgewertet, Ergebnis `false` → nichts.
2. Challenge-Event → nichts (`createNewRound` verweigert dort ohnehin).
3. Keine aktuelle Runde → nichts. **Die erste Runde erzeugt weiterhin ein Mensch**: sie hängt an
   finalisierten Meldungen, nicht an einem Rundenabschluss, und `createNewRound` warnt dort über
   Meldungen ohne Startnummer.
4. Keine Folgerunde in der Kette → nichts.
5. Aktuelle Runde nicht abgeschlossen → nichts.
6. Sonst: `createNewRound(eventId, competitionId, userId)`.

Gerufen wird die Funktion an fünf Stellen:

- `LiveDashboardService.finishMatchInternal` — der Hauptweg, geteilt von Schiedsrichter-Dashboard
  und Regattabüro (`EventScheduleService.finishSlot`).
- `EventScheduleService.setSlotSkipped` — ein abgesagter Lauf kann der letzte fehlende sein.
- Die drei Ergebnis-Schreibwege in `CompetitionExecutionService`: `updateMatchResult`,
  `updateMatchResultByFile` und `applyRaceClockerRows`. Ein Lauf kann beendet sein und erst danach
  vollständig gewertet werden — dann ist die Ergebnis-Eingabe der letzte Auslöser.

Ausdrücklich **nicht** gerufen von `deleteCurrentRound` (sonst ließe sich keine Runde mehr löschen,
sie käme sofort zurück) und nicht von der Aktivierung.

### Idempotenz

Fällt strukturell an: `getCurrentAndNextRound` erklärt jede Runde, die Läufe hat, zur aktuellen.
Ein zweiter Aufruf sieht also die eben erzeugte Runde als „aktuell", findet sie unvollständig
(keine Läufe beendet) und tut nichts. Es gibt keinen Pfad, auf dem zweimal dieselbe Runde entsteht.

### Fehler

Fehler aus `createNewRound` werden protokolliert und verschluckt. Ein „Beenden" im
Schiedsrichter-Dashboard darf nicht daran scheitern, dass das Setup der nächsten Runde zu wenig
Bahnen hat — der Lauf ist gefahren, das ist eine Tatsache, und die Automatik ist eine Bequemlichkeit
obendrauf. Der Knopf im Durchführungs-Tab meldet denselben Fehler weiterhin sichtbar.

### Keine automatische Aktivierung

Die Automatik aktiviert nichts. `createNewRound` ruft am Ende bereits
`ScheduleChainService.resumeIfParked`, und der entscheidet nach `chain_progression_mode`, ob und
welcher Lauf an den Start geht. Bei `DEAKTIVIERT` passiert gar nichts; bei `SCHIEDSRICHTER` und
`REGATTABUERO` greift genau die Kette, die es heute schon gibt. Es kommt keine Aktivierung hinzu,
die es vor diesem Entwurf nicht gab.

## Nachträgliche Korrekturen

Die Ergebnis-Sperre bleibt, wie sie ist: `checkUpdateMatchResult` lässt weiterhin nur die zuletzt
erzeugte Runde zu und antwortet sonst mit `MatchResultsLocked`. Der Weg zur Korrektur ist damit:

> Folgerunde löschen → Ergebnis korrigieren → die Automatik erzeugt die Paarungen neu.

Der Schutz bereits gestarteter Läufe ist damit strukturell gegeben statt geprüft: Die Automatik
legt nur dort an, wo keine Läufe sind. Sie kann eine bestehende Runde nicht überschreiben, also
auch keinen gestarteten Lauf verändern. Wer korrigieren will, während die Folgerunde schon läuft,
läuft in die bestehende Sperre — dieselbe Antwort wie vor diesem Entwurf.

`deleteCurrentRound` bekommt **keine** neue Sperre gegen bereits gestartete Läufe. Das wäre eine
Verhaltensänderung für Admins außerhalb dieses Auftrags: Heute darf das Regattabüro eine laufende
Runde löschen, und dabei bleibt es.

### Der Vermerk

Gesetzt wird er in `createNewRound` selbst, nicht in der Automatik. Nach der obigen Entscheidung
kann die Wiedererzeugung auch von Hand ausgelöst werden, und beide Fälle verdienen denselben
Hinweis.

Regel je erzeugter Runde:

- `competition_setup_round.materialized_at` war gesetzt → die neuen Läufe bekommen
  `pairings_recalculated_at = now()`.
- Sonst → nur `materialized_at = now()`, kein Vermerk.

Im DTO wird der Vermerk auf `null` gesetzt, sobald der Lauf aktiviert ist (`activated_at != null`).
Eine Regel an einer Stelle statt derselben Bedingung in zwei Frontends. Der Zeitstempel in der
Datenbank bleibt stehen; er ist Historie.

## Oberfläche

- **Veranstaltung:** Schalter in `EventDialog.tsx` direkt neben `chainProgressionMode` — dieselbe
  Familie von Ablauf-Einstellungen.
- **Wettkampf:** Kleinroute `GET/PUT /event/{eventId}/competition/{competitionId}/roundProgression`
  nach dem Vorbild von `timing-config`. Bedienung im Durchführungs-Tab neben „Nächste Runde
  erstellen", als Dreier-Auswahl *Veranstaltung folgen / ein / aus* mit Anzeige des geerbten Werts.
- **Vermerk:** Chip „Paarung neu berechnet" im Durchführungs-Tab
  (`CompetitionExecutionRound.tsx`, neben dem RaceClocker-Chip) und im Schiedsrichter-Dashboard.
  Beide Ansichten hängen an `ReadEventGlobal` bzw. `ReadLiveDashboardGlobal`.
- **Öffentliche Anzeige und Athletenanzeige bekommen das Feld nicht.** `AthleteBoardDto` und die
  `eventInfo`-DTOs werden nicht angefasst.
- i18n DE, EN und DA — alle drei Dateien sind gepflegt (`frontend/src/i18n/*/translations.json`).

## Tests

**Rein** (`AutoRoundProgressionLogicTest`, kotlin.test wie die übrigen `*LogicTest`):

- Vererbung: `null` erbt an, `null` erbt aus, `true` gegen ausgeschaltete Veranstaltung, `false`
  gegen eingeschaltete Veranstaltung.
- Vollständige Runde mit DNF, Disqualifikation, Nichtantritt, Abmeldung und Freilos in einem Lauf.
- Unvollständig, weil ein Lauf kein `finished_at` hat.
- Unvollständig, weil in einem Lauf ein Platz fehlt.
- Runde ohne Läufe ist nicht abgeschlossen.
- Je ein Kettenaufbau: K.-o.-Baum, Vorrunde → Zwischenrunde → Finale, Qualifikation mit Freilosen,
  Einzelrunde ohne Folgerunde.

**Gegen echtes Postgres** (`testComprehension` mit Testcontainers, Muster aus
`ClubShortNameRepoTest`):

- Auslöser erzeugt genau eine Folgerunde.
- Zweiter Aufruf erzeugt nichts dazu (Idempotenz).
- `activated_at` bleibt nach der Erzeugung überall `null`.
- Einstellung aus ⇒ keine Erzeugung; Übersteuerung am Wettkampf schlägt die Veranstaltung.
- Löschen + Korrektur erzeugt neu und setzt `pairings_recalculated_at`; die Erst-Erzeugung setzt
  ihn nicht.
- Ist ein Lauf der Folgerunde gestartet, bleibt die Korrektur mit `MatchResultsLocked` liegen und
  nichts ändert sich.

## Betroffene Dateien

Backend:

- `db/migration/V2026080915xx__auto_create_following_rounds.sql` (neu)
- `competitionExecution/boundary/AutoRoundProgressionLogic.kt` (neu, rein)
- `competitionExecution/boundary/AutoRoundProgressionService.kt` (neu)
- `competitionExecution/boundary/CompetitionExecutionService.kt` (Vermerk in `createNewRound`,
  Auslöser in den Ergebnispfaden)
- `competitionExecution/control/CompetitionMatchRepo.kt` und
  `competitionSetup/control/CompetitionSetupRoundRepo.kt` (Lese- und Schreibzugriff auf die neuen
  Spalten)
- `liveDashboard/boundary/LiveDashboardService.kt` (Auslöser in `finishMatchInternal`, Feld im DTO)
- `eventSchedule/boundary/EventScheduleService.kt` (Auslöser in `setSlotSkipped`)
- `event/` (Einstellung in DTO, Create/Update-Request, Conversions, Repo)
- `competition/` bzw. neue Kleinroute für die Übersteuerung
- `openapi/documentation.yaml`

Frontend:

- `components/event/EventDialog.tsx`
- `components/event/competition/excecution/CompetitionExecution.tsx` und
  `CompetitionExecutionRound.tsx`
- Schiedsrichter-Dashboard-Karte
- `api/` neu generiert, i18n DE/EN

## Offen gelassen

- Die erste Runde bleibt Handarbeit (siehe oben).
- `deleteCurrentRound` bleibt ungesperrt (siehe oben).
- Kein Protokoll darüber, wer welche Runde automatisch erzeugen ließ, über `created_by` hinaus.
  Der Vermerk am Lauf ist die sichtbare Aussage; eine Historie verlangt niemand.
