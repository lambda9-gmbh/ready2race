# Design: Poll-Datenvolumen des Schiedsrichter-Dashboards senken (A5.2–4)

**Stand:** 2026-08-02
**Backlog-Punkt:** A5 aus `2026-07-30-schiedsrichter-dashboard-backlog.md`, Ideen 2 bis 4
**Erledigt vorab:** A5.1 (gzip) — am laufenden Server gemessen: Faktor 15.

## Ausgangslage

Eine Antwort von `GET /event/{id}/liveDashboard` war unkomprimiert rund 100 KB (Seed: 5 Läufe,
30 Mannschaften, 150 Teilnehmer). Den größten Anteil daran haben die Teilnehmerdaten: für
**jede** Person jeder Mannschaft jedes Laufs gehen Name, Rolle, Jahrgang, Geschlecht, Verein,
Ummeldung und alle Teilnahmebedingungen mit Zeitstempel, Notiz und Zeitfenster-Prüfung über die
Leitung.

Die Liste zeigt davon zwei abgeleitete Dinge: ein Ampel-Icon je Mannschaft und ein
Ummeldungs-Symbol. Alles andere sieht erst, wer eine Mannschaft antippt.

## 1. Teilnehmerdaten aus dem Listen-Poll nehmen (A5.2)

`LiveDashboardTeamDto` verliert `participants` und bekommt stattdessen:

- `requirements: {total, fulfilled, missingRequired, missingOptional, timeIssues}`
- `substituted: Boolean`

Die Ampel wird weiterhin **im Frontend** aus diesen Zahlen abgeleitet, nach denselben Regeln wie
heute: Rot bei fehlender Pflichtbedingung oder offener Rechnung, Gelb bei einer Prüfung außerhalb
des Zeitfensters, Grau wenn nur Optionales fehlt, sonst Grün. Die Zahlen erlauben zusätzlich die
Anzeige „x von y erfüllt", die es bisher nicht gab.

Neu: `GET /event/{eventId}/liveDashboard/team/{teamId}` liefert `LiveDashboardTeamDetailDto` mit
den Teilnehmern in der heutigen Form. Der Dialog holt sie **einmal beim Öffnen**;
Teilnahmebedingungen werden am Zelt abgehakt und ändern sich während eines Laufs praktisch nicht.
Der Kopfbereich des Dialogs (Mannschaft, Platz, Zeit, Strafe) kommt weiter aus der Liste und
bleibt damit aktuell.

## 2. Unveränderte Antworten ohne Rumpf (A5.3)

Der Dashboard-Endpoint liefert einen `ETag`. Schickt der Client `If-None-Match` mit demselben
Wert, antwortet der Server mit **304** und ohne Rumpf.

Der ETag ist ein Hash über den serialisierten Rumpf, nicht über `max(updated_at)`. Ein
Zeitstempel-Kriterium müsste jede beteiligte Tabelle korrekt erfassen — Ergebnisse,
Bedingungen, Ummeldungen, Rechnungen — und wäre bei Löschungen still falsch. Der Hash ist es nie.
Die Datenbankabfrage läuft dabei weiterhin; gespart wird die Übertragung, und genau darum geht es
im Mobilfunknetz.

Umgesetzt als neue `ApiResponse.ETagged`-Variante, damit weitere Polling-Endpoints sie später
ohne Sonderweg nutzen können.

## 3. Nur laden, was der Tab braucht (A5.4)

Der Endpoint bekommt `scope=LIVE|ALL` (Standard `ALL`). `LIVE` liefert die laufenden Läufe und —
falls keiner läuft — den nächsten anstehenden.

Der Live-Tab pollt mit `LIVE`, der Läufe-Tab mit `ALL`. Beim Tabwechsel wird neu geladen und
solange die Ladeanzeige gezeigt; die Seite hält bewusst nur einen Datenstand.

Der Hinweispunkt am Live-Tab („es hat sich etwas getan") vergleicht die laufenden Läufe. Der
`ALL`-Stand des Läufe-Tabs enthält sie, der Vergleich funktioniert also unverändert.

## Poll-Takt

Bleibt bei 10 Sekunden mit den vorhandenen Optionen 5/10/30/60. Nach diesen drei Änderungen ist
nicht der Takt das Problem, sondern war es die Nutzlast.

## Wirkung

Nachgemessen am 2026-08-02 mit den Seed-Daten (5 Läufe, 30 Mannschaften, 150 Teilnehmer, drei
Bedingungen je Person), beide Nutzlasten aus denselben Daten aufgebaut:

| Antwort | roh | gzip |
|---|---:|---:|
| vorher, alle Läufe | 133.748 B | 19.857 B |
| nachher, alle Läufe | 11.116 B | 1.728 B |
| nachher, nur Live-Tab | 2.237 B | 712 B |

Ein Abruf im Live-Tab kostet damit rund 712 statt 19.857 Byte — Faktor 28 gegenüber dem Stand
nach gzip, Faktor 187 gegenüber dem ursprünglichen unkomprimierten Poll. Über drei Stunden im
10-Sekunden-Takt sind das etwa 0,8 MB statt der ursprünglich beobachteten 200 MB, und
unveränderte Abrufe fallen dank 304 noch einmal deutlich darunter.

## Tests

- Backend: `LiveDashboardLogic` bekommt die Verdichtung der Bedingungen als reine Funktion, dazu
  Tests für Pflicht/Optional/Zeitfenster. ETag-Verhalten (200 mit ETag, 304 bei gleichem
  `If-None-Match`) als Ktor-Test ohne Datenbank, analog zum Kompressionstest.
- Frontend: Vitest für die Ampel-Ableitung aus den neuen Zahlen.

## Nicht Teil dieser Änderung

- Poll-Takt ändern.
- Detaildaten im Dialog mitpollen.
