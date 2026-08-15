import {describe, expect, it} from 'vitest'
import {
    byNullsLast,
    competitionLabel,
    crewLines,
    enumerationUnits,
    formatCountdownClock,
    roundMatchLabel,
    runningBadge,
    solidOr,
    streamNameForms,
    teamTrailingLabel,
} from './streamDisplay.ts'

describe('solidOr', () => {
    it('lässt Hex-Farben durch', () => {
        expect(solidOr('#112233', '#000000')).toBe('#112233')
    })

    it('ersetzt Nicht-Hex (z. B. rgba aus dem noch ladenden Theme) durch den Fallback', () => {
        expect(solidOr('rgba(0,0,0,0.87)', '#1d1d1d')).toBe('#1d1d1d')
    })
})

describe('byNullsLast', () => {
    it('sortiert NULL-Werte ans Ende, sonst aufsteigend', () => {
        const items = [{v: null}, {v: 2}, {v: 1}]
        expect(items.sort(byNullsLast(i => i.v)).map(i => i.v)).toEqual([1, 2, null])
    })
})

describe('crewLines', () => {
    const team = {
        clubsShort: 'RG',
        clubsFull: 'Rudergemeinschaft',
        teamName: null,
        participants: [{name: 'Anna Muster'}, {name: 'Bea Beispiel'}],
    }

    it('CLUBS_FIRST (Default): Verein prominent, Besatzung klein', () => {
        expect(crewLines(team, 'CLUBS_FIRST', true)).toEqual({
            primary: 'RG',
            secondary: 'Anna Muster · Bea Beispiel',
        })
    })

    it('PARTICIPANTS_FIRST: Besatzung prominent, Verein klein', () => {
        expect(crewLines(team, 'PARTICIPANTS_FIRST', true)).toEqual({
            primary: 'Anna Muster · Bea Beispiel',
            secondary: 'RG',
        })
    })

    it('PARTICIPANTS_FIRST ohne erfasste Besatzung fällt auf den Verein zurück', () => {
        expect(crewLines({...team, participants: []}, 'PARTICIPANTS_FIRST', true)).toEqual({
            primary: 'RG',
            secondary: null,
        })
    })

    it('CLUBS_ONLY lässt die Besatzung weg', () => {
        expect(crewLines(team, 'CLUBS_ONLY', true)).toEqual({primary: 'RG', secondary: null})
    })

    it('useShortNames=false nimmt die Langform', () => {
        expect(crewLines(team, 'CLUBS_FIRST', false).primary).toBe('Rudergemeinschaft')
    })
})

/**
 * Grundlage des zweizeiligen Umbruchs in StreamBoatRow: gebrochen wird nur zwischen ganzen
 * Einheiten (Verein/Athlet/Teamname), das Trennzeichen klebt per NBSP an der vorangehenden.
 */
describe('enumerationUnits', () => {
    it('zerlegt eine Vereinskette samt Teamname an " / " und " | "', () => {
        expect(enumerationUnits('RC Flensburg / Kieler RG | Boot 2')).toEqual([
            'RC Flensburg\u00A0/\u00A0',
            'Kieler RG\u00A0|\u00A0',
            'Boot 2',
        ])
    })

    it('zerlegt eine Besatzungskette an " · "', () => {
        expect(enumerationUnits('Anna Muster · Bea Beispiel')).toEqual([
            'Anna Muster\u00A0·\u00A0',
            'Bea Beispiel',
        ])
    })

    it('lässt Text ohne Trennzeichen als eine Einheit stehen', () => {
        expect(enumerationUnits('Rudergemeinschaft Musterstadt')).toEqual([
            'Rudergemeinschaft Musterstadt',
        ])
    })

    it('leerer Text ergibt keine Einheiten', () => {
        expect(enumerationUnits('')).toEqual([])
    })

    it('bricht nicht an bloßen Zeichen ohne umgebende Leerzeichen (z. B. "SG Kiel/Eckernförde")', () => {
        expect(enumerationUnits('SG Kiel/Eckernförde · Ole Otto')).toEqual([
            'SG Kiel/Eckernförde\u00A0·\u00A0',
            'Ole Otto',
        ])
    })

    it('bleibt verlustfrei: NBSP zurückgetauscht ergibt wieder den Eingabetext', () => {
        const text = 'RC A / RC B / RC C | Boot 1'
        expect(enumerationUnits(text).join('').replace(/\u00A0/g, ' ')).toBe(text)
    })
})

describe('teamTrailingLabel', () => {
    const outcome = (
        over: Partial<Parameters<typeof teamTrailingLabel>[0]> = {},
    ): Parameters<typeof teamTrailingLabel>[0] => ({
        place: null,
        timeString: null,
        failed: false,
        failedReason: null,
        bye: false,
        deregistered: false,
        deregisteredReason: null,
        ...over,
    })

    it('Platz + Zeit als Ordinal', () => {
        expect(teamTrailingLabel(outcome({place: 1, timeString: '3:45.2'}), 'DNF')).toBe(
            '1st 3:45.2',
        )
    })

    it('DNF/DNS/DSQ statt Platz/Zeit', () => {
        expect(
            teamTrailingLabel(outcome({failed: true, failedReason: 'DNF'}), 'nicht gewertet'),
        ).toBe('DNF')
    })

    it('ohne jedes Teilergebnis (Aufstellung) null', () => {
        expect(teamTrailingLabel(outcome(), 'DNF')).toBeNull()
    })

    it('das Freilos steht vor Platz, Zeit und Abmeldung', () => {
        // Ein Boot mit vergebenem Freilos hat weder Platz noch Zeit - ohne diese Beschriftung
        // stünde seine Zeile leer da und wäre von "Ergebnis fehlt noch" nicht zu unterscheiden.
        expect(teamTrailingLabel(outcome({bye: true}), 'DNF', 'Abgemeldet', 'Freilos')).toBe(
            'Freilos',
        )
        expect(
            teamTrailingLabel(
                outcome({bye: true, deregistered: true, place: 1}),
                'DNF',
                'Abgemeldet',
                'Freilos',
            ),
        ).toBe('Freilos')
    })

    it('ohne Beschriftung bleibt das Freilos beim bisherigen Verhalten', () => {
        // Aufrufer, die die Angabe nicht führen (ältere Panels), sollen sich nicht ändern.
        expect(teamTrailingLabel(outcome({bye: true, place: 2, timeString: '3:50.0'}), 'DNF')).toBe(
            '2nd 3:50.0',
        )
    })

    /**
     * Die Abmeldung ist kein Ergebnis und steht deshalb vor allem anderen — auch in einem Panel,
     * in dem noch gar nichts gewertet sein kann.
     */
    it('nennt eine Abmeldung samt Grund und schlägt dabei jedes Ergebnis', () => {
        expect(
            teamTrailingLabel(
                outcome({deregistered: true, deregisteredReason: 'Krankheit'}),
                'DNF',
                'Abgemeldet',
            ),
        ).toBe('Abgemeldet — Krankheit')
        expect(
            teamTrailingLabel(outcome({deregistered: true}), 'DNF', 'Abgemeldet'),
        ).toBe('Abgemeldet')
    })

    /** Aufrufer ohne die Angabe bleiben beim alten Verhalten. */
    it('bleibt ohne Abmelde-Beschriftung beim Ergebnis', () => {
        expect(teamTrailingLabel(outcome({deregistered: true, place: 2}), 'DNF')).toBe('2nd')
    })
})

describe('formatCountdownClock', () => {
    it('formatiert 272_000 ms als "4:32"', () => {
        expect(formatCountdownClock(272_000)).toBe('4:32')
    })

    it('klemmt negative Werte auf "0:00"', () => {
        expect(formatCountdownClock(-500)).toBe('0:00')
    })
})

describe('roundMatchLabel', () => {
    it('dedupliziert gleichen Runden-/Laufnamen', () => {
        expect(roundMatchLabel('Vorlauf 1', 'Vorlauf 1')).toBe('Vorlauf 1')
    })

    it('zeigt beide, wenn sie sich unterscheiden', () => {
        expect(roundMatchLabel('Vorlauf', 'Lauf 2')).toBe('Vorlauf · Lauf 2')
    })

    it('leer ohne beides', () => {
        expect(roundMatchLabel(null, null)).toBeNull()
    })
})

describe('competitionLabel', () => {
    it('nimmt die Kurzform, wenn vorhanden', () => {
        expect(competitionLabel('Langer Name', 'Kurz', true)).toBe('Kurz')
    })

    it('fällt ohne gepflegtes Kürzel auf den vollen Namen zurück', () => {
        expect(competitionLabel('Langer Name', null, true)).toBe('Langer Name')
    })

    it('useShortNames=false erzwingt die Langform', () => {
        expect(competitionLabel('Langer Name', 'Kurz', false)).toBe('Langer Name')
    })
})

/**
 * Wettkampfname und Vereinsnamen sind getrennt einstellbar; ein Board ohne den neuen
 * Vereins-Schalter muss weiterhin genau so aussehen wie vorher (siehe streamDisplay.ts).
 */
describe('streamNameForms', () => {
    it('nimmt ohne jede Angabe für beides die Kurzform', () => {
        expect(streamNameForms({})).toEqual({competitions: true, clubs: true})
    })

    it('lässt die Vereine dem Wettkampf-Schalter folgen, solange sie nicht gesetzt sind', () => {
        expect(streamNameForms({useShortNames: false})).toEqual({
            competitions: false,
            clubs: false,
        })
    })

    it('erlaubt Wettkampf-Kürzel mit ausgeschriebenen Vereinen', () => {
        expect(streamNameForms({useShortNames: true, useShortClubNames: false})).toEqual({
            competitions: true,
            clubs: false,
        })
    })

    it('erlaubt auch die Gegenrichtung: voller Wettkampfname, Vereinskürzel', () => {
        expect(streamNameForms({useShortNames: false, useShortClubNames: true})).toEqual({
            competitions: false,
            clubs: true,
        })
    })

    it('behandelt null wie „nicht gesetzt"', () => {
        expect(streamNameForms({useShortNames: null, useShortClubNames: null})).toEqual({
            competitions: true,
            clubs: true,
        })
    })
})

/**
 * Bug vom Regattatag (14.08.): das Badge sagte „LÄUFT", sobald ein Lauf nur an den Start
 * gerufen war (PREPARING) — der Running-Block des Servers führt auch diese Läufe, und
 * Slot 0 nimmt den letzten daraus. Andere Zustände als PREPARING/RUNNING liefert der
 * Block nicht (LiveDashboardLogic.deriveMatchState mit activatedAt != null).
 */
describe('runningBadge', () => {
    it('PREPARING: „In Vorbereitung" ohne Indikator-Punkt — der Punkt heißt „on air"', () => {
        expect(runningBadge('PREPARING')).toEqual({labelKey: 'preparing', indicator: false})
    })

    it('RUNNING: „LÄUFT" mit Punkt', () => {
        expect(runningBadge('RUNNING')).toEqual({labelKey: 'running', indicator: true})
    })
})
