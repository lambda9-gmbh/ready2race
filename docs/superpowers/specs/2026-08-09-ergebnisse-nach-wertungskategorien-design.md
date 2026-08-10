# Ergebnisse nach Wertungskategorien

Entwurf vom 09.08.2026. Branch `claude/results-by-rating-categories`, mündet in `feature/crf-2026`.

## Warum

Eine Wertungskategorie („Meisterschaften", „Breitensport", Altersklassen) entscheidet, gegen wen
ein Boot antritt. Gefahren wird trotzdem gemeinsam: dieselbe Bahn, derselbe Lauf, dieselbe Zeit.
Bis heute zeigt ready2race deshalb genau eine Rangliste je Lauf und je Wettkampf — wer in der
Breitensportwertung Erster ist, findet sich dort als Sechster wieder und muss selbst
zusammensuchen, welche der fünf Boote davor überhaupt in seiner Wertung fahren.

Ab jetzt trennt jede Ergebnisdarstellung nach Wertungskategorie und zählt in jedem Abschnitt neu
ab 1.

## Nicht Gegenstand

- **Der Platz auf der Urkunde bleibt der wettkampfweite.** Das steht bewusst quer zur
  Platzierungsansicht und ist eine ausdrückliche Entscheidung: eine Urkunde wandert ins Bootshaus
  und wird dort mit Urkunden aus zehn Jahren verglichen, in denen „3. Platz" den Platz im Rennen
  meinte. Neu ist auf der Urkunde nur die Kategoriezeile.
- **Keine Änderung an `computeCompetitionPlaces`.** Die Rundenlogik (Seeding, Aufsteiger,
  Nicht-Aufsteiger) bleibt wie sie ist; die Kategoriewertung setzt auf ihr Ergebnis auf.
- **Die Platzierungs-CSV bleibt unverändert.** Sie trägt bereits eine Kategoriespalte und wird
  maschinell weiterverarbeitet; eine zweite Platzspalte würde bestehende Auswertungen brechen.
- **Challenge-Veranstaltungen** (Vereins- und Einzelwertung) bleiben unberührt. Sie filtern
  bereits nach Kategorie und kennen keine Läufe.

---

## 1. Datenmodell: die Reihenfolge der Kategorien

Eine „konfigurierte Sortierreihenfolge" gab es nicht. Weder `rating_category` noch
`event_rating_category` hatten eine Ordnungsspalte; sortiert wurde überall alphabetisch.

`V202608091500__rating_category_sort_order.sql`:

```sql
set search_path to ready2race, pg_catalog, public;

alter table event_rating_category add column sort_order int not null default 0;

-- Backfill: die bisher gezeigte alphabetische Reihenfolge festschreiben, damit sich die
-- Anzeige durch die Migration allein nicht veraendert.
update event_rating_category erc
set sort_order = numbered.position
from (select erc2.event,
             erc2.rating_category,
             row_number() over (partition by erc2.event order by rc.name) - 1 as position
      from event_rating_category erc2
               join rating_category rc on rc.id = erc2.rating_category) numbered
where erc.event = numbered.event
  and erc.rating_category = numbered.rating_category;
```

Die Reihenfolge hängt an der Veranstaltung, nicht an der Kategorie: dieselbe Kategorie darf bei
zwei Regatten unterschiedlich einsortiert sein, und eine global gepflegte Reihenfolge wäre für
Veranstalter, die nur eine Teilmenge der Kategorien benutzen, sinnlos.

`event_rating_category_view` in `afterMigrate.sql` bekommt `erc.sort_order`.

## 2. Eine Stelle, die gruppiert und zählt

`ratingcategory/boundary/RatingCategoryRanking.kt`, rein und ohne Datenbank:

```kotlin
data class RatingCategoryRef(val id: UUID, val name: String, val sortOrder: Int)

data class RankedCategory<T>(
    /** null = Abschnitt „Ohne Wertungskategorie" */
    val category: RatingCategoryRef?,
    val entries: List<RankedEntry<T>>,
)

data class RankedEntry<T>(val item: T, val categoryPlace: Int?)

fun <T> groupAndRank(
    items: List<T>,
    category: (T) -> RatingCategoryRef?,
    place: (T) -> Int?,
    tieBreak: (T) -> Int,
): List<RankedCategory<T>>
```

Regeln, die die Funktion durchsetzt:

1. **Abschnittsreihenfolge**: `sortOrder` aufsteigend, bei Gleichstand der Name. Der Abschnitt
   ohne Kategorie steht immer am Ende — auch wenn eine Kategorie `sortOrder` grösser als alle
   anderen hätte.
2. **Zählung ab 1** je Abschnitt, in der Reihenfolge des zugrunde liegenden Platzes.
3. **Gleichstand**: gleicher Ausgangsplatz ⇒ gleicher Kategorieplatz, danach Lücke (1, 1, 3).
   Gleichstände sind real — `CompetitionSetupPlacesOption.EQUAL` gibt einer ganzen Runde
   denselben Platz.
4. **Ohne Wertung** (`place == null`: abgemeldet, ausgeschieden, disqualifiziert, noch nicht
   gewertet) ⇒ `categoryPlace == null`, einsortiert am Ende des eigenen Abschnitts nach
   `tieBreak` (Startnummer). Diese Boote verschwinden nicht: eine Besatzung, die ihr Boot im
   Ergebnis nicht findet, hält das für einen Anzeigefehler.
5. **Leere Abschnitte entstehen nicht.** Eine der Veranstaltung zugeordnete Kategorie, in der
   kein Boot gemeldet ist, taucht in keiner Ergebnisliste auf.

Alle fünf Verwendungsstellen rufen diese Funktion. Sie ist die einzige Stelle, an der die neue
Zählung rechnet.

## 3. Lauf-Ergebnisse: öffentlich, Schiedsrichter, Athleten

Die drei Ansichten zeigen Ergebnisse je Lauf. Sie behalten flache Mannschaftslisten; je
Mannschaft kommen `ratingCategory` (Id, Name, `sortOrder`) und `categoryPlace` hinzu:

| DTO | Ansicht |
|---|---|
| `MatchResultTeamInfo` | öffentliche Ergebnisseite |
| `AthleteBoardResultTeam` | Athletenanzeige (leitet sich aus `LatestMatchResultInfo` ab) |
| `LiveDashboardTeamDto` | Schiedsrichter-Dashboard |

Gerechnet wird im Backend, gruppiert wird im Frontend durch **einen** gemeinsamen Helper neben
`sortByPlaces` in `utils/helpers.ts`, den alle drei Ansichten benutzen. Die Alternative — das
Backend liefert fertig verschachtelte Abschnitte — hätte drei DTO-Formen umgebaut und die
Nutzlast der Schiedsrichter-Karte vergrössert, die im Mobilfunknetz am Steg geladen wird.

Die Abfragen in `CompetitionMatchTeamRepo` und `LiveDashboardRepo` joinen dafür
`competition_registration.rating_category` auf `rating_category` und `event_rating_category`.

## 4. Wettkampf-Platzierungen

`CompetitionMatchTeamWithRegistration.ratingCategory` trägt heute nur einen Namensstring und wird
auf `RatingCategoryRef` (Id, Name, `sortOrder`) erweitert — ohne Id lässt sich nicht zuverlässig
gruppieren, ohne `sortOrder` nicht sortieren.

Darauf setzen auf:

- `CompetitionTeamPlaceDto` mit `ratingCategory` und `categoryPlace`; `getCompetitionPlaces`
  liefert sie mit, `CompetitionPlaces.tsx` zeigt Abschnitte mit Kategorieüberschrift.
- Das Ergebnis-PDF (`ResultsService.buildPdf`) gliedert jeden Wettkampf in Abschnitte und druckt
  den Kategorieplatz.

`computeCompetitionPlaces` selbst bleibt unverändert und liefert weiter die wettkampfweite
Platzierung — daran hängen die Urkunden (siehe „Nicht Gegenstand").

## 5. Urkunden

`AwardCertificateOptions.printRatingCategory: Boolean`, Query-Parameter `ratingCategory`,
Standard `false`.

Neuer Platzhaltertyp `RATING_CATEGORY` in `GapDocumentPlaceholderType`, neues Feld in
`GapPlaceholderValues`. Urkunden sind Gap-Vorlagen mit frei positionierten Platzhaltern; ein
eigener Typ ist die einzige Form, in der die Kategorie als klar erkennbare eigene Zeile erscheint,
statt sich in einen fremden Platzhalter zu drängen. Preis: bestehende Vorlagen müssen den
Platzhalter einmalig gesetzt bekommen.

Ist die Option aus, bleibt der Wert `null` — die Ausgabe ist dann byte-gleich zu heute. Ein
Platzhalter in der Vorlage, für den kein Wert vorliegt, bleibt leer; das ist bereits das
bestehende Verhalten von `GapPlaceholderLogic.fill`.

Im `AwardCertificateDialog` ein Schalter unter den bestehenden Optionen, Übersetzungen de/en/da.

## 6. Tests

**Backend** (JUnit, ohne Datenbank):

- `RatingCategoryRankingTest`: Abschnittsreihenfolge nach `sortOrder`, Name als Tiebreak,
  „Ohne Wertungskategorie" zuletzt, Zählung ab 1 je Abschnitt, Gleichstand 1/1/3, Boote ohne
  Wertung ohne Platz und am Ende, keine leeren Abschnitte.
- `AwardCertificateLogicTest`: Kategoriezeile bei aktiver Option, unveränderte Werte bei
  inaktiver Option.

**Frontend** (vitest): der Gruppierungs-Helper in `utils/helpers.ts` — dieselben Fälle wie oben,
damit die drei Ansichten nachweislich identisch gruppieren.
