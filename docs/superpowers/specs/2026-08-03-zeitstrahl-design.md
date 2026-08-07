# Design: Zeitstrahl als Grundkonzept (Backlog B2)

**Stand:** 2026-08-03
**Status:** Design abgenommen, Implementierung ausstehend
**Kontext:** Backlog-Punkt B2 aus `2026-07-30-schiedsrichter-dashboard-backlog.md`. Umfasst
außerdem A2 (Zustandsmodell „läuft") und das `finished_at`-Follow-up aus A4. Grundlage sind die
realen Zeitpläne der Coastal Regatta Flensburg (FördeRACE/FördeSPRINT 2025, Excel-Dateien von
Thomas) und die Mail-Korrespondenz mit Cees (RaceClocker).

---

## 1. Problem

Die Startzeit hängt heute am erzeugten Lauf (`competition_match.start_time`), entsteht immer
`null` und wird einzeln pro Lauf in einem Dialog gepflegt — erst möglich, nachdem die Runde
gesetzt wurde (`createNewRound`). Bis dahin hat der Zeitplan Lücken. Die automatische Lauf-Kette
wählt den nächsten Lauf rein über `start_time` (veranstaltungsweit) und greift bei Lücken den
falschen Lauf — deshalb existiert der Abschalter `event.auto_activate_next_match` (Default aus).

Weitere Löcher, die dieses Design mit schließt:

- **„Beendet" ist nirgends persistiert.** Vier Stellen rechnen es unabhängig aus „alle Teams
  haben ein Ergebnis" zurück; ein ohne Ergebnisse beendeter Lauf fällt auf „Anstehend" zurück
  (A4-Loch).
- **`currently_running` trägt zwei Bedeutungen** („in Bearbeitung" und „auf dem Wasser");
  `elapsedMinutes` rechnet ab der geplanten Startzeit und zeigt vor dem Start „0 min" (A2).
- `deleteCurrentRound` wirft gepflegte Startzeiten unwiederbringlich weg.

Nicht Teil des Problems: `competition_setup_match.start_time_offset` ist entgegen der
Backlog-Vermutung **nicht** ungenutzt — es beschreibt den Versatz zwischen Booten *innerhalb*
eines Laufs (Zeitfahren) und bleibt unangetastet.

## 2. Zielbild

Ein **Zeitstrahl pro Veranstaltung**: eine geordnete Liste von Slots mit **fixen Uhrzeiten**,
angelegt lange bevor Läufe existieren. Ein Slot ist entweder an eine Setup-Zeile
(`competition_setup_match`) gebunden oder ein **freier Slot** (Pause, Siegerehrung, Besprechung).
Die Uhrzeit der Läufe leitet sich aus dem Zeitstrahl ab; die Lauf-Kette folgt der
Slot-Reihenfolge und **wartet** an Slots, deren Lauf noch nicht gesetzt ist, statt abzubrechen
oder den falschen Lauf zu greifen. Alle Anzeigen (Schiedsrichter-Dashboard, Athleten-Board,
Kiosk) zeigen wartende Slots als „Lauf noch nicht gesetzt" — die Rückfragen am Start/Ziel
entfallen.

Reales Szenario (FördeSPRINT): Timetrial um 10:00, daraus entstehen AF 1–5, geplant ab 10:30 alle
10 Minuten. Die AF-Slots existieren ab der Planung als Platzhalter; nach Ergebniseingabe und
„Nächste Runde erstellen" füllen sie sich automatisch und die Kette läuft weiter.

### Getroffene Entscheidungen (Brainstorming 2026-08-03)

| Frage | Entscheidung |
|---|---|
| Slot-Modell | Fixe Uhrzeit pro Slot |
| Ebene | Ein Zeitstrahl pro Veranstaltung; Renntage gliedern nur die Anzeige |
| Slot-Inhalt | Hybrid: FK auf `competition_setup_match` **oder** freier Slot mit Name |
| Breakpoint | Kette wartet am ungesetzten Slot (kein Abbruch); `createNewRound` stößt sie wieder an |
| Überspringen | Bewusste Aktion mit Bestätigung; im Zeitplan-Tab und auf dem Schiedsrichter-Dashboard |
| Verzug | Shift-Dialog mit drei Methoden (+X Min / neue Uhrzeit / Stauchen bis Ziel-Slot), Vorschau, nichts wird vor dem Bestätigen persistiert |
| Planung | Excel-Import als primärer Weg (ersetzt alle Slots), Editor nur für Korrekturen |
| Scope | inkl. `finished_at`, A2-Zustandsmodell, geplante vs. reale Startzeit; issue/94 wird nach feature/crf-2026 gemergt; Wave-Name-Export wird nur vorbereitet, nicht gebaut |
| Interaktion | Kiosk/Athleten-Board rein visualisierend; das Schiedsrichter-Dashboard hat bewusst Interaktionen |
| UI | Agenda-Liste als neuer Event-Tab „Zeitplan" |

### Verworfene Alternativen

- **Startzeit auf die Setup-Zeile** (`planned_start_time` an `competition_setup_match`): keine
  freien Slots möglich, und Setup-Zeilen dienen zugleich als Templates — eine Vorlage darf keine
  Uhrzeit tragen.
- **Eigene Slot-Zustandsmaschine** (PLANNED/READY/…): würde das vierte parallele Zustandsmodell
  neben `LiveDashboardMatchState`, `AthleteBoardStartState` und den Kiosk-SQL-Prädikaten
  schaffen und alle Lesepfade umbauen. Stattdessen: abgeleitete Zustände, Write-Through.

## 3. Datenmodell

### Neue Tabelle `event_schedule_slot`

| Spalte | Typ | Bedeutung |
|---|---|---|
| `id` | uuid PK | |
| `event` | uuid FK → `event`, not null, on delete cascade | ein Zeitstrahl pro Veranstaltung |
| `start_time` | timestamp not null | die fixe Slot-Zeit — Quelle der geplanten Startzeit |
| `competition_setup_match` | uuid FK → `competition_setup_match`, nullable, **unique** | Lauf-Slot |
| `name` | text, nullable | freier Slot („Mittagspause", „Siegerehrung") |
| `duration_minutes` | int, nullable | für Anzeige und Stauch-Untergrenze |
| `skipped_at` / `skipped_by` | timestamp / uuid FK app_user, nullable | manuelles Überspringen mit Audit |
| `created_at/by`, `updated_at/by` | | wie im übrigen Schema |

Constraints: XOR auf (`competition_setup_match`, `name`) — genau eines gesetzt (Muster wie der
Setup/Template-Check an `competition_setup_round`). `unique` auf `competition_setup_match`: ein
Lauf hat höchstens einen Slot.

### Neue Spalten auf `competition_match`

| Spalte | Bedeutung |
|---|---|
| `started_at` (timestamp, nullable) | **realer** Start (Ist). Quellen: Start-Aktion des Schiedsrichters; RaceClocker-Ist-Start aus dem Feed (überschreibt den manuellen Wert — externe Zeitnahme ist Quelle der Wahrheit, dieselbe Regel wie bei den Zeitstrafen). Bei versetzten Starts gilt der früheste Teilnehmer-Start. |
| `finished_at` (timestamp, nullable) | persistiertes Lauf-Ende. Gestempelt von `finishMatch`. Schließt das A4-Loch: „beendet" ist nie wieder ableitungsabhängig. |

`competition_match.start_time` behält seine Rolle als **geplante** Startzeit und wird bei
slot-verknüpften Läufen ausschließlich per Write-Through aus dem Slot gespeist. Damit
unterscheiden alle Anzeigen „geplant 10:30 · gestartet 10:34".

`currently_running` bleibt bestehen und heißt fortan nur noch „vom Schiedsrichter aktiv
gesetzt" (in Bearbeitung). Der Lauf-Zustand ergibt sich kombiniert:

- aktiv, kein `started_at` → **in Vorbereitung**
- aktiv, `started_at` gesetzt → **auf dem Wasser** (`elapsedMinutes` ab `started_at`)
- `finished_at` gesetzt → **beendet**, unabhängig von der Ergebnislage

### Slot-Zustände (abgeleitet, keine Statusspalte)

| Zustand | Ableitung |
|---|---|
| **frei** | nur `name` gesetzt |
| **wartet** | Setup-Zeile verknüpft, kein `competition_match`, Runde nicht materialisiert — „Lauf noch nicht gesetzt" |
| **verknüpft** | `competition_match` existiert (1:1 über die Setup-Zeilen-ID) |
| **entfällt** | Runde materialisiert, aber für diese Setup-Zeile ist kein Lauf entstanden (kleinere Bracket-Größe, übersprungene Runde). Automatisch, weil der Slot nachweislich nie mehr gefüllt wird; lebt wieder auf, wenn die Runde gelöscht und größer neu erzeugt wird |
| **übersprungen** | `skipped_at` gesetzt |

Die Verknüpfung braucht keinen Zuordnungsschritt: `competition_match` hat dieselbe ID wie seine
Setup-Zeile (PK = FK, 1:1). Ein Slot auf „AF 1" ist mit dem Lauf verbunden, sobald
`createNewRound` ihn erzeugt. Die Setup-Namen können sich bei der Rundensetzung noch verfeinern
(`competition_setup_match_naming`); der Slot zeigt bis dahin den Setup-Namen, danach den
endgültigen.

`deleteCurrentRound` korrigiert sich von selbst: Der Lauf verschwindet, der Slot fällt auf
„wartet" zurück, die Zeit überlebt im Slot und wird beim Neu-Erzeugen wieder aufgestempelt —
heute gehen die Zeiten dabei verloren.

## 4. Lauf-Kette & Zustandslogik

### Kette über Slots

`LiveDashboardService.finishMatch` ändert die Kandidatenwahl:

1. `finished_at` wird gestempelt (zusätzlich zum bestehenden A4-Dialog für offene Ergebnisse).
2. Die Kette sucht den Slot des beendeten Laufs und wandert die Slot-Reihenfolge (nach
   `start_time`) vorwärts. Übersprungene und entfallene Slots werden ausgelassen; freie Slots
   werden übergangen — „aktivieren" heißt nur noch *in Vorbereitung*, nach der Mittagspause steht
   die nächste Crew also bereits mit sichtbarer Startzeit auf den Boards.
3. Ist der nächste Lauf-Slot **wartend** (Lauf nicht gesetzt): Es wird nichts aktiviert und
   nichts persistiert — die Anzeigen zeigen den Slot als „Lauf noch nicht gesetzt". Das ist der
   wartende Breakpoint.
4. **`createNewRound` wird zweiter Ketten-Auslöser:** Nach dem Materialisieren (inkl.
   Write-Through der Slot-Zeiten) prüft es, ob kein Lauf aktiv ist und der nächste fällige Slot
   soeben gefüllt wurde — dann wird er aktiviert. Die Kette läuft nahtlos weiter, sobald das
   Regattabüro die Runde setzt.
5. Parallele Starts wie heute: Alle Slots mit identischer Startzeit werden gemeinsam aktiviert.

**Rückwärtskompatibilität:** Hat der beendete Lauf keinen Slot, greift die heutige
`start_time`-Logik unverändert. `event.auto_activate_next_match` bleibt der Ein/Aus-Schalter;
der Hinweistext im Event-Dialog wird angepasst („mit gepflegtem Zeitplan zuverlässig").

### Anpassung der drei bestehenden Zustandsmodelle (kein viertes)

- `LiveDashboardLogic`: `FINISHED` = `finished_at` gesetzt (Fallback „alle Ergebnisse da" für
  Altdaten). „Running" differenziert in Vorbereitung / auf dem Wasser; `elapsedMinutes` ab
  `started_at` (kein `started_at` → keine Laufzeit-Anzeige, stattdessen „in Vorbereitung") —
  „läuft seit 0 min" vor dem Start verschwindet (A2 erledigt).
- Athleten-Board & Kiosk: wartende Slots erscheinen als Platzhalter-Karten
  („AF 1 — 10:30 — Lauf noch nicht gesetzt") in den Upcoming-Listen; die SQL-Prädikate bekommen
  `finished_at` als Beendet-Kriterium.
- Die Slot-Zustandsableitung existiert an genau einer Stelle (`deriveSlotState`, analog
  `LiveDashboardLogic`) und wird von allen Konsumenten benutzt — nicht in SQL dupliziert.

### Start-Stempel

Neue Aktion „Start" auf der Karte des Schiedsrichter-Dashboards setzt `started_at`. Der
RaceClocker-Pull übernimmt das `Start`-Feld aus dem Feed (pro Teilnehmer; frühester Wert zählt)
und überschreibt den manuellen Stempel. Die Feed-Keys sind lokalisiert (`Start`/`Finish` vs.
deutsche Varianten) — das Mapping sucht tolerant.

## 5. Frontend: Zeitplan-Tab, Import, Shift-Dialog

### Neuer Event-Tab „Zeitplan"

Agenda-Liste, gruppiert pro Renntag (Datumsanteil der Slot-Zeiten). Zeile: Uhrzeit, Inhalt
(Wettkampf → Runde → Laufname bzw. Name des freien Slots), Status-Chip, optional Dauer.
Bearbeitung hinter `UpdateEventGlobal`. Funktionen: Zeit inline ändern, Slot
hinzufügen/löschen, Überspringen (mit Bestätigung), Shift-Dialog, Import. Unter der Agenda eine
Liste **„nicht verplante Läufe"** (Setup-Zeilen ohne Slot) als Vollständigkeits-Hilfe.

Der Slot-Dialog bietet Kaskadenauswahl Wettkampf → Runde → Setup-Zeile (bereits verplante
ausgeblendet) oder Name+Zeit+Dauer für freie Slots. Eine Mehrfach-Anlage gibt es nicht — das
leistet der Import.

### Excel-Import (primärer Planungsweg)

Flache Tabelle (xlsx), vier Spalten:

| Datum | Uhrzeit | Wettkampf | Lauf |
|---|---|---|---|
| 17.08. | 08:00 | | Wettkampfrichter-Besprechung |
| 17.08. | 08:40 | CMix 4x+ | Finale A |

- **Matching:** „Wettkampf" gegen Kurzname/Identifier des Wettkampfs, „Lauf" gegen den Namen der
  Setup-Zeile (VF1, HF2, Finale A, …). Treffer → verknüpfter Slot. „Wettkampf" leer oder kein
  Treffer → freier Slot mit dem „Lauf"-Text als Namen.
- **Vorschau vor dem Schreiben:** jede Zeile mit Ergebnis (verknüpft mit X / frei / nicht
  eindeutig). Erst „Importieren" ersetzt **alle** bisherigen Slots des Events transaktional.
- Datum ohne Jahr → Jahr der Veranstaltung; Datum/Uhrzeit werden als Ortszeit geparst
  (konsistent mit dem übrigen System; Postgres läuft auf UTC, die JVM liest zeitzonenlose
  Timestamps als Europe/Berlin).

### Shift-Dialog „Zeitplan anpassen"

Start-Slot wählen (Standard: erster nicht beendeter Slot), dann eine von drei Methoden:

1. **+X Minuten** — alle Slots ab dem Start-Slot, begrenzt auf den Renntag.
2. **Nächster Lauf startet um HH:MM** — absolute Eingabe; die folgenden Slots behalten ihre
   Abstände.
3. **Stauchen bis Ziel-Slot** — ab Start-Slot schieben, Abstände bis zum Ziel-Slot so verkürzen,
   dass dieser seine Zeit behält. Untergrenze pro Slot: `duration_minutes`, wenn gesetzt, sonst
   5 Minuten Mindestabstand. Bei Verletzung zeigt die Vorschau eine Warnung und schlägt den
   nächstmöglichen Ziel-Slot vor.

Immer mit Vorschau-Tabelle alt→neu pro Slot; **persistiert wird ausschließlich beim
Bestätigen**, dann transaktional inklusive Write-Through.

### Bestehende Masken

- Der Lauf-Daten-Dialog der Wettkampf-Ausführung zeigt die Startzeit bei slot-verknüpften Läufen
  nur noch lesend, mit Verweis „wird über den Zeitplan gepflegt". Für Events ohne Zeitstrahl
  unverändert. (Der dort gefundene Reset-Bug — Öffnen+Speichern löscht die Startzeit — wird
  separat gefixt.)
- Kiosk und Athleten-Board bleiben rein visualisierend; das Schiedsrichter-Dashboard bekommt
  bewusst die Interaktionen Start-Stempel und Überspringen (mit Bestätigung), zusätzlich zum
  bestehenden „Lauf beenden".

## 6. API & Backend

Neuer `EventScheduleService`, Routen unter `/event/{eventId}/schedule`:

| Endpoint | Zweck | Privileg |
|---|---|---|
| `GET /schedule` | Slots mit abgeleitetem Status | Event-Leserecht |
| `POST /schedule/slot` | Slot anlegen | `UpdateEventGlobal` |
| `PUT /schedule/slot/{id}` | Zeit/Name/Dauer ändern | `UpdateEventGlobal` |
| `DELETE /schedule/slot/{id}` | Slot entfernen | `UpdateEventGlobal` |
| `PUT /schedule/slot/{id}/skip`, `/unskip` | Überspringen mit Audit | `UpdateEventGlobal` **oder** Live-Dashboard-Update (Schiedsrichter) |
| `POST /schedule/shift` | Verschieben/Stauchen | `UpdateEventGlobal` |
| `POST /schedule/import` | Excel-Import (Multipart) | `UpdateEventGlobal` |

**Vorschau-Prinzip:** `shift` und `import` nehmen ein `dryRun`-Flag. `dryRun=true` liefert die
alt→neu-Liste bzw. das Zeilen-Matching ohne zu schreiben; das Bestätigen sendet denselben
Request mit `dryRun=false` (stateless, transaktional). Shift-Request:
`{fromSlotId, mode: PLUS_MINUTES | SET_TIME | COMPRESS_TO_TARGET, minutes?, newTime?, targetSlotId?}`.

**Write-Through an zwei Stellen:**

1. Slot-Mutation (anlegen, ändern, Shift, Import) → existiert zur Setup-Zeile ein
   `competition_match`, wird dessen `start_time` in derselben Transaktion mitgeschrieben.
2. `createNewRound` → nach dem Erzeugen der Matches Slots nachschlagen, Slot-Zeiten auf
   `start_time` stempeln, danach der Ketten-Check (Abschnitt 4, Punkt 4).

Der alte Pfad `updateMatchData` lehnt `startTime`-Änderungen ab, wenn der Lauf einen Slot hat
(eine Quelle der Wahrheit).

**Handwerkliches:** KIO-Aufrufe konsequent mit `!` binden; OpenAPI erweitern und den
Frontend-Client regenerieren; i18n-Schlüssel de/en (ggf. da). Fehler als eigener
`EventScheduleError` (SlotNotFound, SetupMatchAlreadyPlanned, MatchAlreadyStarted,
CompressionImpossible, DuplicateImportRow, …), Muster wie `CompetitionExecutionError`.

## 7. RaceClocker

- **Voraussetzung:** `origin/issue/94` wird nach `feature/crf-2026` gemergt (Entscheidung
  2026-08-03; ersetzt die frühere Regel, die Integration als separaten MR zu führen).
  Konflikte zugunsten des crf-2026-Stands lösen — issue/94 datiert vor der Lauf-Kette; sein
  Pull ruft `prepareForNewPlaces` noch ohne das Teilergebnis-Konzept.
- **Ist-Start übernehmen:** Das `Start`-Feld des Feeds (pro Teilnehmer, bislang bewusst
  verworfen) wird gemappt → `competition_match.started_at` (frühester Wert, überschreibt den
  manuellen Stempel). Lokalisierte Feld-Keys tolerant behandeln.
- **Wave-Name nur vorbereitet:** Der Zeitstrahl ist die definierte Quelle für die Startzeit im
  Wave-Namen (laut Cees gehört sie dorthin — nur dort sieht sie die Timer-App). Die
  Export-Änderung selbst (heute: Startzeit in `buildCsv` auskommentiert, Spalte liefert nur
  Offsets ab 00:00:00) ist **nicht** Teil dieser Umsetzung.

## 8. Randfälle

- **Mehrdeutiges Import-Matching** (z. B. „HF1" in DM- und International-Variante desselben
  Kurznamens): Vorschau markiert „nicht eindeutig", Commit importiert die Zeile als freien Slot —
  sichtbar und korrigierbar. Dieselbe Setup-Zeile zweimal in der Datei → Commit blockiert.
- **Import/Shift während laufender Regatta:** Beendete Läufe behalten ihre `start_time`
  (Write-Through ist passiert), auch wenn der Import ihre Slots ersetzt. Die Vorschau warnt,
  wenn laufende/beendete Läufe betroffen sind.
- **Stauchen unmöglich:** `dryRun` liefert die Verletzung samt frühestem machbarem Ziel-Slot;
  Commit mit Verletzung → 422.
- **Überspringen eines gestarteten Laufs:** abgelehnt. Überspringbar sind wartende,
  verknüpft-nicht-gestartete und freie Slots; Aufheben möglich, solange nichts gestartet ist.
- **Slot löschen, dessen Lauf existiert:** erlaubt; der Lauf behält die letzte Zeit und fällt in
  die Legacy-Ketten-Logik zurück.
- **Setup-Änderungen:** Der FK `event_schedule_slot.competition_setup_match` ist
  `on delete cascade` — löscht man eine verplante Setup-Zeile, verschwindet ihr Slot mit. Die
  Lücke wird über die Liste „nicht verplante Läufe" bzw. den Zeitplan selbst sichtbar; die
  Setup-Maske wird nicht angefasst.

## 9. Tests

- **Backend:** Ketten-Szenarien (beenden → nächster Slot aktiviert; wartender Breakpoint;
  `createNewRound` füllt Slot und Kette läuft weiter; übersprungene/entfallene Slots
  ausgelassen; Parallelstarts; Lauf ohne Slot → Legacy-Logik). Shift-Mathematik aller drei Modi
  inkl. Mindestabstand. Import-Matching (Treffer/frei/mehrdeutig/doppelt). Write-Through an
  beiden Stellen. `finished_at`-Regressionstest fürs A4-Loch. Zustandsableitung inkl.
  Runde-löschen-Wiederaufleben. `started_at`-Vorrangregel (RaceClocker überschreibt manuell).
- **Frontend (vitest):** Agenda-Gruppierung pro Renntag, Status-Chips, Shift- und
  Import-Vorschau, Read-only-Startzeitfeld bei Slot-Bindung.
- **Funktional:** Das `5eed`-Seed-Szenario wird um einen Zeitstrahl erweitert (Slots über zwei
  Renntage, wartende AF-Slots, freie Slots), damit Kette + Breakpoint + Dashboards lokal
  durchspielbar sind.

## 10. Abgrenzung / Follow-ups

- Wave-Name-Export mit Startzeit (Abschnitt 7) — eigener Schritt nach diesem Umbau.
- A1 (Speichern & Lauf inaktiv setzen, Ergebnis-Freigabe) bleibt offen; `result_verified_*` ist
  durch Challenge-Events belegt und hier nicht verwendbar.
- Der Reset-Bug im Lauf-Daten-Dialog (Startzeit wird beim Bearbeiten verworfen) wird unabhängig
  gefixt.
- Perspektivisch kann `auto_activate_next_match` auf Default „an" wechseln, wenn sich der
  Zeitstrahl bewährt.
