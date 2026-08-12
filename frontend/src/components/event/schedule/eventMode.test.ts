import {describe, expect, it} from 'vitest'
import {isSlotSelected, nextEventModeSelection} from './eventMode.ts'

// Nur die Felder, die die Auswahl-Logik liest — die Funktionen nehmen bewusst ein Pick des DTO.
const slot = (competitionId: string | null, competitionName: string | null = 'CM 1x') => ({
    competitionId,
    competitionName,
})

describe('nextEventModeSelection', () => {
    it('lädt den Wettkampf des geklickten Laufs, wenn rechts nichts geladen ist', () => {
        expect(nextEventModeSelection(null, slot('c-1', 'CM 1x'))).toEqual({
            competitionId: 'c-1',
            competitionName: 'CM 1x',
        })
    })

    it('wechselt beim Klick auf den Lauf eines anderen Wettkampfs', () => {
        const current = {competitionId: 'c-1', competitionName: 'CM 1x'}
        expect(nextEventModeSelection(current, slot('c-2', 'JM 4x'))).toEqual({
            competitionId: 'c-2',
            competitionName: 'JM 4x',
        })
    })

    it('schließt die Fläche beim nochmaligen Klick auf denselben Wettkampf', () => {
        const current = {competitionId: 'c-1', competitionName: 'CM 1x'}
        // Auch ein ANDERER Lauf desselben Wettkampfs schließt: rechts steht ohnehin der ganze
        // Wettkampf, ein Wechsel auf sich selbst wäre ein No-Op ohne sichtbare Wirkung.
        expect(nextEventModeSelection(current, slot('c-1'))).toBeNull()
    })

    it('ignoriert Slots ohne Wettkampf (freie Programmpunkte)', () => {
        const current = {competitionId: 'c-1', competitionName: 'CM 1x'}
        expect(nextEventModeSelection(current, slot(null))).toEqual(current)
        expect(nextEventModeSelection(null, slot(null))).toBeNull()
    })

    it('fällt bei fehlendem Wettkampfnamen auf einen leeren String zurück', () => {
        expect(nextEventModeSelection(null, slot('c-1', null))).toEqual({
            competitionId: 'c-1',
            competitionName: '',
        })
    })
})

describe('isSlotSelected', () => {
    const selection = {competitionId: 'c-1', competitionName: 'CM 1x'}

    it('markiert alle Läufe des gewählten Wettkampfs', () => {
        expect(isSlotSelected(selection, slot('c-1'))).toBe(true)
    })

    it('markiert fremde Wettkämpfe und freie Slots nicht', () => {
        expect(isSlotSelected(selection, slot('c-2'))).toBe(false)
        expect(isSlotSelected(selection, slot(null))).toBe(false)
    })

    it('markiert nichts, solange rechts nichts geladen ist', () => {
        expect(isSlotSelected(null, slot('c-1'))).toBe(false)
    })
})
