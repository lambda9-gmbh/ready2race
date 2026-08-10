# Testplan: Helfer-App als PWA

**Stand:** 2026-08-10 · **Branch:** `claude/pwa-app-git-project-c4fa2a` (enthält `feature/crf-2026` vollständig)
**Spezifikation:** `2026-08-09-pwa-helfer-app-design.md` · **Übergabe:** `2026-08-10-pwa-helfer-app-uebergabe.md`

Automatisiert abgedeckt sind 637 Tests (reine Logik), `tsc -b` und ein Build, der die
Precache-Einträge selbst prüft. **Alles, was hier steht, ist damit nicht abgedeckt** — Service
Worker, Installation, Offline-Verhalten und Sitzungsdauer brauchen ein echtes Gerät.

Voraussetzung für alles: **HTTPS**. Auf `localhost` läuft ein Service Worker auch ohne, im WLAN
über die IP-Adresse nicht.

---

## A — Installation

| # | Schritt | Erwartet |
|---|---|---|
| A1 | `/app` in Chrome auf Android öffnen, „Zum Startbildschirm hinzufügen" | Symbol mit R2R-Logo, Name „R2R", Start im Vollbild ohne Adressleiste |
| A2 | Dasselbe in Safari auf iOS | Symbol vorhanden (nicht der graue Platzhalter), Start im Vollbild |
| A3 | Symbol auf Android prüfen | Logo wird nicht angeschnitten — die maskierbare Fassung hat Rand |
| A4 | Aus der installierten App heraus zu `/dashboard` navigieren | bleibt im Vollbild, springt nicht in den Browser |

**A5 — der wichtigste Punkt des ganzen Plans.** Verwaltungsoberfläche (`/dashboard`), `/results`
und `/board` im selben Browser aufrufen, dann in den Entwicklerwerkzeugen unter *Application →
Service Workers* nachsehen: **Dort darf kein Service Worker registriert sein.** Der Scope ist
`/app/`, und das ist die Zusage an die lambda9-Entwickler. Wenn hier einer auftaucht, ist der
Ablageort der Datei kaputt und der ganze Ansatz hinfällig.

## B — Offline

| # | Schritt | Erwartet |
|---|---|---|
| B1 | App online öffnen, Dashboard laden, Flugmodus an, App schließen und neu öffnen | App startet, Funktionsauswahl erscheint mit denselben Kacheln wie online |
| B2 | Im Flugmodus das Dashboard öffnen | Letzter Stand sichtbar, gelbes Banner „Stand von TT.MM.JJJJ, HH:MM", **alle** Aktionsknöpfe verschwunden |
| B3 | Flugmodus aus, warten | Banner verschwindet, Knöpfe kommen zurück |
| B4 | Ohne vorherigen Abruf im Flugmodus öffnen | Verständlicher Hinweis, kein Absturz, keine Panikseite |
| B5 | Ohne gültige Sitzung im Flugmodus öffnen | Anmeldebildschirm mit Hinweis, kein weißer Schirm |

B2 ist der sicherheitsrelevante Fall: Auf veralteter Grundlage darf sich kein Lauf starten,
beenden, aktivieren oder überspringen lassen. Nach dem Merge sind es **fünf** Aktionen —
„Lauf beenden", „an den Start", „läuft", „RaceClocker-Abruf freigeben" und
„Slot überspringen". Alle fünf müssen weg sein, nicht nur die ersten drei.

## C — Sitzung

| # | Schritt | Erwartet |
|---|---|---|
| C1 | In der App anmelden, Telefon eine Stunde weglegen, App war zwischenzeitlich aus dem Speicher | weiterhin angemeldet, keine erneute Anmeldung |
| C2 | Nach über sechs Stunden Untätigkeit öffnen | Anmeldebildschirm — die Frist greift |
| C3 | In der App abmelden, dann Entwicklerwerkzeuge → *Application → Local Storage* | `session.app`, `session.user`, `eventId`, `appFunction` und alle `r2r.readCache.*` sind weg |
| C4 | **Im Flugmodus** abmelden | dasselbe Ergebnis wie C3 — das ist der Fall, der vorher nicht funktioniert hat |
| C5 | In der Verwaltungsoberfläche in einem zweiten Tab abmelden | Die Helfer-Sitzung im ersten Tab bleibt bestehen |
| C6 | Verwaltungsoberfläche allein benutzen, `sessionStorage` prüfen | Token liegt dort wie bisher und verschwindet beim Schließen des Tabs |

C3 und C4 sind der Datenschutzteil: Auf einem geteilten Tablet liegen Token, Privilegien und
zwischengespeicherte Teilnehmerdaten mit Klarnamen auf dem Gerät.

## D — Rechte

| # | Schritt | Erwartet |
|---|---|---|
| D1 | Nutzer mit ausschließlich `READ LIVE_DASHBOARD`, ohne jedes `APP_*`-Recht | sieht die Dashboard-Kachel, landet **nicht** auf der Verbotsseite |
| D2 | Nutzer mit nur `APP_*`-Rechten | sieht seine Scanner-Kacheln, keine Dashboard-Kachel |
| D3 | Nutzer mit beidem | sieht alles, jede Kachel führt an ihr eigenes Ziel |
| D4 | Nutzer ganz ohne Rechte | Verbotsseite wie bisher |
| D5 | Scanner-Kachel anklicken | führt zum Scanner, nicht zum Dashboard — die Trennung der Begriffe hält |

## E — Betrieb

| # | Schritt | Erwartet |
|---|---|---|
| E1 | Neue Fassung einspielen, App offen lassen | Snackbar „Neue Version verfügbar" mit Knopf „Jetzt laden" |
| E2 | Auf „Jetzt laden" tippen | Seite lädt neu und zeigt die neue Fassung |
| E3 | Snackbar ignorieren und weiterarbeiten | Oberfläche tauscht sich **nicht** von selbst aus |
| E4 | „App zurücksetzen" in der Funktionsauswahl | Nach Bestätigung: Registrierung und Caches weg, App läuft ohne Service Worker weiter, Anmeldung bleibt |
| E5 | Nach E4 die App neu laden | Service Worker registriert sich wieder |

## F — Nach dem Merge besonders prüfen

Der Branch hat `feature/crf-2026` aufgenommen; ein Konflikt in `LiveDashboardPage.tsx` wurde von
Hand aufgelöst. Deshalb zusätzlich am Laptop, in der **Verwaltungsoberfläche**:

| # | Schritt | Erwartet |
|---|---|---|
| F1 | `/event/…/liveDashboard` öffnen | Dashboard wie gewohnt, Kurzbezeichnungen lassen sich umschalten |
| F2 | Alle fünf Aktionen durchspielen | funktionieren unverändert |
| F3 | Entwicklerwerkzeuge → *Local Storage* am Arbeitsplatzrechner | **kein** `r2r.readCache.*` — der Lese-Cache läuft nur in der App |
| F4 | Netz kurz trennen, während das Dashboard offen ist | Verhalten wie vor diesem Vorhaben, kein Startwert aus dem Cache |

## G — Was ein Review am Quelltext ansehen sollte

Für die abschließende Durchsicht, die Fable übernimmt:

1. **`frontend/vite.config.ts`** — der Umzugs-Hook ist die tragende Konstruktion des ganzen
   Vorhabens. Er verschiebt den Service Worker nach `dist/app/`, macht die Precache-Adressen
   absolut und bricht den Build ab, wenn ein Eintrag ins Leere zeigt. Genau hier steckten zwei
   der drei kritischen Fehler.
2. **`frontend/src/contexts/user/UserProvider.tsx`** — Reihenfolge beim Abmelden (erst Gerät,
   dann Server), der auf `TypeError` eingegrenzte Panikpfad, und die Bindung von
   `touchSessionToken` an `/app`.
3. **`frontend/src/pages/event/LiveDashboardPage.tsx`** — die von Hand aufgelöste Merge-Stelle,
   die Sperre der fünf Aktionen, und die `scopeRef`-Sperre, die den Startwert aus dem Cache
   überleben lässt.
4. **`frontend/src/contexts/app/AppSessionContext.tsx`** — der Wechsel von `sessionStorage` auf
   `localStorage` für Veranstaltung und Aufgabe, und die Zwischenspeicherung der
   Veranstaltungsliste.

Offene Abwägung, bewusst nicht entschieden: Die Metatags und der Manifest-Link stehen in der
gemeinsamen `index.html` und gelten damit auch für `/board` und `/results`. Siehe Abschnitt 1.2
der Übergabe.
