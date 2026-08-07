# Siegerurkunden als PDF und Word

Stand: 2026-08-05

## Ziel

Urkunden für Wettkampf-Platzierungen sollen in ready2race unmittelbar herunterladbar sein — als
druckfertiges PDF und als Word-Datei zum Nachbearbeiten. Bisher füllt der Veranstalter die vom DRV
verteilte PowerPoint-Vorlage von Hand aus, eine Datei pro Urkunde.

Zusätzlich erhält die bereits existierende Teilnahmeurkunde einen Word-Download.

## Ausgangslage

### Die DRV-Vorlage

Analysiert wurden `2025_Urkunden_Coastal DM.pptx` sowie zwei ausgefüllte Exemplare
(`1 CF 1x.pptx`, `2 CM 1x.pptx`).

- A4 hoch (7559675 × 10691813 EMU)
- Das Design liegt als Bild `Urkundenvorlage "Sport"` in der Master-Folie und ist dort
  **`hidden="1"`** — es dient nur als Positionierungshilfe. Gedruckt wird auf vorgedrucktes
  DRV-Papier: lila Kopfleiste mit Logo, „URKUNDE" in einer Serifenschrift, drei Trennlinien,
  Unterschrift des Präsidenten.
- Variabler Inhalt ist ein zentrierter Textblock ab 44,8 % Seitenhöhe, Schrift TheSansOffice:

  | Zeile | Beispiel | Format |
  |---|---|---|
  | Platz | `1. Platz` | 20pt fett |
  | Wettbewerb | `CF 1x Frauen-Einer` | 20pt |
  | Name | `Carina Hein` | 24pt fett |
  | Verein/RG | `RC Allemannia Hamburg v. 1866` | 20pt |
  | Ergebnis | `33:17,7 min` | 20pt kursiv |

- Veranstaltungsname und Datum stehen zwischen Trennlinie 1 und 2 (ca. 26 % Höhe), Ort mit Datum
  bei 82,9 %, der Name des Präsidenten bei 94 % — unterhalb der vorgedruckten Unterschrift.

**Konsequenz:** Die Ausgabe muss die Positionen der Vorlage treffen, und das PDF darf das Design
nicht mitdrucken, sonst liegt es doppelt auf dem Amtspapier.

### Was im Code schon existiert

- **Gap-Dokument-Mechanik**: PDF-Vorlage hochladen, Platzhalter im Frontend visuell platzieren
  (`PdfPlaceholderEditor.tsx`, `PlaceholderSidebar.tsx`), Befüllung mit PDFBox in
  `backend/pdf/documents.kt` (`document(original, additions)`). Genutzt für
  `GapDocumentType.CERTIFICATE_OF_PARTICIPATION`.
- **Platzierungen und Zeiten**: `CompetitionExecutionService.computeCompetitionPlaces(competitionId)`
  liefert `List<Pair<CompetitionMatchTeamWithRegistration, Int>>` mit `place`, `timeString`,
  `clubName`, `registrationName`, `participants`, sowie den Flags `deregistered`, `out`, `failed`.
- **RG-Logik**: `ResultsService.generateResultsDocument` bestimmt den anzuzeigenden Vereinsnamen
  (ein Verein → dessen Name, sonst `event.mixedTeamTerm`).
- **Dependencies**: PDFBox 3.0.4 und POI 5.4.1 inkl. `poi-ooxml` sind vorhanden.
- **Vorlagenzuweisung**: `gap_document_template_usage.type` ist Primary Key — Gap-Vorlagen werden
  **global pro Typ** zugewiesen, nicht pro Veranstaltung.
- **OpenAPI**: `backend/src/main/resources/openapi/documentation.yaml` ist die handpflegte Quelle;
  der Frontend-Client entsteht daraus mit `npm run generate`. Das Verzeichnis `api/*.tsp` enthält
  die Gap-Dokument-Endpunkte nicht und wird hier nicht angefasst.

## Entscheidungen

| Frage | Entscheidung | Begründung |
|---|---|---|
| Umfang | Neue Siegerurkunde **und** Word für die bestehende Teilnahmeurkunde | Beides gewünscht; der DOCX-Renderer wird geteilt |
| Editierbares Format | Word (.docx) | Explizit gewünscht, obwohl der DRV-Workflow PPTX ist |
| Vorlagenpflege | Bestehende Gap-Mechanik: PDF-Export der PPTX hochladen, Platzhalter visuell setzen | Maximale Wiederverwendung; jede Zeile hat eigene Koordinaten, dadurch kein Nachbau von PowerPoints vertikaler Absatzlogik |
| Einstiegspunkte | Pro Wettkampf, ganze Veranstaltung, einzelne Urkunde | Alle drei angefordert |
| Mannschaftsboote | Im Download-Dialog umschaltbar: pro Athlet oder pro Boot | Beides kommt vor |
| Platzierungen | Standard Plätze 1–3, im Dialog auf alle umstellbar | Medaillenplätze sind der Normalfall |
| Schrift | Optionaler Schrift-Upload pro Vorlage, Fallback Helvetica | TheSansOffice ist kommerziell lizenziert und liegt nicht vor; auf dem Amtspapier steht neben den Urkundentexten kein Sans-Text, der Fallback fällt daher kaum auf |

Verworfen: PPTX direkt einlesen (PowerPoints vertikale Absatzlogik müsste für PDF nachgebaut
werden), Layout fest im Code (nicht für andere Veranstalter nutzbar), LibreOffice im Container
(Betriebsaufwand, Laufzeit bei Serien).

## Datenmodell

`GapDocumentType` erhält `AWARD_CERTIFICATE`.

`GapDocumentPlaceholderType` erhält:

| Typ | Inhalt | Quelle |
|---|---|---|
| `PLACE` | `1. Platz` | berechnete Platzierung |
| `COMPETITION_NAME` | `CF 1x Frauen-Einer` | `competition.name` |
| `COMPETITION_SHORT_NAME` | Kurzform, falls die Vorlage schmal ist | `competition.shortName`, Fallback `name` |
| `CLUB_NAME` | `RC Allemannia Hamburg v. 1866` | Verein bzw. RG-Bezeichnung |
| `TEAM_NAME` | Bootsname, falls gesetzt | `registrationName` |
| `EVENT_DATE` | `16.–17. August 2025` | Renntage der Veranstaltung |
| `EVENT_LOCATION` | `Flensburg` | `event.location` |
| `FREE_TEXT` | `Moritz Petri – Präsident` | fest am Platzhalter hinterlegt |

Bestehend und weiterverwendet: `FIRST_NAME`, `LAST_NAME`, `FULL_NAME`, `RESULT`, `EVENT_NAME`.
`FIRST_NAME` und `LAST_NAME` sind für die Siegerurkunde ebenfalls erlaubt, tragen aber nur im Modus
„pro Athlet" einen Wert; im Modus „pro Boot" bleiben sie leer, weil dort mehrere Personen auf einer
Urkunde stehen. `FULL_NAME` ist deshalb der Standardplatzhalter für das Namensfeld.

`GapDocumentType` bekommt eine Eigenschaft `allowedPlaceholders: Set<GapDocumentPlaceholderType>`,
die über `GapDocumentTypeDto` ans Frontend geht — der Editor bietet dann nur die für den jeweiligen
Dokumenttyp sinnvollen Platzhalter an. Für `CERTIFICATE_OF_PARTICIPATION` bleibt es beim bisherigen
Satz, damit sich an der Teilnahmeurkunde nichts ändert.

### Migration `V202608051200__award_certificate_templates.sql`

```sql
alter table gap_document_placeholder
    add column font_size   int,
    add column bold        boolean not null default false,
    add column italic      boolean not null default false,
    add column static_text text;

alter table gap_document_template
    add column font_name text;

create table gap_document_template_font
(
    template  uuid primary key references gap_document_template on delete cascade,
    file_name text  not null,
    data      bytea not null
);
```

- `font_size` in Punkt. Ist die Spalte leer, gilt das bisherige Verhalten (Schriftgröße =
  Kastenhöhe) — die bestehende Teilnahmeurkunde bleibt dadurch unverändert.
- `static_text` trägt den Inhalt für `FREE_TEXT`.
- `font_name` ist der Schriftname, der ins DOCX geschrieben wird (z. B. `TheSansOffice`).
- `gap_document_template_font` folgt dem Muster von `gap_document_template_data` und hält die
  Schriftdatei getrennt vom Vorlagen-Datensatz, damit Listenabfragen die Binärdaten nicht mitladen.

Anschließend `./mvnw jooq:generate`.

## Backend

### Datenbeschaffung

Neuer `AwardCertificateService` im bestehenden Paket
`app/certificate/boundary`, mit den Datentypen in `app/certificate/entity`.

```kotlin
data class AwardCertificateOptions(
    val maxPlace: Int = 3,
    val mode: AwardCertificateMode,   // PER_ATHLETE | PER_TEAM
    val withBackground: Boolean = false,
)

data class AwardCertificateEntry(
    val place: Int,
    val competitionName: String,
    val competitionShortName: String?,
    val competitionIdentifier: String,
    val teamName: String?,
    val clubName: String,
    val result: String?,          // timeString
    val names: List<String>,      // ein Eintrag bei PER_ATHLETE, alle bei PER_TEAM
    val registrationId: UUID,
)
```

Ablauf für einen Wettkampf:

1. `computeCompetitionPlaces(competitionId)` aufrufen.
2. Boote mit `deregistered`, `out` oder `failed` verwerfen; ebenso `place > maxPlace`.
3. Vereinsnamen mit derselben Logik wie `generateResultsDocument` bestimmen (ein Verein → dessen
   Name, sonst `event.mixedTeamTerm`), damit Ergebnisliste und Urkunde übereinstimmen.
4. Je Modus eine oder mehrere Einträge erzeugen: `PER_ATHLETE` eine pro Teilnehmer inklusive
   Steuerleuten, `PER_TEAM` eine pro Boot mit allen Namen.
5. Sortierung: Wettkampf nach `lexiNumberComp { identifier }`, dann Platz aufsteigend, dann
   Bootsname bzw. Nachname.

Auf Veranstaltungsebene läuft das über alle Wettkämpfe der Veranstaltung, in derselben Sortierung.

Fehler in `AwardCertificateError`: `MissingTemplate`, `NoResults`, `PlacesNotComputed`,
`CompetitionNotInEvent`.

### Platzhalter füllen

Eine gemeinsame Funktion bildet `AwardCertificateEntry` plus Veranstaltungsdaten auf
`List<AdditionalText>` ab — analog zu den bestehenden `mapNotNull`-Blöcken in
`CertificateService`, aber einmal statt dreimal. Die vorhandenen drei Stellen im
`CertificateService` werden auf dieselbe Hilfsfunktion umgestellt, damit die Zuordnung
Platzhaltertyp → Inhalt nur an einer Stelle existiert.

`PLACE` wird als `"$place. Platz"` gerendert, `EVENT_DATE` als Bereich der Renntage
(`16.–17. August 2025`, bei einem Tag ohne Bereich), `RESULT` bleibt leer wenn keine Zeit vorliegt.
Bei `PER_TEAM` enthält `FULL_NAME` die Namen mit `\n` getrennt.

`AdditionalText` erhält die Felder `fontSize: Float?`, `bold: Boolean`, `italic: Boolean`.

### PDF-Rendering

Erweiterung in `backend/pdf/documents.kt`:

```kotlin
fun gapDocuments(
    template: ByteArray,
    font: ByteArray?,
    withBackground: Boolean,
    pages: List<List<AdditionalText>>,
): PDDocument
```

- Eine Seite pro Urkunde, Seitenformat aus der ersten Seite der Vorlage.
- `withBackground = false`: leere Seiten. `true`: die Vorlagenseite wird mit `LayerUtility` als
  Layer auf jede Seite gelegt, wie es `document(pageTemplate, builder)` schon macht.
- Schrift: ist eine Datei hinterlegt, wird sie mit `PDType0Font.load` eingebettet; Fett und Kursiv
  werden dann nur bedient, wenn die entsprechenden Schnitte vorliegen, sonst wird der
  Standardschnitt genutzt. Ohne Datei die Standard-14-Schnitte `HELVETICA`, `HELVETICA_BOLD`,
  `HELVETICA_OBLIQUE`, `HELVETICA_BOLD_OBLIQUE`.
- Schriftgröße: `fontSize` in Punkt wenn gesetzt, sonst Kastenhöhe wie bisher.
- Mehrzeilige Inhalte werden an `\n` getrennt, im Kasten vertikal zentriert gestapelt und je Zeile
  nach `textAlign` ausgerichtet.
- Textfarbe bleibt wie bisher.

Die bestehende Funktion `document(original, additions)` bleibt erhalten und delegiert auf die neue,
damit sich am Verhalten der Teilnahmeurkunde nichts ändert.

### DOCX-Rendering

Neues Paket `backend/docx` mit

```kotlin
fun gapDocumentsDocx(
    pageSize: PDRectangle,
    fontName: String?,
    pages: List<List<AdditionalText>>,
): XWPFDocument
```

- Seitenformat und Ränder aus `pageSize` (`w:pgSz`, Ränder 0).
- Jeder Platzhalter wird ein Absatz mit `w:framePr` — Words klassischer absolut positionierter
  Textrahmen. Position und Größe relativ zur Seite (`hAnchor="page"`, `vAnchor="page"`), berechnet
  aus den relativen Koordinaten des Platzhalters, Angaben in Twips. Kein Drawing-XML, keine
  VML-Formen: in Word normal anklickbar und verschiebbar.
- Absatzausrichtung aus `textAlign`, Run-Eigenschaften aus `fontName`, `fontSize`, `bold`,
  `italic`. Mehrzeilige Inhalte werden mehrere Absätze im selben Rahmen.
- Seitenumbruch zwischen den Urkunden.
- Kein Hintergrundbild — gedruckt wird auf Amtspapier.

### API

Neue Endpunkte, eingehängt unter `/event/{eventId}`:

```
GET /event/{eventId}/awardCertificates
GET /event/{eventId}/competition/{competitionId}/awardCertificates
GET /event/{eventId}/competition/{competitionId}/awardCertificates/{registrationId}
```

Query-Parameter für alle drei: `format=pdf|docx` (Standard `pdf`), `maxPlace` (Standard 3),
`mode=perAthlete|perTeam` (Standard `perAthlete`), `background=true|false` (Standard `false`).
Beim Einzeldownload mit `mode=perAthlete` zusätzlich `participantId`; fehlt er, entsteht eine Seite
pro Athlet des Bootes.

Berechtigung `Privilege.ReadEventGlobal`, wie beim Ergebnisdokument — kein öffentlicher Zugriff.

Die bestehenden Teilnahmeurkunden-Endpunkte erhalten `?format=pdf|docx` und nutzen denselben
DOCX-Renderer. Bei `format=docx` und dem ZIP-Download bleibt es beim ZIP, dessen Einträge dann
`.docx` sind.

Dateinamen: `urkunden_<veranstaltung>_<wettkampf>.pdf`, auf Veranstaltungsebene
`urkunden_<veranstaltung>.pdf`, einzeln `urkunde_<wettkampf>_<platz>_<name>.pdf`, jeweils analog
für `.docx`. Namen werden wie bisher über die vorhandene Dateinamen-Behandlung geführt.

### Vorlagenverwaltung

`GapDocumentTemplateRequest` und `GapDocumentPlaceholderRequest` werden um die neuen Felder
erweitert (`fontName`, `fontSize`, `bold`, `italic`, `staticText`). Der Schrift-Upload läuft über
die bestehende Multipart-Route der Vorlage als zweiter, optionaler Dateiteil; Validierung auf
Dateiendung `.ttf`/`.otf` und Ladbarkeit durch PDFBox, damit ein unbrauchbarer Upload beim Anlegen
auffällt und nicht erst beim Generieren.

## Frontend

- **Konfiguration**: neuer Vorlagentyp „Siegerurkunde" in der bestehenden Gap-Vorlagenverwaltung.
  `PlaceholderSidebar` bekommt Felder für Schriftgröße, Fett, Kursiv und — bei `FREE_TEXT` — den
  festen Text. Am Vorlagenformular kommen Schriftname und optionaler Schrift-Upload hinzu. Die
  Auswahl der Platzhaltertypen richtet sich nach `allowedPlaceholders` des Dokumenttyps.
- **Wettkampf-Ergebnisse**: Button „Urkunden" öffnet einen Dialog mit Format (PDF/Word), „bis
  Platz", „pro Athlet / pro Boot" und „Design mitdrucken" (standardmäßig aus).
- **Veranstaltungsebene**: derselbe Dialog für alle Wettkämpfe.
- **Ergebniszeile**: Icon-Button für den Einzeldownload mit den Dialog-Standardwerten.
- Fehlt die Vorlage, zeigt der Dialog einen Hinweis mit Verweis auf die Konfiguration statt eines
  Fehlers beim Download.
- `documentation.yaml` erweitern, danach `npm run generate`.
- Übersetzungen für `de`, `en`, `da`.

## Tests

Backend:

- Datenauswahl: `maxPlace` filtert, abgemeldete/ausgeschiedene/disqualifizierte Boote fehlen,
  `PER_ATHLETE` erzeugt eine Seite pro Teilnehmer inklusive Steuermann, `PER_TEAM` eine pro Boot,
  RG-Boote zeigen `mixedTeamTerm`, Sortierung nach Wettkampf und Platz.
- PDF: Seitenanzahl, und mit `PDFTextStripper` je Seite die erwarteten Texte in der erwarteten
  Reihenfolge; `background=false` erzeugt Seiten ohne Vorlageninhalt, `true` mit.
- DOCX: erneutes Einlesen mit POI prüft Rahmenkoordinaten, Ausrichtung, Schriftname, Größe und
  Schnitte sowie die Seitenumbrüche.
- Randfälle: fehlende Vorlage, fehlende Zeit (leeres Ergebnisfeld), noch nicht berechnete Plätze,
  Wettkampf gehört nicht zur Veranstaltung.
- Rückwärtskompatibilität: eine Teilnahmeurkunde ohne `font_size` rendert unverändert.

Testdaten: eine minimale einseitige A4-PDF-Vorlage in den Test-Resources.

## Nicht im Scope

- Kein PPTX-Import und kein LibreOffice im Container.
- Kein E-Mail-Versand der Siegerurkunden — nur Download. Die bestehende E-Mail-Zustellung der
  Teilnahmeurkunde bleibt wie sie ist und behält PDF.
- Keine Vorlagen pro Veranstaltung; die Zuweisung bleibt global pro Dokumenttyp.
- Kein Mitliefern von TheSansOffice. Die Schrift ist kommerziell lizenziert, ready2race ist
  GPL-3.0 — die Datei muss der Veranstalter einmal selbst hochladen.
- Keine Siegerurkunden für Challenge-Events; dafür bleibt die Teilnahmeurkunde.

## Risiken

- **Schrifttreue im PDF** ohne hochgeladene Schrift. Auf dem Amtspapier steht neben den
  Urkundentexten kein Sans-Text, der Unterschied fällt daher kaum auf; mit Upload ist er weg.
- **Rahmenpositionierung in Word**: `w:framePr` ist gut unterstützt, aber die Darstellung kann sich
  zwischen Word-Versionen minimal unterscheiden. Der Druck aufs Amtspapier läuft im Regelfall über
  das PDF; das DOCX ist das Nachbearbeitungsformat.
- **Serienlänge**: eine große Veranstaltung mit „alle Plätze, pro Athlet" kann mehrere hundert
  Seiten erzeugen. Beobachten; falls nötig später eine Obergrenze mit Hinweis im Dialog.
