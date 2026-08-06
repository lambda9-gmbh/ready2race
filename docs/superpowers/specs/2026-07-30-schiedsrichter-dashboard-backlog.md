# Backlog: Schiedsrichter-Dashboard & Zeitplanung

**Stand:** 2026-07-30, fortgeschrieben am 2026-08-02
**Status:** Sammlung zum Brainstormen. **A3, A4 und A5 sind inzwischen umgesetzt** (siehe die
Design-Dokumente vom 2026-08-02 daneben); A1, A2, B1 und B2 sind weiter offen.
**Kontext:** Entstanden am Ende der Session, in der das Schiedsrichter-Dashboard, Teilergebnisse,
Zeitstrafen und die Lauf-Kette gebaut wurden (siehe
`2026-07-29-live-dashboard-schiedsrichter-design.md`).

Wo RaceClocker eine Rolle spielt, stützen sich die Aussagen auf die Mail-Korrespondenz mit Cees
(Juni–Juli 2026): Startzeiten gehören in den Wave-Namen, versteckte Teilnehmer-IDs gibt es nur für
Integrationspartner, und Inhalte in „Extra info" sind für Endnutzer sichtbar.

Jeder Punkt hat drei Teile: **Wunsch** (was gesagt wurde), **Ausgangslage** (was heute im Code
existiert, damit die Recherche nicht neu anfängt) und **Offene Fragen** (was beim Brainstormen zu
entscheiden ist).

---

## A. Anpassungen am bestehenden Ablauf

### A1. Ergebnis-Eingabe: „Speichern" vs. „Speichern und Lauf inaktiv setzen"

**Wunsch:** Beim Eintragen eines Ergebnisses in der Web-UI soll man zwischen „Speichern" und
„Speichern und Lauf inaktiv setzen" wählen können. Damit beendet der Schiedsrichter das Rennen
aktiv aus der Ergebnis-Maske heraus.

Zusatzgedanke: Ein Flag, dass ein Schiedsrichter sein OK für den Lauf gegeben hat — danach ist das
Ergebnis nicht mehr änderbar.

**Ausgangslage:**
- Der Ergebnis-Dialog (`frontend/src/components/event/competition/excecution/CompetitionExecution.tsx`)
  hat heute „Speichern" und „Speichern und Weiter" (`saveAndNext`).
- `prepareForNewPlaces` im `CompetitionExecutionService` setzt `currently_running = false`
  **automatisch, sobald alle Boote ein Ergebnis haben** (Teilergebnis-PR). Ein Teilergebnis lässt
  den Lauf laufen. Ein manueller Schalter wäre also die dritte Variante neben Automatik und dem
  „Lauf beenden"-Button im Dashboard — Überschneidungen prüfen.
- **Prüfen:** `competition_match_team` hat bereits `result_verified_by` und `result_verified_at`.
  Möglicherweise existiert das Freigabe-Konzept in Teilen schon; vor einem neuen Flag klären, wofür
  diese Spalten heute genutzt werden.

**Offene Fragen:**
- Freigabe pro Lauf oder pro Mannschaft (die vorhandenen Spalten sind pro Mannschaft)?
- Wer darf eine Freigabe wieder aufheben — und wird das protokolliert?
- Was passiert bei einem RaceClocker-Pull auf einen freigegebenen Lauf: blockieren oder überschreiben?

### A2. „Läuft seit X Minuten" kollidiert mit dem Begriff „aktiv"

**Wunsch:** Die Anzeige rechnet stumpf ab Startzeit. Im mentalen Modell heißt „aktiv" aber
„bereits gestartet" — beides passt nicht zusammen, weil Schiedsrichter einen Lauf schon in der
Vorbereitung aktiv sehen wollen. Sauber aufräumen.

**Ausgangslage:**
- `currently_running` ist heute ein Boolean und trägt zwei Bedeutungen gleichzeitig: „vom
  Schiedsrichter in Bearbeitung" und „läuft auf dem Wasser".
- `elapsedMinutes` = `Duration.between(startTime, now)`, auf ≥ 0 geklemmt
  (`LiveDashboardService`). Vor der Startzeit steht dort also „0 min".
- Die Kiosk-Anzeige (`EventInfoPage`) nutzt dasselbe Flag.

**Offene Fragen:**
- Zwei getrennte Zustände (z.B. „in Vorbereitung" / „gestartet") oder ein abgeleiteter Zustand aus
  Startzeit + Flag?
- Woher käme der echte Start? Denkbar: RaceClocker liefert pro Teilnehmer eine `Start`-Zeit — daraus
  ließe sich „ist wirklich losgefahren" ableiten. Seit Juli 2026 gibt es dort einen Countdown-Start
  in der Wave-Startliste, der Startzeitpunkt ist also sauber gesetzt.
- Was zeigt das Dashboard vor dem Start statt „Läuft seit 0 min" — Countdown bis Start?

### A3. DNS und DQ zusätzlich zu DNF — **umgesetzt am 2026-08-02**

Entschieden wurde gegen eine Enum-Spalte: `failed_reason` bleibt Freitext, der Status wird nur
sichtbar gemacht. Angezeigt wird DSQ, erkannt werden auch DQ und DISQ. Siehe
`2026-08-02-result-status-dns-dq-design.md`.


**Wunsch:** Bei den Ergebnissen soll es nicht nur DNF geben, sondern auch DNS und DQ. RaceClocker
kann das und liefert es; wir sollen es übernehmen.

**Ausgangslage:**
- Heute gibt es nur `competition_match_team.failed` (Boolean) + `failed_reason` (Freitext).
- Das Dashboard zeigt hart „DNF" (`event.liveDashboard.team.failedShort`).
- Der RaceClocker-Pull (`origin/issue/94`) erkennt den Status bereits: Ein Result-Feld, das keine
  Zeit ist, landet als `noResultReason` — dort stehen genau „DNS", „DNF", „DQ".
- Der Excel-Import macht dasselbe (`timeIsValid`-Logik im `CompetitionExecutionService`).

**Offene Fragen:**
- Enum-Spalte (`DNS | DNF | DQ`) neben `failed`, oder `failed` durch einen Status ersetzen
  (Migration bestehender Daten)?
- Wie werden unbekannte Statustexte aus dem Feed behandelt — als Freitext behalten?
- Zählen DNS-Boote bei der Platzberechnung anders als DNF (Startgeld, Wertung)?

### A4. Beenden mit offenen Ergebnissen: nachfragen — **umgesetzt am 2026-08-02**

Der Dialog bietet DNS, DNF, DSQ, offen lassen und abbrechen. Abmelden ist bewusst keine Option,
das bleibt dem Regattabüro vorbehalten. Dabei fiel auf, dass abgemeldete Boote in Backend und
Frontend als „Ergebnis fehlt" zählten — korrigiert. Siehe
`2026-08-02-finish-match-open-results-design.md`.


**Wunsch:** Wenn der Schiedsrichter ein Rennen beendet und es noch offene Ergebnisse gibt, soll er
gefragt werden, was mit den restlichen passiert.

**Ausgangslage:**
- Heute nur ein Hinweistext in der Karte („Es fehlen noch Ergebnisse — beenden aktiviert trotzdem
  die nächsten Läufe"), keine Rückfrage. Der „Lauf beenden"-Button hat 5 s Undo.
- Sichtbares Symptom (Funktionstest 03.08.): Ein so beendeter Lauf fällt in der Zustandsableitung
  auf „Anstehend" zurück (nicht laufend, kein vollständiges Ergebnis) und taucht auf dem
  Live-Tab wieder unter „Als Nächstes" auf, bis die restlichen Ergebnisse eingetragen sind. Die
  Ableitung kennt kein „beendet ohne Ergebnis" — dasselbe Schema-Loch wie beim Follow-up
  `finished_at` für die Lauf-Kette.

**Offene Fragen:**
- Welche Optionen bietet der Dialog? Denkbar: alle offenen als DNS/DNF markieren, offen lassen,
  abbrechen.
- Hängt mit A3 zusammen: Die Auswahl braucht die Statuswerte.

### A5. Datenvolumen des Pollings — **umgesetzt am 2026-08-02**

Alle vier Ideen sind umgesetzt: gzip (am laufenden Server gemessen: Faktor 15), Teilnehmerdaten
aus dem Listen-Poll heraus mit eigenem Detail-Endpoint, ETag mit 304, und `scope=LIVE` für den
Live-Tab. Der Poll-Takt bleibt bei 10 Sekunden. Siehe
`2026-08-02-live-dashboard-payload-design.md`.


**Wunsch:** Das Dashboard hat bei 5-Sekunden-Takt in rund drei Stunden etwa 200 MB verbraucht. Das
ist zu viel — insbesondere für Schiedsrichter im Mobilfunknetz. Schlanker machen.

**Ausgangslage (gemessen am 2026-07-30 mit dem Seed-Szenario: 5 Läufe, 30 Mannschaften,
150 Teilnehmer):**
- Eine Antwort von `GET /event/{id}/liveDashboard`: **103.731 Bytes** unkomprimiert.
- Hochgerechnet: 3 h ÷ 5 s = 2160 Abrufe × ~101 KB ≈ **210 MB** — deckt sich exakt mit der
  Beobachtung.
- Dieselbe Antwort **gzip-komprimiert: 4.860 Bytes** — Faktor 21.
- Im Backend ist **keine Kompression installiert** (kein `install(Compression)` in
  `backend/src/main/kotlin/.../plugins/`). Das ist der größte und billigste Hebel.
- Zweitgrößter Posten im Payload: Für **jeden** Teilnehmer jedes Teams jedes Laufs werden alle
  Teilnahmebedingungen mit Zeitstempel, Notiz und Ampel ausgeliefert (im Seed: 150 Personen ×
  3 Bedingungen). Die Liste zeigt davon nur ein Aggregat (ein Ampel-Icon pro Mannschaft); die
  Details braucht erst der Detail-Dialog nach dem Antippen.

**Lösungsideen, grob nach Wirkung:**
1. **gzip aktivieren** (Ktor `Compression`-Plugin) — ~200 MB auf ~10 MB, eine Zeile Konfiguration,
   wirkt für alle Endpoints.
2. **Bedingungen aus dem Listen-Poll nehmen**: pro Mannschaft nur Ampel-Severity und „x von y
   erfüllt" liefern, die Details per eigenem Endpoint beim Öffnen des Dialogs nachladen.
3. **ETag / 304**: Wenn sich seit dem letzten Abruf nichts geändert hat, Antwort ohne Body. Braucht
   ein verlässliches Änderungskriterium (z.B. `max(updated_at)` über die beteiligten Tabellen).
4. **Nur laden, was der Tab braucht**: Der Live-Tab benötigt die laufenden Läufe und den nächsten —
   die vollständige Läufe-Liste könnte seltener oder auf Anforderung geladen werden.

**Offene Fragen:**
- Reicht 1 + 2, oder lohnt der Aufwand für ETags?
- Soll der Standard-Poll-Takt (heute 10 s, konfigurierbar 5/10/30/60 s) angepasst werden?

---

## B. Neue Bausteine

### B1. Athleten-Dashboard an Start und Ziel

**Wunsch:** Ein Dashboard für die Athleten, sichtbar am Start/Ziel. Schlicht: „Aktueller Lauf" und
„Nächster Lauf".

**Ausgangslage:**
- Die öffentliche, rotierende Kiosk-Anzeige gibt es schon: `EventInfoPage`
  (`/event/$eventId/info`) mit den View-Typen `RUNNING_MATCHES`, `UPCOMING_MATCHES`,
  `LATEST_MATCH_RESULTS`, konfigurierbar pro Veranstaltung, ohne Login erreichbar.
- Die Daten kommen aus den öffentlichen `eventInfo`-Endpoints — **ohne** die sensiblen Felder des
  Schiedsrichter-Dashboards (keine Rechnungen, keine Teilnahmebedingungen).

**Offene Fragen:**
- Reicht eine weitere Info-View („Aktuell & Nächster"), oder braucht es eine eigene, sehr reduzierte
  Seite für einen fest montierten Bildschirm?
- Was genau sehen Athleten: Bahn, Team, Startzeit — und Ergebnisse des gerade beendeten Laufs?
- Ein Bildschirm pro Position (Start vs. Ziel) mit unterschiedlichem Inhalt?

### B2. Zeitstrahl als Grundkonzept

**Wunsch (der große Brocken):** Ein Blanko-Zeitstrahl, in dem Läufe *vorbereitet* werden können und
dem ein konkreter Lauf (wie er heute existiert) zugeordnet wird. Die Uhrzeit leitet sich aus dem
Zeitstrahl ab — nicht pro Runde neu setzen. Daran orientiert sich vieles, unter anderem die
Information „das nächste Rennen ist noch nicht gesetzt" — und genau das wird zum **Breakpoint**.

Damit löst sich das Problem, dass zu früh ein Qualifying aktiv gesetzt wird.

**Ausgangslage:**
- Heute hängt die Startzeit am erzeugten Lauf (`competition_match.start_time`) und kann erst
  gepflegt werden, wenn die Runde gesetzt wurde (`createNextRound`). Bis dahin hat der Zeitplan
  Lücken.
- `competition_setup_match.start_time_offset` existiert bereits im Setup (Planungsebene!) und
  scheint ungenutzt — **das ist der erste Ort, an dem zu recherchieren ist**: Vielleicht ist die
  halbe Struktur für den Zeitstrahl schon da.
- Wegen der Lücken ist die automatische Lauf-Kette pro Veranstaltung abschaltbar
  (`event.auto_activate_next_match`, Default aus). Mit einem Zeitstrahl könnte diese Krücke
  entfallen.
- Für den Export zur Zeitnahme relevant: Laut Cees gehört die Startzeit **in den Wave-Namen** — nur
  dort ist sie auch in der Timer-App sichtbar. Ein Zeitstrahl bei uns wäre damit zugleich die Quelle
  für diesen Namensbestandteil.

**Offene Fragen:**
- Was ist ein Slot: fixe Uhrzeit, Dauer + Abstand, oder Reihenfolge mit abgeleiteten Zeiten?
- Zeitstrahl pro Veranstaltung, pro Renntag oder pro Bahn/Strecke?
- Wie verhält sich der Strahl bei Verzug — schiebt sich alles Folgende automatisch?
- Was passiert mit Slots, deren Lauf noch nicht gesetzt ist: Platzhalter mit Wettkampf/Runde, der
  später mit dem echten Lauf verknüpft wird?
- Zusammenspiel mit der Kette: Der Breakpoint stoppt die automatische Aktivierung — reicht das als
  Regel, oder braucht es zusätzlich eine Bestätigung?

---

## Reihenfolge-Vorschlag zum Diskutieren

Nicht entschieden, nur als Gesprächseinstieg: A5 Punkt 1 (gzip) ist eine Zeile und spart sofort
95 % des Traffics — unabhängig von allem anderen. A3 (DNS/DQ) ist klein und blockiert A4. A2 und A1
hängen beide am Zustandsmodell von `currently_running` und sollten zusammen gedacht werden. B2
(Zeitstrahl) ist Voraussetzung dafür, dass die Kette ohne Abschalter verlässlich wird — und damit
das eigentliche Fundament. B1 ist unabhängig und könnte jederzeit dazwischen.

**Stand 2026-08-02:** A5, A3 und A4 sind in dieser Reihenfolge abgearbeitet. Offen bleiben A1, A2,
B1 und B2. A2 berührt die Frage, woher der echte Startzeitpunkt kommt, und überschneidet sich damit
mit B2 — beide sollten nicht parallel angefasst werden.

**Stand 2026-08-04:** B2 (Zeitstrahl) ist umgesetzt, inklusive `started_at`/`finished_at` (damit
sind Teile von A2 erledigt: geplante vs. reale Startzeit existieren, "läuft seit" rechnet ab dem
Ist-Start). B1 (Athleten-Board) war bereits vorher fertig. Offen bleiben A1 und die verbliebenen
A2-Reste (Zustands-Aufräumen von `currently_running`); dazu kommt der neue Abschnitt C.

---

## C. Feedback aus dem Zeitstrahl-Funktionstest (04.08.2026)

Gesammelt beim ersten Durchspielen des Zeitstrahls mit dem Task-20-Seed. Kleinigkeiten
(Hilfetexte im Shift-Dialog, Hinweis bei komplett verplanten Läufen, Programmpunkte im
Schiedsrichter-Dashboard, Buttons nebeneinander, Start-Button entfernt, Navigation Slot → Lauf)
wurden direkt umgesetzt und stehen hier nicht mehr.

### C1. Kette: Auto-Beenden bei vollständiger Ergebniseingabe — einstellbar

**Beobachtung:** Trägt man über die Wettkampf-Durchführung alle Ergebnisse ein, setzt
`prepareForNewPlaces` den Lauf automatisch inaktiv — aber ohne `finished_at` und ohne
Ketten-Trigger. Der nächste Lauf startet also nicht, obwohl der Lauf faktisch fertig ist. Nur der
"Lauf beenden"-Button im Schiedsrichter-Dashboard stempelt und zieht die Kette weiter.

**Wunsch:** Die Kette sollte nur dann unterbrochen werden, wenn ein Lauf *manuell* deaktiviert
wurde. Ob ein Schiedsrichter aktiv "Lauf beenden" drücken muss oder ob die vollständige
Ergebniseingabe als Beenden gilt, muss **einstellbar** sein — noch unklar, ob die Schiedsrichter
den Pflicht-Klick akzeptieren. Alternative Betriebsart: eine Person aus dem Orga-Team aktiviert
und deaktiviert die Läufe über den Zeitplan.

**Offene Fragen:**
- Schalter pro Veranstaltung ("Beenden durch Schiedsrichter" vs. "automatisch bei vollständigen
  Ergebnissen")? Wie verhält sich der RaceClocker-Pull dabei (der trägt auch Ergebnisse ein)?
- Orga-Betriebsart: Aktivieren/Deaktivieren direkt aus dem Zeitplan-Tab heraus?

### C2. Zeitstrahl-Indikator

Ein durchgehender Fortschritts-Indikator ("wo stehen wir gerade, was ist aktiv, was kommt als
Nächstes") — im Zeitplan-Tab und/oder als kompakte Leiste im Schiedsrichter-Dashboard. Ergänzt
C1: Wer die Läufe über den Zeitplan steuert, braucht diese Sicht.

### C3. Ganze Runde überspringen

**Beobachtung:** Scheiden im Zeitfahren so viele Boote aus, dass die Folgerunde nicht stattfinden
muss, kann man heute nur die einzelnen Slots überspringen. Eine Aktion "Runde überspringen"
(alle Slots der Runde + die Runde selbst als übersprungen markieren, Kette läuft zur übernächsten
Runde) fehlt.

### C4. createNextRound-Trigger hinterfragen (mit Ilka besprechen)

Warum braucht es den manuellen "Nächste Runde erstellen"-Klick noch? Eventuell gibt es keinen
fachlichen Grund mehr — mit dem Zeitstrahl könnten Runden automatisch materialisieren, sobald die
Vorrunde beendet ist. **Aber:** Der Export zu RaceClocker hat Latenz — der Lauf wäre in ready2race
schon gesetzt, während er in RaceClocker noch nicht existiert. Klären, ob der manuelle Trigger
genau diese Lücke bewusst offenhält.

### C5. Navigation überarbeiten

Im Wettkampf fehlt ein Zurück-Button zur Veranstaltung; generell die Navigationswege prüfen
(Breadcrumbs? Zurück-Pfeile auf den Unterseiten?). Unabhängig vom Zeitstrahl, fiel beim Testen auf.

### C6. Kleinere technische Follow-ups aus dem Final-Review

- `maxReductionMinutes` als strukturiertes Feld im 422-Body statt Regex auf den Fehlertext.
- Write-Through-Guard: `start_time` beendeter Läufe bei Import/Shift nicht mehr überschreiben.
- Produktentscheidung Parallelstart-Paar: Kette rückt weiter, sobald *ein* Lauf des Paars beendet
  ist — gewollt oder auf "alle beendet" warten?
- DB-Integrationstests für die Ketten-Trigger (finishMatch/createNewRound).
- Same-Day-Overlap-Prüfung bei negativen Shifts.

**Entscheidungen 04.08.2026 (abends, Thomas):**
- **C1 + A1 sind entschieden und verschmolzen:** Ein Lauf wird NUR durch aktiven Input beendet
  (kein Auto-Beenden bei vollständigen Ergebnissen, kein Schalter). Begründung: Der Beenden-Klick
  ist das Signal ans Regattabüro, dass der Stand final ist und der RaceClocker-Import starten
  kann — beendet sich ein Rennen automatisch, drohen Fehlannahmen, wenn die Zeitnahme auf
  Anweisung des Schiedsrichters noch eine Strafe nachträgt. Offen bleiben nur die
  Ausgestaltungsfragen (siehe C1-Detailfragen im Chat: stille Deaktivierung bei
  Ergebnis-Vollständigkeit abschaffen? Zwischenzustand "Ergebnisse vollständig — wartet auf
  Beenden"? Beenden auch aus dem Zeitplan durch Orga?).
- **C6/Parallelstart ist entschieden:** Die Kette rückt erst weiter, wenn ALLE Läufe derselben
  Startzeit beendet sind (nicht schon beim ersten). Wird umgesetzt.
- **A2 ist durch den Zeitstrahl erledigt** (geplant/real getrennt, keine "läuft seit 0 min"-Anzeige
  mehr vor dem Start).
- C4 klärt Thomas per Rückfrage (Ilka); C5 (Navigation, inkl. Zurück-Button) bewusst vertagt.

**C1-Ausgestaltung entschieden (04.08.2026, spät):** Drei-Wege-Modus `chain_progression_mode`
pro Veranstaltung (ersetzt den Boolean `auto_activate_next_match`; Migration: false →
DEAKTIVIERT, true → SCHIEDSRICHTER):
- **SCHIEDSRICHTER:** Beenden + Kette über das Schiedsrichter-Dashboard (wie heute).
- **REGATTABUERO:** Beenden/Aktivieren **exklusiv** über den Zeitplan-Tab (Beenden-Button
  verschwindet vom Schiedsrichter-Dashboard); das Büro gibt nach Kontrolle frei, dann Kette.
- **DEAKTIVIERT:** Beenden wirkt nur auf den Lauf, keine automatische Aktivierung.
Zusätzlich: Die **stille Deaktivierung bei vollständiger Ergebniseingabe entfällt** — der Lauf
bleibt aktiv, die Karte zeigt „Ergebnisse vollständig — wartet auf Beenden", bis der zuständige
Akteur klickt. Der RaceClocker-Pull meldet nur Daten und beendet nie.

**Konkreter Anlass für die DB-Integrationstests (C6), 05.08.2026:** Der Guard „beendete Läufe
nicht reaktivieren" las `match_finished_at` aus `getSlotWithContext` — diese Einzel-Slot-Query
selektierte den Alias aber nicht (im Gegensatz zu `getSlots`/`getChainSlots`). Folge: jOOQ warf
`IllegalArgumentException` und **jedes** Aktivieren über den Zeitplan endete mit HTTP 500,
unabhängig vom Laufzustand. Reine Unit-Tests auf den `*Logic`-Objekten können diese Fehlerklasse
(gelesener Alias fehlt in der konkreten Query) nicht sehen — ein DB-gestützter Test pro Repo-Query
hätte sie sofort gefangen.

**C3 präzisiert und umgesetzt (05.08.2026):** "Runde entfällt" sitzt in der **Durchführung** (nicht im
Zeitplan — dort wäre es zu prominent) und erscheint nur, wenn die Runde nichts zu fahren hat: Runde
materialisiert und kein Lauf mit zwei oder mehr startenden Booten (Freilos-Fall). Sonst müssen die
Läufe gefahren werden, damit die Setzung der Folgerunde stimmt — der Endpoint lehnt das serverseitig
mit 409 ab (auch bei noch nicht gesetzter Runde; dort bleibt nur der Einzel-Slot-Entfall).
