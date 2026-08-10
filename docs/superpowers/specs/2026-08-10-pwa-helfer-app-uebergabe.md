# Übergabe: Helfer-App als PWA

**Stand:** 2026-08-10, nachts
**Branch:** `claude/pwa-app-git-project-c4fa2a`, 21 Commits über `f8488dfb`
**Spezifikation:** `2026-08-09-pwa-helfer-app-design.md` · **Plan:** `../plans/2026-08-09-pwa-helfer-app.md`

Alle neun Aufgaben sind umgesetzt, einzeln begutachtet und nachgebessert. Der Stand ist grün:
549 Tests, `tsc -b` fehlerfrei, `vite build` mit Exit-Code 0, `dist/app/sw.js` mit acht
Precache-Einträgen, die alle auf vorhandene Dateien zeigen.

**Nicht getan, weil es dir gehört:** kein Merge nach `feature/crf-2026`, kein Push, kein MR.

---

## 1. Was vor dem Zusammenführen ansteht

### 1.1 `feature/crf-2026` ist aufgenommen, der Rückmerge steht aus

**Erledigt am 10.08. vormittags:** `feature/crf-2026` (Stand `cc78980a`) ist in diesen Branch
gemergt (`a0930846`). Ein Konflikt in `LiveDashboardPage.tsx` wurde von Hand aufgelöst: Der
Zustand für die Kurzbezeichnungen aus `crf-2026` und der Startwert aus dem Lese-Cache stehen
jetzt nebeneinander. Die Offline-Sperre ist auf **alle fünf** schreibenden Aktionen erweitert
(`onFinish`, `onSetActivated`, `onMarkStarted`, `onResumeAutoPull`, `onSkipSlot`); das Kennzeichen
`raceClockerAutoPull` ist keine Handlung und bleibt unberührt. Danach: 637 Tests grün, `tsc`
sauber, Build mit Exit-Code 0.

Sicherungstag vor dem Merge: `vor-crf-merge-pwa`.

**Was noch fehlt: der Rückmerge nach `feature/crf-2026`.** Ich habe ihn bewusst nicht ausgeführt.
Zwei Gründe, beide unabhängig voneinander ausreichend:

1. Im Hauptcheckout `/Users/thomas/Developer/privat/ready2race` liegt **fremde, nicht committete
   Arbeit** einer anderen Sitzung — das „Mein Event"-Dashboard samt Migration
   `V202608101000__participant_requirement_publicly_visible.sql`. Ein Merge dort hätte in einen
   laufenden fremden Arbeitsstand hineingegriffen.
2. `feature/crf-2026` hat um 08:22 Uhr einen weiteren Commit bekommen (`155ae39a`), während
   dieser Branch gebaut wurde. Dort arbeitet gerade jemand.

Sobald der Hauptcheckout frei ist, ist es ein Zweizeiler — der zweite Merge holt nur die
Commits, die inzwischen dazugekommen sind:

```bash
git -C /Users/thomas/Developer/privat/ready2race merge --no-ff claude/pwa-app-git-project-c4fa2a
```

Vorher `git rev-list --count feature/crf-2026 ^claude/pwa-app-git-project-c4fa2a` prüfen: Bei `0`
ist es ein Fast-Forward, sonst kommt ein weiterer Merge dazwischen und `LiveDashboardPage.tsx`
kann erneut kollidieren.

### 1.2 Eine Entscheidung, die ich nicht getroffen habe

Die Metatags und der Manifest-Link stehen in der gemeinsamen `index.html` und gelten damit auch
für `/board`, `/results` und die Verwaltungsoberfläche. Auf iOS könnte `/board` dadurch im
Vollbild zum Homescreen hinzugefügt werden. Für Chrome ist das entschärft, seit `start_url` und
`scope` beide auf `/app/` stehen — dort verlangt die Installierbarkeit zusätzlich einen
passenden Service Worker, und den gibt es außerhalb von `/app` nicht.

Zwei Wege: so lassen und in der MR-Beschreibung benennen, oder den Manifest-Link zur Laufzeit
nur im `/app`-Zweig einhängen. Ich habe nichts geändert, weil es eine Abwägung ist, keine
Fehlerbehebung.

## 2. Die Handprüfliste ist noch nicht abgearbeitet

Abschnitt 11 der Spezifikation, zehn Punkte, dazu neu: Nach einem Kaltstart ohne Netz und mit
gültiger Sitzung muss die Funktionsauswahl mit denselben Kacheln erscheinen wie online.

Nichts davon ist automatisiert prüfbar — Service Worker, Installation und Offline-Betrieb
brauchen ein echtes Gerät. Vor dem 14.08. einplanen. Die Punkte 1 bis 4 waren vor den
Korrekturen dieser Nacht sämtlich nicht bestehbar; sie sind jetzt plausibel, aber ungeprüft.

## 3. Was in dieser Nacht schiefging und wieder geradegezogen wurde

Der Vollständigkeit halber, weil es zeigt, wo die dünnen Stellen liegen:

- **Der Precache zeigte ins Leere.** Workbox schreibt relative Adressen und löst sie gegen den
  Ort des Workers auf. Unter `/app/` wurde aus `assets/index-x.js` also `/app/assets/index-x.js`.
  Die App wäre installierbar gewesen und offline trotzdem leer. Mein Kommentar im Plan behauptete
  das Gegenteil. Behoben, und der Build prüft es jetzt selbst.
- **Der Startwert aus dem Lese-Cache wurde beim Montieren sofort verworfen**, weil ein
  Bestands-`useEffect` auch beim ersten Rendern läuft. Offline dauerhaft leer, online ein roter
  Fehlerblitz bei jedem Öffnen.
- **Abmelden ohne Netz räumte nichts weg**, weil `userLogout()` wirft. Auf einem geteilten Tablet
  wären Token, Privilegien und Dashboard-Daten mit Klarnamen liegengeblieben.
- Dazu `start_url` außerhalb des Scopes, eine Frist, die sich von außerhalb der App verlängern
  ließ, ein Lese-Cache, der auch am Arbeitsplatzrechner lief, und die fehlende
  Zwischenspeicherung der Veranstaltungsliste.

Alle Einzelreviews waren sauber. Gefunden hat das erst das Schlussreview über den ganzen Branch —
die Fehler entstanden ausnahmslos zwischen den Aufgaben, nicht in ihnen.

## 4. Kleinigkeiten, die liegen bleiben dürfen

- Doppelter `<link rel="manifest">` in `dist/index.html`: `vite-plugin-pwa` fügt trotz
  `injectRegister: false` selbst einen ein, der handgeschriebene könnte weg.
- `ENTRY_ICONS` in `AppFunctionSelectPage` ist `Record<string, …>`; ein fehlender Schlüssel ergäbe
  `undefined`. `scannerLabels` daneben ist erschöpfend getippt, das wäre hier auch möglich.
- Drei kleine Testlücken im Lese-Cache: der Feldvergleich von Nutzerkennung und Veranstaltung ist
  durch den Schlüsselnamen verdeckt und isoliert ungetestet, kein Test für vollen Speicher, keiner
  für gültiges aber falsch geformtes JSON.
- `writeCachedRead` läuft bei jedem Poll und serialisiert das komplette Dashboard synchron.
- Der Verfall des Caches ist rein passiv: Einträge verschwinden beim Lesen oder Abmelden, nicht
  von selbst.

## 5. Für die MR-Beschreibung

Zwei Punkte brauchen ausdrücklich eine Entscheidung der lambda9-Entwickler:

1. **Der Sitzungstoken liegt für Helfer-Sitzungen dauerhaft auf dem Gerät** statt beim Schließen
   zu verschwinden, begrenzt auf sechs Stunden und auf `/app`. Begründung in Abschnitt 5 der
   Spezifikation. Ohne das steht der Schiedsrichter nach jedem Neustart der App wieder vor dem
   Anmeldebildschirm.
2. **Der Service Worker lebt in einem gemeinsamen Build**, auch wenn sein Scope auf `/app/`
   begrenzt ist.

Dazu der Transparenzhinweis auf die KI-Unterstützung, wie in Abschnitt 13 der Spezifikation
festgehalten.
