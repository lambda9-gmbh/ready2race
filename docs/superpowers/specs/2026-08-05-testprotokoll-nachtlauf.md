# Testprotokoll Testkatalog crf-2026 — 05.08.2026

**Stand:** ab 22:05 Uhr `d7c3cadf` (`feature/crf-2026` inkl. beider Fixes), davor `11f01efd`
**Fixes gemergt:** `1633e3e6` (Verlaufszustände), `d7c3cadf` (Quali-Warnung + Import-Hinweis) — lokal, nicht gepusht
**Umgebung:** Backend `:8080`, Frontend `:5123`, Dev-DB `:7653`, Seed `seed-foerde.sql` (f0de) eingespielt
**Tester:** Claude (browsergesteuert), Abnahme durch TF offen

## RaceClocker-Testrennen (beide **Private**, nur per Link erreichbar)

| Rennen | Key | Typ |
|---|---|---|
| R2R Test Laeufe (nicht sichtbar) | `83da5cfc` | Wave starts |
| R2R Test Zeitfahren (nicht sichtbar) | `995967d0` | Individual starts (Time trial) |

## Ergebnisse

### F — Zeitnahme-Einstellungen

| ID | Ergebnis | Notiz |
|---|---|---|
| F1 | ✓ | Tab „Zeitnahme" zwischen Durchführung und Platzierungen |
| F2 | ✓ mit Befund | nicht gesetzt = nur Auswahl; RaceClocker = 2 URLs + 2 Presets; Webscorer = 1 Preset ohne URLs. **Befund 1** |
| F3 | ✓ | Wechsel auf „nicht gesetzt" räumt System, Presets und URLs in der DB |
| F4 | ✓ | Startliste = Direktaktion, Menü nur „als PDF / als CSV", kein Preset-Dialog |
| F6 | ✓ mit Befund | leerer Quali-Slot → 400 `STARTLIST_CONFIG_NOT_CONFIGURED`, kein Rückfall. **Befund 2** |
| F10 | teilweise ✓ | Lücken werden im Tab benannt und verschwinden nach Vervollständigung; Quali-Lücke wird nie gemeldet (Befund 2) |
| F12 | ✓ | Speichern ohne URLs erlaubt, Warnung schrumpft korrekt |

### C — RaceClocker

Aufbau: Wettkampf „2 | Männer Doppelvierer mit Steuerperson", Runde Finale, 6 Boote.
Fünf davon nach RaceClocker importiert (Sonderburg absichtlich nicht), vier mit Zeit,
Eckernförde ohne Zielzeit.

| ID | Ergebnis | Notiz |
|---|---|---|
| C1 | ✓ | CSV trägt R2R-UUID in Spalte 1, keine Kopfzeile, UTF-8 korrekt (`C3 B6` für ö); im Feed steht sie als Wert in `ExtraInfo` |
| C2 | ✓ | Wellenname `10:20 Finale`, Lauf wird im Feed gefunden |
| C3 | ✓ | Schleswig 1 (0:20:58.1), Flensburg 2, Kiel 3, Husum 4 — Plätze aus den Zeiten abgeleitet, jede Zeit am richtigen Boot |
| C4 | ✗ → ✓ nach Fix | Vor dem Fix wurde das ungetimte Boot als ausgeschieden markiert (**Befund 3**); nach dem Fix bleibt es unangetastet, die vier getimten behalten Zeit, Platz und Strafe |
| C5 | ✓ | `started_at` = 2026-08-15 10:20:00, aus der frühesten gemessenen Startzeit auf den Renntag gelegt |
| C6 | ✓ | Eckernförde hatte beim ersten Pull `Start 00:00:00.0`; `started_at` wurde trotzdem auf 10:20 gesetzt, nicht auf Mitternacht |
| C7 | ✓ | 15 s Strafe: Zeit 21:15.3 → 21:30.3 (genau einmal gerechnet), `penalty_seconds = 15`, Grund am Boot |
| C8 | ✓ | Nach Tausch in RaceClocker folgen die Bahnen der neuen Reihenfolge, Zeiten und Strafe bleiben am Boot, der Bib wandert nicht mit |
| C9 | offen | noch nicht geprüft |
| C10 | ✗ → ✓ nach Fix | Vor dem Fix: 204 und **alle sechs Boote auf DNS**. Nach dem Fix: 400 „No timed results for this heat in the RaceClocker feed yet", DB unverändert |
| C11 | ✓ | Vier unterscheidbare Fälle: 422 (Adresse nicht auf raceclocker.com), 400 `RACECLOCKER_URL_MISSING`, 502 `RACECLOCKER_MALFORMED_FEED` (unbekannter Rennschlüssel), 400 `RACECLOCKER_MATCH_NOT_IN_FEED` |
| C12 | ✓ | Zweiter Pull ohne Änderung in RaceClocker lässt die Bahnen 1–5 unverändert |
| C13 | ✓ | Sonderburg (keine Zeile im Feed) behält Bahn 6, oberhalb der importierten, keine Kollision |
| C14 | offen | noch nicht geprüft |

Gegenprobe zum Fix: Eckernförde in RaceClocker auf **DNF** gesetzt → Pull schreibt `failed = true`,
Grund `DNF`. Der Fix unterdrückt echte Ausscheidungen also nicht.

### A — Athleten-Anzeige

Aufbau: Renntag 1 der Förde Testregatta per SQL auf den heutigen Abend gelegt (20:00–23:58).
21:00 Frauen Doppelzweier gelaufen und gewertet, 21:50 Männer Doppelvierer laufend mit
Teilergebnissen, danach wartende Slots und Programmpunkte.

| ID | Ergebnis | Notiz |
|---|---|---|
| A1 | ✓ | Bei 1920 px drei Spalten: links „Letztes Ergebnis", Mitte „Aktueller Lauf", rechts „Nächster Lauf" |
| A4 | ✓ | „gestartet 21:52" unter der geplanten Zeit 21:50, geplante Zeit bleibt stehen |
| A5 | ✓ | Gewertete Boote „Platz. Zeit", das ungewertete Boot zeigt rechts nichts |
| A6 | ✓ | „inkl. 15 s Zeitstrafe · Bojenberuehrung" unter der Zeit, Zeit um genau 15 s höher |
| A7 | ✓ | Manuelle Strafe erscheint als „inkl. 10 s Zeitstrafe · Bahn verlassen" |
| A9 | ✓ | DNF statt Zeit, gedämpft, Platz „–" im beendeten und ohne Platz im laufenden Lauf |
| A10 | ✗ → ✓ nach Fix zu Befund 4 | „– | Husumer Ruderverein | Bahn 3 | abgemeldet · Steuerfrau erkrankt" unten im Ergebnis, Platz „–", keine Zeit |
| A12 | ✓ | Große Zahl links = Bahn (laufender Lauf); im Ergebnis große Zahl = Platz, Bahn als Unterzeile |
| A13 | ✓ | „Lauf noch nicht gesetzt" an der Stelle des Zeitplan-Slots |
| A8 | ✓ | Beendeter Lauf wandert nach „Letztes Ergebnis", Zeiten und Strafhinweise bleiben unveraendert |
| A14 | ✓ | „Mittagspause" und Besprechungen erscheinen erst, nachdem `show_breaks_on_public_boards` gesetzt wurde |
| A15 | teilweise | Verstrichene Startzeit zeigt „erwartet" statt negativem Countdown ✓, aber Platzhalter verschwinden nie. **Befund 5** |
| A18 | ✓ | Bei 390 px Blöcke untereinander, Namen brechen um, `scrollWidth == innerWidth` (keine waagerechte Rolle) |
| A19 | ✓ | Abruf alle 15 s gemessen, nie schneller als 10 s |
| A20 | ✓ | In einem isolierten Browser-Kontext ohne Anmeldung vollständig nutzbar |

### D — Schiedsrichter-Dashboard

| ID | Ergebnis | Notiz |
|---|---|---|
| D1 | ✓ | Live- und Läufe-Tab vorhanden, Liste vollständig |
| D9 | ✓ | Formular sagt „Teilergebnisse sind möglich: Zeilen ohne Platz und ohne Zeit bleiben offen" |
| D10 | ✓ | DNS/DNF/DSQ als getrennte Schalter mit Notizfeld, unterscheidbar dargestellt |
| D11 | ✓ | „Zeitstrafen werden nur vermerkt und nicht auf die Zeit angerechnet — die eingetragene Zeit gilt wie erfasst (inkl. Strafe)" |
| D13 | ✓ | Vereinsnamen gekürzt („RV Eckernförde"), große Schrift |
| D14 | ✓ | Programmpunkte erscheinen an ihrer Zeitposition mit Kennzeichnung „Programmpunkt" |
| D15 | **durchgefallen** | Karte sagt „Beendet", obwohl `finished_at` null ist. **Befund 7** |
| D17 | ✓ | Kein Start-Knopf; „Lauf aktivieren" setzt nur `currently_running`, `started_at` bleibt unangetastet |
| D18 | ✓ | Platzhalterkarte „Lauf noch nicht gesetzt" an ihrer Zeitposition, mit Absage-Aktion |

Nebenbei: die Route heißt `/event/{eventId}/liveDashboard`, im Katalog steht `live-dashboard`.

## Befunde

### Befund 1 — RaceClocker-Hinweis erscheint auch unter Webscorer

`frontend/src/components/event/competition/timing/CompetitionTimingConfig.tsx:179`

Der Alert `event.competition.timing.importHint` hängt am Zweig `timingSystem !== 'NONE'`, sein Text
ist aber rein RaceClocker-spezifisch („… insbesondere die interne Kennung auf ‚Extra info'").
Unter Webscorer steht damit eine Anweisung, die dort ins Leere zeigt. Kosmetisch, irreführend.

### Befund 2 — Fehlender Quali-Slot wird nirgends gewarnt, bricht aber den Export

`frontend/src/components/event/competition/timing/timingConfigForm.ts:75`

`timingConfigWarnings` lässt Zeitfahren-URL und Quali-Preset bewusst aus (Kommentar: ein Wettkampf
ohne Qualifikationsrunde braucht beides nie). Bei einem Wettkampf, der **eine Qualifikationsrunde
hat** (hier „12 | Männer Einer Sprint"), ist der Startlisten-Export der Quali-Runde damit still
kaputt: 400 im Hintergrund, keine Warnung im Zeitnahme- und keine im Durchführungs-Tab.
Vorschlag: die Warnung an „Wettkampf hat eine Quali-Runde" knüpfen statt sie pauschal auszunehmen.

### Befund 3 — Ein Pull mitten im Lauf wertet jedes noch fahrende Boot als ausgeschieden

`backend/.../app/raceclocker/entity/RaceClockerFeedRow.kt:52` und
`backend/.../app/competitionExecution/boundary/CompetitionExecutionService.kt:920`

**Schwere: hoch.** Das ist genau der Ablauf, den der Katalog für A5, A6 und D9 vorschreibt —
Ergebnisse holen, während der Lauf läuft.

Beobachtung am Testlauf:

| Zustand in RaceClocker | `Result` im Feed | was r2r daraus macht |
|---|---|---|
| Zeit genommen | `00:21:15.3` | Zeit + Platz, korrekt |
| gestartet, noch auf dem Wasser | `In race...` | `failed = true`, `failed_reason = "In race..."` |
| noch nicht gestartet | `Not started` | `failed = true`, `failed_reason = "Not started"` |

Die Ursache ist eine Annahme, die nicht zutrifft. `RaceClockerFeedRow` kommentiert:
„RaceClocker has no status field … Anything that is not a parsable time is therefore a no-result
reason." RaceClocker benutzt die Result-Spalte aber auch für **Verlaufszustände**: `Not started`
ist der Ausgangszustand jedes Eintrags, `In race...` heißt „fährt gerade". Beides ist keine
Ausscheidungsmeldung — DNS/DNF/DQ setzt der Zeitnehmer separat über das Dropdown.

`CompetitionExecutionService` will diesen Fall ausdrücklich abfangen — „Crews that have not been
timed yet are skipped rather than treated as an error, so the pull can be repeated as the heat
progresses" — aber die Prüfung lautet `it.result != null`, und `"In race..."` ist nicht null.
Die Zeile rutscht durch und wird über `noResultReason` zu `failed = true`.

Folgen:
- Bei jedem Pull mitten im Lauf stehen alle noch fahrenden Boote als „Ausgeschieden (In race...)".
- Die Athleten-Anzeige zeigt für sie einen Ausscheidungsgrund statt gar nichts (A5, A9).
- Für Urkunden gilt ein `failed`-Boot als ausgeschlossen (G12).

Es heilt sich selbst: sobald eine Zeit ankommt, setzt `applyParsedTeamResults` `failed` wieder auf
`false` (`CompetitionExecutionService.kt:854`). Während des Laufs — also genau dann, wenn die
Anzeige draußen steht — ist das Bild aber falsch.

Vorschlag: `isTime` um eine Liste bekannter Verlaufszustände ergänzen (`Not started`, `In race...`)
und diese Zeilen wie ungetimte behandeln, statt sie als Ausscheidungsgrund zu übernehmen. Nur
`DNS`, `DNF` und `DQ` sind echte Ausscheidungen. Die genaue Liste sollte bei Cees abgesichert
werden — siehe die bestehende Korrespondenz.

### Befund 4 — Nach dem Setzen der Runde lässt sich kein Boot mehr abmelden

`backend/.../app/competitionDeregistration/boundary/CompetitionDeregistrationService.kt`

**Schwere: hoch.** Der Fall kommt am Regattatag ständig vor.

```kotlin
!KIO.failOn(match?.teams?.any { it.place != null || !it.failed } ?: false) {
    CompetitionDeregistrationError.ResultsAlreadyExists
}
```

`!it.failed` ist invertiert. Ein Boot „hat ein Ergebnis", wenn es einen Platz hat **oder**
ausgeschieden ist — hier ist die Bedingung für jedes nicht-ausgeschiedene Boot wahr, und in einer
frisch gesetzten Runde ist das jedes Boot. Ergebnis: 409 mit „In the current round the results were
already entered", obwohl kein einziges Ergebnis erfasst ist.

Nachgestellt an „1 | Frauen Doppelzweier": Runde Finale mit vier Booten frisch erstellt, keine
Ergebnisse, Abmeldung → 409.

Zweiter Teil: **das Frontend zeigt den Fehler nicht.** Der Abmelde-Dialog bleibt offen stehen, ohne
Meldung — aus Nutzersicht passiert beim Klick auf „Speichern" nichts.

Fix beauftragt.

### Befund 5 — Veraltete Platzhalter verstopfen „Nächster Lauf"

`backend/.../app/eventInfo/boundary/EventInfoService.kt`, `mergeWithPendingPlaceholders`

**Schwere: hoch**, weil es die öffentliche Anzeige betrifft.

Die 30-Minuten-Karenz wird nur an die Abfrage der **echten** Läufe gereicht. Wartende Slots und
Programmpunkte werden ohne Zeitprüfung dazugemischt und dann auf drei Einträge gekappt.

Gemessen um 22:32 Uhr, Spalte „Nächster Lauf":

| Eintrag | Startzeit | Rückstand |
|---|---|---|
| Wettkampfrichter-Besprechung | 20:00 | 2,5 h |
| Obleute-Besprechung | 20:30 | 2 h |
| Frauen Einer (wartender Slot) | 21:10 | 82 min |

Die tatsächlich anstehenden Läufe um 22:50 und 23:35 waren dadurch **nicht sichtbar**. Dasselbe
Muster im Schiedsrichter-Dashboard: „Als Nächstes" zeigte um 22:22 Uhr die Besprechung von 20:00.

Fix beauftragt.

### Befund 6 — Abmeldung vor dem Setzen der Runde hinterlaesst eine unsichtbare Zeile

**Schwere: niedrig.** Nach dem Fix zu Befund 4 ist A10 gruen (siehe oben) — dieser Rest bleibt.

Es gibt zwei Wege zu einem abgemeldeten Boot, und sie verhalten sich unterschiedlich:

| Zeitpunkt der Abmeldung | `competition_setup_round` in `competition_deregistration` | Ergebnis |
|---|---|---|
| **nach** dem Setzen der Runde | gesetzt | Boot bleibt im Lauf, erscheint ueberall als „abgemeldet · Grund" — korrekt |
| **vor** dem Setzen der Runde | `null` | Boot bekommt trotzdem eine `competition_match_team`-Zeile mit eigener Bahn, wird aber weder in der Durchfuehrung noch im Dashboard noch auf der Anzeige gezeigt |

Konkret an „1 | Frauen Doppelzweier": Ruderverein Kiel wurde vor dem Setzen abgemeldet und belegt in
der Datenbank Bahn 5 des Finales, waehrend alle Ansichten nur vier Boote kennen. Die Bahnnummer ist
damit vergeben, aber unsichtbar.

Das ist kein Anzeigefehler im engeren Sinn — ein Boot, das vor dem Setzen zurueckgezogen wurde,
gehoert nicht auf die Anzeige. Die Frage ist, ob es ueberhaupt eine Zeile im Lauf bekommen sollte.
Zur Entscheidung an Ilka gegeben — siehe `2026-08-06-offene-fragen-ilka.md`, Punkt 1.

### Befund 7 — „Beendet" auf einem Lauf, der nie beendet wurde (D15)

`backend/.../app/liveDashboard/boundary/LiveDashboardLogic.kt:42` (`deriveMatchState`)

```kotlin
finishedAt != null -> LiveDashboardMatchState.FINISHED
teamResults.isNotEmpty() && teamResults.all { it } -> LiveDashboardMatchState.FINISHED
```

Beide Fälle liefern denselben Zustand. Ein Lauf mit vollständigen Ergebnissen, aber ohne gedrücktes
„Beenden" (`finished_at` ist null), wird deshalb als **„Beendet"** beschriftet und aus dem Live-Tab
und dem Zeitstrahl entfernt.

Der Katalog verlangt für D15 ausdrücklich das Gegenteil: „Vollständige Ergebnisse … beenden den Lauf
nicht; die Karte zeigt ‚Ergebnisse vollständig — wartet auf Beenden'." Der Zustand, den D15
beschreibt, existiert im Modell gar nicht — es braucht einen eigenen Wert neben `FINISHED`.

Noch kein Fix beauftragt: das ist eine Modelländerung mit Auswirkung auf die Laufkette
(Fortschaltmodus), die mit Thomas abgestimmt werden sollte.

### E — Betrieb

| ID | Ergebnis | Notiz |
|---|---|---|
| E1 | ✓ | `Content-Encoding: gzip` auf dem oeffentlichen Board-Endpoint |
| E2 | ✓ | 40 Abrufe bei warmem Cache -> **0** DB-Transaktionen (`pg_stat_database.xact_commit`). Wichtig beim Nachmessen: offene Browser-Tabs pollen mit und verfaelschen die Zahl |
| E3 | ✓ | 700 gleichzeitige Abrufe: 500 x 200, 200 x 429 mit `Retry-After: 3` und „Too many requests. Try again in 3 seconds" |
| E4 | ✓ | `/liveDashboard` ohne Session 401, `/info/athlete-board` ohne Session 200 |
| E5 | ✓ | Dashboard-Antwort traegt je Boot nur, was die Liste zeigt: Platz, Zeit, failed, deregistered, Auflagen-Zaehler, Rechnungsstatus, substituted — keine Personendaten |
| E6 | ✓ | Postgres `Etc/UTC`, Anwendung meldet 22:51 (CEST), Slot steht als naiver Zeitstempel 22:50 in der DB und erscheint als 22:50 |
| E7 | ✓ | `event_view` liegt als VIEW vor, Migration `202608051000 chain progression mode` erfolgreich |
| E8 | ✓ | Flyway-Verlauf: `202608051200` (Rang 70, 21:12) hinter `202608051500` (Rang 69, 15:53) — Migration ausser der Reihe lief mit `-Dflyway.outOfOrder=true` nach |

### Befund 8 — Eine Absage bleibt auf der Athleten-Anzeige unsichtbar

**Schwere: hoch.** Ein Lauf absagen ist genau das, was man bei Wetterumschwung oder Zeitverzug tut —
und die Athleten sehen es nicht.

Nachgestellt: Slot 23:40 „Mixed Doppelzweier – Finale" ueber „Nur diesen Lauf entfallen lassen"
abgesagt (`skipped_at` und `skipped_by` sind gesetzt, Audit stimmt). Danach:

- Athleten-Anzeige, Spalte „Naechster Lauf": **„Mixed Doppelzweier, Finale, 23:40" steht weiter drin**
- Schiedsrichter-Dashboard: Zustand `UPCOMING`, kein Hinweis auf die Absage

Ursache-Vermutung: die Abfrage der echten Laeufe (`CompetitionMatchRepo.getUpcomingMatchesForBoard`)
prueft den Slot-Zustand nicht. Die Platzhalter-Seite tut es (`pendingSlotOrNull` filtert auf WAITING,
SKIPPED faellt raus) — sobald die Runde aber **gesetzt** ist, gibt es einen echten Lauf, und der
kennt die Absage seines Slots nicht.

### Befund 9 — Ein aktivierter Lauf laesst sich absagen und ist danach beides zugleich

`backend/.../app/eventSchedule/boundary/EventScheduleService.kt:216`

Die Schutzregel prueft ausschliesslich `started_at` (die Ist-Startzeit aus der Zeitnahme):

```kotlin
if (matchStartedAt != null) {
    return@comprehension KIO.fail(EventScheduleError.MatchAlreadyStarted(slotId))
}
```

`currently_running` bleibt aussen vor. Ein Lauf, den der Schiedsrichter aktiviert hat, dessen
Ist-Start aber noch nicht aus RaceClocker angekommen ist, laesst sich damit absagen — genau das
Zeitfenster zwischen „Boote gehen an den Start" und „Zeitnahme meldet den Start".

Nachgestellt am 22:50-Lauf: `PUT /schedule/slot/{id}/skip` antwortet **204**, danach steht in der
Datenbank `skipped_at` gesetzt **und** `currently_running = true`. Die Athleten-Anzeige zeigt den
Lauf unveraendert als „Aktueller Lauf", das Dashboard als `RUNNING`.

Der Doc-Kommentar der Funktion beschreibt genau dieses Verhalten als gewollt („erlaubt fuer FREE,
WAITING und LINKED ohne `started_at` am Lauf"). Der Katalog verlangt unter B5 aber „ein bereits
gestarteter Lauf laesst sich nicht absagen" — und aus Sicht des Schiedsrichters ist ein aktivierter
Lauf gestartet. Zu entscheiden: Schutzregel auf `currently_running` erweitern, oder die Absage muss
den Lauf zugleich deaktivieren. Ein Zustand „abgesagt und laufend" darf jedenfalls nicht entstehen.

### B — Zeitstrahl und Laufkette

| ID | Ergebnis | Notiz |
|---|---|---|
| B1 | teilweise ✓ | Slot per API angelegt/geaendert/geloescht; die Zeit im Bearbeiten-Dialog ist `readonly` und nur ueber den Datumswaehler erreichbar |
| B2 | ✓ | Zustaende „Programmpunkt", „Verknuepft", „Beendet", „Lauf noch nicht gesetzt" werden unterschieden |
| B3 | ✓ | Drei Methoden (+X Minuten / Startzeit setzen / Aufholen bis); „Anwenden" bleibt gesperrt, bis eine Vorschau lief; Vorschau und Ergebnis stimmten exakt ueberein |
| B4 | ✓ funktional, **Meldung durchgefallen** | Shift ueber Mitternacht wird abgelehnt — aber mit „Shift request parameters are inconsistent". **Befund 10** |
| B5 | ✓ Audit, **zweite Haelfte durchgefallen** | `skipped_at`/`skipped_by` werden gesetzt; ein aktivierter Lauf laesst sich trotzdem absagen. **Befund 9** |
| B6 | teilweise | Oberflaeche sagt „entfaellt" und „wird von der Kette ausgelassen" — „uebersprungen" kommt nicht vor, „abgesagt" aber auch nicht |
| B8 | ✓ | REGATTABUERO: Dashboard-Beenden fehlt in der Oberflaeche und antwortet mit 409 „Finishing is handled by the regatta office for this event". SCHIEDSRICHTER: Beenden im Dashboard vorhanden und wirksam |
| B9 | ✓ | Nach dem Beenden des 21:50-Laufs hielt die Kette am wartenden Slot; sobald dessen Runde gesetzt war, wurde er automatisch aktiv |
| B11 | ✓ | Aktivieren und Beenden aus dem Zeitplan je 204; erneutes Aktivieren eines beendeten Laufs 409 „is already finished" |
| B12 | ✓ | Manuelle Startzeit am Slot-gefuehrten Lauf: 409 „Start time is managed by the event schedule" |
| B13 | ✓ | „Jetzt"-Marke im Zeitplan-Tab je Renntag und auf dem Dashboard an der richtigen Stelle |
| B14 | ✓ | „Runde entfaellt" sitzt in der Durchfuehrung, erscheint nur an der Freilos-Runde („Teams mit Freilos (2)"), und markiert beim Ausloesen genau deren beide Slots |
| B15 | ✓ | Runde mit fahrbaren Laeufen: 409 „still has runs to race — they must be executed for seeding". Nicht gesetzte Runde: 409 „has no runs yet — cancel its slots individually instead" |
| B16 | teilweise | Der Lauf-Dialog sagt „Nur diesen Lauf" ✓; der Runden-Dialog nennt **keine Anzahl** („Die Runde … entfaellt komplett" statt „alle N Laeufe") |
| B19 | ✓ | „Cannot compress: only 20 minutes available" mit `details.maxReductionMinutes: 20` — strukturiert |
| B20 | ✓ | Slot wandert auf 21:55, der **beendete** Lauf behaelt `start_time` 21:50 |
| B21 | ✓ funktional | Vorziehen um −35 min (ueberholt den Vorgaenger) abgelehnt, −20 min erlaubt; Meldung wie B4 (Befund 10) |
| B23 | ✓ | Aktionssymbole stehen ueber alle Zeilen in festen Spalten |

Für den Freilos-Fall (B14) wurde zusaetzlich `seed-freilos.sql` eingespielt (Praefix `fee1`,
Event „Freilos Testregatta") — er laesst `5eed` und `f0de` unangetastet.

### Befund 10 — Vier verschiedene Ablehnungsgruende, eine nichtssagende Meldung

`backend/.../app/eventSchedule/entity/EventScheduleError.kt:45`

`InvalidShiftRequest` → „Shift request parameters are inconsistent" deckt ab:

1. Verschiebung um 0 Minuten
2. ungueltiges Ziel beim Stauchen
3. Verschiebung ueber den Renntag hinaus (**B4** verlangt hier ausdruecklich „eine verstaendliche Meldung")
4. Vorziehen, das den Vorgaenger-Slot ueberholen wuerde (**B21**)

Die Meldung ist ausserdem unuebersetzt und schlaegt woertlich in die Oberflaeche durch. Zum
Vergleich: der Nachbarfall `CompressionImpossible` macht es richtig — „Cannot compress: only 20
minutes available" samt `details.maxReductionMinutes`. Vorschlag: je Grund ein eigener Fehler mit
eigenem Text (und dort, wo es hilft, strukturierten Angaben wie der frueheste erlaubte Zeitpunkt).

Nachtrag zum B-Block (Excel-Import, am Event „Freilos Testregatta" geprueft):

| ID | Ergebnis | Notiz |
|---|---|---|
| B7 | ✓ mit Befund | Zeilenweise Vorschau mit echten Excel-Zeilennummern (2–6), Namensabgleich „FL" + „AF1" → „Freilos Demo Einer – Achtelfinale – AF1", Programmpunkte als FREE. **Aber:** eine unlesbare Zelle meldet nur „Import file could not be read" ohne Zeilennummer. **Befund 11** |
| B20 (Import) | ✓ | Datei gibt fuer das **beendete** Zeitfahren 10:30 vor — der Lauf behaelt `start_time` 10:00, nur der Slot wandert; die ungelaufenen AF1/AF2 folgen der Datei |
| B22 | ✓ | Vorschau schreibt nichts (Slots unveraendert); Import ersetzt alle Slots (neue IDs, AF2 10:10→11:15, neuer Punkt dazu); doppelte Zeile blockiert mit 422 „Import contains duplicate matches in rows [4, 5]" |

### Befund 11 — Unlesbare Zelle im Zeitplan-Import nennt die Zeile nicht

`backend/.../app/eventSchedule/boundary/EventScheduleService.kt:558`

```kotlin
}.mapError { EventScheduleError.ImportFileUnreadable }
```

Der XLS-Leser liefert praezise Fehler — `UnparsableStringValue(row, col, value)`, `CellBlank(row, col)`,
`WrongCellType(row, col, actual, expected)`, `ColumnUnknown(expected)` (siehe `xls/XLSReadError.kt`) —
und `mapError` wirft sie samt Zeilennummer weg. Uebrig bleibt „Import file could not be read".

Nachgestellt mit einer Datei, deren Zeile 3 in der Spalte „Uhrzeit" den Text „viertel nach zehn"
enthaelt: 422 ohne jeden Hinweis, wo der Fehler steckt. Bei einem Zeitplan mit 100 Zeilen ist das
Suchen von Hand.

Der Duplikat-Fall daneben macht es vorbildlich („duplicate matches in rows [4, 5]") — die
Zeilennummern sind also bereits das etablierte Muster.

Nachtrag B (Kette und Slots):

| ID | Ergebnis | Notiz |
|---|---|---|
| B10 | ✓ | Aufbau: Quali beendet → Kette parkt am wartenden 08:30-Slot, bleibt auch nach dem Setzen des Halbfinales dort stehen. Absage des Wartepunkts → HF1 (09:00) wird aktiviert |
| B17 | ✓ | Wartender Slot („Lauf noch nicht gesetzt") laesst sich in Zeit und Dauer bearbeiten |
| B18 | ✓ | Verknuepfter Slot verlinkt auf `/event/{id}/competition/{id}?tab=execution` |

Merkposten zum Nachstellen von B10: `resumeIfParked` bricht ab, solange **irgendein** Lauf der
Veranstaltung laeuft (`hasRunningMatch`). Beim ersten Versuch lief noch ein Lauf von Renntag 1, und
die Kette blieb korrekt stehen — das sah zunaechst nach einem Fehler aus, war aber mein Aufbau.

### Befund 12 — Massenfeld-Finale mit aufsteigenden Plaetzen liefert 500

`backend/.../app/competitionExecution/boundary/CompetitionExecutionService.kt:1183` (`computeCompetitionPlaces`)

**Schwere: hoch.** Trifft die Platzierungen-Ansicht, die Urkunden und alles, was Plaetze liest.

`GET /api/event/{e}/competition/{c}/competitionExecution/places` antwortet mit **500**
(`IndexOutOfBoundsException: Index 4 out of bounds for length 0`), wenn die **letzte** Runde

- ein Massenfeld ist (`competition_setup_match.teams IS NULL`) **und**
- `places_option` auf `ASCENDING` (oder `CUSTOM`) steht.

Nachgestellt am Wettkampf „1 | Frauen Doppelzweier" der Förde Testregatta. Gegenprobe: dieselbe Runde
mit `teams = 5` statt `null` liefert korrekt die Plaetze 1–5.

Warum: fuer die letzte Runde ist `maxTeamsNeeded = 0` (es gibt keine Folgerunde). `getSeedingList`
fuellt bei `teams == null` nur, solange `seedingsTaken < maxTeamsNeeded` — also nie. Die Seeding-Liste
bleibt `[[]]`, und `seedingList!![matchIndex][realPlace - 1]` greift ins Leere.

Fuer Coastal Rowing ist das Massenfeld-Finale der Normalfall — der Seed `seed-foerde.sql` modelliert
es genau so. Aufgefallen ist es nur deshalb nicht sofort, weil der Seed `places_option = EQUAL`
verwendet, und dieser Zweig kommt ohne Seeding-Liste aus (jedes Boot bekommt Platz 1).

Nebenbei in derselben Funktion (Zeile ~1214):

```kotlin
if (round.placesOption != CompetitionSetupPlacesOption.ASCENDING.name || round.placesOption != CompetitionSetupPlacesOption.CUSTOM.name)
```

Diese Bedingung ist **immer wahr** — ein Wert kann nicht gleichzeitig beides sein. Der Kommentar
darueber sagt „Only relevant if the placesOption is 'ascending' or 'custom'", gemeint war also
vermutlich `==` und `||`. Folgenlos, solange die Liste ohnehin gebraucht wird, aber irrefuehrend.

**Hinweis zum Seed:** weil `seed-foerde.sql` die Finals auf `EQUAL` stellt, tragen alle daraus
erzeugten Siegerurkunden „1. Platz". Wer den Seed fuer Urkunden-Tests nutzt, muss die Runde vorher
auf `ASCENDING` stellen — und stolpert dann ueber den Fehler oben.

### Befund 13 — Typografische Zeichen werden aus allen PDF-Texten geloescht

`backend/src/main/kotlin/de/lambda9/ready2race/backend/text/TextExtensions.kt:31`

```kotlin
.filter {
    !Character.isISOControl(it) &&
        Character.UnicodeBlock.of(it) != null &&
        it !in 0x200E..0x206F
}
```

Der Bereich `0x200E..0x206F` sollte offenbar unsichtbare Steuerzeichen fernhalten (LRM, RLM,
Zero-Width, die Formatierungszeichen ab 0x2060). Er enthaelt aber auch lauter **sichtbare** Zeichen:
Bindestrich U+2010, Gedankenstrich U+2013, Geviertstrich U+2014, typografische Anfuehrungszeichen
U+2018/2019/201C/201D/201E, Aufzaehlungspunkt U+2022, Auslassungspunkte U+2026, Guillemets
U+2039/203A. Alle werden **ersatzlos geloescht** — nicht ersetzt.

Belegt an einer erzeugten Siegerurkunde:

| Eingabe | auf der Urkunde |
|---|---|
| `5.–16. August 2026` (aus `AwardCertificateLogic.formatEventDate`) | `5.16. August 2026` |
| `AZS Łódź – Sekcja Wioślarska` | `AZS ?ódz  Sekcja Wioslarska` (Gedankenstrich weg, doppeltes Leerzeichen bleibt) |
| `ČVK Praha ’Vltava’` | `CVK Praha Vltava` |

Der Datumsfall trifft **jede** Urkunde einer mehrtaegigen Veranstaltung: `formatEventDate` baut den
Bereich korrekt mit Gedankenstrich, und der Sanitizer macht daraus „5.16. August 2026".

Betroffen ist nicht nur die neue Siegerurkunde — `sanitizeNonPrintable` laeuft in `pdf/documents.kt`
vor jeder Textausgabe, also auch bei den bestehenden Teilnahmeurkunden.

Zwei Stellschrauben: den Filterbereich auf die tatsaechlich unsichtbaren Teilbereiche eingrenzen
(0x200B–0x200F, 0x202A–0x202E, 0x2060–0x206F), und in `sanitizeForFont` eine ASCII-Ersetzung fuer
gaengige Typografie ergaenzen (– zu -, ’ zu ', „ zu ", … zu ...). Letzteres hilft auch G20: `ł`
zerlegt sich per NFD nicht und wird deshalb zu `?`, obwohl `l` die naheliegende Ersetzung waere.

Zur Abgrenzung: **G20 selbst ist gruen** — der Download laeuft durch, bricht nicht ab, und die
uebrigen Zeichen werden sinnvoll ersetzt (ó zu o, ś zu s, Č zu C).

### Befund 14 — Quali mit mehr Booten als Aufsteigern liefert 500 (gleiche Wurzel wie Befund 12)

`CompetitionExecutionService.kt:1246`, ausgeloest ueber `GET /api/event/{e}/awardCertificates`

```
IndexOutOfBoundsException: Index 5 out of bounds for length 4
```

Wettkampf „12 | Maenner Einer Sprint", aufgebaut wie das eingebaute Beachsprint-Template:

| Runde | places_option | Laeufe | teams je Lauf | Boote |
|---|---|---|---|---|
| Zeitfahren | ASCENDING | 1 | `null` (Massenfeld) | 6 |
| Halbfinale | EQUAL | HF1, HF2 | 2 | je 2 |
| Finale | CUSTOM | Finale A, Finale B | 2 | noch nicht gesetzt |

Die Zeitfahren-Runde ist nicht die letzte, also `maxTeamsNeeded = 4`; die Seeding-Liste wird
`[[1,2,3,4]]`. `nonAdvancingTeamsToMatchIndex` enthaelt aber genau die Boote, die **nicht**
weiterkommen — mit `realPlace` 5 und 6. Der Zugriff `seedingList!![matchIndex][realPlace - 1]`
greift daneben.

Das ist der Standardaufbau fuer Beachsprint, kein Randfall: sobald das Halbfinale gesetzt ist, sind
die Platzierungen-Ansicht und der Urkunden-Download ueber die Veranstaltung fuer diesen Wettkampf
kaputt. Beide Abstuerze (Befund 12 und 14) kommen aus demselben Ausdruck und sind gemeinsam an den
Fix-Agenten gegeben.

### G — Urkunden

Aufbau: Vorlagen selbst erzeugt (reportlab), nachempfundenes DRV-Amtspapier mit Hilfslinien an den
fuenf Platzhalter-Positionen; per Multipart-API angelegt und zugewiesen.

| ID | Ergebnis | Notiz |
|---|---|---|
| G1 | ✓ | Typ „Siegerurkunde" anlegbar; der Server prueft die erlaubten Platzhalter und dass Siegerurkunden einseitig sind (Platzhalter auf Seite 2 → 400) |
| G2 | ✓ | Unbrauchbare Schriftdatei faellt **beim Anlegen** auf („Font file could not be read"), nicht erst beim Erzeugen; gilt auch fuer eine falsche Endung |
| G3 | ✓ | Ohne hochgeladene Schrift wird Helvetica genutzt, die Urkunde ist druckbar |
| G4 | ✓ | Plaetze 1–3, nach Platz sortiert, alle fuenf Platzhalter gefuellt (Platz, Wettkampf, Verein, Renntage, Ort) |
| G7 | ✓ (Datei) | `?format=docx` liefert „Microsoft OOXML". **Nicht geprueft:** ob die Rahmen in Word an den Vorlagenkoordinaten sitzen — das braucht Word |
| G8 | **durchgefallen** | Download ueber die Veranstaltung endet mit 500. **Befund 14** |
| G9 | ✓ | Einzeldownload liefert die Urkunde fuer **Platz 5**, obwohl `maxPlace=3` mitgegeben wurde — die Platzgrenze gilt dort korrekt nicht |
| G10 | ✓ | Standardmaessig ohne Hintergrund (kein Amtspapier-Text im PDF); mit `background=true` erscheint die Vorlage |
| G11 | ✓ | `maxPlace=3` → 3 Seiten (1.–3. Platz), `maxPlace=99` → 5 Seiten (1.–5.); das DNF-Boot bleibt in beiden Faellen draussen |
| G12 | ✓ (Daten) | Abgemeldete, ausgeschiedene und DNF-Boote erscheinen nicht in den Urkunden. **Nicht geprueft:** ob in der Ergebniszeile das Urkunden-Symbol fehlt (Oberflaeche) |
| G13 | ✓ | Ohne berechnete Plaetze: 400 „No placed teams for these certificates" statt leerer Urkunden |
| G14 | ✓ (Backend) | Ohne zugewiesene Vorlage: 409 „There is no template assigned for award certificates". **Nicht geprueft:** der Hinweis mit Verweis auf die Konfiguration im Dialog |
| G17 | ✓ | Ohne Anmeldung 401 |
| G20 | ✓ | `AZS Łódź – Sekcja Wioślarska` und `ČVK Praha ’Vltava’` laufen durch, kein Abbruch; Zeichen werden ersetzt (ó→o, ś→s, Č→C, ł→?). Die geloeschten Striche und Apostrophe sind **Befund 13**, nicht G20 |
| G23 | ✓ | Vorlage des falschen Typs zuweisen: 400 „Template type does not match the document type it is assigned to" |
| G24 | ✓ | Kaputte PDF wird beim Anlegen abgewiesen („Unsupported file type") |

Nicht abnehmbar ohne echtes Papier und Drucker: **G4 (Sitz auf dem Amtspapier), G10, G21**. Ebenso
offen: **G5** (pro Athlet) und **G6** (Renngemeinschaften) — der Seed enthaelt keine Personen und keine
RG; **G19** (Serienlaenge) braucht die groesste Veranstaltung.

Nachtrag G (am `5eed`-Wettkampf „Coastal Frauen Doppelvierer mit Steuerfrau/mann", der als einziger
Personen an den Meldungen hat — dafuer habe ich dort die Ergebnisse aller fuenf Vorlaeufe eingetragen):

| ID | Ergebnis | Notiz |
|---|---|---|
| G5 | ✓ | „pro Athlet": 15 Seiten = 3 Plaetze x 5 Personen, **Steuerfrau inklusive**, `FULL_NAME` traegt den Personennamen. „pro Boot": 3 Seiten, `FULL_NAME` traegt alle fuenf Namen der Mannschaft untereinander |
| G6 | offen | Keine Renngemeinschaft in den Daten |
| G15/G16/G18/G22 | offen | Teilnahmeurkunden sind an Challenge-Events gebunden (`CertificateService`: „Event is not a challenge event"); keine der drei Veranstaltungen ist eins. Zum Pruefen braucht es ein Challenge-Event mit abgelaufenem `challenge_end_at` |

Die zweiseitige Teilnahme-Vorlage ist bereits angelegt und zugewiesen (`vorlage-teilnahme-2s.pdf`,
Platzhalter auf Seite 1 **und** 2) — G22 laesst sich damit sofort pruefen, sobald ein Challenge-Event
existiert. Nebenbei bestaetigt: die Einseitigkeits-Regel gilt nur fuer Siegerurkunden, die
Teilnahme-Vorlage nimmt Platzhalter auf Seite 2 an.

### Nachtrag: restliche Faelle

| ID | Ergebnis | Notiz |
|---|---|---|
| A2 | ✓ | Kiosk: Uhr („00:12") und „Stand" stehen frei; „Konfigurieren"/„Vollbild" liegen darueber. Im Vollbild sitzt das Beenden-Symbol ebenfalls oberhalb und verdeckt nichts |
| A3 | ✓ | Aktivierter Lauf ohne Ergebnisse: geplante Zeit gross, darunter „in Vorbereitung", keine Zeiten |
| A11 | ✓ | Meldung ohne Namen erscheint als „Ruderclub Schleswig \| Team 2" aus `team_number` |
| A16 | ✓ | Lauf ohne Startzeit: Zustand `UNSCHEDULED`, Anzeige „Zeit offen", Lauf bleibt mit seinen Booten sichtbar (sortiert ans Ende, deshalb nur bei hoeherem Limit sichtbar) |
| A17 | ✓ | Nach ~45 s ohne Backend: „Stand 22:57 — Verbindung unterbrochen", letzter Stand bleibt vollstaendig stehen, nie „kein Lauf auf dem Wasser"; erholt sich selbst |
| C9 | ✓ | Doppelte Zeile im Feed: 400 `RACECLOCKER_DUPLICATE_TEAMS` mit Wellenname und Bootsnamen |
| D2 | ✓ | Intervall 5/10/30/60 s waehlbar, Auswahl liegt im `localStorage` (`live_dashboard_poll_interval`), bleibt am Geraet |
| D3 | ✓ | Nach ~50 s ohne Backend: „Verbindung gestoert — Anzeige ist moeglicherweise nicht aktuell.", letzter Stand bleibt |
| D5 | ✓ | Mannschafts-Dialog oeffnet mit Nummer, Name, Verein, Rechnungsstatus und uebersteht einen Nachlade-Takt |
| D8 | teilweise | Rueckfrage kommt und wertet nichts stillschweigend („Offen lassen" laesst das Boot offen). **Aber:** sie sagt „Ein Boot hat noch kein Ergebnis" und **benennt das Boot nicht** |
| F5 | ✓ | Rueckfall auf das Runden-Preset bei Webscorer **und** bei „nicht gesetzt" (Altdaten-Fall per DB hergestellt, ueber die API ist die Kombination nicht mehr speicherbar) |
| F7 | ✓ (Backend) | Beide Slots leer: 400 mit `errorCode: STARTLIST_CONFIG_NOT_CONFIGURED` |
| F8 | ✓ | Upload-Dialog hat nur die Dateiauswahl, kein Preset |
| F9 | ✓ | Bei RaceClocker enthaelt das Ergebnis-Menue „von RaceClocker holen" **und** xlsx als Notausgang |
| F11 | ✓ | Global geloeschtes Preset setzt die Referenz am Wettkampf auf NULL, der Wettkampf laedt weiter |
| F13 | ✓ | Vollstaendiger Round-Trip: Presets hinterlegt, Startlisten fuer Quali **und** Laeufe-Runde geladen, beide in RaceClocker importiert, Ergebnisse gezogen |
| F15 | ✓ | Tab, Feldnamen und alle Fehlermeldungen vollstaendig in de/en/da, inkl. der zwei heute ergaenzten Warnungen. Preset-Namen kommen aus der Datenbank und sind bewusst unuebersetzt |

Beobachtung am Rande (kein Katalogfall): der CSV-Import in RaceClocker **ersetzt** die Startliste,
er ergaenzt sie nicht. Der Kommentar in `CompetitionExecutionService` sagt das Gegenteil
(„RaceClocker only ever inserts, it never updates: importing the same start list twice leaves
duplicates"). Fuer C9 musste das Duplikat deshalb in der Datei stehen. Wert, bei Cees abzusichern.

Zweite Beobachtung: die Kiosk-Seite `/event/{id}/info` laedt ihre Ansichten ueber `/info-views` und
bekommt ohne Anmeldung 401 — sie braucht also eine Sitzung, anders als `/board/{eventId}`. Passt zu
E4, steht aber so nicht im Katalog.

### Nachtrag 06.08.: D6, D7 und G6 mit dem neuen Seed

Grundlage ist `docs/superpowers/seeds/2026-08-06-seed-auflagen.sql` (Praefix `a4f1`, Event
„Auflagen-Testregatta 2026"), der genau fuer diese drei Faelle gebaut wurde.

| ID | Ergebnis | Notiz |
|---|---|---|
| D6 | ✓ | Auf der Laufkarte je Boot ein Symbol — rotes ✗ (fehlende Pflicht), gruener Haken, ⚠ (Zeitfenster). Im Dialog fuenf unterscheidbare Zustaende je Person: „Erfuellt am …", „Nicht erfuellt", „Nicht erfuellt (optional)", „Zu frueh erfuellt … 3 h 30 min vor Start", „Spaet erfuellt … 8 min vor Start" — jeweils mit Zeitstempel, Abstand zum Start und Notiz. Die rollenbezogene Auflage („Steuerpersonen-Wiegen") erscheint nur bei der Steuerperson |
| D7 | ✓ | Im Dialog: „Timm Christiansen (Ruderer:in) — Umgemeldet fuer Malte Soerensen · Rueckenverletzung beim Einrudern – Ersatz aus dem Vereinskader". Ersetzte Person und Grund stehen beide da; auf der Karte weist ein ⇄-Symbol am Bootsnamen darauf hin |
| G6 | ✓ | Urkunde des RG-Boots zeigt „Renngemeinschaft" (aus `mixed_team_term`), die drei anderen ihren Vereinsnamen |

Nebenbei bestaetigt: der Fix zu Befund 12 haelt auch an frischen Daten — die Finale-Runde des neuen
Seeds ist ein Massenfeld (`teams IS NULL`) und liefert mit `ASCENDING` die Plaetze 1–4 statt eines 500ers.

Zwei Regeln, die beim Aufbau auffielen und richtig greifen: Ergebnisse einer nicht mehr aktuellen
Runde sind gesperrt („Match results locked. Only results of the latest round can be edited."), und ein
falsch formatierter Zeitstring wird von der Validierung mit Feldangabe abgewiesen.

### Nachtrag 06.08. (2): D15, Sichtbarkeit, restliche Urkunden

| ID | Ergebnis | Notiz |
|---|---|---|
| D15 | ✗ → ✓ nach Fix | Karte trug „Beendet" bei leerem `finished_at`. Nach dem Fix: Etikett „Ergebnisse vollstaendig — wartet auf Beenden", Knopf „Lauf beenden" statt „Lauf aktivieren", und der Lauf steht wieder im Live-Tab statt „Aktuell laeuft kein Rennen" |
| G15 | ✓ | `?format=docx` liefert „Microsoft OOXML" |
| G16 | ✓ | Vorlage, deren Platzhalter `font_size = NULL` tragen, rendert unveraendert; Name und Ergebnis stehen an ihrer Stelle |
| G18 | ✓ mit Befund | Ein Challenge-Event liefert keine Siegerurkunde — aber mit der Begruendung „No placed teams for these certificates" statt „Challenge-Events haben keine Siegerurkunden". Stimmt nur zufaellig; an den Fehlermeldungs-Auftrag gegeben |
| G22 | ✓ | Zweiseitige Vorlage liefert zwei Seiten; die Platzhalter von Seite 2 (Ergebnis, Nachname) fehlen nicht. Summe ueber zwei Wettkaempfe korrekt: 38500 + 52400 = 90900 m |

### Neu: Sichtbarkeit oeffentlicher Ergebnisse

Der offene Katalogpunkt „Lauf doppelt sichtbar" ist entschieden und umgesetzt: `event.public_results_visibility`
mit zwei Stufen. Am Foerde-Event gemessen (Ergebnis-Limit voruebergehend auf 20 gesetzt):

| Einstellung | sichtbare Ergebnisse |
|---|---|
| `FINISHED_ONLY` (Voreinstellung) | 4 — nur echt beendete Laeufe |
| `RESULTS_COMPLETE` | 6 — zusaetzlich die beiden, die auf „Beenden" warten |

Voreinstellung ist `FINISHED_ONLY` **ohne Backfill**: bestehende Veranstaltungen wechseln damit das
Verhalten. Begruendung im Migrationskommentar — ein zu frueh veroeffentlichtes und danach korrigiertes
Ergebnis ist bereits fotografiert und geteilt, ein spaeter erscheinendes kostet nur den Beenden-Klick,
den der Schiedsrichter ohnehin macht.

### Befund 15 — Teilnahmeurkunde ohne Ergebnis wurde mit „0 m" ausgestellt

`CertificateService.downloadCertificateOfParticipation`

`ChallengeResultParticipantViewRepo.getByEventIdAndParticipantId` liefert eine Liste, nie `null`; das
nachgeschaltete `onNullFail` konnte deshalb nie feuern und `CertificateError.NoResults` war toter Code.
Wer kein Ergebnis hatte — oder nur ein unbestaetigtes, das `verifiedIfNeededOnly` ohnehin aussortiert —
bekam eine Urkunde ueber „0 m", also einen Nachweis ueber eine Teilnahme, die es nicht gab.

Behoben in `7f6034d3` (Pruefung auf die leere Liste). Nachgewiesen am Challenge-Seed:

| Person | vorher | nachher |
|---|---|---|
| Ruben Ostermann (kein Ergebnis) | 200, Urkunde „0 m" | 400 „No results in this event for this participant" |
| Antje Duschek (nur unbestaetigt) | 200, Urkunde „0 m" | 400, dieselbe Meldung |
| Mette Kjaergaard (bestaetigt) | 200, 90900 m | 200, unveraendert 90900 m |

### Nachtrag 06.08. (3): Fehlermeldungen der crf-2026-Features

Gemergt als `2a143861` (fuenf Commits). Backend-Tests danach **347 gruen**, Frontend **362 gruen**. Umfang: 43 Fehlerzweige in vier Bloecken — Zeitplan-Rest (8),
Urkunden (13), Durchfuehrung (19), Live-Anzeigen (9). Damit sinkt die Bestandsaufnahme von ~139 auf
~91 Zweige ohne ErrorCode; die verbleibenden liegen in Stammdaten, Benutzerverwaltung und WebDAV,
also ausserhalb der crf-2026-Features.

Vier neue Frontend-Module nach dem Muster von `deregistrationError.ts`: `certificateError.ts`,
`executionError.ts`, `liveDashboardError.ts` und die Erweiterung von `scheduleError.ts`.

**Live nachgeprueft** (Backend neu gebaut, Server neu gestartet):

| Fall | Antwort | ErrorCode |
|---|---|---|
| Siegerurkunden auf dem Challenge-Event (G18) | 400 „Award certificates are not available for a challenge event" | `AWARD_CERTIFICATE_IS_CHALLENGE_EVENT` |
| Teilnahmeurkunde ohne Ergebnis (Ruben) | 400 | `CERTIFICATE_NO_RESULTS` |
| Beenden im Regattabuero-Modus | 409 | `LIVE_DASHBOARD_FINISH_RESERVED_FOR_OFFICE` |
| **Gegenprobe** Siegerurkunden auf `5eed` | 200, 22 772 Bytes | — |
| **Gegenprobe** Siegerurkunden Foerde | 400 „No placed teams" | `AWARD_CERTIFICATE_NO_RESULTS` |
| **Gegenprobe** Teilnahmeurkunde Mette | 200, unveraendert | — |

Die letzten beiden Gegenproben sind der Beleg, dass die neue Challenge-Pruefung vor `entriesForEvent`
nicht zu frueh greift: Foerde bekommt weiterhin „keine platzierten Teams", nicht die Challenge-Meldung.

**Drei Befunde, die der Agent nebenbei mitgenommen hat** — jeder davon liess vorher einen rohen
i18n-Schluessel oder englischen Text in der Oberflaeche stehen:

- `Substitutions.tsx`: `add.error` und `delete.error` sind in **de und da** vertauscht (add traegt das
  Objekt, delete den String), der Code benutzt es umgekehrt. Beide Ummeldungs-Meldungen erschienen
  als roher Schluessel.
- `ParticipantForEventTable.tsx` gab `error.message` direkt aus — der einzige rohe englische
  Backend-Satz in der Oberflaeche.
- `PARTICIPANT_IMPORT_UNKNOWN_GENDER_VALUE` fehlte in `documentation.yaml` und damit im generierten
  Frontend-Typ; der Fall war dort gar nicht benennbar.

Zusaetzlich abgespalten: `CompetitionExecutionError.MatchIsBye`. `MatchResultsLocked` trug zwei
Bedeutungen; der Freilos-Fall las sich als „nur die aktuelle Runde ist bearbeitbar".
`PlacesNotContinuous` nennt jetzt erwarteten und eingetragenen Platz.

**Zwei Grenzen dieser Pruefung**, damit sie niemand ueberschaetzt:

1. Die Urkunden-Meldungen sind ueber die Oberflaeche kaum erreichbar. Das Download-Menue erscheint nur
   bei `hasChallengeResults`, der Siegerurkunden-Dialog nur ueber Platzierungen eines Wettkampfs —
   ein Challenge-Event hat keine. Die deutschen Texte greifen damit vor allem, wenn sich der Zustand
   zwischen Seitenaufbau und Klick aendert. Richtig ist die Absicherung trotzdem.
2. Gleiches beim Beenden im Regattabuero-Modus: die Oberflaeche blendet den Knopf aus, der Fehler ist
   der Schutz fuer die offene Seite. Nachgewiesen ueber die API, nicht ueber einen Klick.

**Eigener Fehler beim Pruefen, der Datenstand kostete:** Ich habe „Runde loeschen" auf dem Finale des
Auflagen-Seeds geklickt in der Annahme, es werde abgelehnt. Der Knopf ist ein Loeschen, keine Absage —
die Runde war weg. Wiederhergestellt durch erneutes Einspielen von `2026-08-06-seed-auflagen.sql`; der
Seed steht damit wieder auf seinem dokumentierten Stand, meine waehrend D15 dort eingetragenen
Finale-Ergebnisse sind fort. Die Foerde Testregatta ist davon nicht beruehrt.

---

## Zusammenfassung der Nacht

**Endstand:** `9d190227` auf `feature/crf-2026`, sieben Fixes lokal gemergt, nichts gepusht.
Backend-Tests **303 gruen**, Frontend zuletzt **121 gruen**.

### Gemergte Fixes

| Merge | Behebt | Nachgeprueft |
|---|---|---|
| `1633e3e6` | Befund 3 — Pull mitten im Lauf wertete jedes fahrende Boot als ausgeschieden | ✓ C4 und C10 danach gruen, echtes DNF kommt weiterhin an |
| `d7c3cadf` | Befund 1 + 2 — RaceClocker-Hinweis unter Webscorer, fehlende Warnung fuer den Quali-Slot | ✓ beide in der Oberflaeche geprueft |
| `8de0dbcf` | Befund 4 — Abmeldung nach Rundensetzung unmoeglich, Fehler unsichtbar | ✓ Abmeldung geht wieder, damit wurde A10 pruefbar und ist gruen |
| `a761b523` | Befund 5 — veraltete Platzhalter verstopften „Naechster Lauf" | ✓ Spalte zeigt wieder die echten naechsten Laeufe |
| `19eac396` | Befund 8 + 9 — Absage blieb unsichtbar, aktivierter Lauf absagbar | ✓ abgesagter Lauf faellt aus der Anzeige, Dashboard zeigt `SKIPPED`, aktivierter Lauf gibt 409 |
| `411d1fbf` | Befund 13 — sichtbare Typografie wurde aus PDFs geloescht | ✓ „5.–16. August 2026" und der Strich im Vereinsnamen sind zurueck |
| `9d190227` | Befund 12 + 14 — zwei Abstuerze der Platzberechnung | ✓ Massenfeld-Finale liefert Plaetze, Veranstaltungs-Download laeuft (G8 gruen) |

### Offene Befunde ohne Fix

| Nr. | Kurz | Warum offen |
|---|---|---|
| 6 | Abmeldung **vor** dem Setzen hinterlaesst eine unsichtbare Zeile im Lauf | Entscheidung noetig: soll es die Zeile ueberhaupt geben? |
| 7 | Karte sagt „Beendet", obwohl niemand beendet hat (D15) | Modelaenderung mit Wirkung auf die Laufkette — mit dir abzustimmen |
| 10 | Vier Ablehnungsgruende beim Verschieben, eine nichtssagende Meldung | Klein, aber Textentscheidung (B4 verlangt Verstaendlichkeit) |
| 11 | Unlesbare Zelle im Zeitplan-Import nennt die Zeile nicht | Klein; der XLS-Leser liefert die Zeilennummer bereits |
| — | D12: kein Hinweis, warum der Beenden-Knopf im Regattabuero-Modus fehlt | Auslegungsfrage, was D12 genau verlangt |

### Nicht pruefbar mit dieser Datenlage

- **D6, D7** — keine Personen, Auflagen oder Ersatzleute im Foerde-Seed
- **G6** — keine Renngemeinschaft
- **G15, G16, G18, G22** — Teilnahmeurkunden sind an Challenge-Events gebunden, es gibt keins
- **G19** — braucht die groesste Veranstaltung
- **G4 (Sitz auf Papier), G10, G21** — brauchen echtes DRV-Amtspapier und einen Drucker
- **G7 (Word-Rahmen), F15 (Sprachen)** — teilweise; Datei entsteht, die Sichtpruefung fehlt

### Zustand des Teststands

- Renntag 1 der Foerde Testregatta liegt auf **05.08.** (per SQL verschoben), Renntag 2 unveraendert auf 16.08.
- `show_breaks_on_public_boards` steht auf **true**
- Zwei Vereine tragen Testnamen: `AZS Łódź – Sekcja Wioślarska` und `ČVK Praha ’Vltava’`
- Die Finale-Runden von Wettkampf 1 und 2 stehen auf `ASCENDING` (Seed liefert `EQUAL`)
- Das Preset **„Webscorer Teamrennen" wurde bei F11 geloescht** und muss neu angelegt werden
- Zusaetzlich eingespielt: `seed-freilos.sql` (Event „Freilos Testregatta", Praefix `fee1`), dessen
  Zeitplan durch die Import-Tests ersetzt wurde
- Zwei Urkundenvorlagen sind angelegt und zugewiesen (Sieger einseitig, Teilnahme zweiseitig)
- RaceClocker: das Laeufe-Rennen `83da5cfc` traegt nach dem C9-Test nur noch drei Zeilen mit einem
  absichtlichen Duplikat; das Zeitfahren-Rennen `995967d0` hat die sechs Quali-Boote. Beide **Private**.
