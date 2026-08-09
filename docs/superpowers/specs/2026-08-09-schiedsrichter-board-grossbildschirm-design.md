# Design: Schiedsrichter-Board auf Tablet und Laptop

**Stand:** 2026-08-09
**Status:** abgestimmt, zur Umsetzung
**Branch:** `claude/referee-board-tablet-laptop-21b10f`
**Betroffen:** `frontend/src/pages/event/LiveDashboardPage.tsx`, `frontend/src/components/event/liveDashboard/`

## 1. Ausgangslage

Das Schiedsrichter-Board ist mobile-first entstanden (siehe
`2026-07-29-live-dashboard-schiedsrichter-design.md`) und sieht auf einem größeren Bildschirm
entsprechend aus:

- Der Inhalt steht in einer Spalte mit `maxWidth: 700`, zentriert.
- Der Umschalter „Live" / „Läufe" ist eine `BottomNavigation`, die über die **volle Fensterbreite**
  am unteren Rand klebt. Unter einer 700 px breiten Spalte auf einem 1440-px-Laptop wirkt das wie
  ein verirrtes Telefon-Element.
- Auf dem Laptop gibt es keinen Grund, zwischen „Live" und „Läufe" umzuschalten — beides passt
  nebeneinander.

## 2. Ziel

Ab `md` (900 px) nutzt das Board die Breite: **beide Ansichten gleichzeitig**, kein Umschalter,
keine Bottom-Bar. Darunter bleibt alles unverändert.

`md` ist derselbe Bruch, an dem `RootLayout` von Drawer auf feste Sidebar wechselt
(`useMediaQuery(theme.breakpoints.down('md'))`) — das Board springt also gemeinsam mit dem Rest
der App um. Ein iPad im Hochformat (820 px) bleibt damit bewusst in der Handy-Ansicht: zwei Spalten
à 400 px wären für die Karten zu eng.

## 3. Aufbau der breiten Ansicht

```
┌──────────────────────────────────────────────────────────┐
│ Titel                       12:04:33   ⟳ Aktualisierung  │   volle Breite
│ ▁▁▁▁▁▂▂▃▃▃▃▄▄▄ Zeitstrahl-Indikator ▄▄▄▅▅▅▆▆▆▇▇▇        │   volle Breite
├───────────────────────────┬──────────────────────────────┤
│ Live                      │ Läufe                        │
│ ┌───────────────────────┐ │ ┌──────────────────────────┐ │
│ │ laufender Lauf        │ │ │ 09:00 …                  │ │
│ └───────────────────────┘ │ ├──────────────────────────┤ │
│ ┌───────────────────────┐ │ │ 09:06 …                  │ │
│ │ Als Nächstes          │ │ ├──────────────────────────┤ │
│ └───────────────────────┘ │ │ …                        │ │
│  (sticky)                 │ │ Nicht terminiert         │ │
└───────────────────────────┴──────────────────────────────┘
```

- Seitenbreite wächst von `maxWidth: 700` auf `1400`.
- Grid `minmax(0, 1fr) minmax(0, 1fr)`, gleiche Gewichtung beider Spalten.
- Die linke Spalte ist `position: sticky` mit `alignSelf: 'start'`, damit sie beim Scrollen der
  langen Liste stehen bleibt. `maxHeight: calc(100vh - …)` plus eigener Scroll als Notbremse für
  den Fall, dass mehrere Läufe gleichzeitig laufen.
- Spaltenüberschriften nutzen die vorhandenen Übersetzungen `event.liveDashboard.tabs.live` und
  `…tabs.matches`. Keine neuen i18n-Schlüssel.

## 4. Was daran nicht nur CSS ist

**Fetch-Scope.** Heute bestimmt der Tab, wie viel der Server liefert: `LIVE` (nur laufende plus der
nächste Lauf) oder `ALL`. In der breiten Ansicht wird immer `ALL` geholt, weil beide Spalten
gleichzeitig sichtbar sind. Die Entscheidung wandert als reine Funktion `dashboardScope(wide, tab)`
nach `common.ts`.

**ETag.** `etagRef` wird heute im `onChange` des Umschalters zurückgesetzt. Beim Wechsel über den
Breakpoint (Fenster wird größer, Gerät wird gedreht) gäbe es diesen Klick nicht — der ETag des
alten Scopes ginge mit auf die Reise. Das Zurücksetzen zieht deshalb in einen Effekt auf `scope`.
Ein falsches 304 wäre zwar unwahrscheinlich (der Server hasht das serialisierte DTO, ein
`LIVE`-Ausschnitt hat einen anderen Hash als die Gesamtliste), aber die Kopplung an den Klick ist
schlicht die falsche Stelle.

**„Hat sich was getan"-Punkt.** Das rote Badge am Live-Tab meldet, dass sich im nicht sichtbaren
Live-Tab etwas geändert hat. Breit ist Live immer sichtbar — der Punkt wird dort nicht gesetzt.

**DOM-Ids.** Ein laufender Lauf steht breit **zweimal** auf der Seite: links in „Live" und rechts
in der Gesamtliste. Die heutige ID-Vergabe (`live-dashboard-entry-<id>`) wäre damit doppelt, und
der Klick auf den Zeitstrahl-Indikator würde per `getElementById` das erste Vorkommen erwischen.
Die Ids bekommen deshalb ein Spalten-Präfix (`list` / `live`); der Klick-Handler sucht erst die
Liste, dann die Live-Spalte. Damit landet der Sprung breit immer in der Gesamtliste — die
Live-Spalte steht ohnehin schon im Blick.

## 5. Nachtrag aus dem Handtest: die Karte misst sich selbst

Bei 1024 px (Tablet quer) sind zwei Spalten je ~330 px breit — **schmaler als ein Telefon**. Die
Karte entschied ihre Kurzformen aber am *Fenster* (`{xs: …, sm: …}`) und nahm bei 1024 px die
Langform „Ergebnisse vollständig — wartet auf Beenden". Deren Chip sitzt im Kopf-Grid in derselben
`auto`-Spalte wie die geplante Startzeit, zieht die Spalte also über beide Zeilen auf: vom
Laufnamen blieb „V…", vom Wettkampf „Mixed Dop…".

`LiveDashboardMatchCard` bekommt deshalb `containerType: 'inline-size'`, und die Umschaltung
lang/kurz läuft über `@container (min-width: 480px)` statt über den Viewport. Die Karte richtet
sich damit nach ihrer eigenen Breite — richtig für beide Fälle, auch für den bestehenden schmalen
Einspalter. Abschnitt 8 bleibt in der Sache gültig: es kommt kein Inhalt hinzu, die Schwelle sitzt
nur am richtigen Maß.

## 6. Code-Zuschnitt

`LiveDashboardPage.tsx` ist heute 410 Zeilen und würde die beiden Inhaltsblöcke sonst doppelt
enthalten (einmal je Modus). Die Blöcke wandern deshalb als `LiveColumn` und `MatchListColumn`
nach `components/event/liveDashboard/LiveDashboardColumns.tsx`. Schmale und breite Ansicht rendern
dieselben Komponenten, nur in einem anderen Rahmen. Die Seite behält Daten, Polling und Aktionen.

## 7. Prüfung

- `dashboardScope` und `dashboardEntryDomId` sind reine Funktionen und werden in `common.test.ts`
  mitgetestet. Das Projekt hat kein jsdom/testing-library, Komponenten werden nicht gerendert.
- Das Layout selbst wird im laufenden Dev-Server geprüft: 1440 px (zwei Spalten), 1024 px (zwei
  Spalten, Tablet quer), 820 px (eine Spalte, Bottom-Bar), 390 px (unverändert).
- `npm run type-check` bzw. der Build müssen grün sein.

## 8. Bewusst nicht Teil dieser Änderung

- Karteninhalte bleiben identisch — keine ausgeschriebenen Vereinsnamen oder Statuslabel für breite
  Bildschirme. Die Kurzformen sind fachlich gewollt, nicht platzbedingt.
- Der Zeitstrahl-Indikator bleibt einzeilig über beiden Spalten.
- Am Backend ändert sich nichts.
