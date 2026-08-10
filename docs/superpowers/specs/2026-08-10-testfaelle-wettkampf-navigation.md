# Testfälle: Navigation auf der Wettkampfseite

**Stand:** 2026-08-10
**Betrifft:** `37110c69` (Seitenleiste mit Rennen, bereits in `feature/crf-2026`) und
`59fa03d4` (Umschalter Rennen/Zeitplan, auf `claude/regie-ansicht`)

**Warum ein eigenes Dokument:** Der Testkatalog `2026-08-05-testkatalog-crf-2026.md` lag beim
Aufschreiben mit 44 anderen Dateien im Index einer parallel laufenden Sitzung. Diese Fälle sind so
geschrieben, dass sie beim Merge unverändert als **Block N und O** in den Katalog übernommen werden
können — gleiche Spalten, gleiche Regeln.

> **Für das Review (Fable):** Der interessante Teil ist nicht die Liste, sondern **N11** und
> **O6**. Alles andere ist in der laufenden Anwendung gesehen worden; diese beiden nicht.

## Ausgangslage

Vor `37110c69` führte aus einem Wettkampf kein Weg zurück außer über das Seitenmenü links.
Die Gegenrichtung Zeitplan → Wettkampf gab es schon (Sprung-Symbol am Slot, `goToExecution`),
zurück führte nichts. Beide Commits schließen diesen Kreis.

## Vorbereitung

Ein Stand mit **beiden** Commits, eine Veranstaltung mit mindestens 15 Wettkämpfen und einem
gepflegten Zeitplan. Angemeldet als Konto mit `READ EVENT` (sonst fehlt der Zeitplan-Umschalter,
siehe N2). Für N5 muss ein Lauf tatsächlich unterwegs sein.

## N — Umschalter Rennen/Zeitplan

| ID | Fall | Erwartung | testbar ab | Nachweis |
|---|---|---|---|---|
| N1 | Nur angemeldet | Abgemeldet zeigt die Wettkampfseite **keine** Leiste und keinen Umschalter — nur den Zurück-Link. Der Grund ist nicht Kosmetik: der Zeitplan-Endpunkt antwortet abgemeldet mit 401 | `59fa03d4` | `59fa03d4`, 10.08. |
| N2 | Recht für den Zeitplan | Ein angemeldetes Konto **ohne** `READ EVENT` (Vereinsvertreter) sieht die Rennliste, aber **keinen** Umschalter „Zeitplan". Gegenprobe: `GET /event/{id}/schedule` liefert 401 | `59fa03d4` | |
| N3 | Gemerkter Modus greift nicht ins Leere | Modus „Zeitplan" wählen, abmelden, mit einem Konto ohne `READ EVENT` neu anmelden: die Leiste steht auf „Rennen", nicht auf einer leeren Zeitplanliste. Der gemerkte Modus darf niemanden festnageln | `59fa03d4` | |
| N4 | Gruppierung nach Tagen | Der Zeitplan ist nach Tagen gruppiert, die Tagesüberschrift bleibt beim Scrollen oben stehen | `59fa03d4` | `59fa03d4`, 10.08. |
| N5 | Laufender Lauf | Ein Lauf, der **an den Start gerufen** ist (`matchActivatedAt`), aber noch nicht per Zeitnahme gestartet, ist bereits grün hinterlegt. Genau dieses Fenster — Boote stehen am Start, Uhr läuft noch nicht — war der Grund, nicht nur `matchStartedAt` zu prüfen | `59fa03d4` | |
| N6 | Beendeter Lauf | Ein Lauf mit `matchFinishedAt` ist **nicht** mehr grün. Sonst bliebe der halbe Tag grün stehen | `59fa03d4` | |
| N7 | Markierung des offenen Wettkampfs | Der früheste Slot des gerade offenen Wettkampfs ist markiert und wird in den sichtbaren Ausschnitt geholt — auch wenn er weit unten im Tag liegt | `59fa03d4` | `59fa03d4`, 10.08. |
| N8 | Freie Programmpunkte | Slots ohne Wettkampf (Pausen, Siegerehrungen) sind ausgegraut und nicht anklickbar | `59fa03d4` | `59fa03d4`, 10.08. |
| N9 | Sprung aus dem Zeitplan | Klick auf einen Slot wechselt den Wettkampf, die Markierung wandert mit, und die Leiste **bleibt** auf „Zeitplan" stehen | `59fa03d4` | `59fa03d4`, 10.08. |
| N10 | Zeitplan lädt nur bei Bedarf | Im Modus „Rennen" darf **keine** Anfrage an `/event/{id}/schedule` gehen. Im Netzwerk-Reiter prüfen: sonst hängt an jedem Aufruf einer Wettkampfseite eine Abfrage mehr | `59fa03d4` | `59fa03d4`, 10.08. |
| N11 | **Kein Selbstaktualisieren** | **Bewusste Lücke, im Betrieb nachzustellen.** Die grüne Markierung ist der Stand vom Seitenaufruf. Läuft im Regattabüro ein Lauf los, während die Seite offen steht, ändert sich nichts, bis jemand neu lädt. Die Frage für den Test: **fällt das jemandem auf die Füße?** Wenn ja, ist ein Aktualisieren-Knopf schnell nachgerüstet; die mitlaufende Ansicht sollte das Live-Dashboard bleiben | `59fa03d4` | |
| N12 | Schmaler Bildschirm | Unter 1200 px liegt die Leiste als Schublade über dem Inhalt, der Umschalter funktioniert dort genauso, und nach der Auswahl geht sie zu | `59fa03d4` | |

## O — Seitenleiste mit den Rennen (Nachtrag zu `37110c69`)

Dieser Commit steckt seit dem 10.08. in `feature/crf-2026` und hat im Katalog bislang **keinen**
Eintrag.

| ID | Fall | Erwartung | testbar ab | Nachweis |
|---|---|---|---|---|
| O1 | Zurück zur Veranstaltung | Der Zurück-Link führt auf `/event/{id}?tab=competitions` — der Wettkämpfe-Tab ist danach aktiv, nicht „Allgemein" | `37110c69` | `37110c69`, 09.08. |
| O2 | Reihenfolge und Markierung | Die Liste führt alle Wettkämpfe nach Kennung sortiert (`1`, `2`, … `16-NC`, `17-NC`), der offene ist hervorgehoben und wird in den Blick gescrollt | `37110c69` | `37110c69`, 09.08. |
| O3 | Eingeklappt bleibt eingeklappt | Einklappen, anderen Wettkampf aufrufen, Seite neu laden: die Leiste bleibt zu (`competition_nav_collapsed`) | `37110c69` | `37110c69`, 09.08. |
| O4 | Filter | Tippen filtert über Kennung, Name und Kurzname; die Kopfzahl wechselt auf `9/19` | `37110c69` | `37110c69`, 09.08. |
| O5 | Schublade auf schmalem Schirm | Unter 1200 px Overlay statt Spalte, nach der Auswahl geht sie zu | `37110c69` | `37110c69`, 09.08. |
| O6 | **Tab bleibt beim Rennwechsel** | **Nie geprüft, und der Punkt mit dem meisten Alltagsnutzen.** In „Durchführung" stehen, in der Leiste ein anderes Rennen anklicken: man muss wieder in „Durchführung" landen, nicht in „Allgemein". Verlangt ein Konto mit `UPDATE EVENT` — deshalb blieb der Fall offen | `37110c69` | |
| O7 | Rückfall bei fehlendem Tab | Ein Tab, den es beim Ziel-Rennen nicht gibt (z. B. „Meldungen" bei geschlossener Meldung), fällt auf „Allgemein" zurück. Ohne diesen Schutz zeigt MUI einen Tab-Streifen ohne Auswahl und einen leeren Inhalt. Gegenprobe über die Adresse: `?tab=execution` mit einem Konto ohne `UPDATE EVENT` | `37110c69` | `37110c69`, 09.08. |
| O8 | Einzelner Wettkampf | Eine Veranstaltung mit genau einem Wettkampf zeigt keine Rennliste — wohl aber den Zeitplan-Umschalter, denn ein Rennen kann mehrere Läufe haben | `59fa03d4` | |
| O9 | Kurznamen | Die Umschaltung auf Kurznamen („CM 4x+") bleibt gemerkt und gilt weiter. **Fremder Beitrag** aus einer anderen Sitzung, hier nur der Vollständigkeit halber | fremd | |

## Was der Test entscheiden soll

1. **N11** — ob der fehlende Selbstaktualisierung im Regattabüro stört. Das ist eine
   Betriebsfrage, keine Codefrage, und nur am echten Tag zu beantworten.
2. **O6** — ob das Mitnehmen des Tabs sich im Alltag richtig anfühlt oder ob man beim Rennwechsel
   lieber immer auf „Allgemein" landet.
3. Ob die Regie-Ansicht als **Modus** der Wettkampfseite ausreicht, oder ob doch ein eigener Ort
   im Router gewünscht ist. Der eigene Ort kostet einen Umbau an elf Dateien, die ihre IDs heute
   direkt aus der Route ziehen (`competitionRoute.useParams()`), darunter Durchführung und
   Auswechslungen — der sicherheitskritischste Teil der Anwendung.

## Nicht in diesen Fällen

- Das **Live-Dashboard** bleibt unberührt. Es ist bewusst die mitlaufende Beobachtungsansicht
  geblieben, die Leiste ist die Arbeitsansicht.
- Der **Backend-Build** auf diesem Zweig ist unabhängig von dieser Arbeit kaputt: die geteilte
  Build-Datenbank auf Port 7652 trägt Migration `202608091501` einer anderen Sitzung, während
  `202608091500` dort fehlt. Reines Frontend liess sich daran vorbei bauen, ein Backend-Build
  nicht.
