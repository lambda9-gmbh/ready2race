# Manueller Check-in und Check-out für einzelne Athlet:innen

Stand: 2026-08-09 · Branch `claude/ready2race-manual-checkin-out-83bb35`

## Anlass

Der QR-Scan am Steg bleibt der reguläre Weg auf und vom Wasser. Er versagt aber in genau den
Lagen, in denen es darauf ankommt: Ein Boot hat abgelegt, ohne dass jemand das Bändchen gezogen
hat — die Crew ist auf dem Wasser, das Protokoll sagt etwas anderes. Bisher gibt es keinen Weg,
das zu berichtigen. `participant_tracking` kennt nur Anlegen, nie Ändern, und der Zeitstempel ist
fest `LocalDateTime.now()`.

Admins und Schiedsrichter brauchen deshalb einen Ausnahmeweg. Er muss zwei Dinge zugleich
leisten: das Protokoll wieder mit der Wirklichkeit in Deckung bringen — und für jeden späteren
Leser erkennbar bleiben als das, was er ist. Ein manueller Eintrag darf nie wie ein Scan aussehen.

## Ist-Stand

- `participant_tracking` (id, participant, event, scan_type `ENTRY|EXIT`, scanned_at, scanned_by):
  reines Anfüge-Protokoll. „Aktueller Status" ist der jüngste Eintrag.
- Einziger Schreibpfad: `POST /event/{eventId}/participant/{participantId}/checkInOut`, geschützt
  durch `UpdateAppCompetitionCheckGlobal` — das Recht der QR-App, nicht das der Schiedsrichter.
- Prüfung in `ParticipantTrackingService.participantCheckInOut`: ENTRY nur, wenn nicht drin;
  EXIT nur, wenn drin.
- Anzeigen: QR-App `TeamCheckInOut.tsx`; Admin-Protokoll `ParticipantTrackingLogTable.tsx` (nur
  lesend); Schiedsrichter `LiveDashboardTeamDialog.tsx` — zeigt die Crew, aber ihren
  Arena-Status bisher nicht je Person.

## Datenmodell

Migration `V202608091600__participant_tracking_manual.sql`. (`…1500` war bereits vom
Auto-Abgleich der Durchführungsseite auf einem parallelen Branch belegt.)

```sql
alter table participant_tracking
    add column source varchar(10) not null default 'QR';   -- QR | MANUAL

create table participant_tracking_change
(
    id                  uuid primary key,
    tracking            uuid references participant_tracking (id) on delete set null,
    participant         uuid        not null references participant (id),
    event               uuid        not null references event (id),
    change_type         varchar(10) not null,   -- CREATE | UPDATE
    previous_scan_type  varchar(10),
    previous_scanned_at timestamp,
    new_scan_type       varchar(10) not null,
    new_scanned_at      timestamp   not null,
    reason              text        not null check (length(btrim(reason)) > 0),
    created_at          timestamp   not null,
    created_by          uuid references app_user (id) on delete set null,
    constraint chk_ptc_previous_matches_type check (
        (change_type = 'UPDATE' and previous_scan_type is not null and previous_scanned_at is not null) or
        (change_type = 'CREATE' and previous_scan_type is null and previous_scanned_at is null))
);
```

Drei Entscheidungen, die hier stecken:

**Die Begründung ist auf Datenbankebene Pflicht.** Ein `not null` mit `btrim`-Prüfung, nicht bloß
ein Validator im Service. Ein vergessener Zweig im Code kann die Spur damit nicht leeren.

**`on delete set null` auf `tracking`.** Die Spur überlebt ihren Eintrag. `participant` und
`event` stehen redundant daneben, damit eine verwaiste Zeile weiterhin einer Person zuzuordnen
ist — sonst wäre „revisionssicher" ein Versprechen, das die Fremdschlüssel nicht halten.

**Kein denormalisiertes „bearbeitet"-Feld.** `participant_tracking_view` bekommt `source`,
`edit_count`, `last_edited_at` und den Namen der zuletzt bearbeitenden Person aus einem Aggregat
über `participant_tracking_change`. Ein zweites Feld, das den gleichen Sachverhalt behauptet,
kann von ihm abweichen; ein Aggregat kann das nicht.

Damit sind die drei Fälle in der Anzeige unterscheidbar:

| Fall | `source` | `edit_count` |
|---|---|---|
| per QR erfasst | `QR` | `0` |
| manuell angelegt | `MANUAL` | ≥ 1 |
| QR-Eintrag korrigiert | `QR` | ≥ 1 |

## Berechtigungen

Alle neuen Endpunkte: `authenticateAny(Privilege.UpdateLiveDashboardGlobal, Privilege.UpdateEventGlobal)`.

Das trifft „Admin und Schiedsrichter" mit Rechten, die es bereits gibt. Ein neues Privileg wäre
sauberer getrennt, landet nach `initializeDatabase` aber nur automatisch auf der Admin-Rolle — die
Schiedsrichter-Rolle in der laufenden Produktion müsste von Hand nachgezogen werden, und bis
dahin sähe niemand die Funktion.

Der **lesende** Endpunkt ist genauso geschützt wie die schreibenden. Begründungen und
Änderungsspur sind interne Angaben; öffentliche Ansichten und die Athletenanzeige bekommen sie
nicht zu sehen. Die QR-App bleibt bei `UpdateAppCompetitionCheckGlobal` und schreibt weiterhin
`source = 'QR'`.

## Backend

Drei Endpunkte unter `/event/{eventId}/participant/{participantId}`:

| Methode | Pfad | Zweck |
|---|---|---|
| `GET` | `/tracking` | Verlauf der Person in dieser Veranstaltung samt Änderungsspur |
| `POST` | `/tracking` | Manuellen Eintrag ergänzen |
| `PUT` | `/tracking/{trackingId}` | Bestehenden Eintrag korrigieren |

Das Request-DTO folgt `CompetitionDeregistrationRequest`: `scanType`, `scannedAt`, `reason` mit
`StringValidators.notBlank`. `scannedAt` ist frei wählbar und ausdrücklich **nicht** an `now()`
gebunden — der Nachtrag betrifft naturgemäß die Vergangenheit, und eine vorausschauende
Eintragung zu verbieten hätte am Steg keinen Nutzen.

### Die Reihenfolgeregel

Die Prüfung wandert in eine reine Funktion `ParticipantTrackingLogic.validateSequence(entries)` —
ohne Datenbank testbar. Regel: Die Einträge einer Person in einer Veranstaltung, chronologisch
sortiert, müssen mit `ENTRY` beginnen und danach abwechseln.

Der Service stellt die Liste **nach** der geplanten Änderung zusammen und lehnt ab, wenn die Regel
bricht. Das fängt auch den Fall, den eine reine Nachbarschaftsprüfung durchließe: ein Eintrag,
der mitten in die Historie gesetzt wird und erst eine spätere Zeile widersprüchlich macht.

Zwei Einträge derselben Person auf dieselbe Sekunde werden abgelehnt. Ihre Reihenfolge wäre sonst
nicht bestimmt, und damit auch nicht die Frage, ob die Person am Ende in der Arena ist.

`participantCheckInOut` ruft dieselbe Funktion auf. Der heutige Zwei-Zeilen-Vergleich entfällt —
zwei Formulierungen derselben Regel laufen mit der Zeit auseinander.

### Fehler

Neu in `ParticipantTrackingError` samt `ErrorCode` und Übersetzung in `liveDashboardError.ts`:

- `TrackingEntryNotFound` (404)
- `TrackingSequenceConflict` (409) — widersprüchliche Abfolge
- `TrackingTimestampCollision` (409) — zwei Einträge auf dieselbe Sekunde

## Oberflächen

Ein Dialog, drei Einstiegspunkte: `ParticipantTrackingDialog.tsx` unter
`components/event/participantTracking/`.

Aufbau:

- Kopf mit Hinweis, dass der reguläre Weg der QR-Scan ist und jede Eingabe hier protokolliert
  wird. Warnfarbe, kein Primär-Button — die Funktion soll sich nicht wie ein Scan anfühlen.
- Verlauf chronologisch. Je Zeile: Typ-Chip, Zeitpunkt, Herkunfts-Chip (**QR** / **manuell** /
  **QR, korrigiert**), erfassende Person, Stift zum Korrigieren.
- Formular: Typ, freie Datum-/Zeitwahl (`@mui/x-date-pickers` ist im Projekt), Begründung als
  Pflichtfeld.
- Änderungsspur darunter, im Klartext: „09.08.2026 14:12 · Thomas Feddersen — Check-out 14:05 →
  13:40 · Grund: …"

Einstiegspunkte:

1. **Admin** — `ParticipantForEventTable`: Zeilen-Aktion im bestehenden Menü.
2. **Admin** — `ParticipantTrackingLogTable`: neue Spalte „Erfassung" mit dem Herkunfts-Chip,
   dazu die Zeilen-Aktion.
3. **Schiedsrichter** — `LiveDashboardTeamDialog`: je Person Arena-Status und Stift-Button. Der
   Status fehlt dort heute ganz; der Detail-Endpunkt liefert ihn mit.

Übersetzungen in de, en, da.

## Tests

- `ParticipantTrackingSequenceTest` — reine Logik, ohne Datenbank: Einfügen mitten in die
  Historie, Korrektur, die eine spätere Zeile widersprüchlich macht, Kollision auf dieselbe
  Sekunde.
- `ManualTrackingTest` (Testcontainers, `testComprehension`): manuelle Anlage ohne Vorlauf;
  Korrektur eines QR-Eintrags mit Prüfung der Vorher-/Nachher-Werte in
  `participant_tracking_change`; leere Begründung wird abgelehnt; Zeitpunkte in Vergangenheit
  und Zukunft werden angenommen.
- `ManualTrackingHttpIT` (`testApplicationComprehension`): Rolle ohne die beiden Privilegien →
  403 auf allen drei Endpunkten, **einschließlich `GET`**; als Admin → 200. Läuft als `IT`
  außerhalb der normalen Suite, wie `DeleteQrCodeHttpIT`.
- `CheckInOutOrderTest` bleibt unverändert und muss grün bleiben — er ist der Beleg, dass der
  QR-Weg von der neuen Regel nicht angefasst wird.

## Bewusst nicht enthalten

**Löschen von Einträgen.** Ein falscher Eintrag wird korrigiert, nicht getilgt. Ein Löschpfad
würde dem Zweck der Revisionssicherheit direkt zuwiderlaufen.

**Massenbearbeitung ganzer Boote.** Die Anforderung nennt ausdrücklich einzelne Athlet:innen. Ein
Sammelknopf „ganze Crew aufs Wasser" wäre genau die Bequemlichkeit, die den Ausnahmeweg zum
Normalweg macht.
