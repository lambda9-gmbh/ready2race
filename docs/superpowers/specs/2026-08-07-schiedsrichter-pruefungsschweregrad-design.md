# Schweregrad je Prüfung im Schiedsrichter-Dashboard

Entwurf vom 07.08.2026, Branch `feature/crf-2026`.

## Problem

Die Ampel im Schiedsrichter-Dashboard ist hart verdrahtet. `teamSeverity` in
`frontend/src/components/event/liveDashboard/common.ts` bewertet vier Sachverhalte mit fest
vergebenen Farben: fehlende Pflicht-Teilnahmebedingung rot, verletztes Zeitfenster gelb, offene
Rechnung rot, bei laufendem Lauf kein Auscheck-Scan rot.

Zwei Dinge stimmen daran nicht:

1. **Schiedsrichter entscheiden am Renntag anders.** Es kommt vor, dass unter bestimmten
   Voraussetzungen beschlossen wird, offene Rechnungen an diesem Tag nicht zu ahnden. Heute lässt
   sich das nur ignorieren — mit dem Ergebnis, dass rote Zeilen zur Gewohnheit werden und die
   Ampel ihre Aussage verliert.
2. **Nicht jede Prüfung gilt für jeden Wettkampf.** Der Beachsprint braucht keine An-/Abmeldung
   aufs Wasser, die Langstrecke schon. Heute meldet das Dashboard beim Beachsprint einen Fehler
   für etwas, das dort gar nicht vorgesehen ist.

## Entscheidungen

- **Konfiguriert wird ausschließlich pro Wettkampf.** Es gibt keine geerbte Veranstaltungs-Ebene
  darüber. Die Verwaltung gleicht das über eine Sammelaktion aus („alle auf Warning"), gespeichert
  wird trotzdem je Wettkampf.
- **Zwei getrennte Fragen, zwei getrennte Orte.** *Gilt die Prüfung überhaupt?* ist eine
  Eigenschaft des Rennformats und steht am Wettkampf. *Wie hart wird sie geahndet?* ist eine
  Tagesentscheidung und steht in der Schiedsrichter-Verwaltung.
- **Bewertet wird im Backend.** Das Dashboard liefert fertige Schweregrade aus; eine Änderung wirkt
  beim nächsten Poll auf allen Geräten gleichzeitig. Eine im Frontend gecachte Konfiguration würde
  am Steg zu Geräten mit unterschiedlichen Farben führen, ohne dass es jemandem auffällt.
- **Die Standardwerte sind das heutige Verhalten.** Ohne einen einzigen Konfigurationsschritt
  verhält sich jede bestehende Regatta wie bisher.

## Datenmodell

Eine Migration, `V202608071200__referee_check_severity.sql`.

### Wettkampf-Flag

```sql
alter table competition_properties
    add column check_in_out_required boolean not null default true;
```

Auf `competition_properties` und nicht auf `competition`, weil diese Tabelle wahlweise an einem
Wettkampf oder an einer Wettkampf-Vorlage hängt: „Beachsprint braucht keine An-/Abmeldung" ist eine
Eigenschaft der Disziplin und wird einmal in der Vorlage gesetzt statt bei jeder Regatta neu.
(Gegenbeispiel: die Zeitnahme-Konfiguration aus `V202608051500` liegt bewusst auf `competition`,
weil sie auf konkrete Rennen in einem Fremdsystem zeigt — das kann eine Vorlage nicht tragen.)

`default true` erhält das heutige Verhalten für alle bestehenden Wettkämpfe.

### Schweregrade

```sql
create table competition_check_severity
(
    competition             uuid      not null references competition on delete cascade,
    check_type              text      not null,
    participant_requirement uuid references participant_requirement on delete cascade,
    severity                text      not null,
    created_at              timestamp not null,
    created_by              uuid references app_user on delete set null,
    updated_at              timestamp not null,
    updated_by              uuid references app_user on delete set null,
    constraint chk_ccs_requirement_matches_check_type check (
        (check_type in ('REQUIREMENT', 'REQUIREMENT_TIME_WINDOW') and participant_requirement is not null) or
        (check_type not in ('REQUIREMENT', 'REQUIREMENT_TIME_WINDOW') and participant_requirement is null) )
);

create unique index on competition_check_severity (competition, check_type, participant_requirement)
    nulls not distinct;
```

Ein einzelner Index mit `nulls not distinct` statt zweier partieller: Postgres behandelt NULLs in
einem Unique-Key sonst als verschieden, was für `INVOICE_OPEN` und `NOT_ON_WATER` (ohne
`participant_requirement`) beliebig viele Duplikate zuließe. `nulls not distinct` schließt das in
einem Index, so wie es das Repo an anderer Stelle bereits macht (`V202506011000__bank_accounts.sql`,
`V202506011100__produce_invoices.sql`).

Der Check-Constraint `chk_ccs_requirement_matches_check_type` koppelt `participant_requirement` an
die passende Prüfungsart: bei `REQUIREMENT`/`REQUIREMENT_TIME_WINDOW` muss sie gesetzt sein, bei
`INVOICE_OPEN`/`NOT_ON_WATER` muss sie fehlen. Ohne ihn ließe sich in der Datenbank ein Zustand
anlegen, den `CheckSeverityConfig.severityFor` nicht sauber abfragen könnte — die gleiche Regel
prüft `UpdateCheckSeverityRequest.validate()` bereits vor dem Insert, der Constraint sichert sie
zusätzlich gegen jeden anderen Schreibweg ab.

`check_type`:

| Wert | Bedeutung | `participant_requirement` |
|---|---|---|
| `INVOICE_OPEN` | Rechnung der Mannschaft offen | null |
| `NOT_ON_WATER` | Boot bei laufendem Lauf nicht vollständig ausgecheckt | null |
| `REQUIREMENT` | Teilnahmebedingung nicht abgehakt | gesetzt |
| `REQUIREMENT_TIME_WINDOW` | abgehakt, aber außerhalb des Zeitfensters | gesetzt |

`severity`: `OK` | `WARNING` | `CRITICAL`.

**Die Tabelle ist dünn besetzt — nur Abweichungen stehen drin.** Fehlt eine Zeile, gilt:

| Prüfung | Standard |
|---|---|
| `INVOICE_OPEN` | `CRITICAL` |
| `NOT_ON_WATER` | `CRITICAL` |
| `REQUIREMENT` | `optional` ? `OK` : `CRITICAL` |
| `REQUIREMENT_TIME_WINDOW` | `WARNING` |

Damit verhalten sich ein neu angelegter Wettkampf und eine neu angelegte Teilnahmebedingung
sofort wie heute, ohne Datenmigration und ohne Pflegeschritt.

## Bewertung

Die Konfiguration kennt drei Stufen, die wirksame Bewertung einer einzelnen Prüfung vier:

| Zustand | wirksam |
|---|---|
| erfüllt | `OK` (grüner Haken) |
| nicht erfüllt, konfiguriert `OK` | `NEUTRAL` (grauer Kreis) |
| nicht erfüllt, konfiguriert `WARNING` | `WARNING` |
| nicht erfüllt, konfiguriert `CRITICAL` | `CRITICAL` |

Der graue Kreis für die Stufe `OK` ist Absicht: „unbezahlt, wird heute nicht geahndet" darf nicht
aussehen wie „bezahlt". Die Tatsache bleibt im Detail-Dialog sichtbar, sie zählt nur nicht mehr in
die Ampel.

Nebeneffekt: die Sonderbehandlung von `optional` verschwindet aus der Anzeige-Logik. Eine optionale,
nicht abgehakte Bedingung ist schlicht eine mit Standard `OK` und sieht aus wie heute (grauer Kreis).

Die Ampel einer Mannschaft ist die schlechteste wirksame Bewertung ihrer Prüfungen; hat die
Mannschaft keine Prüfung, bleibt sie `NEUTRAL`. `NOT_ON_WATER` wird nur bewertet, wenn der Lauf
aktiv ist, der Wettkampf `check_in_out_required` gesetzt hat und die Mannschaft nicht abgemeldet ist.

**Grün in der Team-Ampel kommt ausschließlich aus einer erfüllten Teilnahmebedingung.** Rechnung
und „auf dem Wasser" sind keine Teilnahmebedingungen — sie können die Ampel nur verschlechtern
(Rechnung offen, Boot nicht draußen) oder schweigen (`NEUTRAL`), aber nie nach `OK` heben. Eine
bezahlte Rechnung liefert deshalb `NEUTRAL`, nicht `OK`, und ebenso ein Boot, das schon auf dem
Wasser ist. Das erwies sich während der Umsetzung als tragend, weil daran die zentrale Zusage
dieses Entwurfs hängt: eine Regatta ohne jede eingestellte Prüfung soll aussehen wie vor diesem
Umbau. Vorher gab es für Rechnung und Wasser in der alten Frontend-Formel (`common.ts`, Stand vor
diesem Umbau) keinen Weg zu `'ok'` — nur zu `'error'` oder `'neutral'`. Würden sie hier nach `OK`
zählen, zeigte eine unkonfigurierte Regatta plötzlich überall einen grünen Haken, wo vorher ein
grauer Kreis stand, ohne dass sich an der Datenlage etwas geändert hätte.

Das sind zwei Aufzählungen, nicht eine:

- `CheckSeverity { OK, WARNING, CRITICAL }` — was konfiguriert werden kann, steht in der Spalte
  `severity` und in der Verwaltungs-Schnittstelle.
- `EffectiveSeverity { NEUTRAL, OK, WARNING, CRITICAL }` — was ausgeliefert wird, in den
  Dashboard-DTOs.

Diese Regeln kommen als reine Funktionen nach `LiveDashboardLogic` (`effectiveSeverity`,
`teamSeverity`) neben `computeTimeCheck` — dort prüft `LiveDashboardLogicTest` sie ohne Datenbank.

## Schnittstellen

### Dashboard-DTOs

`backend/.../liveDashboard/entity/LiveDashboardDto.kt`:

- `LiveDashboardRequirementStatusDto` + `severity: EffectiveSeverity`
- `LiveDashboardTeamDto` + `severity: EffectiveSeverity`, + `onWaterRequired: Boolean`
- `LiveDashboardRequirementSummaryDto` entfällt samt `summarizeRequirements`

Der Summary-Typ bestimmte allein die Farbe der Zeile; sonst liest ihn niemand (geprüft: nur
`common.ts:76`). Er bliebe sonst als totes Gewicht in jeder Poll-Antwort.

`onWaterRequired` steuert, ob der Detail-Dialog die Zeile „auf dem Wasser" überhaupt zeigt.

### Verwaltung

Neu im `liveDashboard`-Modul:

- `GET /event/{eventId}/checkSeverity` — Wettkämpfe der Veranstaltung inkl. `checkInOutRequired`,
  die Prüfungsliste inkl. Bedingungsnamen und der Angabe, ob ein Zeitfenster konfiguriert ist,
  sowie die abweichenden Schweregrade. Recht: `readEventGlobal`.
- `PUT /event/{eventId}/checkSeverity` — die vollständige Menge für diese Veranstaltung.
  Standardwerte werden dabei gelöscht statt gespeichert, damit die Tabelle dünn bleibt.
  Recht: `updateEventGlobal`.

Ein Speichern für den ganzen Dialog: „alle auf Warning" ist eine Anfrage, nicht zehn.

### Scan-Übersicht

`TeamForScanOverviewDto` + `checkInOutRequired: Boolean`.

## Oberfläche

### Verwaltungsdialog

Auf der Event-Seite in der Karte „Schiedsrichter-Ansicht" (`EventPage.tsx:428`) ein zweiter Knopf
„Prüfungen verwalten", sichtbar mit `updateEventGlobal`. Er öffnet einen Dialog — dasselbe Muster
wie „Laufende Läufe verwalten" zwei Karten darüber.

Der Dialog gliedert sich **nach Prüfung, nicht nach Wettkampf**: das ist die Blickrichtung des
Obmanns, der über „offene Rechnungen" entscheidet und nicht über „Wettkampf 7".

```
▸ Rechnung offen                          alle: Critical
▸ Nicht auf dem Wasser                    gemischt
▾ Startpass                               alle: Critical
     alle setzen:  [ OK ] [ Warning ] [ Critical ]
     LG  Langstrecke Männer      [ Critical ▾ ]
     BS  Beachsprint             [ Critical ▾ ]
  ▸ └ Zeitfenster verletzt                alle: Warning
▸ Schwimmnachweis                         alle: OK
```

- Eingeklappt steht rechts der verdichtete Zustand (`alle: X` oder `gemischt`) — so ist ohne
  Aufklappen erkennbar, wo vom Standard abgewichen wurde.
- Die Zeitfenster-Zeile hängt eingerückt unter ihrer Bedingung und erscheint nur, wenn für diese
  überhaupt ein Fenster konfiguriert ist (`check_earliest/latest_minutes_before`).
- In „Nicht auf dem Wasser" stehen Wettkämpfe ohne `check_in_out_required` ausgegraut mit dem
  Hinweis „keine An-/Abmeldung erforderlich".

Das Flag `check_in_out_required` wird **nicht** hier bearbeitet, sondern im Wettkampf-Formular: es
steuert auch die QR-App und ist eine Eigenschaft des Rennformats. Zwei Bearbeitungsstellen für ein
Feld wären auf Dauer die schlechtere Wahl.

### Dashboard

Unverändert im Aufbau; die Farben kommen jetzt aus `team.severity` statt aus lokaler Rechnung.
Im Detail-Dialog bekommt „auf dem Wasser" einen eigenen Chip — bisher war es nur eine Zeitangabe
unter dem Mannschaftsnamen, obwohl es in die Ampel einfloss.

Der Rechnungs-Chip ist grün, sobald bezahlt ist, und folgt nur im unbezahlten Fall dem
konfigurierten Schweregrad (ebenso der Wasser-Chip: grün sobald abgelegt, sonst der konfigurierte
Schweregrad). Das ist kein Widerspruch zur Regel „Grün ist den Teilnahmebedingungen vorbehalten"
aus dem Bewertungs-Abschnitt — die gilt für die zusammengefasste Team-Ampel, die aus genau diesem
Grund `NEUTRAL` statt `OK` für eine bezahlte Rechnung liefert (`invoiceSeverity`/`onWaterSeverity`).
Dieser Chip fasst aber nichts zusammen; seine ganze Aussage ist „bezahlt" bzw. „abgelegt um …", und
genau das darf grün sein, wenn es zutrifft. Nur wenn es nicht zutrifft, ist überhaupt eine
Entscheidung nötig, und die folgt dem eingestellten Schweregrad.

`frontend/.../liveDashboard/common.ts`: `requirementSeverity` und `teamSeverity` entfallen ebenso
wie `worstSeverity` — beide Zusammenfassungen laufen jetzt ausschließlich im Backend
(`LiveDashboardLogic.teamSeverity`/`worstSeverity`). `severityChipColor` bleibt, die
Icon-Zuordnung zieht in eine eigene Komponente `SeverityIcon.tsx`.

### QR-App

`TeamCheckInOut.tsx`:

- Teams ohne An-/Abmeldepflicht bekommen im Kartenkopf einen Hinweis-Chip
  („keine An-/Abmeldung nötig").
- Braucht kein einziges Team der Person eine An-/Abmeldung, entfällt der Knopf am unteren Rand;
  stattdessen steht dort ein kurzer Satz.

Die Zweiteilung ist nötig, weil Scans an Person und Veranstaltung hängen, nicht am Wettkampf
(`participant_tracking`): Wer Beachsprint *und* Langstrecke fährt, muss sich weiterhin abmelden.

Bewusst **keine** serverseitige Sperre. Ein Scan ist ein Ereignis-Protokoll; ein zusätzlicher
Abmelde-Scan schadet niemandem, während eine Sperre am Steg Fälle erzeugt, in denen der Knopf nicht
tut, was er verspricht.

## Übersetzungen

Neue Schlüssel in `de`, `en` und `da` unter `event.liveDashboard.checkSeverity.*`:
Dialogtitel, Prüfungsnamen der beiden festen Typen, die drei Stufen, `alle: X` / `gemischt`,
Sammelaktion, der Hinweis für Wettkämpfe ohne An-/Abmeldung. Dazu in
`club.participant.tracking.*` die beiden Texte der QR-App.

## Tests

- `LiveDashboardLogicTest`: Wahrheitstabelle für `effectiveSeverity` (erfüllt / nicht erfüllt ×
  drei Stufen), die vier Standardwerte ohne Konfiguration, `teamSeverity` als Maximum,
  `NEUTRAL` bei leerer Prüfungsliste, `NOT_ON_WATER` nur bei aktivem Lauf + gesetztem Flag +
  nicht abgemeldeter Mannschaft.
- Auflösung Konfiguration → Schweregrad: gespeicherte Zeile schlägt Standard; fehlende Zeile
  ergibt Standard; eine Zeile eines anderen Wettkampfs wirkt nicht.
- `common.test.ts`: die entfallenden Ampel-Tests werden entfernt, die Tests für `worstSeverity`
  und die übrigen Hilfsfunktionen bleiben.
- Frontend-Test für den Verwaltungsdialog: verdichteter Zustand (`alle: X` vs. `gemischt`) und
  die Sammelaktion.

## Reihenfolge

Die jOOQ-Klassen entstehen per Codegen gegen die Build-Datenbank (`localhost:7652`,
`mvn generate-sources`), die generierten Frontend-Typen per `npm run generate` aus
`documentation.yaml`. Daraus folgt die Reihenfolge:

1. Migration + `mvn generate-sources`
2. Backend: Logik, Repo, Service, Routen
3. `documentation.yaml` + `npm run generate`
4. Frontend: Dashboard-Anpassung, Verwaltungsdialog, QR-App
5. Übersetzungen

## Nicht Teil dieses Entwurfs

- Serverseitige Durchsetzung von An-/Abmeldung (bleibt offen, siehe oben).
- Ein Veranstaltungs- oder globaler Standard über der Wettkampf-Ebene.
- Schweregrade für andere Anzeigen als das Schiedsrichter-Dashboard.
