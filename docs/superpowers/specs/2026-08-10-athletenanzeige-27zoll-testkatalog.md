# Athletenanzeige 27″: was von Hand zu prüfen ist

**Stand:** 2026-08-10
**Branch:** `claude/ready2race-athlete-display-27in-ad983c`, gemergt mit `feature/crf-2026` (Stand `0511eb5b`)
**Entwurf:** `2026-08-09-athletenanzeige-27zoll-design.md` · **Plan:** `../plans/2026-08-09-athletenanzeige-27zoll.md`

Dieses Dokument listet, was **noch offen** ist. Was bereits belegt ist, steht am Ende — damit die
Prüfung nicht doppelt läuft.

## Wo die Anzeige liegt

- Öffentlich, ohne Anmeldung: `/board/<eventId>`
- In der Kiosk-Rotation: `/event/<eventId>/info`, wenn dort eine aktive View vom Typ
  „Athleten-Anzeige" konfiguriert ist
- Zielgerät: 27″ 16:9 im Vollbild, also **2560×1440** oder **1920×1080**. Gelesen wird aus etwa
  fünf Metern.

## A — Am echten Backend (nichts davon ist belegt)

Der gesamte Sichttest lief gegen einen Stub-Server mit erfundenen Antworten, weil dieser Worktree
keine `.env` hat und das Backend auf 8080 einem anderen Worktree gehört. **Alles unter A ist
deshalb ungeprüft**, auch wenn die Darstellung selbst belegt ist.

| # | Fall | Erwartung |
| --- | --- | --- |
| A1 | Seite mit echter `eventId` am Seed öffnen | Drei Spalten, Daten stimmen mit Durchführung überein |
| A2 | Zweiten Lauf aktivieren | Vierte Spalte erscheint, alle vier gleich breit |
| A3 | Dritten Lauf aktivieren | Zwei Karten, Kopfzeile: „+1 weiterer Lauf in der Arena" |
| A4 | Lauf aktivieren, nicht starten | „in Vorbereitung" im Kopf der Karte |
| A5 | Lauf starten | „gestartet HH:MM" statt „in Vorbereitung" |
| A6 | Einzelne Boote werten, Zeitstrafe eintragen | Platz, Zeit und „inkl. … s Zeitstrafe" rechts in der Zeile — **während** der Lauf noch läuft |
| A7 | Lauf beenden | Wandert in die Ergebnis-Spalte; große Zahl ist der Platz, darunter „Nr. n" |
| A8 | Boot abmelden, Boot auf DNF setzen | Abmeldung mit Grund bzw. DNF-Grund rechts, beide gedämpft und ans Ende sortiert |
| A9 | **Wertungskategorien** am Wettkampf konfigurieren, Lauf beenden | Ergebnis-Spalte in Abschnitte getrennt, je Kategorie eigene Platzzählung ab 1 |
| A10 | Lauf absagen („Findet nicht statt") | Bleibt durchgestrichen an seiner Stelle stehen, ohne Mannschaften |
| A11 | Runde noch nicht gesetzt | „Lauf noch nicht gesetzt" statt einer Aufstellung |
| A12 | Pause im Zeitplan, „Pausen öffentlich zeigen" an | Programmpunkt mit Chip, ohne Wettkampfbezug |
| A13 | Startzeit verstreichen lassen, Lauf nicht starten | „erwartet" statt negativer Restzeit; nach 30 min verschwindet der Lauf |
| A14 | Startnummern in der Durchführung tauschen | Die große Zahl folgt dem Tausch beim nächsten Abruf |

## B — Kiosk-Einbindung (ungeprüft)

| # | Fall | Erwartung |
| --- | --- | --- |
| B1 | `/event/<eventId>/info` mit der Athleten-Anzeige als einziger aktiver View | Dieselbe Bühne; die Overlay-Knöpfe oben rechts verdecken die Uhr nicht |
| B2 | Zusammen mit anderen Views in der Rotation | Beim Zurückschalten kein Spinner, kein Neuladen von vorn |
| B3 | In der Admin-Maske den Regler „Anzeigedauer" auf 5 s | Die Anzeige taktet trotzdem nicht schneller als alle 10 s |

## C — Konfiguration (teilweise ungeprüft)

| # | Fall | Erwartung |
| --- | --- | --- |
| C1 | Ansichtskonfiguration für `ATHLETE_BOARD` öffnen | Nur noch **ein** Zahlenfeld („Anzahl laufender Läufe"), Minimum 2, mit Hinweistext |
| C2 | Bestehende Konfiguration mit `upcoming`/`results` in `filters` | Läuft unverändert weiter; die Felder werden nur nicht mehr angeboten |
| C3 | Ganz ohne Konfiguration | Vorgabewerte greifen, Seite ist ohne Einrichtung brauchbar |

## D — Betrieb am Renntag (ungeprüft)

| # | Fall | Erwartung |
| --- | --- | --- |
| D1 | Anzeige über Stunden laufen lassen | Kein Speicherwachstum, kein Verrutschen des Layouts |
| D2 | Netzstecker am Anzeigerechner ziehen und wieder stecken | Letzter Stand bleibt stehen, „Verbindung unterbrochen" erscheint, danach räumt sie sich weg |
| D3 | Bildschirm über Nacht an lassen | Uhr und „Stand" stimmen morgens noch (Serverzeit-Verankerung) |
| D4 | Zwei Anzeigen gleichzeitig (Start und Ziel) | Beide zeigen dasselbe; die Datenbanklast steigt nicht (Zwischenspeicher, 5 s) |

## E — Was schon belegt ist (nicht erneut prüfen)

Gemessen im Browser bei **2560×1440 und 1920×1080** gegen einen Stub, in zwölf Datenlagen; je Fall
im DOM nachgerechnet: kein Seitenscroll, keine vertikal beschnittene Bootszeile, kein hart
abgeschnittener Text, richtige Spaltenzahl.

- Ein Lauf je Status (3 Spalten) · zwei und drei parallele Läufe (4 Spalten, Überlauf-Hinweis)
- In Vorbereitung · laufender Lauf mit Zeiten, Zeitstrafen und DNF · beendeter Lauf mit
  Platzierungen, DNF und Abmeldung
- Volles Feld mit acht Booten · volles Feld mit acht Booten **und** sehr langen Vereinsnamen
- Ergebnis in zwei Wertungskategorien, Überschriften im selben Raster wie die Boote
- Alle drei Spalten leer · abgesagter Lauf · wartende Runde · Programmpunkt · verstrichene Startzeit
- Unbekannte Veranstaltung (neutrale Meldung, kein Weiterleiten zur Anmeldung)
- Totes Backend: letzter Stand bleibt, „Verbindung unterbrochen" nach zwei verpassten Intervallen,
  Erholung räumt die Warnung weg
- Gestapelte Ansicht unterhalb `lg` (390×844 und 500×900): scrollt, keine überlappenden Zeilen,
  `--ab-scale` neutral

Automatisiert: **690 Frontend-Tests** (32 Dateien), `tsc -b` sauber, `npm run lint` bei der
Repo-Baseline ohne neue Befunde in berührten Dateien, Backend `AthleteBoardLogicTest` grün.

## F — Wofür ein Blick eines Menschen nötig bleibt

Zahlen sagen nichts über Lesbarkeit. Diese drei Punkte kann nur jemand vor dem Schirm beurteilen:

1. **Schriftgröße aus fünf Metern.** Die Maxima wurden am 10.08. angehoben und am Bild abgeglichen,
   aber nie aus echter Entfernung. Prüfen mit vollem Feld (acht Booten, vier Spalten) — das ist der
   kleinste Zustand.
2. **Die Zahl links.** Im aktuellen und nächsten Lauf ist sie die Startnummer, im Ergebnis der Platz
   innerhalb der Wertungskategorie. Unterschieden werden beide nur über die Spaltenüberschrift.
   Ob das am Steg trägt, entscheidet der Praxistest.
3. **Der Dichtefaktor bei kleinen Feldern.** Bei zwei oder drei Booten wächst die Schrift bis auf das
   1,5-fache. Ob das würdig aussieht oder plakativ, ist Geschmackssache und sollte am echten Schirm
   entschieden werden (`MAX_DENSITY_SCALE` in `boardLayout.ts`).

## G — Bekannte Grenzen, bewusst so gelassen

- **Mobil ist nicht Gegenstand dieses Umbaus.** Unterhalb `lg` steht die alte gestapelte Ansicht;
  sie funktioniert, wurde aber nicht gestaltet. Eine eigene Athletenansicht ist geplant.
- **Die Nachfrist für überfällige Läufe** (30 min) ist nicht konfigurierbar.
- **Mehr als zwei Läufe in der Arena** werden gekappt; der Hinweis nennt nur die Anzahl, nicht
  welche.
