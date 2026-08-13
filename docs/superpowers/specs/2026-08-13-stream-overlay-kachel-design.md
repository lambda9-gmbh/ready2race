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
