# Offene Fragen an Ilka

**Stand:** 2026-08-06
**Status:** Sammelstelle. Bisher waren Fragen an Ilka über die anderen Dokumente verstreut (etwa
C4 im Backlog vom 30.07.); hier stehen sie zusammen, damit ein Gespräch sie am Stück abräumen kann.

Jeder Punkt nennt **was zu entscheiden ist**, **worauf es sich auswirkt** und **was der Code heute
tut** — damit die Frage ohne Vorbereitung besprechbar ist.

---

## 1. Abmeldung vor dem Setzen der Runde: Zeile im Lauf oder nicht?

**Zu entscheiden:** Soll ein Boot, das sich abmeldet, **bevor** die Runde gesetzt wurde, überhaupt
noch im Lauf auftauchen?

**Ausgangslage.** Es gibt zwei Wege zu einem abgemeldeten Boot, und sie verhalten sich heute
unterschiedlich:

| Zeitpunkt der Abmeldung | was passiert |
|---|---|
| **nach** dem Setzen der Runde | Das Boot bleibt im Lauf und erscheint überall als „abgemeldet · Grund" — in der Durchführung, im Dashboard und auf der Athleten-Anzeige. So ist es gedacht (Testkatalog A10). |
| **vor** dem Setzen der Runde | Das Boot bekommt trotzdem eine Zeile im Lauf, samt eigener Bahnnummer — aber **keine** Ansicht zeigt sie. Die Bahn ist vergeben und unsichtbar. |

Nachgestellt an „1 | Frauen Doppelzweier" der Förde Testregatta: Ruderverein Kiel wurde vor dem
Setzen abgemeldet und belegt in der Datenbank Bahn 5 des Finales, während Durchführung, Dashboard
und Anzeige nur vier Boote kennen.

**Worauf es sich auswirkt.** Die Bahnnummern. Ein Lauf mit sechs Meldungen, von denen eine vorab
zurückgezogen wurde, vergibt heute die Bahnen 1–6 und zeigt fünf davon. Ob das stört, hängt daran,
wie ihr die Bahnen am Steg tatsächlich vergebt.

**Mögliche Antworten, die sich anbieten:**
- Gar keine Zeile anlegen — wer vor dem Setzen weg ist, war nie im Lauf.
- Zeile anlegen und **auch zeigen** (wie beim zweiten Weg), damit beide Fälle gleich aussehen.
- So lassen, aber die Bahnen ohne Lücke vergeben.

**Dringlichkeit:** niedrig. Nichts stürzt ab, nichts rechnet falsch — es ist eine Frage der
Darstellung und der Bahnvergabe.

---

## 2. createNextRound-Trigger hinterfragen

Ausführlich beschrieben als **C4** in `2026-07-30-schiedsrichter-dashboard-backlog.md`. Kurz: Braucht
es den manuellen Klick „Nächste Runde erstellen" noch, oder könnten Runden automatisch entstehen,
sobald die Vorrunde beendet ist? Gegenargument im Dokument: der Export nach RaceClocker hat Latenz,
der Lauf wäre in ready2race gesetzt, während er in RaceClocker noch nicht existiert.

Hier nur verlinkt, damit die Frage nicht zweimal gepflegt wird.
