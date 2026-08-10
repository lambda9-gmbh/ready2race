# Design: Athletenanzeige auf 27″ im Querformat

**Stand:** 2026-08-09
**Status:** Entworfen und abgestimmt, noch nicht umgesetzt.
**Vorgänger:** `2026-08-02-athleten-dashboard-design.md` — die dort getroffenen Entscheidungen zu
Datenquelle, Endpoint, Konfiguration, Polling und Betriebshärtung gelten unverändert weiter. Dieses
Dokument ändert ausschließlich die Oberfläche und die Benennung der Startnummer.

## Anlass

Die Anzeige funktioniert, ist aber zu voll. Bei mehreren gleichzeitig relevanten Läufen muss auf
dem fest montierten Bildschirm gescrollt werden — auf einem Schirm ohne Maus ist alles unterhalb der
Falz unsichtbar. Ein Blick darauf soll ohne jede Bedienung vollständig sein.

Zwei weitere Beobachtungen kommen dazu:

1. Auf einer Regatta sind höchstens **zwei** Läufe gleichzeitig relevant. Eine Anzeige, die auf
   beliebig viele auslegt, zahlt dafür mit Enge, ohne je den Nutzen zu sehen.
2. Die große Zahl links an jedem Boot heißt in der Athletenanzeige „Bahn". Im Ergebnis-Block steht
   an derselben Stelle der Platz. Zwei verschiedene Bedeutungen an derselben Position sind auf
   Distanz nicht zu unterscheiden.

## Nicht im Umfang

- **Mobil- und Individualansicht.** Dafür entsteht später eine eigene Athletenansicht. Die
  bestehende gestapelte Darstellung unterhalb des Umbruchpunkts bleibt funktionsfähig, wird aber
  nicht weiterentwickelt.
- **Automatisch rotierende Seiten.** Ausdrücklich ausgeschlossen: Was nicht auf den Schirm passt,
  wird begrenzt, nicht durchgeblättert.
- Backend-Datenbeschaffung, Konfigurationsmodell, Cache, Rate-Limit, Takt-Untergrenze.

## Entscheidung 1: Startnummer und Bahn sind dieselbe Zahl

**Befund.** Es gibt je Boot und Lauf genau eine Zahl: `competition_match_team.start_number`, unique
pro Lauf (`starting_position_unique_in_match`). Die gesamte übrige Anwendung nennt sie
„Startnummer" (Durchführung, Live-Dashboard, Start- und Ergebnislisten-Konfiguration). Eine davon
unabhängige Bahnnummer existiert nirgends im Datenmodell — auch der RaceClocker-Abgleich schreibt
die vor Ort abgelesene Bahnposition genau in dieses Feld zurück
(`CompetitionExecutionService.writeStartNumbers`, `RaceClockerFeedRow.lanesByRow`).

Nur die Athletenanzeige tauft sie in `AthleteBoardDto.lane` / „Bahn" um. Das ist die Verwechslung,
die diesen Entwurf ausgelöst hat.

**Entscheidung.** Eine Zahl, ein Name: **Startnummer**. Das Wort „Bahn" verschwindet aus der
Athletenanzeige — und zwar auf dem ganzen Pfad, nicht nur an der Oberfläche:

| Ort | vorher | nachher |
| --- | --- | --- |
| `AthleteBoardTeam` (Kotlin) | `lane: Int?` | `startNumber: Int?` |
| `AthleteBoardResultTeam` (Kotlin) | `lane: Int` | `startNumber: Int` |
| `eventInfo/control/Conversions.kt` | `lane = startNumber` | `startNumber = startNumber` |
| `openapi/documentation.yaml` | `lane` | `startNumber` |
| `frontend/src/api/*.gen.ts` | — | über `npm run generate` |
| i18n `event.info.athleteBoard.lane` | „Bahn" / „Lane" | `…startNumber`: „Nr." / „No." |

Ein Feld, das `lane` heißt und „Startnummer" anzeigt, wäre dieselbe Falle mit umgekehrtem
Vorzeichen. Der Endpoint ist öffentlich und unversioniert; die einzigen Verbraucher sind die
Athletenanzeige und ihre Kiosk-Einbindung, beide in diesem Repository.

## Entscheidung 2: Begrenzte, gleichwertige Bühne

### Auswahl und Priorität

Eine reine Funktion `selectBoardCards()` in `athleteBoard/boardLayout.ts` (ohne React, damit ohne
Browser prüfbar) wählt in dieser festen Reihenfolge:

| Rang | Inhalt | Anzahl |
| --- | --- | --- |
| 1 | Läufe in der Arena, einschließlich „in Vorbereitung" | höchstens **2** |
| 2 | Nächster Lauf | genau **1** |
| 3 | Letzter Lauf (Ergebnis) | genau **1** |

Daraus ergeben sich **drei oder vier gleich breite Spalten**, nie mehr, nie weniger.

Die drei Status-Spalten stehen **immer**, auch leer und dann mit ihrer neutralen Zeile („Zurzeit
kein Lauf in der Arena"). Ein fest montierter Bildschirm soll seine Struktur nicht wechseln, nur
weil gerade nichts fährt — wer täglich davorsteht, findet seine Spalte über die Position, nicht über
die Überschrift. Die vierte Spalte kommt hinzu, sobald ein zweiter Lauf in der Arena ist, und
verschwindet wieder, wenn er beendet ist.

Reihenfolge von links nach rechts: **Aktuell (1–2) · Nächster · Letzter**. Das ist die
Dringlichkeitsreihenfolge für eine Besatzung am Steg und zugleich die bisherige — wer die Anzeige
kennt, muss nicht umlernen.

### Überlauf

Sind mehr als zwei Läufe in der Arena, zeigt die Bühne die beiden ersten (die Liste kommt bereits
nach Startzeit sortiert). Die übrigen verschwinden nicht stumm: die Kopfzeile trägt dann
„+n weitere Läufe in der Arena". Ein verschwundener Lauf ist von einem Anzeigefehler nicht zu
unterscheiden — dieselbe Regel, die abgesagte Läufe an ihrer Stelle stehen lässt.

Die Backend-Konfiguration (`running`, Vorgabe 3) bleibt unverändert: Der Server liefert weiter, was
eingestellt ist, die Anzeige wählt daraus aus. So bleibt derselbe Endpoint für die spätere
Individualansicht brauchbar, und die Kappung steht an genau einer Stelle.

### Kein Scrollen — durch Bauart

Die Seite ist ein Raster über die volle Höhe:

```
Seite    height: 100dvh; overflow: hidden;
         grid-template-rows: auto 1fr           (Kopfzeile, Bühne)
Bühne    grid-auto-flow: column;
         grid-auto-columns: 1fr; min-height: 0
Karte    grid-template-rows: auto 1fr; min-height: 0
Boote    grid-template-rows: repeat(n, minmax(0, 1fr)); min-height: 0
```

Damit kann nichts überlaufen: Die Bootszeilen teilen sich, was an Höhe da ist. Kein
`overflow: auto`, keine Messung im JavaScript, keine Schleife aus Rendern und Nachjustieren — ein
Bildschirm, der tagelang unbeaufsichtigt läuft, soll bei Größenänderungen nichts neu entscheiden
müssen. Das ist dieselbe Haltung wie bei `AthleteBoardTeamLabel` (beide Vereinsketten stehen im
Dokument, die Breite blendet eine aus).

Damit der Text dabei nicht zu Konfetti wird, liefert `densityScale(maxBoote, spalten)` einen Faktor
zwischen 0,55 und 1,0, der als CSS-Variable `--ab-scale` alle `clamp()`-Größen multipliziert.
Ebenfalls eine reine Funktion: monoton fallend in beiden Argumenten, mit Untergrenze, ohne
Browser prüfbar.

Die Crew-Zeile bleibt erhalten (ausdrückliche Entscheidung), aber **einzeilig mit
Auslassungspunkten**. Das ist die Bedingung dafür, dass eine Bootszeile eine berechenbare Höhe hat;
mit umbrechender Crew hinge die Kartenhöhe an der Länge der Nachnamen.

**Unterhalb `lg`** bleibt die heutige gestapelte, scrollbare Darstellung. Die Mobilansicht ist nicht
Teil dieses Vorhabens, soll aber nicht kaputtgehen.

## Inhalt je Karte

### Aktueller Lauf (inkl. „in Vorbereitung")

Kopf: Wettkampf, Runde und Laufname; rechts der Zustand — „in Vorbereitung" oder „läuft seit 14:28".
Die geplante Startzeit steht wie bisher darüber.

Je Boot eine Zeile:

- **Startnummer** groß links (die Zahl, die bisher „Bahn" hieß)
- Vereinskette und Mannschaftsname
- darunter die Crew, einzeilig
- rechts, sobald die Zeitnahme dieses Boot gewertet hat: Platz, Zeit, darunter die Zeitstrafe

Die Zuordnung Mannschaft ↔ Startnummer ist damit eindeutig und aus der Entfernung lesbar.

### Nächster Lauf

Dieselben Zeilen, aber ohne den Live-Teil: keine Platz-, Zeit- und Straf-Spalte. Der Fokus liegt auf
der organisatorischen Vorbereitung — wer fährt mit welcher Startnummer.

Kopf: geplante Startzeit groß, darunter Restzeit („in 33 min") bzw. „erwartet", wenn die Startzeit
verstrichen ist. Abgesagte Läufe, wartende Runden (`pendingRound`) und Programmpunkte (FREE-Slots)
verhalten sich unverändert.

Der Server füllt Platz, Zeit und Strafe im Block `upcoming` ohnehin nie; die Karte lässt die Spalte
deshalb strukturell weg, statt sie leer mitzuführen.

### Letzter Lauf

Die große Zahl ist der **Platz** — das ist bereits so und bleibt. Darunter steht heute „Bahn 3";
das wird zu **„Nr. 3"**. Zeit, DNF-Grund, Abmeldung und Zeitstrafe unverändert. Die Sortierung
(platzierte zuerst, abgemeldete ans Ende) bleibt.

### Kopfzeile

Eine schlanke Zeile: Veranstaltungsname links; rechts Uhrzeit, „Stand HH:MM" mit Stale-Warnung und
gegebenenfalls der Überlauf-Hinweis aus dem vorigen Abschnitt.

## Was unverändert bleibt

Polling samt Takt-Untergrenze, „letzter guter Stand bleibt stehen", Stale-Erkennung, 404-Fall,
gescheiterter Erstabruf, Serverzeit als Countdown-Bezug, Kiosk-Einbindung samt
`controlsOverlayed`, serverseitiger Zwischenspeicher, Rate-Limit, gzip.

Insbesondere bleibt `useAthleteBoardData` unangetastet.

## Fehler- und Sonderfälle

| Fall | Verhalten |
| --- | --- |
| Kein Lauf in der Arena | Spalte „Aktueller Lauf" bleibt stehen und zeigt ihre neutrale Zeile. Drei Spalten. |
| Zwei Läufe in der Arena | Vier gleich breite Spalten, die beiden aktuellen gleichwertig. |
| Drei oder mehr Läufe in der Arena | Die beiden ersten auf der Bühne, „+n weitere Läufe in der Arena" in der Kopfzeile. |
| Kein nächster Lauf, kein Ergebnis | Spalten bleiben mit neutraler Zeile stehen. |
| Lauf mit vielen Booten | `densityScale` verkleinert; die `1fr`-Zeilen verhindern den Überlauf. |
| Sehr langer Vereins-/Mannschaftsname | Umbruch auf zwei Zeilen erlaubt, danach Auslassungspunkte. Die Crew-Zeile bricht nie um. |
| Fehlende/verspätete Daten | Wie bisher: letzter guter Stand, alternde „Stand"-Angabe, Warnung ab zwei verpassten Intervallen. |
| Erster Abruf schlägt fehl | Wie bisher eigene Fehlermeldung, niemals „kein Lauf in der Arena". |

## Tests

**Neu, `boardLayout.test.ts`** (Vitest, wie die übrigen Testdateien im Frontend):

- Auswahl bei 0, 1, 2 und 3 laufenden Läufen — Anzahl und Reihenfolge der Karten
- Spaltenzahl 3 bzw. 4
- Überlauf-Hinweis: Anzahl der nicht gezeigten Läufe
- Fehlender nächster Lauf / fehlendes Ergebnis: Karte bleibt als leere Statusspalte bestehen
- `densityScale`: monoton fallend in Bootszahl und Spaltenzahl, Ober- und Untergrenze eingehalten

**Bestehend:** `common.test.ts` wird an die umbenannten Schlüssel angepasst, falls betroffen.
`AthleteBoardLogicTest` (Backend, 40 Tests) muss nach der DTO-Umbenennung weiter grün sein.

**Checks:** `npm run lint`, `tsc -b`, `npm run test` im Frontend; `backend/mvnw test` fürs Backend
(braucht ein gesetztes `JAVA_HOME`, siehe Notiz „Lokale Entwicklung: Fallen").

**Sichtprüfung** am Seed bei 2560×1440 im Vollbild, für: je einen Lauf pro Status, zwei parallele
Läufe, „in Vorbereitung", laufender Lauf mit Zeiten und Zeitstrafen, abgeschlossener Lauf mit
Platzierungen, sehr lange Mannschaftsnamen, sowie ein Abruf gegen ein totes Backend.

## Offene Punkte

Keine.
