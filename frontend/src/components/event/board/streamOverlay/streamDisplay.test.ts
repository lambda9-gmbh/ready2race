import {describe, expect, it} from 'vitest'
import {
    byNullsLast,
    competitionLabel,
    crewLines,
    formatCountdownClock,
    roundMatchLabel,
    solidOr,
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

describe('teamTrailingLabel', () => {
    it('Platz + Zeit als Ordinal', () => {
        expect(
            teamTrailingLabel({place: 1, timeString: '3:45.2', failed: false, failedReason: null}, 'DNF'),
        ).toBe('1st 3:45.2')
    })

    it('DNF/DNS/DSQ statt Platz/Zeit', () => {
        expect(
            teamTrailingLabel(
                {place: null, timeString: null, failed: true, failedReason: 'DNF'},
                'nicht gewertet',
            ),
        ).toBe('DNF')
    })

    it('ohne jedes Teilergebnis (Aufstellung) null', () => {
        expect(
            teamTrailingLabel({place: null, timeString: null, failed: false, failedReason: null}, 'DNF'),
        ).toBeNull()
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
