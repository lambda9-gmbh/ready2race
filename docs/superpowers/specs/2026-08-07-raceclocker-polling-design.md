# Automatischer RaceClocker-Abruf (Polling)

Entwurf vom 07.08.2026.

## Problem

Ergebnisse aus RaceClocker kommen heute nur, wenn jemand pro Lauf im Durchführungs-Tab
„Ergebnisse eintragen → RaceClocker" klickt (`POST /results/from-raceclocker` →
`CompetitionExecutionService.updateMatchResultFromRaceClocker`). Am Renntag heißt das: Live-Dashboard,
Athleten-Anzeige und Zeitstrahl zeigen so lange nichts, wie niemand klickt — und der Start eines
Laufs wird überhaupt nicht aus RaceClocker übernommen, obwohl die Zeitnahme ihn dort als erstes
kennt.

Ziel: Ein aktiver Lauf wird alle 5 Sekunden abgerufen, ein bevorstehender einmal pro Minute
beobachtet, ein beendeter nie. Der Start eines Laufs und seine Ergebnisse erscheinen damit ohne
Zutun.

## Entscheidungen

| Frage | Entscheidung |
|---|---|
| Wo läuft das Polling? | Backend-Scheduler. Der bestehende `Scheduler` bekommt einen weiteren Job. |
| Was passiert bei erkanntem Start? | Der Lauf wird aktiviert (`started_at` + `currently_running = true`). |
| Welche bevorstehenden Läufe? | Nur die im konfigurierten Zeitfenster um die geplante Startzeit. |
| Wo wird konfiguriert? | Zeitnahme-Tab der Veranstaltung. Kein Wettkampf-Override. |
| Was gewinnt bei Handeingabe? | Die Handeingabe. Der Lauf wird für die Automatik pausiert. |
| Was sieht die Oberfläche? | Je Lauf: letzter Abruf, letzter Fehler, Knopf „Automatik wieder aufnehmen". |

Unverändert bleibt: **Der Abruf beendet nie einen Lauf** (Entscheidung vom 04.08.2026, dokumentiert
in `LiveDashboardLogic.deriveMatchState`). Er meldet nur Daten.

## Der Job

Ein weiterer Job in `Application.kt`, über `scheduleDynamic`:

- `DynamicIntervalJobState.Empty` — keine Veranstaltung mit eingeschalteter Automatik hat gerade
  einen beobachteten Lauf → **30 s** Pause. Das ist der Normalzustand außerhalb einer Regatta und
  kostet eine kleine Query alle 30 s.
- `DynamicIntervalJobState.Processed` — mindestens ein Lauf ist im Blick → **1 s Herzschlag**.

Der Herzschlag ruft nicht bei jedem Schlag ab. Er prüft je Veranstaltung, ob deren konfigurierter
Takt fällig ist. Dieser Umweg ist nötig, weil die Takte pro Veranstaltung einstellbar sind: ein fest
auf 5 s verdrahteter Job könnte einen auf 3 s gestellten Takt nicht bedienen, und ein Job pro
Veranstaltung wäre eine Job-Verwaltung, die es hier nicht braucht.

Wann eine Veranstaltung zuletzt abgerufen wurde, hält der Job **im Speicher**. Nach einem
Neustart wird sofort wieder abgerufen — das ist harmlos und spart eine Migration für einen Wert,
der niemanden außerhalb des Jobs interessiert.

## Ablauf eines Takts

### 1. Kandidaten laden

Eine Query liefert alle Läufe, die überhaupt in Frage kommen:

- Veranstaltung hat `raceclocker_auto_pull = true`,
- Zeitnahmesystem (`coalesce(competition.timing_system, event.timing_system)`) ist `RACECLOCKER`,
- mindestens eine der beiden URLs ist gesetzt (dieselbe Coalesce-Kette wie
  `CompetitionMatchRepo.getForRaceClockerPull`),
- `competition_match.finished_at is null`,
- `raceclocker_auto_paused_at is null`,
- der Zeitstrahl-Slot des Laufs ist nicht abgesagt (`EventScheduleLogic.skippedMatchIdOrNull`),
- und entweder `currently_running = true`
  oder `start_time` liegt in `[jetzt − Nachlauf, jetzt + Vorlauf]`.

Läufe ohne `start_time` fallen aus dem langsamen Takt heraus — ohne geplante Startzeit gibt es kein
Fenster. Aktiviert wird ein solcher Lauf weiterhin von Hand, danach greift der schnelle Takt.

Challenge-Veranstaltungen sind ausgenommen (`EventService.checkIsChallengeEvent`), wie beim
manuellen Pull.

### 2. Fälligkeit je Veranstaltung

Ist unter den Kandidaten einer Veranstaltung mindestens ein aktiver Lauf, gilt der schnelle Takt,
sonst der langsame. Ist er seit dem letzten Abruf noch nicht verstrichen, wird die Veranstaltung
in diesem Herzschlag übersprungen.

„Mindestens ein aktiver Lauf" wird **nach** dem Durchgang über die Läufe bestimmt, nicht vor ihm:
Erst dort wird ein Lauf aktiviert. Zählte der Takt die Momentaufnahme von davor, notierte
ausgerechnet der Abruf, der den Start entdeckt, noch den langsamen Takt — der frisch gestartete Lauf
wartete dann eine volle Minute auf seinen ersten Ergebnisabruf.

### 3. Feed je URL genau einmal holen

Ein Abruf liefert das ganze Rennen — alle Wellen, alle Runden. Der Job holt deshalb pro benötigter
URL genau einmal (Zeitfahren- oder Läufe-URL, entschieden über
`competition_setup_round.is_qualification`) und verteilt die Antwort auf alle Läufe an derselben
URL. Bei einer Regatta sind das 1–2 Abrufe pro Takt für die gesamte Veranstaltung, unabhängig
davon, wie viele Läufe gerade laufen.

Der manuelle Pull probiert die zweite URL, wenn die Welle in der ersten nicht auftaucht — eine
Runde, die als Zeitfahren gefahren wird, ohne als Qualifikation markiert zu sein, findet sich so
trotzdem. Der Job behält diese Regel bei; er holt vorab alle URLs, die seine Kandidaten überhaupt
brauchen, und der Rückgriff auf die zweite kostet ihn dann keinen weiteren Abruf.

### 4. Je Lauf zuordnen

Zuordnung über `assignFeedRows(rows, teams, waveName)`, unverändert.

- **Kein Treffer** → kein Fehler. Nur `raceclocker_polled_at` wird gesetzt. Eine Welle, die in
  RaceClocker noch nicht angelegt ist, ist vor dem Start der Normalfall und darf nicht als Störung
  erscheinen.
- **Treffer, aber noch kein verwertbares Ergebnis** (`RACECLOCKER_NO_RESULTS`) → ebenfalls kein
  Fehler, aus demselben Grund: Solange in einem Lauf jedes Boot `In race…` zeigt, ist genau das der
  Zustand. Als Fehler gespeichert leuchtete in jedem Lauf über fast seine ganze Dauer eine orange
  Warnung — und eine Warnung, die immer leuchtet, bringt dem Büro bei, auch die eine zu übersehen,
  auf die es ankommt. Geschrieben wird dabei nichts, der Fingerabdruck darf also gemerkt werden.
- **Lauf ist bevorstehend** → geprüft wird nur, ob der Feed für diese Welle einen echten Start
  zeigt. Echt heißt: `RaceClockerFeedRow.start != null` — der Parser wirft den Platzhalter
  `00:00:00.0` bereits weg, mit dem RaceClocker Boote ohne Start füllt — oder ein verwertbares
  Ergebnis (`RaceClockerFeedRow.hasResult`). Trifft das zu, werden `started_at` und `currently_running = true`
  gesetzt — sonst nichts. Ab dem nächsten Takt liegt der Lauf im schnellen Takt und wird voll
  verarbeitet.
- **Lauf ist aktiv** → die vollständige Anwendungslogik: Bahnen aus den Zeilenpositionen, Zeiten,
  Plätze, Strafen.

### 5. Fehler

Ein Fehler wird je Lauf in `raceclocker_poll_error` geschrieben und bricht den Takt nie ab. Ein Lauf
mit doppelten Crews in RaceClocker darf die anderen Läufe derselben Veranstaltung nicht mitreißen.
Ist eine URL nicht erreichbar, bekommen alle Läufe an dieser URL denselben Fehler.

Das gilt auch für **Defekte**, nicht nur für typisierte Fehler: Ein gescheitertes `orDie` (etwa ein
Lock-Timeout auf einem Lauf, den jemand offen hat) würde sonst aus dem Durchgang herausfliegen und
jeden Lauf dahinter für immer verhungern lassen. Der Job führt deshalb jeden Lauf einzeln aus und
legt einen Defekt als `INTERNAL_ERROR` in derselben Spalte ab; der Grund steht im Server-Log.

## Kein zweiter Code-Pfad

`updateMatchResultFromRaceClocker` hängt heute an `CallComprehensionScope`, holt den Feed selbst und
wendet ihn an. Für den Job wird der HTTP-Teil abgeschnitten:

- `applyRaceClockerRows(match, target, rows, userId)` — reine Anwendungslogik, ohne HTTP und ohne
  Call-Scope. Enthält Zuordnung, Duplikatprüfung, `started_at`, Bahnen und Ergebnisse.
- Der Endpunkt holt den Feed wie bisher und ruft diese Funktion auf.
- Der Job holt den Feed einmal je URL und ruft dieselbe Funktion je Lauf auf.

Damit können Knopf und Automatik nicht auseinanderlaufen. Der Knopf verhält sich unverändert.

Schreiber ist `SYSTEM_USER` (`database/static.kt`), wie bei allen anderen Hintergrund-Jobs.

## Schreib-Sparsamkeit

Der Anwendungsschritt löscht und schreibt je Boot Zeitcode und Platz. Alle 5 s für jeden aktiven
Lauf wäre das nicht nur unnötige Last, sondern vor allem irreführend: jeder Lauf sähe im
Änderungsprotokoll alle 5 s „bearbeitet" aus, obwohl sich nichts geändert hat.

Der Job hält deshalb je Lauf einen Fingerabdruck der zugeordneten Feed-Zeilen im Speicher. Ist er
unverändert, wird nur `raceclocker_polled_at` gesetzt und sonst nichts geschrieben. Der
Fingerabdruck deckt alle Felder ab, die in die Datenbank wandern (Name, Position, Bahn, Zeit,
Ausscheidungsgrund, Strafe, Startzeit) — nicht die ganze Feed-Antwort, denn andere Wellen ändern
sich ständig und gehen diesen Lauf nichts an.

## Konfiguration

Fünf Spalten auf `event`, im Zeitnahme-Tab der Veranstaltung. Kein Wettkampf-Override: die Takte
gelten pro RaceClocker-Rennen, und das Rennen gehört der Veranstaltung. Zwei Wettkämpfe am selben
Rennen mit verschiedenen Takten wären nicht sinnvoll auflösbar.

| Spalte | Bedeutung | Vorgabe |
|---|---|---|
| `raceclocker_auto_pull` | Ergebnisse automatisch abrufen | `false` |
| `raceclocker_interval_active_seconds` | Takt bei aktivem Lauf | 5 |
| `raceclocker_interval_upcoming_seconds` | Takt bei bevorstehendem Lauf | 60 |
| `raceclocker_watch_before_minutes` | Vorlauf vor geplanter Startzeit | 15 |
| `raceclocker_watch_after_minutes` | Nachlauf nach geplanter Startzeit | 120 |

Bestandsdaten bleiben aus — nichts ändert sich ungefragt.

Beide Takte bekommen im Code eine Untergrenze über `coerceAtLeast`, wie
`AthleteBoardLogic.refreshIntervalSeconds`. Ohne sie legt eine versehentlich eingetragene `1` die
Regatta auf Dauerfeuer gegen raceclocker.com. Die Untergrenze ist Code, keine Validierung des
Formulars: sie soll auch dann greifen, wenn der Wert auf anderem Weg in die Datenbank kommt.

## Handeingabe und Sichtbarkeit

Drei Spalten auf `competition_match`:

| Spalte | Bedeutung |
|---|---|
| `raceclocker_polled_at` | Zeitpunkt des letzten Abrufversuchs |
| `raceclocker_poll_error` | Grund des letzten Fehlschlags, `null` = in Ordnung |
| `raceclocker_auto_paused_at` | gesetzt = die Automatik lässt diesen Lauf in Ruhe |

`updateMatchResult` (Formular) und `updateMatchResultByFile` (Datei-Upload) setzen
`raceclocker_auto_paused_at` — aber nur dort, wo es eine Automatik gibt (eingeschaltete Automatik an
der Veranstaltung und RaceClocker als effektives System). Sonst sammelte jede Veranstaltung ohne
Zeitnahmesystem an jedem von Hand eingetragenen Lauf einen Vermerk ein, der sich auf nichts bezieht.
Der manuelle Pull setzt es **nicht** — er ist derselbe Weg, nur von Hand ausgelöst, und darf die
Automatik nicht abwürgen.

Die Pause wird ein zweites Mal geprüft, in derselben Transaktion wie das Schreiben: Zwischen dem
Laden der Kandidaten und dem Schreiben liegen bis zu zwei HTTP-Abrufe mit je zehn Sekunden
Zeitlimit — lang genug für eine Handeingabe, die der Job sonst überschriebe.

Der Durchführungs-Tab zeigt je Lauf „zuletzt abgerufen HH:MM:SS", bei Problemen den Grund, und wenn
pausiert einen Knopf **Automatik wieder aufnehmen**, der `raceclocker_auto_paused_at` zurücksetzt
und zugleich den Fingerabdruck im Job vergisst — sonst liefe der nächste Takt in die Abkürzung
„unverändert, nichts schreiben", und die Freigabe hätte sichtbar nichts getan.

Der Knopf hängt am **effektiven** Zeitnahmesystem, nicht an der eigenen Spalte des Wettkampfs: Erbt
er RaceClocker von der Veranstaltung — der Normalfall —, verschwände er sonst genau dort, wo die
Automatik läuft, und ein pausierter Lauf ließe sich nirgends mehr freigeben.

Das Live-Dashboard zeigt nur Fehler und Pause, nicht den Zeitpunkt: `raceclocker_polled_at` steht
bewusst nicht in seinem DTO. `respondETagged` hasht das serialisierte DTO, und ein Feld, das sich
alle fünf Sekunden ändert, macht jede 304-Antwort unmöglich.

## Was der Job bewusst nicht tut

- **Läufe beenden.** Unverändert Handarbeit.
- **Bevorstehende Läufe schreiben.** Kein Bahnen-Update vor dem Start. Ein Umsortieren in
  RaceClocker vor dem Start schlägt erst durch, wenn der Lauf aktiv ist.
- **Abgesagte Slots aktivieren.** Ein `skipped` Slot bleibt abgesagt, auch wenn in RaceClocker
  jemand die Welle startet.
- **Läufe außerhalb der aktuellen Runde anfassen.** `checkUpdateMatchResult` gilt weiter; scheitert
  es, überspringt der Job den Lauf still.

## Tests

Die Fetch-Ebene ist über `MockEngine` bereits getestet (`RaceClockerFeedFetchTest`). Neu, jeweils
als reine Funktion ohne HTTP und ohne Datenbank:

- **Fälligkeit**: welcher Takt für eine Veranstaltung gilt (aktiver Lauf vorhanden oder nicht) und
  ob er seit dem letzten Abruf verstrichen ist.
- **Fenster**: welche Läufe der Vorlauf/Nachlauf einschließt, inklusive der Grenzen und des Falls
  ohne `start_time`.
- **Start-Erkennung**: der Mitternachts-Platzhalter zählt nicht als Start, ein laufendes Boot
  (`In race…`) zählt, `Not started` nicht.
- **Fingerabdruck**: gleiche Zeilen ergeben denselben Wert; eine geänderte Zeit, Bahn oder Strafe
  einen anderen; eine Änderung in einer fremden Welle nicht.
- **Untergrenze der Takte**.

Dazu `RaceClockerPollRepoTest` gegen Postgres (Testcontainers): `getCandidates` ist die einzige
Stelle des Abrufs, die sich nicht als reine Funktion prüfen lässt — und genau dort ist der eine
Fehler entstanden, den kein Lesen gefunden hat (ein aliasierter Ausdruck, den jOOQ im WHERE als
blanken Bezeichner rendert). Jeder Ausschluss der Abfrage steht dort als eigener Fall, dazu die
Coalesce-Reihenfolge Wettkampf vor Veranstaltung.
