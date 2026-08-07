# Design: Live-Dashboard für Schiedsrichter:innen

**Stand:** 2026-07-29
**Status:** Entwurf zur Umsetzung
**Branch:** `feature/live-dashboard-schiedsrichter`
**Betroffene Domains:** `participantRequirement`, `competitionExecution`, `invoice`, `auth/role`, neues Modul `liveDashboard`

## Transparenz & Einordnung

Dieser Branch ist mit KI-Unterstützung entstanden (Claude Code, Modell Fable) und wurde von Thomas Feddersen fachlich geführt, lokal getestet und reviewt. Er versteht sich als **funktionierender Vorschlag mit Lastenheft-Charakter**, nicht als Produktions-Anspruch: Das Team kann ihn übernehmen, überarbeiten oder als Referenz für eine eigene Umsetzung nutzen. Design-Entscheidungen und Datenmodell sind bewusst ausführlich dokumentiert, damit beides auch unabhängig vom Code nutzbar ist.

## 1. Ziel

Schiedsrichter:innen bekommen ein mobiles Live-Dashboard, das ohne Zuruf der Regie auskommt:

- **Immer das aktuelle Rennen** sehen — mit allen prüfrelevanten Informationen.
- **Self-Service über alle Läufe**: vergangene (mit Ergebnissen), laufende, bevorstehende — und jederzeit mit einem Tap zurück auf „Live".
- Pro Team/Teilnehmer:in die **Prüf-Informationen**: Rechnungsstatus des Vereins, alle Teilnehmer-Überprüfungen (z.B. Aktivenpass, Verwiegung) inkl. Zeitpunkt, Notiz (z.B. Extragewichte) und — bei konfiguriertem Zeitfenster — einer Ampel relativ zum Rennstart.
- **Zeiten/Ergebnisse** erscheinen, sobald sie im System sind (heute via Excel-Import, später zusätzlich via RaceClocker-Pull, siehe Issue #94).

## 2. Abgrenzung zum bestehenden Kiosk-Dashboard

Beide Dashboards koexistieren bewusst, weil sie unterschiedliche Zwecke haben:

| | Öffentliche Informationsanzeige (`EventInfoPage`, unverändert) | Schiedsrichter-Dashboard (neu) |
|---|---|---|
| Zweck | Publikums-Anzeige, rotierende Views, Beamer/Kiosk | Arbeitswerkzeug für Prüfungen am Start |
| Zugriff | öffentlich | Login + neues Privileg `LIVE_DASHBOARD` |
| Daten | öffentliche `eventInfo`-Endpoints | neuer aggregierter Endpoint (enthält sensible Daten: Rechnungen, Checks) |
| Gerät | Großbildschirm | Smartphone/Tablet (mobile-first) |

Geteilt wird die Backend-Basis: die Match-Queries in `CompetitionMatchRepo` (`getRunningMatches`, `getUpcomingMatches`, `getMatchesByEvent`) werden wiederverwendet bzw. erweitert — keine Duplikation der Match-Logik.

## 3. Datenmodell (eine Migration)

`participant_requirement` erhält zwei **optionale** Spalten für ein Zeitfenster relativ zum Rennstart:

```sql
alter table participant_requirement
    add column check_earliest_minutes_before int,  -- Check darf frühestens X min vor Start liegen
    add column check_latest_minutes_before  int;   -- Check muss spätestens Y min vor Start vorliegen
```

- Beide nullable und unabhängig setzbar; nur `earliest` gesetzt = reine „max. Alter"-Regel.
- Ist keine der beiden gesetzt → **keine Ampel** für dieses Requirement (bestehendes Verhalten bleibt unverändert).
- Validierung (Request-Ebene): wenn beide gesetzt, muss `earliest > latest` gelten; Werte > 0.

Der Check-Zeitpunkt und die Notiz existieren bereits (`participant_has_requirement_for_event.created_at` / `.note`) — dort ändert sich nichts. Extragewichte o.ä. werden weiterhin als Notiz am Check erfasst (z.B. „Waage 55 kg, +2,5 kg Ausgleich").

## 4. Berechtigungen

- Neues `Privilege.Resource`: **`LIVE_DASHBOARD`**, genutzt mit Action `READ`, Scope `GLOBAL`.
- Taucht damit automatisch in der dynamischen Rollenverwaltung auf; im Prod-System wird es der bestehenden Rolle „Schiedsrichter:in" zugewiesen.
- Frontend-Spiegel in `frontend/src/authorization/privileges.ts` (`readLiveDashboardGlobal`).

## 5. Backend: neues Modul + ein aggregierter Endpoint

Neues Package `app/liveDashboard/` nach bestehendem Muster (`boundary/liveDashboard.kt`, `boundary/LiveDashboardService.kt`, `entity/*`).

**`GET /event/{eventId}/liveDashboard`** — authentifiziert, Privileg `READ LIVE_DASHBOARD`.

Eine Antwort enthält alles, was das Dashboard braucht (genau **ein** Poll-Request pro Zyklus — wichtig für Mobilgeräte mit schwachem Netz):

```
LiveDashboardDto
└─ matches: LiveDashboardMatchDto[]          — ALLE existierenden Läufe des Events
   ├─ state: FINISHED | RUNNING | UPCOMING | UNSCHEDULED
   ├─ competitionId, competitionName, roundName, matchName
   ├─ startTime?, currentlyRunning, elapsedTimeMinutes?   (nur RUNNING)
   └─ teams: LiveDashboardTeamDto[]
      ├─ startNumber, teamName, clubName
      ├─ place?, time?                        — sobald Ergebnisse vorliegen
      ├─ invoiceStatus: PAID | OPEN | NONE
      └─ participants: LiveDashboardParticipantDto[]
         ├─ firstname, lastname, namedRole, year, gender
         └─ requirements: RequirementStatusDto[]   — ALLE Requirements des Events
            ├─ id, name, optional, checked, checkedAt?, note?
            └─ timeCheck?: TimeCheckDto            — nur bei konfiguriertem Fenster UND startTime
               ├─ deltaMinutes?                    — Abstand Check → Rennstart (fehlt bei NOT_CHECKED)
               └─ status: OK | TOO_EARLY | LATE | NOT_CHECKED
```

### Semantik

- **`state`-Ableitung** (in dieser Reihenfolge): `RUNNING` = `currently_running = true`; sonst `FINISHED` = Ergebnisse vorhanden (Plätze gesetzt); sonst `UNSCHEDULED` = keine Startzeit gesetzt; sonst `UPCOMING` (auch wenn die Startzeit bereits überschritten ist — der Lauf steht noch aus). Sortierung: nach `startTime` aufsteigend, `UNSCHEDULED` am Ende.
- **Ampel serverseitig**: Referenzpunkt ist `startTime` des Matches, verglichen mit `checkedAt`.
  - `OK`: Check liegt im Fenster.
  - `TOO_EARLY`: Check liegt weiter vor dem Start als `check_earliest_minutes_before`.
  - `LATE`: Check liegt näher am Start als `check_latest_minutes_before` (oder nach dem Start). **Späte Erfüllung ist erlaubt, muss aber klar sichtbar sein** — erfüllt ist erfüllt, aber die Schiedsrichter:in sieht sofort, dass es außerhalb der Regel war.
  - `NOT_CHECKED`: kein Check vorhanden.
  - Kein `startTime` am Match → kein `timeCheck` (Client zeigt „keine Startzeit geplant"). Läufe erhalten ihre Uhrzeiten wie bisher über die Ausführungs-Ansicht (`PUT …/{matchId}/data`); es braucht kein neues Planungs-Feature, nur die organisatorische Praxis, Zeiten beim Anlegen der Runde zu pflegen. Einschränkung im Modell: Matches entstehen rundenweise (`createNextRound`) — Läufe noch nicht angelegter Runden existieren nicht und erscheinen folglich nicht im Dashboard.
- **`invoiceStatus`**: Verein des Teams → Event-Registrierung → `event_registration_invoice` → `invoice.paid_at`. `PAID` = alle Rechnungen bezahlt, `OPEN` = mindestens eine offen, `NONE` = keine Rechnung vorhanden (neutral, kein Verstoß).
- **OpenAPI**: `documentation.yaml` erweitern, danach Frontend-Client regenerieren (`sdk.gen.ts` / `types.gen.ts`).

## 6. Frontend

### Einstieg & Route

- Neue Route **`/event/$eventId/liveDashboard`** in `routes.tsx`, Guard: `READ LIVE_DASHBOARD`.
- Auf der `EventPage` eine **eigene Sektion „Schiedsrichter-Dashboard"** direkt neben „Öffentliche Informationsanzeige" (gleiches Karten-Muster), nur sichtbar mit Privileg. Die URL ist als Lesezeichen auf dem Schiedsrichter-Gerät gedacht.

### Aufbau (mobile-first)

- **Zwei Tabs** („Live" / „Läufe"), als untenliegende, daumenfreundliche Navigation (MUI `BottomNavigation`):
  - **Live** (Default): laufende Rennen als große Karten — Startnummer, Team, Verein, Rechnungs-Badge, aggregierter Bedingungs-Status pro Teilnehmer:in („2/2" mit Ampelfarbe, analog zur bestehenden „Überprüfte Bedingungen"-Anzeige). Kein laufendes Rennen → Hinweis + Vorschau des nächsten Laufs.
  - **Läufe**: alle Läufe chronologisch — vergangene mit Platz/Zeit, laufende hervorgehoben, bevorstehende mit Startzeit-Countdown, „ohne Startzeit" als Gruppe am Ende.
- **Detail-Dialog** per Tap auf ein Team: alle Bedingungen mit Status, Zeitdelta („Verwiegung vor 1 h 40 min"), Ampelfarbe inkl. „spät erfüllt"/„zu früh erfüllt", Notiz (z.B. Extragewichte), Rechnungsstatus.
- **Immer zurück zu Live**: Live-Tab ist stets ein Tap entfernt; ändert sich die Menge der laufenden Rennen, zeigt der Live-Tab einen Badge-Punkt.
- **Polling**: bestehendes `useFetch({autoReloadInterval})`, Intervall 10 s, „zuletzt aktualisiert"-Zeile. Bei Fetch-Fehler (Funkloch am Steg) bleibt der letzte Stand sichtbar plus Warnhinweis — keine leere Seite.
- **i18n**: neue Keys in `de`, `en`, `da` (`event.liveDashboard.*`).

### Konfigurations-UI

Das bestehende Requirement-Formular (Admin) erhält zwei optionale Zahlenfelder: „frühestens __ min vor Start" / „spätestens __ min vor Start". Mehr nicht.

## 7. Edge Cases

| Fall | Verhalten |
|---|---|
| Kein laufendes Rennen | Live-Tab: Hinweis + nächster Lauf als Vorschau |
| Requirement `optional = true` | wird angezeigt, zählt nicht als roter Verstoß (neutraler Stil) |
| Team ohne Rechnung | `NONE`, neutral dargestellt |
| Match ohne Startzeit | Checks normal sichtbar, statt Ampel Hinweis „keine Startzeit geplant"; Gruppe am Listenende |
| Check außerhalb des Fensters | sichtbar erfüllt, aber klar markiert (`LATE`/`TOO_EARLY`) mit Delta |
| Spätere Runden noch nicht angelegt | erscheinen nicht (Matches existieren rundenweise) |
| Funkloch / Fetch-Fehler | letzter Stand bleibt stehen + Warnhinweis |
| Substitutionen | Werden pro Runde aufgelöst — angezeigt wird die tatsächlich startende Crew inkl. übernommener Rollen (gleiche Logik wie die Startliste) |

## 8. Tests & Verifikation

- **Backend (Kotlin, bestehendes Test-Setup)**: Unit-Tests für die Fensterlogik (alle vier Status inkl. Randwerte, einseitige Fenster, fehlende Startzeit) und die `invoiceStatus`-Ableitung (PAID/OPEN/NONE, mehrere Rechnungen).
- **Request-Validierung**: `earliest > latest`, positive Werte.
- **Frontend**: manuelle Verifikation im mobilen Viewport (Live-Wechsel, Polling, Detail-Dialog, Offline-Verhalten), lokal gegen das Backend getestet.

## 9. Git & Auslieferung

- Feature-Branch `feature/live-dashboard-schiedsrichter` von `main`, am Ende PR nach `main` (Merge-Commit, wie im Projekt üblich).
- Migration folgt dem Flyway-Namensschema `V<timestamp>__…`; OpenAPI-Änderung + Client-Regeneration im selben Branch.
