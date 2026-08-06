# CRF 2026 — Features, Entscheidungen und Verifikation

Dieser Branch (`feature/crf-2026`) bündelt alle Erweiterungen für die Coastal-Rowing-Regatta 2026
in einem Merge-Request. Das Dokument erklärt, **was** gebaut wurde, **warum** es so gebaut wurde
und **wie** es verifiziert ist — als Lesegrundlage für das Review.

**Eckdaten:** Basis `origin/main`; der aktuelle main-Stand (inkl. PR #93) ist in den Branch
gemergt, der Merge ist konfliktfrei. Enthält zusätzlich den Merge von `origin/issue/94`
(RaceClocker-Integration inkl. der drei Hotfix-Commits bis c820c99a). 12 neue Flyway-Migrationen,
alle versioniert **nach** dem letzten Stand von main (V202606221000); keine bestehende Migration
wurde verändert, nur die wiederholbare `afterMigrate.sql` erweitert.

---

## 1. Features

### 1.1 Live-Dashboard für Schiedsrichter:innen

Mobile-first Seite `/event/$eventId/liveDashboard` (neues Privileg `LIVE_DASHBOARD`) zeigt
laufende, anstehende und beendete Läufe mit Rechnungsstatus, Auflagen-Ampel und Ersatzleuten —
gedacht für das Smartphone am Steg.

- Backend: neues Modul `app/liveDashboard/` mit aggregiertem Endpoint
  `GET /event/{eventId}/liveDashboard`; `LiveDashboardLogic` leitet Zustände und
  Zeitfenster-Ampeln als reine, getestete Logik ab.
- Migration `V202607291400`: zwei optionale Zeitfenster-Spalten auf `participant_requirement`.
- Frontend: zwei Tabs (Live / Läufe), Polling, Detail-Dialog pro Team.

### 1.2 Athleten-Anzeige (öffentliches Board)

Öffentliche, loginfreie Seite `/board/$eventId` für montierte Bildschirme an Start und Ziel:
„Letztes Ergebnis / Aktueller Lauf / Nächster Lauf". Auch als View-Typ in der bestehenden
Kiosk-Rotation nutzbar (`ATHLETE_BOARD` in `info_view_type`, Migration `V202608021200`).

- Backend: öffentlicher Endpoint `GET /event/{eventId}/info/athlete-board`;
  `AthleteBoardLogic` (Config-Auflösung, Start-Zustand, Sortierung) als reine Logik.
- Betriebshärtung siehe 1.10.

### 1.3 Zeitplan / Zeitstrahl

Neuer Event-Tab „Zeitplan": Agenda-Liste mit fixen Uhrzeiten pro Slot, Slot-CRUD, Skip/Unskip
mit Audit, Verschiebe-Dialog (drei Modi) und Excel-Import als primärer Planungsweg
(ersetzt transaktional alle Slots).

- Migration `V202608031200`: neue Tabelle `event_schedule_slot`, Write-Through auf
  `competition_match.start_time`; zusätzlich `started_at`/`finished_at` auf `competition_match`.
- Backend: neues Modul `app/eventSchedule/` mit `EventScheduleLogic`
  (Zustandsableitung, Shift-Mathematik).
- Chain-Progression ist jetzt ein Drei-Wege-Modus (`SCHIEDSRICHTER` / `REGATTABUERO` /
  `DEAKTIVIERT`) statt Boolean (Migration `V202608051000`), siehe Entscheidung 2.3.

### 1.4 Teilergebnisse

Die Ergebniserfassung erlaubt partielle Resultate, ohne den Lauf zu beenden: Zeilen ohne
Platz/Zeit bleiben offen. Ergänzt um DNS/DSQ/DNF-Erkennung (Frontend-Parser über
`failed_reason`, bewusst ohne Schema-Änderung) und einen Rückfrage-Dialog beim Beenden
mit noch offenen Booten.

### 1.5 Zeitstrafen (Penalties)

`competition_match_team` erhält `penalty_seconds` und `penalty_note`
(Migration `V202607301500`) — erfassbar manuell im Ergebnis-Formular und automatisch aus dem
RaceClocker-Feed. Die Strafe wird nur **ausgewiesen**, nie auf die Zeit aufgerechnet
(siehe Entscheidung 2.4).

### 1.6 RaceClocker-Integration (issue/94 + Erweiterungen)

Aufbauend auf `origin/issue/94`: Startlisten-Export mit R2R-UUID in „Extra info",
Wellenname mit Startzeit, Ergebnis-Pull (Zeiten, Plätze, Strafen). Erweiterungen dieses Branches:

- Bahnen werden aus der RaceClocker-Listenposition (`Rank`) übernommen statt aus einem fixen Bib.
- Ist-Start (`started_at`) kommt aus dem `Start`-Feld des Feeds und überschreibt den
  manuellen Stempel.
- Feed-Duplikate werden mit konkreter Namensliste abgelehnt statt geraten.
- SSRF-Härtung im Feed-Abruf: Host-Allowlist, Timeout, Antwortgrößen-Limit
  (`app/raceclocker/control/RaceClockerFeed.kt`).
- Migrationen `V202607211200` (Integration) und `V202607231000` (Feedback-Felder).

### 1.7 Urkunden als PDF und Word

Neue Siegerurkunde (Typ `AWARD_CERTIFICATE`) auf Basis der bestehenden Gap-Dokument-Mechanik
(PDF-Vorlage hochladen, Platzhalter visuell positionieren). Teilnahmeurkunden erhalten
denselben Word-Download (`?format=docx`).

- Migration `V202608051200`: Schriftattribute je Platzhalter (`font_size`, `bold`, `italic`,
  `static_text`) und optionaler Schrift-Upload (`gap_document_template_font`).
- Zwei Renderer aus derselben Platzhalterliste: PDFBox (`backend/pdf/`) und
  POI XWPF mit `w:framePr` (`backend/docx/GapDocumentsDocx.kt`), siehe Entscheidung 2.5.
- Neue Endpoints `GET /event/{eventId}/awardCertificates` (gesamt, je Wettkampf, einzeln).

### 1.8 Zeitnahme-Tab

Neuer Wettkampf-Tab „Zeitnahme" ersetzt den `RaceClockerConfigDialog` und beide
Preset-Auswahldialoge. Vier neue Spalten auf `competition` (Migration `V202608051500`):
`timing_system`, zwei Startlisten-Presets, ein Ergebnis-Import-Preset. Die Preset-Auflösung
(`StartListConfigTarget.configId`) entscheidet serverseitig anhand des Runden-Typs.
Neuer Endpoint `GET/PUT …/timing-config` ersetzt `…/raceclocker-config`.

### 1.9 Verständliche Fehlermeldungen

Systematische Nachrüstung von `ErrorCode` + übersetztem Text (de/en/da) für Zeitplan,
Urkunden, Durchführung und Live-Anzeigen (`scheduleError.ts`, `certificateError.ts`,
`executionError.ts`, `liveDashboardError.ts` mit Konsistenztests). Die Zahl der Fehlerzweige
ohne Code sank von ~139 auf ~91; die verbleibenden Blöcke (Stammdaten, Registrierung,
Auth, WebDAV) sind Bestandscode und bewusst nicht Teil dieses Branches.

### 1.10 Betriebshärtung

Für den Regattabetrieb mit vielen gleichzeitigen Zuschauern und mobilem Netz:

- gzip-Kompression für API-Antworten.
- Server-Cache (5 s TTL) für das Athleten-Board — deckelt die DB-Last unabhängig von der
  Zuschauerzahl.
- Rate-Limit `publicInfo` (500 Anfragen / 5 s / IP) auf allen öffentlichen Endpoints.
- ETag/304 + `scope=LIVE|ALL` für das Dashboard-Polling. Gemessene Wirkung im Real-Test:
  Polling-Nutzlast über 3 h von 210 MB auf ~0,8 MB reduziert (Faktor ~190).

### 1.11 Sichtbarkeit öffentlicher Ergebnisse

`event.public_results_visibility` (`FINISHED_ONLY` / `RESULTS_COMPLETE`,
Migration `V202608061200`) regelt, wann ein Lauf auf der öffentlichen Ergebnisseite
erscheint. Die Sichtbarkeitsregel ist in `afterMigrate.sql`
(View `competition_having_results`) und `CompetitionMatchRepo.getMatchResults` bewusst
identisch gehalten und gegenseitig referenziert, damit kein Wettbewerb mit leerer
Ergebnisseite in der Auswahl steht.

---

## 2. Zentrale Entscheidungen (und warum)

### 2.1 Polling mit ETag statt WebSockets

Live-Dashboard und Athleten-Board nutzen das bestehende `useFetch({autoReloadInterval})`-Muster
statt einer neuen WebSocket-Infrastruktur. Die Nutzlast wurde stattdessen dreistufig gesenkt:
Teilnehmerdaten raus aus dem Listen-Poll (eigener Detail-Endpoint, nur bei Dialog-Öffnung),
ETag/304 statt einer `max(updated_at)`-Heuristik (ein Inhalts-Hash ist bei Löschungen nie
still falsch), `scope`-Parameter für tab-spezifische Antworten. Ergebnis siehe 1.10 —
ohne neue Infrastruktur-Abhängigkeit für einen Zwei-Tage-Event.

### 2.2 Athleten-Board: Auflösung im Backend, eigene Route

Die Kiosk-Rotation war ungeeignet (Login-Pflicht über `/info-views`, Rotation und
Bedienelemente auf einem montierten Bildschirm). Die Board-Konfiguration wird deshalb
serverseitig aufgelöst — ein einziger Request pro Aktualisierung, kein Login. Der Countdown
rechnet gegen die mitgelieferte `serverTime`, nicht gegen die Client-Uhr.

### 2.3 Chain-Progression als Drei-Wege-Modus

Nach dem Feldtest vom 04.08. gilt: **ein Lauf wird nur durch aktiven Input beendet** — kein
Auto-Beenden bei vollständigen Ergebnissen, denn der Beenden-Klick ist das Signal ans
Regattabüro für den RaceClocker-Import. Wer den Klick machen darf (Schiedsrichter am Steg
oder nur Regattabüro), ist je Veranstaltung verschieden — daher Modus statt Boolean.
Die Kette wartet an ungesetzten Zeitplan-Slots, statt abzubrechen.

### 2.4 Penalty-Modell: ausweisen, nicht verrechnen

Die erfasste Zielzeit gilt bereits **inklusive** Strafe (so liefert sie die externe
Zeitmessung). Das System weist die Strafe nur aus. RaceClocker ist Quelle der Wahrheit und
überschreibt eine manuell erfasste Strafe; beide Wege am selben Boot zu mischen ist bewusst
kein unterstützter Ablauf.

### 2.5 Urkunden: Gap-Mechanik statt PPTX/LibreOffice

Wiederverwendung der vorhandenen Platzhalter-Mechanik statt PPTX-Import oder
LibreOffice-Container: kein Betriebsaufwand für einen Konverter-Dienst, keine Nachbildung
von PowerPoints Absatzlogik (jede Zeile hat eigene Koordinaten). Word-Export über
`w:framePr` (absolut positionierte Textrahmen) statt Drawing-XML — in Word normal
anklick- und verschiebbar. Schrift-Upload ist optional mit Helvetica-Fallback, weil die
Verbandsschrift kommerziell lizenziert ist und in einem GPL-3.0-Repo nicht mitgeliefert
werden darf.

### 2.6 Zeitnahme-Konfiguration an `competition`, nicht an Vorlagen

Presets zeigen auf konkrete Rennen im Fremdsystem — sie gehören an den Wettkampf, nicht an
`competition_properties`/Vorlagen. Der Rückfall Qualifikation→Runden-Preset gilt bewusst
**nicht** für RaceClocker (er würde die Lauf-Spalte in ein Zeitfahren-Rennen tragen);
bei Webscorer bleibt er, weil dort keine Zweiteilung existiert.

### 2.7 Fixe Uhrzeiten pro Zeitplan-Slot

Verworfen wurden: `planned_start_time` an der Setup-Zeile (Vorlagen dürfen keine Uhrzeit
tragen) und eine vierte parallele Zustandsmaschine (stattdessen abgeleitete Zustände wie
bei `LiveDashboardMatchState`). Excel-Import als primärer Planungsweg, weil der Zeitplan
ohnehin extern entsteht.

---

## 3. Verifikation

### 3.1 Automatisierte Tests

- Backend: **366 Tests, 0 Failures** (33 Testklassen, `./mvnw test`), darunter neue Suiten
  für `LiveDashboardLogic`, `AthleteBoardLogic`, `EventScheduleLogic`/`ScheduleChain`/
  `ScheduleImport`, `RaceClockerFeed` (mit echten Feed-Fixtures), Urkunden
  (PDF-Geometrie-Contract, DOCX, Fonts), Seeding/Platzierungen und Fehlercode-Eindeutigkeit.
- Frontend: **384 Tests, 0 Failures** (13 Testdateien, vitest), u.a.
  Übersetzungs-Konsistenztests für alle neuen Fehlercode-Module in de/en/da.
- `npm run build` läuft fehlerfrei durch.
- Stil der Tests: reine `*Logic`-Objekte statt DB-gebundener Tests, feste Zeitwerte statt
  `now()`, Feed-Fixtures aus echten RaceClocker-Antworten.

### 3.2 Real-Test gegen die laufende Anwendung (Nachtlauf 05./06.08.)

Vor dem Merge-Request wurde ein Testkatalog mit **7 Bereichen und ~130 Fällen**
(Athleten-Anzeige, Zeitplan, RaceClocker, Dashboard, Betrieb, Zeitnahme, Urkunden)
browsergesteuert gegen die laufende Anwendung mit realistischem Seed durchgespielt.
Ergebnis: überwiegend grün, **15 Befunde dokumentiert, alle Befunde hoher Priorität
noch auf dem Branch behoben**, darunter:

- RaceClocker-Pull während eines laufenden Rennens wertete noch fahrende Boote als
  ausgeschieden (`In race…`/`Not started` sind Verlaufszustände, keine Ausfälle).
- Eine invertierte Bedingung verhinderte jede Abmeldung nach Rundensetzung.
- Die 30-Minuten-Karenz galt nicht für Programmpunkte — veraltete Einträge verstopften
  „Nächster Lauf".
- Abgesagte Läufe blieben auf den Live-Anzeigen sichtbar; ein aktivierter Lauf ließ sich
  absagen und war danach „abgesagt und laufend" zugleich.
- `IndexOutOfBoundsException` bei Massenfeld-Finalrunden mit `ASCENDING`/`CUSTOM`-Seeding
  (traf Platzierungen **und** Urkunden-Download).
- PDF-Textfilter löschte typografische Zeichen (Gedankenstriche, Anführungszeichen)
  ersatzlos; Teilnahmeurkunden ohne Ergebnis wiesen „0 m" aus.

Die Betriebs- und Zeitnahme-Bereiche des Katalogs waren vollständig grün, inklusive
Lasttest-Messung des Polling-Verhaltens (siehe 1.10).

### 3.3 Migrationen

- Kompletter Flyway-Lauf gegen eine **leere** PostgreSQL-Datenbank: alle 72 Migrationen
  erfolgreich bis `V202608061200`, `afterMigrate.sql` legt alle 72 Views an.
- Upgrade-Pfad: gegenüber main kommen ausschließlich **neue** Versionsmigrationen hinzu
  (alle nach V202606221000); keine bestehende versionierte Migration wurde angefasst.
  Ein Bestandssystem auf main-Stand migriert also ohne Checksummen- oder
  Reihenfolge-Probleme.
- jOOQ-Codegen (läuft im Build gegen die migrierte DB) und Kompilierung sind grün.

### 3.4 Merge-Sicherheit

- Der aktuelle `origin/main`-Stand (inkl. des Fallback-String-Fixes aus PR #93) ist in den
  Branch gemergt — konfliktfrei; der Merge in main hat damit keine offenen Konflikte.
- `origin/issue/94` ist vollständig enthalten (inkl. der drei Hotfix-Commits bis c820c99a,
  Konfliktauflösung im generierten API-Client durch Neugenerierung aus der gemergten
  OpenAPI-Spec). Ein separater Merge von issue/94 nach main erzeugt damit keine
  logischen Doppelungen.

### 3.5 Code-Review vor dem MR

Der gesamte Diff wurde vor diesem MR intern reviewt (Security, Testabdeckung, Stil,
Konsistenz mit Bestandsmustern). Ergebnisse, die daraufhin noch auf dem Branch behoben
wurden:

- Fehlende `set search_path`-Zeile in `V202608051200` ergänzt (Konvention aller übrigen
  Migrationen).
- Die reine Zuordnungslogik des RaceClocker-Ergebnis-Pulls (`assignFeedRows`) wurde nach
  Projektmuster in `RaceClockerAssignmentLogic` extrahiert und mit Tests für alle
  Fallback-Pfade versehen (Match-Team-ID, Registration+Wave, umbenannte Wave, Duplikate).
- `RaceClockerError`- und `RaceClockerMatchTarget`-Tests ergänzt (Fehlercode-Eindeutigkeit,
  URL-Auswahl-Randfälle) sowie Fehlerpfad-Tests für den Feed-Abruf.
- RaceClocker-Fehlercodes im Frontend verdrahtet (eigene i18n-Keys de/en/da statt
  Sammelmeldung am Pull-Button), ein hartkodierter Alt-Text auf i18n umgestellt.

Geprüft ohne Befund: SQL-Injection (durchgängig jOOQ-DSL), Auth-Abdeckung der neuen
Endpoints (öffentliche Endpoints sind bewusst öffentlich, rate-limitiert und auf
Event-Scope beschränkt), SSRF-Härtung des Feed-Abrufs, Memory-Leaks in der
Polling-/Cleanup-Logik des Frontends.

---

## 4. Bekannte Grenzen und offene Punkte

Bewusst **nicht** Teil dieses Branches (dokumentiert, teils mit dem Verband zu klären):

- Keine Konfiguration pro Runde im Zeitnahme-Tab, keine veranstaltungsweite
  Preset-Vorbelegung; ein drittes Zeitnahme-System wäre ohne Migration nachrüstbar.
- Urkunden: kein E-Mail-Versand (nur Download), Vorlagen bleiben global pro Typ,
  keine Siegerurkunden für Challenge-Events. Ohne hochgeladene Schrift können
  Sonderzeichen außerhalb von Helvetica (z. B. `ł`, Kyrillisch) nur als `?` erscheinen.
- `placeName` in den Info-DTOs wird nie befüllt (keine Orte-Tabelle) — Feld ggf. entfernen.
- Eine Abmeldung **vor** Rundensetzung erzeugt eine unsichtbare
  `competition_match_team`-Zeile mit belegter Bahnnummer (Bestandsverhalten,
  Klärung mit Fachseite offen).
- Vier Ablehnungsgründe beim Zeitplan-Verschieben teilen sich noch einen generischen
  Fehlertext; der Zeitplan-Import meldet unlesbare Zellen ohne Zeilennummer.
- ~91 Fehlerzweige im Bestandscode (Stammdaten, Registrierung, Auth, WebDAV) haben
  weiterhin keinen ErrorCode — Bestandsaufnahme liegt vor, Umsetzung wäre ein Folge-MR.
- Drei Stellen toten Codes im Bestand nur gemeldet, nicht entfernt
  (`CompetitionExecutionError.PlaceAndTimeBothNull`, `QrCodeError.QrCodeNotFound`,
  fest verdrahtete Validierung in `CatererTransactionRequest.kt`).

Die vollständigen Arbeitsdokumente (Design-Specs mit Alternativenabwägung, Testkatalog,
Testprotokoll, Seed-Skripte) liegen auf dem separaten Branch
`docs/crf-2026-arbeitsdokumente` — als eigener MR, damit ihr entscheiden könnt, ob ihr
sie ins Repo übernehmen wollt.
