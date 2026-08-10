# Laufzustand „In Vorbereitung" / „Läuft" und die Arena-Umbenennung

Entwurf vom 09.08.2026. Branch `claude/laufzustand-arena`, mündet in `feature/crf-2026`.

## Warum

Ein Klick auf „aktiv" im Schiedsrichter-Dashboard oder im Durchführungs-Tab bedeutet heute
fachlich „der Lauf ist an den Start gerufen" — die Oberfläche nennt das aber „Aktuell laufend".
Solange niemand einen Ist-Start messen konnte, war die Ungenauigkeit folgenlos. Mit dem
automatischen RaceClocker-Abruf gibt es zum ersten Mal einen zuverlässigen Sender für den
tatsächlichen Start, und damit lohnt sich die Trennung.

Beide Zustände existieren bereits in den Daten, nur ohne Namen:

| Spalte | Bedeutung heute |
|---|---|
| `competition_match.currently_running` | „vom Schiedsrichter aktiviert" |
| `competition_match.started_at` | „tatsächlich losgegangen" |

Die Athleten-Anzeige leitet die Unterscheidung schon ad hoc ab (`AthleteBoardMatchCard`:
`actualStartTime ? „gestartet 14:32" : „in Vorbereitung"`), alle anderen Oberflächen werfen sie
zusammen. Genau das soll aufhören: **der Zustand muss überall identisch sichtbar sein.**

Zweitens ist die Sprache der Prüfungen und Chips ruderspezifisch („auf dem Wasser", „Wasser 3/6",
`NOT_ON_WATER`). ready2race soll auch Sportarten ohne Wasser bedienen; die neutrale Entsprechung
ist **„in der Arena"**.

## Nicht Gegenstand

- Kein Automatismus, der den Ist-Start aus der *geplanten* Startzeit ableitet. Die geplante Zeit
  ist am Regattatag notorisch falsch, und ein automatisch gesetzter Ist-Start wäre eine Messung,
  die niemand gemessen hat.
- Keine Änderung an `matchUnderway` (Absage-Schutz) und am RaceClocker-Takt (`modeFor`). Beide
  müssen weiterhin auf die *Aktivierung* hören, nicht auf den Ist-Start — begründet in ihren
  KDocs, und beim Takt ist der schnelle Takt ja gerade das Mittel, den Start zu entdecken.
- `competition_properties.check_in_out_required` bleibt, wie es heißt: schon neutral.

---

## 1. Datenmodell

### 1.1 Die Spalte wird zum Zeitstempel

`V202608091400__match_activated_at.sql`:

```sql
set search_path to ready2race, pg_catalog, public;

alter table competition_match add column activated_at timestamp;

-- Fuer bestehende aktive Laeufe ist das eine Naeherung: der Aktivierungszeitpunkt wurde nie
-- festgehalten. Bei einer Regatta, die zum Migrationszeitpunkt nicht laeuft, sind das null Zeilen.
update competition_match
set activated_at = coalesce(started_at, updated_at)
where currently_running;

alter table competition_match drop column currently_running;
```

jOOQ wird neu erzeugt (`afterMigrate.sql` mitziehen, wo `cm.currently_running` selektiert wird).

### 1.2 Der abgeleitete Zustand

`LiveDashboardMatchState` (alias `MatchState`) bekommt `PREPARING`. `deriveMatchState` bleibt der
einzige Ort, der die Reihenfolge festlegt:

```kotlin
activatedAt != null && startedAt == null -> PREPARING
activatedAt != null                      -> RUNNING
finishedAt != null                       -> FINISHED
skipped                                  -> SKIPPED
teamResults.isNotEmpty() && all { it }   -> AWAITING_FINISH
startTime == null                        -> UNSCHEDULED
else                                     -> UPCOMING
```

Der einzige inhaltliche Eingriff ist der aufgespaltene erste Zweig. Alles darunter bleibt Wort für
Wort, damit `LiveDashboardLogicTest` weiter genau das festnagelt, was er heute festnagelt.

Die Signatur wechselt von `currentlyRunning: Boolean` auf `activatedAt: LocalDateTime?`.

### 1.3 API-Namen

| heute | künftig |
|---|---|
| `PUT …/liveDashboard/match/{id}/running-state?running=` | `PUT …/liveDashboard/match/{id}/activation?activated=` |
| `PUT …/competitionExecution/…/running-state` (Body) | `…/activation`, Body `UpdateCompetitionMatchActivationRequest(activated)` |
| `EventScheduleSlotDto.matchCurrentlyRunning: Boolean` | `matchActivatedAt: LocalDateTime?` |
| `GET /event/{id}/matches?currentlyRunning=` | `?activated=` |
| `LiveDashboardMatchDto.currentlyRunning` | entfällt — der Zustand steht in `state` |
| `CompetitionMatchDto.currentlyRunning` | `activatedAt` |

`documentation.yaml` und die generierten `types.gen.ts`/`sdk.gen.ts` ziehen mit.

---

## 2. Die Übergänge

| Auslöser | schreibt | Ergebnis |
|---|---|---|
| UI „Lauf aktivieren" (Dashboard, Durchführung, Zeitplan) | `activated_at = now` | **PREPARING** |
| UI „Läuft" (neu verdrahtet) | `started_at = now` falls null, `activated_at = now` falls null | **RUNNING** |
| Kette (`ScheduleChain.activate`) | `activated_at = now` | **PREPARING** |
| Poll, Lauf **nicht** aktiviert, `startDetected` | `activated_at = now`, `started_at = now` falls null | **RUNNING** |
| Poll, Lauf **aktiviert**, Feed meldet Startzeit | `started_at` aus `earliestStart` | **PREPARING → RUNNING** |
| UI „Lauf deaktivieren" | `activated_at = null`, `started_at = null`, `raceclocker_auto_paused_at = now` | zurück auf UPCOMING |
| Beenden (`finishMatch`) | `finished_at = now`, `activated_at = null` | **FINISHED** |

### 2.1 Der Knopf „Läuft"

Der Endpunkt existiert bereits (`PUT /event/{eventId}/liveDashboard/match/{matchId}/start` →
`LiveDashboardService.markMatchStarted`, idempotent) und ist als `startLiveDashboardMatch` im SDK
generiert — **aber von keiner Frontend-Komponente aufgerufen**. Er wird verdrahtet.

Beschriftung ist **„Läuft"**, nicht „Start": „Start" würde suggerieren, dass der Klick eine
Zeitnahme auslöst. Er stellt nur fest, dass das Rennen unterwegs ist.

Ist für die Veranstaltung `raceclocker_auto_pull` eingeschaltet, steht am Knopf der Hinweis, dass
RaceClocker den Start ohnehin selbst meldet, sobald er ihn sieht — der Knopf bleibt trotzdem
bedienbar (Feed-Ausfall, Zeitnahme ohne Startstempel).

### 2.2 Der Ist-Start eines von der Kette aktivierten Laufs

**Die Lücke.** Der Poll-Job stempelt `started_at` heute auf zwei Wegen. Für einen noch nicht
aktivierten Lauf setzt er Aktivierung und Ist-Start in einem Zug. Für einen bereits aktivierten
Lauf geht er über `applyRaceClockerRows` — und dort steht

```kotlin
if (withResult.isEmpty()) return@comprehension KIO.fail(RaceClockerError.NoResults(...))
```

**vor** dem Block, der `started_at` aus `earliestStart` setzt. Ein von der Kette aktivierter Lauf
bleibt deshalb „in Vorbereitung", obwohl RaceClocker längst eine Startzeit meldet — bis das erste
Boot durchs Ziel ist. Genau der Fall beim Renntag-Start.

**Die Lösung.** Der Poll stempelt den Start selbst, in `pollMatch`, symmetrisch zum bestehenden
Aktivierungszweig und **bevor** es in `applyRaceClockerRows` geht:

```kotlin
// Lauf ist aktiviert, aber noch ohne Ist-Start: der Feed weiss ihn vielleicht schon.
if (candidate.startedAt == null) {
    RaceClockerFeedRow.earliestStart(assigned)?.let { start ->
        !CompetitionMatchRepo.update(candidate.matchId) {
            startedAt = (match.startTime?.toLocalDate() ?: LocalDate.now()).atTime(start)
            updatedBy = SYSTEM_USER
            updatedAt = now
        }.orDie()
    }
}
```

`applyRaceClockerRows` bleibt unangetastet. Das ist Absicht: Die Funktion wird auch vom
„nachziehen"-Knopf aufgerufen, und ihr `NoResults`-Zweig läuft in `pollMatch` innerhalb von
`.transact()` — würde man `started_at` dort vor dem Abbruch setzen, rollte die fehlschlagende
Transaktion den Stempel gleich wieder zurück. Der eigene Zweig im Poll umgeht das Problem, statt
es zu verwalten.

`RaceClockerPollCandidate` braucht dafür `startedAt` zusätzlich zu `currentlyRunning`/`activatedAt`
(`RaceClockerPollRepo.getCandidates` mitziehen).

### 2.3 Deaktivieren pausiert die Automatik

Ohne das wäre Deaktivieren wirkungslos: Der Lauf steht im Beobachtungsfenster (geplante Zeit
−15/+120 min), der Job findet die Startzeit im Feed und aktiviert ihn im nächsten Takt wieder —
spätestens nach 60 Sekunden.

Deshalb setzt Deaktivieren `raceclocker_auto_paused_at` — dieselbe Pause, die schon greift, wenn
jemand Ergebnisse von Hand einträgt.

**Die Freigabe muss dort erreichbar sein, wo deaktiviert wurde.** `resumeRaceClockerAutoPull`
(`POST …/results/raceclocker/resume`, löscht die Pause und ruft `RaceClockerPollService.forget`)
gibt es schon, samt Knopf — aber nur im **Durchführungs-Tab**. Der Schiedsrichter deaktiviert im
**Dashboard** und hätte dort keinen Weg zurück. Also: Der Hinweis „Automatischer Abruf pausiert"
in `LiveDashboardMatchCard` (er zeigt den Zustand bereits an, `raceClockerAutoPausedAt` steht im
DTO) bekommt denselben Knopf.

---

## 3. Überall identisch sichtbar

Das ist die eigentliche Anforderung. Es gibt genau eine Quelle — `MatchState` — und jede Oberfläche
liest sie, statt sich ihre eigene Ableitung zu bauen.

| Ort | Änderung |
|---|---|
| `matchStatusChip` | neuer Zweig `PREPARING` → `event.match.status.preparing` („In Vorbereitung"), Farbe `info` |
| `roundCounterChips` (Frontend) | neuer Topf „in Vorbereitung" vor „läuft" |
| `MatchStatusLogic.roundCounters` (Backend) | Feld `preparing` in `RoundCountersDto`; PREPARING zählt **nicht** mehr in `running` und **nicht** in `open` |
| `slotMatchStatus` (Zeitplan) | die nachgebildete Zweig-Reihenfolge um PREPARING erweitern, aus `matchActivatedAt` + `matchStartedAt` |
| `AthleteBoardMatchCard` | die Ad-hoc-Ableitung `actualStartTime ? … : „in Vorbereitung"` entfällt; die Karte liest `state` |
| `AthleteBoardMatch` (Backend-DTO) | trägt `state: MatchState` mit, damit die Karte nichts mehr selbst ableitet |
| `LiveDashboardMatchCard` | zeigt den Chip aus `matchStatusChip` statt eigener Textlogik — dieselbe Aussage wie Durchführung und Zeitplan |
| `isLiveMatch` / `LiveDashboardLogic.selectForScope(LIVE)` | PREPARING gehört in den Live-Ausschnitt: der Lauf ist im Zugriff des Schiedsrichters |
| `matchControls` | bei PREPARING zusätzlich der Knopf „Läuft"; bei RUNNING nicht mehr |

Die Athleten-Anzeige behält ihre bisherige **Auswahl**: „Aktueller Lauf" umfasst PREPARING und
RUNNING. Nur der Untertitel unterscheidet — jetzt aus dem gemeinsamen Zustand statt aus einer
zweiten Ableitung.

### 3.1 Die Kette hört auf den Ist-Start

`ScheduleChain.decideNext`: `siblingStillRunning` prüft heute `it.currentlyRunning`. Künftig
blockiert nur ein Lauf die Gruppe, der **wirklich gestartet** ist (`matchStartedAt != null`). Ein
bloß aktivierter Nachbar hält die Kette nicht mehr an.

`activatable` bleibt an der Aktivierung: ein schon aktivierter Lauf soll nicht erneut aktiviert
werden.

---

## 4. Arena statt Wasser

### 4.1 Der gespeicherte Wert

`V202608091410__check_type_not_in_arena.sql`:

```sql
set search_path to ready2race, pg_catalog, public;

-- „auf dem Wasser" ist ruderspezifisch; ready2race soll auch Sportarten ohne Wasser bedienen.
update competition_check_severity set check_type = 'NOT_IN_ARENA' where check_type = 'NOT_ON_WATER';
```

Die Tabelle ist bewusst dünn besetzt (nur Abweichungen vom Standard), das sind wenige Zeilen.
Der `chk_ccs_requirement_matches_check_type`-Constraint nennt nur die beiden REQUIREMENT-Typen und
bleibt unverändert gültig.

### 4.2 Umbenennungen

Backend:

| heute | künftig |
|---|---|
| `CheckType.NOT_ON_WATER` | `CheckType.NOT_IN_ARENA` |
| `LiveDashboardLogic.teamOnWaterAt` | `teamInArenaAt` |
| `MatchStatusLogic.teamsOnWaterPerMatch` | `teamsInArenaPerMatch` |
| `MatchStatusDto.teamsOnWater` | `teamsInArena` |
| `LiveDashboardTeamDto.onWaterRequired` | `inArenaRequired` |
| `LiveDashboardTeamDto.onWaterSeverity` | `inArenaSeverity` |
| `LiveDashboardTeamDto.onWaterAt` | `inArenaAt` |

Frontend:

| heute | künftig |
|---|---|
| `waterChip` | `arenaChip` |
| `event.match.status.water` | `event.match.status.inArena` |

Texte: de „auf dem Wasser" → „in der Arena", „Wasser 3/6" → „Arena 3/6"; en „on water" → „in
arena"; da entsprechend. Auch die Kommentare in `ParticipantTrackingService`,
`CompetitionPropertiesRequest` und `V202608071200` werden mitgezogen — sie erklären das
Check-in/out und wären sonst die letzte Stelle, an der „Wasser" die Bedeutung trägt.

Der fachliche Begriff für die Handlung bleibt An-/Abmeldung (Check-in/out); es ändert sich nur der
Ort, in den man sich an- und abmeldet.

---

## 5. Tests

Die Zustandsableitung ist reine Logik und wird auch so geprüft — kein laufender Server nötig.

**Backend**

- `LiveDashboardLogicTest`: PREPARING-Zweig, und dass jeder Zweig darunter unverändert trifft.
- `MatchStatusLogicTest`: der neue Zähler-Topf; ein aktivierter Lauf ohne Ist-Start zählt weder als
  „läuft" noch als „offen".
- `ScheduleChainTest`: ein aktivierter, nicht gestarteter Nachbar blockiert die Gruppe **nicht**
  mehr; ein gestarteter schon.
- `EventScheduleLogicTest`: `matchUnderway` bleibt bei „Aktivierung genügt" — Regressionsschutz.
- `RaceClockerPollLogicTest`: unverändert (`startDetected` ändert sich nicht).
- **neu** `RaceClockerPollStartStampTest`: ein aktivierter Lauf ohne `started_at` bekommt den
  Stempel aus dem Feed, auch wenn keine Zeile ein Ergebnis trägt.
- Testcontainers-Lauf (`testComprehension`) für die beiden Migrationen.

**Frontend**

- `matchStatusChip.test.ts`: PREPARING-Chip; `arenaChip` unter neuem Namen; die Zählerleiste.
- `liveDashboard/common.test.ts`: `isLiveMatch` und `matchControls` für PREPARING.
- `schedule/common.test.ts`, `timelineIndicator.test.ts`: die Slot-Ableitung.
- `editMatchForm.test.ts`, `roundCancellation.test.ts`: mitziehen.

## 6. Reihenfolge

1. Migrationen + jOOQ + `afterMigrate.sql`.
2. Backend: Zustand, Übergänge, Poll-Stempel, Kette, DTOs, Endpunkte, `documentation.yaml`.
3. Backend: Arena-Umbenennung.
4. Backend-Tests grün (`./mvnw test`, `JAVA_HOME` setzen).
5. Frontend: `types.gen.ts`/`sdk.gen.ts` neu erzeugen.
6. Frontend: Chips, Zähler, Slot, Athleten-Anzeige, Dashboard-Karte, Knopf „Läuft",
   Resume-Knopf im Dashboard, Übersetzungen.
7. Frontend-Tests + `npm run build` grün.

Schritt 2 und 3 fassen teils dieselben Dateien an (`LiveDashboardDto`, `MatchStatusLogic`) und
laufen deshalb nacheinander, nicht parallel.

## 7. Risiko

Die Regatta ist am **14.08.2026**, fünf Tage nach diesem Entwurf. Der Eingriff ist breit (21
Backend-Dateien, 16 Frontend-Dateien) und berührt mit `finishMatch`, der Kette und dem Poll drei
Pfade, die am Renntag tragen müssen. Die Migration `drop column currently_running` ist nicht
rückwärtskompatibel: Ein Rollback auf einen älteren Backend-Stand nach der Migration schlägt fehl.

Deshalb: vollständig grüne Tests auf beiden Seiten **und** ein Handtest gegen den Förde-Seed
(`seed-foerde.sql`) über die ganze Kette — aktivieren, Start, beenden, nächster Lauf — bevor das
in `feature/crf-2026` mündet.
