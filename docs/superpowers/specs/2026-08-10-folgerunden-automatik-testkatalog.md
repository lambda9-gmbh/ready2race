# Folgerunden-Automatik — was von Hand zu prüfen ist

Stand 10.08.2026, Zweig `claude/ready2race-auto-follow-rounds-7ab377`, gemergt mit
`feature/crf-2026`. Entwurf: `2026-08-09-folgerunden-automatik-design.md`.

Automatisiert belegt sind Abschluss-Erkennung, Vererbung, Idempotenz, Nicht-Aktivierung,
Korrekturweg und Sperre (Backend-Suite, dazu die reinen Frontend-Prüfungen). **Nicht** belegt ist
alles, was man sehen muss: die Oberfläche, das Zusammenspiel am Renntag und die drei Auslöser ohne
eigenen Verdrahtungstest. Genau dafür ist diese Liste.

## Vorbereitung

Ein Wettkampf mit zweistufigem Ablauf: Vorlauf (zwei Läufe zu je zwei Booten) → Finale. Vier
Meldungen mit Startnummern. Der Zeitplan darf zunächst leer bleiben; wo er gebraucht wird, steht es
beim jeweiligen Fall.

Die erste Runde wird wie bisher von Hand erzeugt — die Automatik greift erst ab der zweiten. Das ist
Absicht und kein Fehler.

---

## A — Einstellung und Vererbung

**A1** Veranstaltung bearbeiten: Der Schalter „Folgerunden automatisch erzeugen" steht neben der
Lauf-Kette und ist bei einer neuen Veranstaltung **aus**. Ein- und ausschalten, speichern, Dialog
erneut öffnen — der Wert steht noch.

**A2** Wettkampf → Durchführung: Die Dreier-Auswahl steht neben „Nächste Runde erstellen" und
beginnt auf „Veranstaltung folgen". Darunter steht, was daraus folgt.

**A3** Auswahl auf „Ein" stellen, **ohne zu speichern**: Der Hinweistext darunter muss der Wahl
sofort folgen. Tut er das erst nach dem Speichern, ist die Vorschau nicht angeschlossen.

**A4** Veranstaltung auf „ein", Wettkampf auf „Aus" speichern, Seite neu laden — die Auswahl steht
auf „Aus", der Hinweis sagt, dass nicht automatisch erzeugt wird. Danach zurück auf „Veranstaltung
folgen": der Hinweis kippt auf „wird erzeugt". Das ist der Unterschied zwischen `null` und `false`,
der im Datenmodell steckt.

**A5** Nachdem alle Runden erzeugt sind, verschwindet die Auswahl aus der Oberfläche. Bekannt und
gewollt; hier nur festhalten, dass es niemanden überrascht.

---

## B — Der Regelfall

**B1** Automatik ein. Vorlauf vollständig werten, beide Läufe beenden. **Das Finale muss ohne
weiteres Zutun dastehen** — keine Meldung, kein Knopfdruck.

**B2** Direkt danach ins Schiedsrichter-Dashboard: Das Finale ist **nicht** an den Start gerufen
(kein „In Vorbereitung", kein „Läuft"). Ob und wann es aufgerufen wird, entscheidet weiter der
Zeitstrahl.

**B3** Nur einen der beiden Vorlauf-Läufe beenden: Es darf nichts passieren. Erst der zweite löst
aus.

**B4** Automatik aus (Veranstaltung und Wettkampf): Beide Läufe beenden — es passiert nichts, der
Knopf „Nächste Runde erstellen" steht wie bisher zur Verfügung und funktioniert.

**B5** Einen Lauf mit DNF, einen mit Disqualifikation und einen mit Nichtantritt werten, dann
beenden. Die Runde gilt trotzdem als abgeschlossen, das Finale entsteht.

**B6** Ein Wettkampf mit Freilos in der Qualifikation: Das Freilos hat kein „Beenden" und darf die
Runde nicht aufhalten.

---

## C — Die drei Auslöser ohne automatischen Test

Diese drei sind im Code verdrahtet, aber nur durch Lesen geprüft. Sie sind der wichtigste Teil
dieser Liste.

**C1** *Ergebnis per Datei*: Vorlauf bis auf einen Lauf fertig und beendet. Den letzten Lauf
beenden, **dann** sein Ergebnis per xlsx-Upload nachtragen. Das Finale muss danach stehen.

**C2** *RaceClocker*: Dasselbe mit einem Wettkampf, dessen Ergebnisse automatisch abgerufen werden.
Nach dem Takt, der den letzten fehlenden Lauf vollständig macht, muss das Finale entstehen.

**C3** *Slot absagen*: Einen Lauf des Vorlaufs über den Zeitplan absagen, dessen Boote **gewertet**
sind, und den anderen Lauf beenden. Das Finale muss entstehen.

**C4** *Slot-Absage zurücknehmen*: Danach die Absage zurücknehmen. Es darf dadurch **nichts** neu
entstehen.

**C5** *Runde entfällt*: Eine Runde, in der es nichts zu fahren gibt (nur Freilose), über „Runde
entfällt" abräumen. Die Folgerunde muss entstehen. Dieser Weg fehlte in der ersten Fassung ganz.

---

## D — Der Vermerk „Paarung neu berechnet"

**D1** Finale löschen, ein Vorlauf-Ergebnis korrigieren. Das Finale entsteht neu, und **jeder
seiner Läufe trägt den Hinweis** „Paarung neu berechnet" — im Durchführungs-Tab und im
Schiedsrichter-Dashboard.

**D2** Beim **ersten** Erzeugen einer Runde darf der Hinweis nicht erscheinen.

**D3** Den betroffenen Lauf an den Start rufen: Der Hinweis verschwindet.

**D4** Den Lauf danach **beenden** und den Durchführungs-Tab neu laden: Der Hinweis darf **nicht
zurückkommen**. Das war ein echter Fehler und ist behoben — bitte gegenprüfen, weil Beenden die
Aktivierung zurücknimmt.

**D5** Öffentliche Anzeige und Athleten-Anzeige aufrufen, während der Hinweis im Orga-Bereich steht:
Dort darf **nichts** davon zu sehen sein.

---

## E — Korrektur und Schutz

**E1** Finale steht, ein Lauf davon ist gestartet. Ein Vorlauf-Ergebnis ändern wollen: Die Eingabe
muss mit „Ergebnisse gesperrt" abgewiesen werden, und es darf sich nichts ändern.

**E2** *Die bekannte Einschränkung.* Finale löschen, Vorlauf-Lauf 1 korrigieren, dann Vorlauf-Lauf 2
korrigieren. Der zweite Schritt scheitert, weil die Automatik das Finale nach der ersten Korrektur
sofort neu erzeugt hat. Das ist kein Fehler, sondern die Folge der Entscheidung „Sperre bleibt,
Korrektur über Löschen" — **wer zwei Läufe richtigstellen will, muss vor jeder Korrektur einmal
löschen.** Bitte im Betrieb bewerten, ob das am Renntag tragbar ist.

**E3** *Neu:* Beim Löschen einer Runde, von der schon Läufe auf den Anzeigen stehen (aufgerufen,
laufend, beendet oder vollständig gewertet), muss die Bestätigung das sagen und die Anzahl nennen.
Bei einer Runde, die nur geplant ist, kommt der alte, schlichte Text.

**E4** Eine Runde nur aus Freilosen löschen: Es darf **nicht** gewarnt werden — Freilose tragen ihren
Platz seit der Erzeugung und wären sonst dauerhaft „schon zu sehen".

---

## F — Formate

Je einmal mit einem echten Wettkampf durchspielen, bis die Kette steht:

**F1** K.-o.-Baum über mehrere Runden (Viertelfinale → Halbfinale → Finale).

**F2** Vorrunde → Zwischenrunde → Finale.

**F3** Qualifikation mit Freilosen, die in den Baum führt.

**F4** Ein Wettkampf mit nur einer Runde: Es darf nie etwas erzeugt werden, und es darf keine
Fehlermeldung erscheinen.

---

## G — Zusammenspiel mit dem Zeitstrahl

**G1** Zeitplan gepflegt, Kette auf „Schiedsrichter". Letzten Vorlauf beenden: Das Finale entsteht,
und die Kette rückt anschließend regulär vor — der wartende Slot ist nicht mehr blockiert.

**G2** Kette auf „Deaktiviert": Das Finale entsteht, aber es wird nichts aktiviert.

**G3** Kette auf „Regattabüro": Beenden über den Zeitplan. Auch dieser Weg muss die Folgerunde
auslösen.

---

## H — Was schiefgehen darf, ohne wehzutun

**H1** Ein Wettkampf, dessen Folgerunde zu wenig Bahnen hat: Den Vorlauf beenden. Das **Beenden muss
gelingen**; die Folgerunde entsteht nicht, und der Knopf meldet den Fehler weiterhin sichtbar. Im
Serverlog steht eine Warnung. Scheitert stattdessen das Beenden, ist die wichtigste Zusage der
Automatik verletzt.

**H2** Challenge-Veranstaltung: Nichts von alledem darf greifen.

---

## Vor dem Ausrollen

Die Migration `V202608091501` setzt `materialized_at` für alle Runden, die schon Läufe haben. Auf
einem Abzug der produktiven Datenbank einmal durchlaufen lassen und danach stichprobenartig prüfen,
dass eine bestehende Runde nach Löschen und Neuerzeugen den Hinweis trägt (ohne den Nachtrag
bekäme sie ihn nicht).

Die Migration liegt direkt neben `V202608091500` aus einer parallelen Sitzung. Beide sind hier
gemergt und kollidieren nicht mehr.
