import {describe, expect, it} from 'vitest'
import {EventExportBundleItemDto} from '@api/types.gen.ts'
import {
    excludedItemsParam,
    includesGeneratedStartlists,
    initialBundleSelection,
    isPdfName,
    reorderedItemIds,
    toggleBundleItem,
} from './exportBundle.ts'

const doc = (id: string, name: string): EventExportBundleItemDto => ({
    id,
    kind: 'DOCUMENT',
    document: `doc-${id}`,
    documentName: name,
})

const placeholder = (id: string): EventExportBundleItemDto => ({
    id,
    kind: 'GENERATED_STARTLISTS',
})

const bundle = [doc('a', 'Hinweise.pdf'), placeholder('p'), doc('b', 'Regelwerk.pdf')]

describe('reorderedItemIds', () => {
    it('tauscht mit dem direkten Nachbarn', () => {
        expect(reorderedItemIds(bundle, 'p', 'up')).toEqual(['p', 'a', 'b'])
        expect(reorderedItemIds(bundle, 'p', 'down')).toEqual(['a', 'b', 'p'])
    })

    it('liefert null am Rand der Liste', () => {
        expect(reorderedItemIds(bundle, 'a', 'up')).toBeNull()
        expect(reorderedItemIds(bundle, 'b', 'down')).toBeNull()
    })

    it('liefert null für unbekannte Einträge', () => {
        expect(reorderedItemIds(bundle, 'fremd', 'up')).toBeNull()
    })
})

describe('isPdfName', () => {
    it('erkennt PDF-Dateinamen unabhängig von der Schreibung', () => {
        expect(isPdfName('Hinweise.pdf')).toBe(true)
        expect(isPdfName('REGELWERK.PDF')).toBe(true)
        expect(isPdfName('zeitplan.xlsx')).toBe(false)
        expect(isPdfName('pdf')).toBe(false)
    })
})

describe('Auswahl im Export-Dialog', () => {
    it('startet mit allem gewählt und lässt den Parameter dann weg', () => {
        const selected = initialBundleSelection(bundle)
        expect(selected.size).toBe(3)
        expect(excludedItemsParam(bundle, selected)).toBeUndefined()
    })

    it('schickt genau die abgewählten Einträge', () => {
        const selected = toggleBundleItem(initialBundleSelection(bundle), 'b')
        expect(excludedItemsParam(bundle, selected)).toEqual(['b'])
    })

    it('toggelt hin und zurück', () => {
        const once = toggleBundleItem(initialBundleSelection(bundle), 'a')
        expect(once.has('a')).toBe(false)
        const twice = toggleBundleItem(once, 'a')
        expect(twice.has('a')).toBe(true)
    })
})

describe('includesGeneratedStartlists', () => {
    it('true, solange der Platzhalter gewählt ist', () => {
        expect(includesGeneratedStartlists(bundle, initialBundleSelection(bundle))).toBe(true)
    })

    it('false, wenn der Platzhalter abgewählt wurde', () => {
        const selected = toggleBundleItem(initialBundleSelection(bundle), 'p')
        expect(includesGeneratedStartlists(bundle, selected)).toBe(false)
    })

    it('true ohne Platzhalter-Datensatz - der Server hängt die Startlisten dann hinten an', () => {
        const withoutPlaceholder = [doc('a', 'Hinweise.pdf')]
        expect(
            includesGeneratedStartlists(withoutPlaceholder, initialBundleSelection(withoutPlaceholder)),
        ).toBe(true)
    })
})
