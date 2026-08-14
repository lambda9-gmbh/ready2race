# Stream-Overlay-Kachel für das Board-System

Stand: 13.08.2026, mit Thomas abgestimmt (Zuschnitt A, Inhalt B+konfigurierbar, Farbe A,
Ergänzungen: Vereins-Kurz-/Langform, Zeitstrafen, Rundenzeiten, r2r-Theming).

## Zweck

Livestreamer laden eine öffentliche Board-URL als Browser-Quelle in ihre Streaming-Software
(OBS u.ä.), filtern eine einheitliche Hintergrundfarbe heraus (Chroma-Key) und legen den
verbleibenden Inhalt als Overlay über ihr Streambild. Das Board-System bekommt dafür einen
neuen Kachel-Typ **Stream-Overlay**: vollflächige Seite in Key-Farbe, unten ein Lower-Third
im r2r-Design.

## Entscheidungen

1. **Zuschnitt:** Eigenes Board mit vollflächiger Kachel (Mechanismus der Sprecher-Kachel).
   Kein Raster-Zuschnitt, keine eigene Route außerhalb des Board-Systems.
2. **Inhalt:** Modus je Kachel im Board-Editor:
   - `AUTO` (Voreinstellung): laufender Lauf; läuft keiner, das letzte Ergebnis mit
     „Ergebnis"-Kennzeichnung; gibt es auch das nicht, bleibt die Seite reine Key-Farbe.
   - `RUNNING`: nur laufende Läufe, sonst leer.
   - `RESULTS`: nur das letzte Ergebnis.
   - `UPCOMING`: der nächste anstehende Lauf (Aufstellung, Startzeit).
   Streamer können sich mehrere Boards mit unterschiedlichen Modi bauen und in der Regie
   umschalten.

   Ein Lower-Third zeigt immer genau EINEN Lauf. Laufen mehrere gleichzeitig (Ketten-Modus),
   gewinnt der zuletzt gestartete — das ist der, über den die Regie gerade redet.
3. **Key-Farbe:** Die vorhandene Kachel-Hintergrundfarbe wird wiederverwendet;
   Voreinstellung für diesen Kachel-Typ ist reines Grün `#00FF00`. Umstellen (Magenta, Blau)
   geht über den bestehenden Farbwähler im Board-Editor.
4. **Vereinsname:** Kurz-/Langform als Kachel-Einstellung (Voreinstellung Kurzform), analog
   zur bestehenden Kurzform-Logik der übrigen Anzeigen.
5. **Zeitstrafen und Rundenzeiten** werden angezeigt: Zeitstrafe als Sekunden + Vermerk an
   der Bootszeile (Warnfarbe), Rundenzeiten als eigene, tabellarisch ausgerichtete Zeile —
   beide Felder liefert der Board-Endpoint bereits.

## Darstellung (Chroma-Regeln)

- Lower-Third am unteren Rand, volle Breite mit Außenabstand, maximal ~⅓ der Höhe.
- Kopfzeile: Wettkampfname (+ Kategorie), Runde/Lauf, Zustand („Läuft" / „Ergebnis" /
  „Anstehend"), Startzeit.
- Je Boot eine Zeile: Startnummer, Verein (Kurz- oder Langform), Zeit/Platz sobald gewertet,
  Zeitstrafe, Rundenzeiten.
- **Vollständig deckende Flächen** — keine Halbtransparenz, keine weichen Schatten, kein
  Blur an Außenkanten: halbtransparente Pixel mischen sich mit der Key-Farbe und erzeugen
  Farbsäume beim Keying. Harte Kanten, leichte Rundung erlaubt.
- r2r-Design: Panel- und Textfarben sowie Typografie aus dem Theme (inkl. Custom Font),
  Akzente in der Primärfarbe. Dunkles, deckendes Panel mit hellem Text.

## Technik

- **Backend:** Neuer Wert im Kachel-Typ (`STREAM`), Modusfeld wiederverwendet bzw. um `AUTO`
  ergänzt; Kurz-/Langform als Kachel-Einstellung. Die Daten (laufende Läufe inkl.
  Teilzeiten, Zeitstrafen und Rundenzeiten; letzte Ergebnisse; anstehende Läufe) liefert der
  bestehende Board-View-Endpoint — kein neuer Datenpfad, dieselben
  Ergebnis-Sichtbarkeits-Tore wie heute (`publicResultsVisibility` unberührt).
- **Frontend:** Neue Overlay-Komponente im BoardRenderer; ein Board mit Stream-Kachel
  rendert vollflächig. Poll-Takt und Cache-Invalidierung wie bei den übrigen Boards
  (EventChangeMarker). Board-Editor: Typ wählbar, Modus-Dropdown, Kurz-/Langform-Schalter,
  Farb-Voreinstellung Grün.
- **Leer-/Fehlerzustand:** Nichts zu zeigen → reine Key-Farbe (Overlay verschwindet im
  Stream von selbst). Poll-Fehler → letzter guter Stand bleibt stehen (wie Boards).

## Tests

- Auswahllogik des `AUTO`-Modus (laufend → Ergebnis → leer) als reine Funktion mit vitest.
- Backend: bestehende Board-Tests decken den Datenpfad; neuer Kachel-Typ-Wert in
  Validierung/Serialisierung mitgeprüft.
- Browser-Verifikation mit dem CRF-Seed: laufender Lauf, Ergebnis-Rückfall, Leerzustand,
  Zeitstrafe und Rundenzeiten — Screenshots als Beleg.

---

# Ausbaustufe (13./14.08.2026, mit Thomas abgestimmt — „mit Uhrzeiten")

## Neue Anforderungen

1. **Zentrierte Panels für Ergebnis und Als-Nächstes.** „Läuft" bleibt Lower-Third; `result`
   und `upcoming` (Einzellauf) rendern als prominentes, mittig zentriertes Panel im
   TV-Grafik-Stil: großer Kopf (Wettkampf + Runde/Lauf + Zustand), darunter die Boote als
   breite Zeilen. Uhrzeit/Countdown im Als-Nächstes-Panel über das vorhandene Flag
   `showCountdown` wählbar; die Weiterkommens-Regel („Weiter kommen N Boote → …") über das
   vorhandene Flag `showAdvancement` (Server liefert sie nur bei Anforderung —
   `dataNeeds.advancement`).
2. **Boot-Darstellung einstellbar** (`streamCrew` am STREAM-Element):
   - `CLUBS_FIRST` (Voreinstellung): Vereinsname prominent, Personen als kleine Zeile.
   - `PARTICIPANTS_FIRST`: Personennamen prominent, Verein klein.
   - `CLUBS_ONLY`: keine Personen.
   Personennamen kommen aus den Team-`participants`; Crew-Details werden serverseitig nur
   angefordert, wenn `streamCrew != CLUBS_ONLY` (Sparsamkeitsregel der Boards).
3. **Laufende Uhr** im Lower-Third des laufenden Laufs: Zehntel-genau ab `actualStartTime`,
   100-ms-Tick, synchronisiert über `BoardViewDto.serverTime` (Client-Versatz = clientNow −
   serverTime beim Eintreffen der Antwort; das Poll-Delay verschiebt nur das Erscheinen,
   nie den Wert). Text-Fade-in (~400 ms) sobald `actualStartTime` erstmals eintrifft;
   Einfrieren, sobald alle Boote gewertet/ausgeschieden sind; danach ~5 s halten und
   Text-Fade-out. Fades ausschließlich als Text-Opacity AUF dem deckenden Panel — nie als
   Flächen-Fade über der Key-Farbe.
4. **Neuer Modus `LAPS` („Rundenanzeige").** Schmales Bauchband unten: die letzten drei
   eingetroffenen Rundenzeiten des laufenden Laufs (Boot · Rundenname · Zeit), neueste
   zuerst und hervorgehoben. „Eingetroffen" = `created_at` der Runde; das DTO
   (`MatchTeamLapDto`) wird additiv um `recordedAt` erweitert. Kein laufender Lauf oder
   keine Runden → reine Key-Farbe.
5. **Neuer Modus `UPCOMING_LIST` („Nächste Läufe").** Zentriertes Panel mit den nächsten
   fünf anstehenden Läufen: Startzeit („mit Uhrzeiten"), Wettkampf, Runde/Lauf — eine
   Zeile je Lauf. `dataNeeds` fordert dafür `upcomingLimit ≥ 5` an.

6. **Bewegung („wie beim Skifahren", Thomas 13.08. nachts).** Zeiten bauen sich sichtbar
   auf: Im laufenden Lower-Third sortieren sich Bootszeilen um, sobald Zeiten/Plätze
   eintreffen — die neue Zeile schiebt sich von unten an ihre Rangposition, bestehende
   rutschen animiert nach oben/unten (FLIP-Prinzip: Positionen messen, per Transform
   invertieren, zur Identität animieren, ~350 ms ease-out). Boote ohne Zeit stehen in
   Startnummern-Reihenfolge darunter. Ergebnis-Panel: Zeilen erscheinen gestaffelt per
   Slide-in von unten. Rundenband: die neue Rundenzeit schiebt von rechts herein, ältere
   rücken nach. Alles ausschließlich über Transforms auf deckenden Flächen (chroma-sicher);
   keine neue Animations-Bibliothek — kleiner eigener FLIP-Helfer.

## Broadcast-Konventionen (Rechercheergebnis, bindend für die Darstellung)

- **Tabellenziffern** (`fontVariantNumeric: tabular-nums`) für ALLE live tickenden oder
  wechselnden Werte — sonst springen die Ziffernbreiten sichtbar.
- **Panel-Einblendungen als Transform-Slide** (translateY), nie als Flächen-Opacity:
  harte Kanten bleiben chroma-sicher, der Look entspricht TV-Einblendungen.
- Zustands-Akzent im Kopf: „LÄUFT" mit Punkt-Indikator, „ERGEBNIS", „ALS NÄCHSTES" —
  Akzentfarbe aus dem Theme.
- Dichte: höchstens die nötigen Zeilen, keine Deko; Schrift serifenlos, hoher Kontrast.

## Unverändert

Chroma-Regeln, Ein-Lauf-Regel, Ergebnis-Tore, Kachel-Farbe, Kurz-/Langform-Schalter
(`useShortNames` wirkt in allen Darstellungen), Editor-Sperren.
