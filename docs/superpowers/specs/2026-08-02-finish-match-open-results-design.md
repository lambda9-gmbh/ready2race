# Design: Rückfrage beim Beenden mit offenen Ergebnissen (A4)

**Stand:** 2026-08-02
**Backlog-Punkt:** A4 aus `2026-07-30-schiedsrichter-dashboard-backlog.md`
**Baut auf:** A3 (`2026-08-02-result-status-dns-dq-design.md`) — die Sammelaktionen tragen die
dort eingeführten Kürzel ein.

## Verhalten

Beim „Lauf beenden" im Schiedsrichter-Dashboard:

- **Alle Boote erfasst:** unverändert. Der Button startet direkt die 5-Sekunden-Bedenkzeit.
- **Offene Boote vorhanden:** ein Dialog fragt, was mit ihnen geschieht:
  - *Als DNS eintragen* — alle offenen Boote werden ausgeschieden mit Grund „DNS".
  - *Als DNF eintragen* — dasselbe mit „DNF".
  - *Offen lassen* — beendet wie bisher, Ergebnisse bleiben leer.
  - *Abbrechen* — nichts passiert.

Nach der Wahl läuft die gewohnte 5-Sekunden-Bedenkzeit; erst danach geht der Aufruf raus.
Gemischte Fälle — ein Boot DNS, eins DNF — trägt der Schiedsrichter wie bisher in der
Ergebnismaske ein. Der Dialog ist die Sammelaktion für den Rest.

## „Offen" ist bisher falsch definiert

Ein Boot gilt als offen, wenn es weder Platz noch Zeit noch einen Ausscheidungsgrund hat.
Abgemeldete Boote zählen nicht dazu — für sie kommt nie ein Ergebnis.

Genau das fehlt heute an zwei Stellen:

- `LiveDashboardLogic.teamHasResult(place, failed)` kennt die Abmeldung nicht. Über
  `deriveMatchState` bedeutet das: ein Lauf mit einem abgemeldeten Boot erreicht nie den Zustand
  `FINISHED`.
- `resultsComplete` in `LiveDashboardMatchCard` rechnet genauso. Die Warnung „Es fehlen noch
  Ergebnisse" steht dort dauerhaft, obwohl alles erfasst ist.

Beide bekommen die Abmeldung als dritten Parameter. Das ist Voraussetzung für A4: sonst fragt
der Dialog nach Booten, für die es nichts zu entscheiden gibt.

## Backend

`PUT /event/{eventId}/liveDashboard/match/{matchId}/finish` bekommt einen optionalen
Query-Parameter `openResults` mit den Werten `DNS` oder `DNF`. Fehlt er, bleibt alles wie bisher.

Ist er gesetzt, markiert `finishMatch` vor dem Beenden alle offenen Boote des Laufs in **einem**
Update: `failed = true`, `failed_reason` = das Kürzel. Offen heißt in SQL: `out` ist falsch,
`failed` ist falsch, `place` ist null, `timecode` ist null und es gibt keinen Eintrag in
`competition_deregistration`.

Ein Update statt einer Schleife über einzelne Ergebnisse, weil hier nichts umzurechnen ist:
Plätze und Zeiten der bereits erfassten Boote bleiben unangetastet, und die markierten Boote
bekommen keinen Platz.

## Frontend

- `common.ts` bekommt `teamHasResult(team)` und `openResultTeams(match)`. Beide Stellen — die
  Vollständigkeitsprüfung der Karte und der Dialog — benutzen dieselbe Definition.
- `FinishMatchButton` erhält die Zahl der offenen Boote und meldet die getroffene Wahl
  (`'DNS' | 'DNF' | null`) nach oben. Nur bei offenen Booten öffnet sich der Dialog.
- `LiveDashboardPage.handleFinish` reicht die Wahl als Query-Parameter durch.
- Neue i18n-Keys unter `event.liveDashboard.control.openResults.*` in de/en/da.

## Tests

- Backend: `LiveDashboardLogicTest` deckt `teamHasResult` mit Abmeldung ab, inklusive der
  Auswirkung auf `deriveMatchState`.
- Frontend: Vitest für `openResultTeams` — Boot mit Platz, Boot mit Zeit ohne Platz,
  ausgeschiedenes Boot, abgemeldetes Boot, offenes Boot.
- Die SQL-Bedingung des Sammel-Updates bleibt ungetestet; dafür fehlt ein DB-gestützter
  Integrationstest für den `LiveDashboardService`, der schon vorher als Follow-up notiert war.

## Nicht Teil dieser Änderung

- Status je offenem Boot einzeln wählen — dafür bleibt die Ergebnismaske zuständig.
- Rückfrage bei vollständigen Ergebnissen; der Undo-Knopf deckt den Fehltipper ab.
