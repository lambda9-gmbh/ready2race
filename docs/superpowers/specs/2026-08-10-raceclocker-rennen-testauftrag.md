# Testauftrag: Benannte RaceClocker-Rennen

**Stand:** 2026-08-10
**Zweig:** `claude/raceclocker-polling-optimization-d69666` (crf-2026 eingemergt)
**Spec:** [2026-08-09-raceclocker-rennen-design.md](2026-08-09-raceclocker-rennen-design.md)
**Regatta:** 14.08.2026 — vier Tage

Was automatisch geprüft ist, steht in §5. Dieses Dokument listet, was **nur ein Mensch** prüfen kann,
in der Reihenfolge, in der es am Renntag weh täte.

---

## 1. Vor allem anderen: die Migration gegen echte Daten

**Das ist der einzige Punkt, der einen Merge blockieren sollte.** Alles andere lässt sich am Renntag
notfalls umgehen; eine Migration, die eine Zuordnung verliert, nicht.

Die Migration ist bisher nur gegen **konstruierte** Altdaten gelaufen (sieben Fälle, alle korrekt —
siehe §5). Ein Abzug von CRF 2026 stand mir nicht zur Verfügung: dafür braucht es das Session-Token
aus dem Browser.

**Ablauf:**

1. Prod-Abzug ziehen (Weg siehe die Skripte unter `.claude/`), in eine leere lokale Datenbank spielen.
2. **Vorher** festhalten, was da ist:
   ```sql
   select count(*) from event
    where raceclocker_tt_results_url is not null or raceclocker_heats_results_url is not null;
   select count(*) from competition
    where raceclocker_tt_results_url is not null or raceclocker_heats_results_url is not null;
   ```
3. Flyway laufen lassen (beide Migrationen: `V202608101000`, `V202608101010`).
4. **Nachher** prüfen:
   ```sql
   -- Je Veranstaltung so viele Rennen wie es dort verschiedene Adressen gab -- nicht mehr.
   select e.name, r.name, r.start_mode, r.results_url
     from raceclocker_race r join event e on e.id = r.event
    order by e.name, r.position;

   -- Kein Wettkampf, der vorher eine eigene Adresse hatte, darf jetzt ohne Anwahl dastehen.
   select count(*) from competition
    where raceclocker_race_qualification is null and raceclocker_race_rounds is null;
   ```

**Erwartung:** Keine Namensdubletten. Die zweite Zahl entspricht der Zahl der Wettkämpfe, die vorher
*keine* eigene Adresse hatten. Weicht etwas ab, ist das ein Fehler in der Migration und wird dort
behoben, nicht hier umgangen.

**Und:** vor dem Migrieren in Produktion einen Dump ziehen. Nach `V202608101010` gibt es kein Zurück
ohne Backup — der Ausbau/Abbau-Zuschnitt legt einen gestaffelten Rollout nahe, in Löschrichtung
existiert er aber nicht.

---

## 2. Handtests in der laufenden App (F16–F22)

Sie stehen im [Testkatalog CRF 2026](2026-08-05-testkatalog-crf-2026.md). **Nichts davon habe ich in
der laufenden Anwendung gesehen** — der ganze Oberflächenteil ist ungeklickt.

| ID | Fall | Warum er zählt |
|---|---|---|
| F16 | Drei Rennen anlegen (Timetrials/Einzelstarts, Langstrecke/Läufe mit Rundenzeiten-Haken, Kurzstrecke/Läufe) | Der Grundfall. Geht das nicht, geht nichts. |
| F17 | Voreinstellung anwählen, Wettkampf zeigt sie als „geerbt" mit **Namen** | Die Vererbung ist der Kern; früher stand hier eine URL |
| F18 | Wettkampf weicht ab → erscheint im Event-Tab unter „Wettkämpfe mit eigener Zeitnahme" **mit Rennennamen** | Ohne den Namen ist die Liste nutzlos |
| F19 | Override-Schalter aus → erbt wieder, Abweichungs-Liste nennt ihn nicht mehr | Der einzige Weg zurück zum Erben |
| F20 | Absichtlich falsches Rennen anwählen, Ergebnisse abrufen | **Der Rückfall ist die Lebensversicherung.** Greift er nicht, steht bei Fehlkonfiguration ein Wettkampf still |
| F21 | Angewähltes Rennen löschen | Rückfrage muss die betroffenen Wettkämpfe **namentlich** nennen; danach erben sie wieder |
| F22 | Zwei Rennen mit gleicher Adresse / gleichem Namen anlegen | Muss verständlich abgewiesen werden, nicht als „Unerwarteter Fehler" |

**Zusätzlich, nicht im Katalog:** Eine Adresse einfügen, die *nicht* zu raceclocker.com gehört. Das
ist am Renntag der wahrscheinlichste Fehlgriff, und die Meldung dafür ist neu.

---

## 3. Was der Umbau eigentlich bringen sollte — einmal messen

Das ist der Zweck der ganzen Änderung und **durch keinen Test gedeckt**, weil er sich nur im
Netzwerkverkehr zeigt.

**Ablauf:** Automatik einschalten, aktiver Takt 5 s, eine Regatta, die nur Kurzstrecke fährt. Dann im
Backend-Log oder per `tcpdump`/Proxy zählen, wie viele Anfragen pro Takt an raceclocker.com gehen.

**Erwartung: eine.** Vorher waren es zwei (angewähltes Rennen plus bedingungsloser Rückfall). Stehen
dort zwei, greift die zweite Runde ungewollt — dann ist entweder die Anwahl falsch oder ein Lauf wird
in seinem Rennen nicht gefunden.

Zweite Messung: Kurzstrecke **und** Langstrecke gleichzeitig → zwei Anfragen. Nicht drei.

---

## 4. Die drei Stellen, an denen ich beim Review am genauesten hinsehen würde

Für den abschließenden Review — hier sitzt das Risiko, nicht in der Breite.

1. **`RaceClockerPollService.pollEvent`** ist von einer Schleife auf vier Phasen umgebaut. Zu prüfen:
   Kann ein Lauf still durchfallen, der vorher abgerufen wurde? Bleibt die Isolation je Lauf in
   *jeder* Phase erhalten (ein Defekt an einem Lauf darf den Takt nicht beenden)? Bleiben
   Fingerabdruck, Pausen-Prüfung und `recordPoll` unverändert wirksam?
   **Bekannter, behobener Fallstrick:** Läufe, die Phase 1 aussortiert (Runde nicht mehr aktuell,
   gesperrt, Freilos), bekamen zwischenzeitlich keinen `recordPoll` mehr — ein alter Fehlercode wäre
   für immer stehen geblieben und hätte im Schiedsrichter-Board eine Dauerwarnung erzeugt.
2. **Der Backfill in `V202608101000`.** Insbesondere: `left join competition_properties` (ein Inner
   Join hätte einen Wettkampf ohne Eigenschaftszeile still auf den Feed der *Veranstaltung* umgelenkt),
   die Entdopplung über `(event, results_url)` und die Namenskollisions-Behandlung.
3. **Die zwei jOOQ-Selbstjoins** (`RaceClockerPollRepo.getCandidates`,
   `CompetitionMatchRepo.getForRaceClockerPull`). Dieselbe Tabelle zweimal unter Alias — ein Lesefehler
   bleibt hier stumm, weil die Abfrage *etwas* liefert, nur aus dem falschen Alias.

---

## 5. Was bereits automatisch geprüft ist (nicht erneut von Hand)

- **612 Backend-Tests**, darunter gegen echtes Postgres (Testcontainers): `RaceClockerPollRepoTest`
  (Vererbung, Wettkampf-Override, gelöschtes Rennen, Knopf-Weg), `RaceClockerRaceRepoTest`.
- **574 Frontend-Tests**, `tsc` sauber, `npm run build` grün, Lint ohne neue Befunde.
- **Reine Funktionen**: `RaceClockerFetchPlanTest` (welche Adressen Runde 1 und Runde 2 anfragen),
  `RaceClockerMatchTargetTest` (Anwahl, Rückfall, Entdopplung).
- **Backfill gegen konstruierte Altdaten** in einer Wegwerf-Datenbank, sieben Fälle: geteilte Adresse
  → ein Rennen; Teil-Override erbt die andere Seite; Wettkampf ohne `competition_properties` bekommt
  sein eigenes Rennen; Leerstring erzeugt nichts; fehlendes Kürzel fällt auf `identifier`;
  Namenskollision bekommt Suffix; Veranstaltung ohne Adressen bekommt nichts.

**Abschlussreview (Fable, 10.08.):** „Merge nach Korrekturen". Ein wichtiger Befund, behoben — ein
Defekt beim Auflösen eines Laufs stempelte einen *sauberen* Abruf, statt sichtbar zu werden. Das war
ein Rückschritt aus der Korrektur des vorigen Reviews: Beim Trennen von „bewusst übersprungen" und
„Fehler" fielen Defekte auf die falsche Seite. Jetzt unterscheidet `Resolution` zwischen `Skip`
(sauberer Stempel) und `Defect` (`INTERNAL_ERROR` in der Spalte). Ebenso: eine Zuordnung, die
unerwartet scheitert, endet nicht mehr als „Welle noch nicht da". Fünf kleinere Befunde ebenfalls
behoben (www-Altbestand bei der Duplikatsprüfung, Abweichungsliste dünnte zu großzügig aus, drei
veraltete Kommentare).

**Bewusste Lücke:** `pollEvent` hat keinen End-to-End-Test — die Phasen sind einzeln gedeckt, aber
nichts fährt 1→2→3→4 zusammen. Dafür müsste `fetchRows` injizierbar werden. Vier Tage vor der Regatta
habe ich diesen Umbau nicht mehr angefasst; er gehört auf die Liste für danach.

---

## 6. Offener Merge-Schritt

Der Zweig hat `feature/crf-2026` **zweimal** eingemergt (der Integrationszweig zog während der
Arbeit weiter: Wellenname mit Wettkampf, Regie-Ansicht, PWA) und ist danach grün. Das Vorziehen von
`feature/crf-2026` auf diesen Stand steht **noch aus**: Der Hauptcheckout war zum Zeitpunkt der
Fertigstellung mit 44 offenen Dateien einer anderen Sitzung belegt, und ein fremder Arbeitsstand ist
nichts, was man nebenbei anfasst.

**Beim Merge zusammengeführt:** Der parallele Zweig hat `WaveName.format` um Rennnummer und Kürzel
erweitert (`10:00 | 1 JM4x | Lauf 1`). Beide Repo-Abfragen projizieren jetzt zusätzlich
`competition_properties.identifier` und `.short_name` **neben** den sechs Rennen-Spalten. Das ist die
Stelle, an der ein Merge-Fehler stumm bliebe: Bauen die beiden Abfragen den Wellennamen
unterschiedlich, findet der Knopf-Weg die Welle und der Job nicht (oder umgekehrt). Ein Test nagelt
das jetzt fest.

Sobald er frei ist:

```bash
git -C /Users/thomas/Developer/privat/ready2race merge --ff-only claude/raceclocker-polling-optimization-d69666
```

Vorher prüfen, dass die Migrationsnummern noch frei sind — `V202608101000` und `V202608101010` waren
es am 10.08. um 02:00 Uhr. Es laufen mehrere Zweige parallel, und genau daran ist diese Arbeit schon
einmal hängengeblieben: `V202608091600` war beim ersten Anlauf bereits von
`participant_tracking_manual` belegt.
