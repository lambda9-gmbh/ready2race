import {describe, expect, it} from 'vitest'
import {isSlotSelected, nextEventModeSelection} from './eventMode.ts'

// Nur die Felder, die die Auswahl-Logik liest — die Funktionen nehmen bewusst ein Pick des DTO.
const slot = (
    competitionId: string | null,
    matchId: string | null = null,
    competitionName: string | null = 'CM 1x',
) => ({competitionId, competitionName, matchId})

describe('nextEventModeSelection', () => {
    it('lädt den Wettkampf des geklickten Laufs, wenn rechts nichts geladen ist', () => {
        expect(nextEventModeSelection(null, slot('c-1', 'm-1', 'CM 1x'))).toEqual({
            competitionId: 'c-1',
            competitionName: 'CM 1x',
            matchId: 'm-1',
        })
    })

    it('lädt einen Slot ohne materialisierten Lauf ohne Sprungziel', () => {
        expect(nextEventModeSelection(null, slot('c-1'))).toEqual({
            competitionId: 'c-1',
            competitionName: 'CM 1x',
            matchId: null,
        })
    })

    it('wechselt beim Klick auf den Lauf eines anderen Wettkampfs', () => {
        const current = {competitionId: 'c-1', competitionName: 'CM 1x', matchId: 'm-1'}
        expect(nextEventModeSelection(current, slot('c-2', 'm-9', 'JM 4x'))).toEqual({
            competitionId: 'c-2',
            competitionName: 'JM 4x',
            matchId: 'm-9',
        })
    })

    it('springt beim Klick auf einen anderen Lauf desselben Wettkampfs nur dorthin', () => {
        const current = {competitionId: 'c-1', competitionName: 'CM 1x', matchId: 'm-1'}
        expect(nextEventModeSelection(current, slot('c-1', 'm-2'))).toEqual({
            competitionId: 'c-1',
            competitionName: 'CM 1x',
            matchId: 'm-2',
        })
    })

    it('schließt die Fläche beim nochmaligen Klick auf den geladenen Lauf', () => {
        const current = {competitionId: 'c-1', competitionName: 'CM 1x', matchId: 'm-1'}
        expect(nextEventModeSelection(current, slot('c-1', 'm-1'))).toBeNull()
    })

    it('schließt bei einer lauflosen Zeile desselben Wettkampfs (kein Ziel zum Springen)', () => {
        const current = {competitionId: 'c-1', competitionName: 'CM 1x', matchId: 'm-1'}
        expect(nextEventModeSelection(current, slot('c-1'))).toBeNull()
    })

    it('ignoriert Slots ohne Wettkampf (freie Programmpunkte)', () => {
        const current = {competitionId: 'c-1', competitionName: 'CM 1x', matchId: 'm-1'}
        expect(nextEventModeSelection(current, slot(null))).toEqual(current)
        expect(nextEventModeSelection(null, slot(null))).toBeNull()
    })

    it('fällt bei fehlendem Wettkampfnamen auf einen leeren String zurück', () => {
        expect(nextEventModeSelection(null, slot('c-1', 'm-1', null))).toEqual({
            competitionId: 'c-1',
            competitionName: '',
            matchId: 'm-1',
        })
    })
})

describe('isSlotSelected', () => {
    const selection = {competitionId: 'c-1', competitionName: 'CM 1x', matchId: 'm-1'}

    it('markiert alle Läufe des gewählten Wettkampfs', () => {
        expect(isSlotSelected(selection, slot('c-1', 'm-2'))).toBe(true)
    })

    it('markiert fremde Wettkämpfe und freie Slots nicht', () => {
        expect(isSlotSelected(selection, slot('c-2'))).toBe(false)
        expect(isSlotSelected(selection, slot(null))).toBe(false)
    })

    it('markiert nichts, solange rechts nichts geladen ist', () => {
        expect(isSlotSelected(null, slot('c-1'))).toBe(false)
    })
})
