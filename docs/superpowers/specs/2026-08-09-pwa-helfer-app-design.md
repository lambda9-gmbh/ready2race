# Design: Helfer-App unter `/app` als PWA, mit Schiedsrichter-Dashboard

**Stand:** 2026-08-09
**Status:** Design abgenommen, Implementierung ausstehend
**Kontext:** Die Helfer-App ist heute ein mobil optimierter Zweig der bestehenden SPA
(`frontend/src/pages/app/`, Route `appRoute` in
[routes.tsx:398](../../../frontend/src/routes.tsx)). Sie soll installierbar werden, einen
Kaltstart ohne Netz überstehen und das Schiedsrichter-Dashboard mit aufnehmen. Kein Zugriff auf
das Hosting — alles muss aus dem Build kommen.

---

## 1. Problem

Die Helfer-App ist keine App. Sie ist eine URL, die man sich merken muss, ohne Symbol auf dem
Homescreen, ohne Vollbild, und bei jedem Kaltstart lädt sie das gesamte Bundle neu. Gemessen am
07./09.08.2026 gegen `rkf.ready2race.info`: **3.064.283 Bytes, unkomprimiert ausgeliefert** —
Caddy setzt kein `content-encoding`, auch wenn der Client gzip und br anbietet. Am Steg über
Mobilfunk ist das der Unterschied zwischen „geht" und „geht nicht".

Zwei weitere Löcher, die dieses Vorhaben mit schließt:

- **Das Schiedsrichter-Dashboard ist nur über die Verwaltungsoberfläche erreichbar.** Die Route
  `/event/$eventId/liveDashboard` hängt am `mainLayoutRoute`, also an Kopfleiste und
  Seitenleiste. Auf dem Telefon ist das unbrauchbar, obwohl die Seite selbst längst responsiv
  ist ([LiveDashboardPage.tsx:100](../../../frontend/src/pages/event/LiveDashboardPage.tsx)
  schaltet unterhalb `md` einspaltig um, mit `BottomNavigation`).
- **Die Sitzung stirbt mit dem App-Prozess.** Der Token liegt in `sessionStorage`. Serverseitig
  läuft die Sitzung sechs Stunden *gleitend* — `useSessionToken` setzt bei jedem
  authentifizierten Request `expiresAt = tokenLifetime.afterNow()`
  ([AuthService.kt:80](../../../backend/src/main/kotlin/de/lambda9/ready2race/backend/app/auth/boundary/AuthService.kt),
  `tokenLifetime = 6.hours` in Zeile 21). Wer das Telefon eine Stunde weglegt, steht trotzdem
  wieder vor dem Anmeldebildschirm, sobald das Betriebssystem die App aus dem Speicher geworfen
  hat. Das ist ein reines Frontend-Problem.

## 2. Zielbild

`/app` wird eine installierbare PWA mit eigenem Symbol, Start im Vollbild und einem Precache, der
den Kaltstart vom Netz entkoppelt. Das Schiedsrichter-Dashboard wird ein Eintrag der
Funktionsauswahl — sichtbar für jeden, der `readLiveDashboardGlobal`
([privileges.ts:150](../../../frontend/src/authorization/privileges.ts)) besitzt — und läuft im
App-Layout ohne Verwaltungs-Chrome. Ohne Netz zeigt es den zuletzt geladenen Stand mit
Zeitstempel und gesperrten Aktionen. Die Helfer-Sitzung übersteht einen App-Neustart innerhalb
des Sechs-Stunden-Fensters, das der Server ohnehin gewährt.

Alles außerhalb von `/app` bleibt unangetastet: Verwaltungsoberfläche, `/results` und `/board`
bekommen keinen Service Worker und kein verändertes Sitzungsverhalten.

## 3. Nicht Teil dieses Vorhabens

- **Offline-Erfassung mit Queue.** Scans und Dashboard-Aktionen brauchen Netz. Eine
  IndexedDB-Queue verlangt Idempotenz im Backend, sonst erzeugt jeder Wiederholungsversuch
  doppelte Check-ins.
- **Dynamisches Manifest pro Regatta.** Name, Farben und Logos kommen zur Laufzeit aus
  `theme.json`; das Manifest ist statisch und zeigt R2R. Ein gebrandetes Manifest bräuchte einen
  Backend-Endpoint.
- **Code-Splitting gegen die 3-MB-Ladung.** Der größte Einzelgewinn für Telefonnutzer, aber ein
  Eingriff quer durch die Anwendung.
- **`encode gzip` im Caddyfile.** Einzeiler, aber am Hosting, auf das wir keinen Zugriff haben.

## 4. Architektur

### 4.1 Service Worker und Scope

Der Scope eines Service Workers leitet sich aus seinem Ablageort ab. Die Datei wird deshalb nach
`app/sw.js` gebaut und bekommt damit Scope `/app/` — ohne den `Service-Worker-Allowed`-Header,
für den Hosting-Zugriff nötig wäre. Verwendet wird `vite-plugin-pwa` in der Betriebsart
`injectManifest`, weil eigener Service-Worker-Code gebraucht wird: Navigations-Allowlist,
Cache-Regeln und Kill-Switch.

Registriert wird ausschließlich aus dem `/app`-Zweig: `injectRegister: false` in der
Plugin-Konfiguration, dazu ein Hook `useRegisterAppSW`, den
[AppLayout.tsx](../../../frontend/src/layouts/AppLayout.tsx) aufruft, mit explizitem
`{scope: '/app/'}`. Wer nie `/app` öffnet, bekommt nie einen Service Worker.

### 4.2 Manifest und Icons

`app/manifest.webmanifest` mit `start_url: '/app'`, `scope: '/app/'`, `display: 'standalone'`,
`name: 'Ready2Race'`, `short_name: 'R2R'`, `theme_color: '#4d9f85'` (der R2R-Standard aus
[ThemeConfigDto.kt:28](../../../backend/src/main/kotlin/de/lambda9/ready2race/backend/app/globalConfigurations/entity/ThemeConfigDto.kt)).
Kein `lang`, weil die App Deutsch, Englisch und Dänisch kann.

Neue Icons unter `frontend/public/app/`: `icon-192.png`, `icon-512.png`,
`icon-maskable-512.png` (mit Sicherheitsrand, sonst beschneidet Android das Logo) und
`apple-touch-icon.png`. Quelle ist `frontend/public/r2r_logo.png` (660×451); das Logo wird
zentriert auf quadratischen Grund gesetzt, nicht verzerrt. Die zugehörigen Metatags fehlen heute
komplett in [index.html](../../../frontend/index.html) und kommen dort hinzu.

### 4.3 Precache und Navigations-Routing

Precached wird, was der Build ausliefert: Bundle, CSS, Fonts, Icons. Die `index.html` wird
**nicht** precached, sondern über eine `NavigationRoute` mit `NetworkFirst` bedient. Allowlist
`/^\/app\//`, Denylist `/^\/api\//` und `/^\/static\//`. Damit laufen Verwaltungsoberfläche,
`/results` und `/board` weiter unverändert gegen das Netz.

Die API bleibt für den Service Worker netzwerk-only. Begründung siehe Abschnitt 6.

## 5. Sitzung über App-Neustarts hinweg

Ein Modul `frontend/src/contexts/user/sessionToken.ts` kapselt Lesen, Schreiben und Löschen des
Tokens. Für Helfer-Sitzungen — die Unterscheidung `isInApp` existiert in
[UserProvider.tsx](../../../frontend/src/contexts/user/UserProvider.tsx) bereits — liegt der
Token samt `lastUsedAt` in `localStorage`; sonst wie bisher in `sessionStorage`. Der vorhandene
Response-Interceptor frischt `lastUsedAt` auf; beim Lesen wird ein Eintrag verworfen, der älter
als sechs Stunden ist. Damit spiegelt der Client exakt das Serververhalten aus Abschnitt 1.

Betroffen sind vier Stellen in `UserProvider.tsx`: der Initialwert (Zeile 46), das Verwerfen bei
ungültiger Sitzung (119), das Setzen nach der Anmeldung (142) und das Abmelden (158).

**Bewusst in Kauf genommen:** Der Token liegt für Helfer-Sitzungen dauerhaft auf dem Gerät statt
beim Schließen zu verschwinden. Auf einem geteilten Tablet findet der Nächste die Sitzung des
Vorherigen vor, solange niemand abmeldet, und bei einer XSS-Lücke ist das Zeitfenster größer.
Abgefedert durch die Sechs-Stunden-Grenze, die Beschränkung auf `/app` und den vorhandenen
Abmelden-Knopf. Die Alternative — ein Refresh-Token im Backend — ist die sauberere Lösung, aber
Migration plus neue Endpunkte und damit ein eigenes Vorhaben. Diese Abwägung gehört sichtbar in
die MR-Beschreibung.

## 6. Lese-Cache und Veraltet-Verhalten

Der Lese-Cache liegt in der App-Schicht, **nicht** im Service Worker. Ein SW-Runtime-Cache auf
die API würde Teilnehmerdaten mit Klarnamen und Jahrgängen in der CacheStorage ablegen, auf
Geräten, die sich mehrere Leute teilen; er müsste beim Abmelden gezielt geleert werden und würde
im ungünstigen Fall den Stand des vorigen Nutzers ausliefern. Außerdem braucht die Oberfläche den
Zeitpunkt des letzten erfolgreichen Abrufs, und der ist aus einer Cache-Antwort nur umständlich
zu gewinnen.

`frontend/src/pwa/readCache.ts`: Jeder erfolgreiche Abruf von `getLiveDashboard` und `getEvents`
legt `{payload, fetchedAt, userId, eventId}` unter einem aus `userId` und `eventId` gebildeten
Schlüssel ab. Schlägt ein Abruf fehl, liest die Seite den letzten Stand zurück. Verworfen wird
er bei einem Alter über zwölf Stunden (ein Regattatag), bei abweichender `userId` und beim
Abmelden — dafür hängt sich das Modul an `logout`, wo heute schon
`sessionStorage.removeItem('session')` steht.

Die zwölf Stunden liegen bewusst über dem Sechs-Stunden-Fenster der Sitzung aus Abschnitt 5: Der
Cache ist damit nie der begrenzende Faktor. Überlebt er die Sitzung, ist er ohne Belang, weil
ohne gültige Sitzung ohnehin der Anmeldebildschirm kommt.

Solange ein veralteter Stand angezeigt wird, nennt ein Banner Datum und Uhrzeit des letzten
echten Abrufs, und die schreibenden Aktionen des Dashboards sind nicht bedienbar: „Lauf
starten", „Lauf beenden", das Aktivieren der nächsten Startzeit, das Freigeben des
RaceClocker-Abrufs und „Slot überspringen". Ein Schiedsrichter soll sehen, was er zuletzt
wusste, aber nichts auf veralteter Grundlage auslösen.

Umgesetzt wird das über den vorhandenen Mechanismus: Die fünf Handlungen werden der Ansicht
heute schon nur übergeben, wenn die Nutzerin steuern darf, und die Karten blenden ihre Knöpfe
daran aus (siehe den Vertrag in `LiveDashboardColumns.tsx`). Bei veraltetem Stand entfallen sie
auf demselben Weg. Den Grund trägt das Banner, nicht ein Tooltip an jedem einzelnen Knopf.

**Reichweite:** Der Lese-Cache hilft im laufenden Betrieb — Bildschirm aus, App im Hintergrund,
Funkloch auf dem Weg zum Steg. Nach einem Kaltstart ohne Netz greift er nur, wenn die Sitzung
nach Abschnitt 5 noch gültig ist; ohne gültige Sitzung steht der Nutzer vor dem
Anmeldebildschirm, und dort hilft kein Cache.

## 7. Schiedsrichter-Dashboard in der App

`LiveDashboardPage` liest `eventId` heute fest über `eventLiveDashboardRoute.useParams()`
([Zeile 95](../../../frontend/src/pages/event/LiveDashboardPage.tsx)). Diese Kopplung wird
aufgelöst: Die Komponente nimmt `eventId` als Prop, zwei dünne Wrapper montieren sie unter der
bestehenden Verwaltungsroute und unter der neuen Route `/app/dashboard`. Die App-Variante liegt
im `AppLayout` und zieht die `eventId` aus dem `AppSessionContext`, den die Event-Auswahl schon
füllt.

Die Rechteprüfung nutzt denselben Mechanismus wie die Verwaltungsroute: `/app` hat keine eigene
Anmeldung, `checkAuthApp` ([routes.tsx:86](../../../frontend/src/routes.tsx)) prüft nur
`context.loggedIn` gegen denselben `User`-Context. Ein in der App angemeldeter Schiedsrichter
trägt seine vollen Privilegien mit sich.

**Begriffe sauber trennen.** `AppFunction` bleibt der Union der Scanner-Funktionen — er wird
auch von [QrScannerPage.tsx:82](../../../frontend/src/pages/app/QrScannerPage.tsx) benutzt, um
das Scanner-Verhalten abzuleiten, und darf keinen Eintrag bekommen, mit dem der Scanner nichts
anfangen kann. Stattdessen entsteht in `components/qrApp/common.ts` ein `appEntries(user)`, das
die Kacheln der Auswahlseite liefert: die Scanner-Funktionen aus `getUserAppRights` plus, bei
`readLiveDashboardGlobal`, den Eintrag „Schiedsrichter-Dashboard" mit eigenem Ziel.
[AppFunctionSelectPage.tsx](../../../frontend/src/pages/app/AppFunctionSelectPage.tsx) schickt
heute jede Kachel zum Scanner; künftig trägt jeder Eintrag sein Ziel. Die Weiterleitung auf
`APP_Forbidden` greift nur noch, wenn beide Listen leer sind — sonst landet ein Schiedsrichter
ohne `APP_*`-Rechte fälschlich dort.

## 8. Updates und Kill-Switch

Der Service Worker meldet neue Versionen an die App; die zeigt eine Snackbar „Neue Version
verfügbar — jetzt laden" über das vorhandene `notistack`-Setup. Kein stilles `skipWaiting`:
Mitten im Rennbetrieb soll sich die Oberfläche nicht unter den Händen austauschen.

Der Kill-Switch hat zwei Ebenen. In der App ein Schalter unter der Funktionsauswahl, der
Registrierung und Caches löscht und neu lädt — für ein einzelnes klemmendes Gerät. Im Repo
zusätzlich `src/pwa/swKill.ts`, eine Notfallvariante, die nichts tut außer sich selbst zu
deregistrieren und alle Caches zu leeren; sie wird nur eingespielt, wenn eine Version
flächendeckend Ärger macht. Beides funktioniert ohne Serverzugriff.

**Fehlerfälle.** Registrierung schlägt fehl (kein HTTPS, Browser ohne Unterstützung, iOS im
privaten Modus) → die App läuft unverändert weiter, ohne Offline, ohne Fehlerdialog. Precache
schlägt fehl → der neue Service Worker installiert sich nicht, der alte bleibt aktiv. Kein Netz
und kein Cache-Eintrag → die bestehende Fehlerdarstellung der Seite, ergänzt um einen
Offline-Hinweis.

## 9. Betroffene Dateien

**Neu:**

- `frontend/src/pwa/sw.ts` — Service Worker (injectManifest)
- `frontend/src/pwa/swKill.ts` — Notfallvariante
- `frontend/src/pwa/registerAppSW.ts` — Registrierung samt Update-Snackbar
- `frontend/src/pwa/readCache.ts` + `readCache.test.ts`
- `frontend/src/contexts/user/sessionToken.ts` + `sessionToken.test.ts`
- `frontend/src/pages/app/AppDashboardPage.tsx` — Wrapper
- `frontend/public/app/icon-192.png`, `icon-512.png`, `icon-maskable-512.png`,
  `apple-touch-icon.png`

**Geändert:**

- `frontend/vite.config.ts`, `frontend/package.json` — Plugin und Konfiguration
- `frontend/index.html` — iOS-Metatags, `theme-color`
- `frontend/src/routes.tsx` — Route `appDashboardRoute`
- `frontend/src/pages/event/LiveDashboardPage.tsx` — `eventId` als Prop, Veraltet-Banner,
  gesperrte Aktionen
- `frontend/src/pages/app/AppFunctionSelectPage.tsx` — Kacheln aus `appEntries`, Ziel pro Eintrag
- `frontend/src/components/qrApp/common.ts` — `appEntries`
- `frontend/src/contexts/app/AppSessionContext.tsx` — neue `AppView` samt Pfad
- `frontend/src/contexts/user/UserProvider.tsx` — vier Stellen auf `sessionToken` umgestellt
- `frontend/src/layouts/AppLayout.tsx` — Registrierung
- `frontend/src/i18n/{de,en,da}/translations.json` — neue Texte

## 10. Tests

Der Bestand testet reine Logik mit vitest, keine Komponenten; dem folgt dieses Vorhaben.

- **`readCache`**: Alter über der Grenze, abweichende `userId`, anderes Event, kaputter
  JSON-Inhalt, leerer Speicher.
- **`sessionToken`**: App- gegen Verwaltungs-Ablage, Sechs-Stunden-Grenze, Auffrischen von
  `lastUsedAt`, Löschen beim Abmelden.
- **Banner-Zustand**: Ableitung aus Abrufzeitpunkt und Fehlerlage, inklusive der Frage, ob
  Aktionen gesperrt sind.
- **`appEntries`**: alle Rechtekombinationen, insbesondere „nur Dashboard-Recht, keine
  Scanner-Rechte" — der Fall, der heute fälschlich auf `APP_Forbidden` führt.

## 11. Handprüfliste

Vor dem 14.08. abzuarbeiten, weil Tests das nicht abdecken:

1. Installation auf Android (Chrome) und iOS (Safari): Symbol, Name, Start im Vollbild.
2. Kaltstart im Flugmodus bei gültiger Sitzung: App startet, Dashboard zeigt letzten Stand mit
   Zeitstempel, Aktionen gesperrt.
3. Kaltstart im Flugmodus ohne gültige Sitzung: Anmeldebildschirm mit verständlichem Hinweis,
   kein Absturz.
4. Netz während laufender Sitzung trennen und wiederherstellen: Banner erscheint und verschwindet,
   Aktionen werden wieder freigegeben.
5. Telefon eine Stunde weglegen, App war zwischenzeitlich aus dem Speicher: weiterhin angemeldet.
6. Neue Version einspielen: Snackbar erscheint, Neuladen übernimmt die neue Fassung.
7. Kill-Switch in der App: Registrierung und Caches weg, App läuft ohne Service Worker weiter.
8. Nachweis des Scopes: Verwaltungsoberfläche und `/board` in denselben Browser laden und prüfen,
   dass dort kein Service Worker registriert ist.
9. Schiedsrichter mit ausschließlich `readLiveDashboardGlobal`: sieht die Dashboard-Kachel, landet
   nicht auf `APP_Forbidden`.
10. Abmelden auf einem geteilten Gerät: Token und Lese-Cache sind weg.

## 12. Risiken

- **Ein fehlerhafter Service Worker lässt sich ohne Serverzugriff nur durch einen neuen Build
  ersetzen.** Deshalb Kill-Switch und Update-Snackbar in der ersten Fassung statt später.
- **Der Token auf dem Gerät** (Abschnitt 5) ist eine Sicherheitsentscheidung, die lambda9
  mittragen muss.
- **Der Precache enthält auch Verwaltungscode**, weil Build und Bundle gemeinsam sind. Kostet
  Bytes, schadet aber nicht; die Alternative wäre ein zweiter Vite-Entry mit dauerhaftem
  Wartungsaufwand.

## 13. Transparenzhinweis für den MR

Dieses Vorhaben ist KI-unterstützt entstanden und als Vorschlag mit Lastenheft-Charakter an die
lambda9-Entwickler gedacht. Der Hinweis gehört in die MR-Beschreibung. Zwei Punkte brauchen dort
ausdrücklich eine Entscheidung des Teams: die Token-Ablage aus Abschnitt 5 und der
Service-Worker-Scope, der zwar auf `/app/` begrenzt ist, aber in einem gemeinsamen Build lebt.
