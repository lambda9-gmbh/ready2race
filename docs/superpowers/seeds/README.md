# Seed-Skripte für den Teststand

SQL-Seeds, mit denen sich einzelne Testkatalog-Fälle in der Dev-Datenbank nachstellen lassen,
ohne die Oberfläche von Hand zu befüllen.

## Konventionen

Jeder Seed folgt denselben Regeln, damit mehrere Seeds nebeneinander in derselben Datenbank
liegen können, ohne sich zu stören:

- **Eigener UUID-Präfix für alle Zeilen.** Jede vom Seed erzeugte Zeile beginnt mit den vier
  Hex-Zeichen des Seeds. Damit ist jederzeit erkennbar, wem eine Zeile gehört.
- **Cleanup-Block ganz vorn.** Der Seed löscht zuerst seine eigenen alten Zeilen — ausschließlich
  den eigenen Präfix, in FK-Reihenfolge (Kinder vor Eltern). Dadurch ist jeder Seed beliebig oft
  wiederholbar, und fremde Daten bleiben unangetastet.
- **`set search_path` und `set time zone 'Europe/Berlin'`** am Anfang: die Spalten sind
  `timestamp without time zone`, der Server läuft auf UTC, die Anwendung rechnet in Berlin.
- **Kopfkommentar**, der den Aufbau und die getesteten Fälle beschreibt.

## Belegte Präfixe

| Präfix | Seed | Wofür |
| --- | --- | --- |
| `5eed` | `seed-zeitstrahl.sql` (nicht im Repo) | Grundstand Zeitstrahl / Live-Dashboard |
| `f0de` | `seed-foerde.sql` (nicht im Repo) | Große Regatta über zwei Renntage, volle Rundenkette |
| `fee1` | `seed-freilos.sql` (nicht im Repo) | Freilos-Fälle |
| `a4f1` | [`2026-08-06-seed-auflagen.sql`](2026-08-06-seed-auflagen.sql) | Auflagen (D6), Ersatzleute (D7), Renngemeinschaften (G6) |
| `c4a1` | [`2026-08-06-seed-challenge.sql`](2026-08-06-seed-challenge.sql) | Challenge-Event für die Teilnahmeurkunden (G15, G16, G18, G22) |

Ein neuer Seed nimmt einen bisher unbenutzten Präfix und trägt sich hier ein.

> **Ablage:** Die älteren Seeds liegen in einem gitignorierten Worktree unter
> `.claude/worktrees/zeitstrahl/.superpowers/sdd/` und sind dort schwer zu finden. Neue Seeds
> liegen deshalb hier im Repo. Ob die älteren nachziehen, entscheidet Thomas.

## Einspielen

Dev-Datenbank starten (`cd backend && docker compose up -d`), dann:

```sh
docker exec -i backend-db-1 psql -U developer -d ready2race -v ON_ERROR_STOP=1 --single-transaction \
  < docs/superpowers/seeds/2026-08-06-seed-auflagen.sql
```

`--single-transaction` sorgt dafür, dass bei einem Fehler nichts halb Eingespieltes zurückbleibt;
`ON_ERROR_STOP=1` bricht beim ersten Fehler ab statt weiterzulaufen.

Der Seed darf mehrfach hintereinander eingespielt werden — der Cleanup-Block räumt den eigenen
Präfix vorher weg. Das gilt auch, nachdem in der Oberfläche damit gearbeitet wurde: QR-Codes,
Anwesenheits-Scans und Urkunden-Jobs zu den eigenen Personen werden mit entfernt.

## `2026-08-06-seed-auflagen.sql` (Präfix `a4f1`)

Eine kleine Regatta mit einem Renntag, einem Wettkampf (Coastal Mixed Doppelvierer mit
Steuerperson), zwei Runden (Vorlauf → Finale) und vier Booten. Beide Runden sind bereits
materialisiert, der Vorlauf läuft. Deckt ab:

- **D6 Auflagen** — alle vier Zustände nebeneinander an Boot „Nordwind 1“: erfüllte Pflicht,
  fehlende Pflicht, fehlende optionale, sowie eine Auflage mit verletztem Zeitfenster. Eine
  Auflage ist rollenbezogen (nur Steuerperson).
- **D7 Ersatzleute** — eine Auswechslung mit Grund an Boot „Schleiwind 1“, in die Folgerunde
  vererbt.
- **G6 Renngemeinschaften** — Boot „RG Nordwind/Schleiwind“ mit Crew aus zwei Vereinen; das Event
  setzt `mixed_team_term = 'Renngemeinschaft'`.

Alle Zeitstempel stehen fest auf dem 06.08.2026 im Skript; ein `update`-Block am Ende zieht den
Renntag auf `current_date` und verschiebt alle Zeiten um denselben Versatz, sodass der Vorlauf
zwölf Minuten vor dem Einspielen gestartet ist. Die im Kopfkommentar gerechneten
Zeitfenster-Abstände bleiben dabei exakt erhalten. Am besten tagsüber einspielen — mitten in der
Nacht rutschen die frühen Programmpunkte rechnerisch auf den Vortag.

## `2026-08-06-seed-challenge.sql` (Präfix `c4a1`)

Die „Winter-Challenge 2026“ — das erste Challenge-Event in der Dev-Datenbank. Ohne eins lässt
sich die Teilnahmeurkunde nicht erzeugen (`CertificateService` antwortet mit „Event is not a
challenge event“), deshalb waren G15, G16, G18 und G22 bislang ungeprüft. Zwei Wettkämpfe
(Einer und Doppelzweier) mit abgelaufenem Wertungszeitraum, zwei Vereinen und fünf Personen:

- **drei Personen mit bestätigtem Ergebnis** — eine davon (Mette Kjærgaard) mit Ergebnissen aus
  beiden Wettkämpfen, damit die Summenbildung der Urkunde sichtbar wird (38500 + 52400 =
  90900 m).
- **eine Person mit unbestätigtem Ergebnis** (Antje Duschek) — sie fällt heraus, weil das Event
  auf `submission_needs_verification = true` steht. Flag umstellen, und sie kommt dazu: so sind
  beide Zweige der Abfrage in `ChallengeResultParticipantViewRepo` prüfbar.
- **eine Person ohne Ergebnis** (Ruben Ostermann) für den Fehlerfall `NoResults`.
- Namen und Vereine mit `æ`, `ø` und `ś` für den PDF-Sanitizer.

Die Zeitstempel stehen fest in der Vergangenheit, ein Versatz-Block wie im `a4f1`-Seed ist hier
nicht nötig: geprüft wird nur, ob `challenge_end_at` vor „jetzt“ liegt, und es gibt keinen
laufenden Lauf. Der Seed lässt sich also zu jeder Tageszeit einspielen.

Die Vorlage vom Typ `CERTIFICATE_OF_PARTICIPATION` liegt bereits in der Datenbank und wird vom
Seed nicht angefasst.
