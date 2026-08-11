# Urkundenvorlagen teilen und genauer setzen

Stand: 07.08.2026

## Problem

Eine Urkundenvorlage besteht heute aus einem PDF, einer Liste von Platzhaltern mit relativer
Position und optional einer Schriftdatei. Sie entsteht ausschließlich im Dialog
[GapDocumentTemplateDialog.tsx](../frontend/src/components/gapDocumentTemplate/GapDocumentTemplateDialog.tsx)
und lebt danach nur in der Datenbank der jeweiligen Instanz. Zwei Folgen daraus:

1. Wer dieselbe Vorlage in einer zweiten Instanz braucht — lokal getestet, dann auf dem Server, oder
   ein zweiter Verein mit demselben Verbandsbogen — muss jeden Platzhalter erneut von Hand setzen.
   Ein offizieller Bogen wie die DRV-Siegerurkunde ist damit für jede Instanz Fleißarbeit.
2. Gesetzt wird ausschließlich mit der Maus. Position und Größe stehen in der Seitenleiste nur als
   abgelesene Prozentwerte
   ([PlaceholderSidebar.tsx:223](../frontend/src/components/gapDocumentTemplate/PlaceholderSidebar.tsx:223)),
   und im Editor sind die Kästen leer — ob ein langer Vereinsname in die Breite passt, zeigt erst
   die Vorschau der gespeicherten Vorlage.

## Ziel

Eine fertig eingerichtete Vorlage lässt sich als Datei exportieren und in einer anderen Instanz
importieren, und das Setzen der Platzhalter wird auf Zahlenwerte statt Mausgefühl gestellt.

## Nicht-Ziele

- Keine im Produkt mitgelieferten Standardvorlagen. Verbandsbögen tragen fremdes Material (Logo,
  eingescannte Unterschrift) und lizenzpflichtige Schriften; die gehören nicht in die Auslieferung.
- Keine Anbindung an den bestehenden WebDAV-Export/-Import. Der transportiert Veranstaltungsdaten,
  Urkundenvorlagen sind globale Konfiguration.
- Kein Überschreiben beim Import. Ein Import legt immer eine neue Vorlage an.

## Teil 1: Vorlagen-Paket

### Format

Eine ZIP-Datei mit der Endung `.r2rtpl.zip`:

```
template.json      Metadaten und Platzhalter
template.pdf       das Design-PDF, unverändert
font.ttf|font.otf  optional, die eingebettete Schrift
```

`template.json`:

```json
{
  "formatVersion": 1,
  "name": "urkunde-vorlage.pdf",
  "type": "AWARD_CERTIFICATE",
  "fontName": "TheSansOffice",
  "fontFile": "font.otf",
  "placeholders": [
    {
      "name": null,
      "type": "PLACE",
      "page": 1,
      "relLeft": 0.0,
      "relTop": 0.447,
      "relWidth": 1.0,
      "relHeight": 0.04,
      "textAlign": "CENTER",
      "fontSize": 20,
      "bold": true,
      "italic": false,
      "staticText": null
    }
  ]
}
```

Die Platzhalter-Objekte sind feldgleich mit
[GapDocumentPlaceholderRequest](../backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/entity/GapDocumentPlaceholderRequest.kt),
`type` und `fontName` feldgleich mit `GapDocumentTemplateRequest`. Ein Paket ist damit dasselbe
Datenmodell wie ein normaler Upload, nur in einer Datei gebündelt — Export und Import brauchen keine
eigene Übersetzungsschicht.

`name` ist der Vorlagenname, der heute aus dem PDF-Dateinamen entsteht
([GapDocumentTemplateService.kt:82](../backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/boundary/GapDocumentTemplateService.kt:82)).
Beim Import wird er übernommen.

`formatVersion` erlaubt späteren Feldern eine Migration; beim Import wird eine unbekannte Version
abgelehnt statt halb gelesen.

### Backend

Zwei Endpunkte in
[documentTemplate.kt](../backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/boundary/documentTemplate.kt),
mit denselben Rechten wie die vorhandenen Operationen:

- `GET /gapDocumentTemplate/{id}/export` — `ReadEventGlobal`, liefert das ZIP als
  `ApiResponse.File`. Baut das Paket aus Template-Record, Placeholder-Records, `template_data.data`
  und, falls vorhanden, `gap_document_template_font`.
- `POST /gapDocumentTemplate/import` — `UpdateEventGlobal`, nimmt das ZIP als Multipart entgegen,
  packt es aus und ruft `GapDocumentTemplateService.addTemplate` mit denselben Argumenten auf, die
  der normale Upload erzeugt.

Der Import muss durch dieselben Prüfungen laufen wie der Upload, nicht daran vorbei: gültiges PDF
(`checkValidPdf`), Font-Endung `ttf`/`otf`, Platzhaltertyp für den Dokumenttyp zulässig, Platzhalter
auf einer vorhandenen Seite. Diese Prüfungen liegen heute teils in der Route, teils im Service — was
der Import braucht, wandert in den Service, damit beide Wege dieselbe Prüfung sehen und nicht zwei
Kopien entstehen.

Neue Fehlerfälle in
[GapDocumentTemplateError](../backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/entity/GapDocumentTemplateError.kt)
mit je eigenem `ErrorCode`, damit das Frontend sie benennen kann statt „konnte nicht angelegt
werden":

- `InvalidPackage` — kein lesbares ZIP, `template.json` fehlt oder ist kein gültiges JSON,
  `template.pdf` fehlt.
- `UnsupportedPackageVersion` — `formatVersion` ist nicht 1.

Die bestehenden Fehler (`InvalidFont`, `PlaceholderTypeNotSupported`,
`PlaceholderPageNotSupported`) gelten unverändert auch für den Import.

Beim Auspacken werden nur die drei erwarteten Einträge gelesen und alles andere ignoriert; Pfade mit
Verzeichnisanteil oder `..` werden abgelehnt, damit ein präpariertes Archiv nichts außerhalb
erreicht. Der normale Upload kennt heute keine Größengrenze; für die entpackten Teile wird eine
gesetzt (20 MB je Eintrag), damit ein kleines Archiv nicht zu beliebig viel Speicher wird.

### Frontend

- Ein Export-Knopf je Zeile in
  [GapDocumentTemplateTable.tsx](../frontend/src/components/gapDocumentTemplate/GapDocumentTemplateTable.tsx),
  neben dem vorhandenen Vorschau-Knopf.
- Ein Import-Knopf über der Tabelle, der eine Datei entgegennimmt und danach die Tabelle neu lädt.
- Nach erfolgreichem Import öffnet sich die neue Vorlage im Bearbeiten-Dialog, damit sichtbar ist,
  was angekommen ist. Der Dokumenttyp ist dort dann gesperrt — wie bei jeder bestehenden Vorlage.
- Die neuen Fehlercodes werden in
  [certificateError.ts](../frontend/src/components/certificate/certificateError.ts) auf
  Übersetzungsschlüssel abgebildet, deutsche, englische und dänische Texte ergänzt.

## Teil 2: Editor

Alle drei Punkte betreffen nur das Frontend; das Datenmodell bleibt unverändert.

### Zahlenfelder

Position und Größe werden in
[PlaceholderSidebar.tsx](../frontend/src/components/gapDocumentTemplate/PlaceholderSidebar.tsx) von
Anzeige auf Eingabe umgestellt: vier Felder (X, Y, Breite, Höhe) in Prozent mit einer
Nachkommastelle. Werte werden auf 0–100 begrenzt und so beschnitten, dass der Kasten auf der Seite
bleibt — dieselbe Klemmung, die das Ziehen heute schon anwendet
([PdfPlaceholderEditor.tsx:118](../frontend/src/components/gapDocumentTemplate/PdfPlaceholderEditor.tsx:118)).
Damit lässt sich ein vermessenes Layout direkt eintippen, statt es nachzuziehen.

### Tastatur

Ist ein Platzhalter ausgewählt, verschieben die Pfeiltasten ihn um 0,1 %, mit Shift um 1 %. Die
Tastenbehandlung hängt am Editor-Container, nicht am Dokument, damit sie nicht zuschlägt, während in
einem Eingabefeld getippt wird.

### Live-Vorschau

Statt leerer Kästen zeichnet der Editor Beispieltexte — dieselben Werte, die die Server-Vorschau
verwendet
([GapDocumentTemplateService.kt:205](../backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/boundary/GapDocumentTemplateService.kt:205)),
damit beide dasselbe zeigen. Gerendert wird mit der eingestellten Schriftgröße, Ausrichtung und
Auszeichnung.

Die Schrift wird per `FontFace` geladen, wenn sie verfügbar ist: beim Anlegen aus der gerade
gewählten Datei, bei einer bestehenden Vorlage über einen neuen Endpunkt
`GET /gapDocumentTemplate/{id}/font` (`ReadEventGlobal`, liefert die Datei; 404, wenn keine
hinterlegt ist). Fehlt eine Schrift, wird mit einer Fallback-Schrift gezeichnet.

Die Vorschau im Editor ist eine Näherung und ersetzt die Server-Vorschau nicht: das Backend
zeichnet mit PDFBox, bildet Fett über eine Kontur und Kursiv über eine Scherung nach
([documents.kt:216](../backend/src/main/kotlin/de/lambda9/ready2race/backend/pdf/documents.kt:216))
und zentriert den Text vertikal im Kasten. Der Editor kommt dem nahe, aber die verbindliche
Kontrolle bleibt die gespeicherte Vorschau. Das wird als Hinweis im Dialog benannt.

## Tests

Backend:

- Paket schreiben und wieder einlesen ergibt dieselbe Vorlage — mit Schrift und ohne.
- Import lehnt ab: kaputtes ZIP, fehlende `template.json`, fehlendes `template.pdf`, unbekannte
  `formatVersion`, PDF das keines ist, Schriftdatei mit falscher Endung, Platzhaltertyp der nicht
  zum Dokumenttyp gehört, Eintragspfad mit Verzeichnisanteil.
- Import einer Siegerurkunden-Vorlage in eine Instanz ohne passende Vorlage erzeugt eine Vorlage,
  die die Urkundenerzeugung akzeptiert.

Frontend:

- Zahlenfelder: Eingabe setzt den Kasten, Werte außerhalb werden geklemmt, Ziehen und Tippen
  schreiben in dasselbe Feld.
- Pfeiltasten verschieben nur bei ausgewähltem Platzhalter und nicht während einer Texteingabe.
- Die neuen Fehlercodes werden auf ihre Übersetzungsschlüssel abgebildet
  (`certificateError.test.ts` deckt dieses Muster bereits ab).

## Reihenfolge

1. Prüfungen aus der Route in den Service ziehen, mit Tests auf dem bestehenden Upload-Weg.
2. Paketformat, Export, Import im Backend.
3. Export-/Import-Knöpfe und Fehlertexte im Frontend.
4. Zahlenfelder und Tastatur.
5. Live-Vorschau samt Font-Endpunkt.

Schritte 4 und 5 hängen nicht an 1–3 und können vorgezogen werden, wenn das Setzen der
DRV-Vorlage vor der Regatta dringender ist als das Teilen.

## Offene Punkte

- Der Export enthält die hinterlegte Schriftdatei. Bei lizenzpflichtigen Schriften wie
  TheSansOffice ist die Weitergabe des Pakets an Dritte damit eine Lizenzfrage — bewusst so
  entschieden, weil die Pakete zwischen eigenen Instanzen wandern sollen. Ein Hinweis am
  Export-Knopf benennt das.
