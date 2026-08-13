import {describe, expect, it} from 'vitest'
import {EventExportBundleItemDto} from '@api/types.gen.ts'
import {
    bundleAttachDisabledReason,
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

describe('bundleAttachDisabledReason', () => {
    it('erlaubt das Anhängen, sobald die Mappe mindestens ein Dokument trägt', () => {
        expect(bundleAttachDisabledReason(bundle, false, null)).toBeNull()
        // Ein Reload (pending trotz geladener Einträge) lässt den Schalter nicht flackern.
        expect(bundleAttachDisabledReason(bundle, true, null)).toBeNull()
    })

    it('benennt den wahrscheinlichsten Fall: nur der Startlisten-Platzhalter', () => {
        expect(bundleAttachDisabledReason([placeholder('p')], false, null)).toBe('ONLY_STARTLISTS')
        // Leere Liste (über die API unerreichbar, GET legt den Platzhalter an): derselbe Grund -
        // es gibt so oder so nichts anzuhängen.
        expect(bundleAttachDisabledReason([], false, null)).toBe('ONLY_STARTLISTS')
    })

    it('meldet Laden, solange keine Antwort da ist - auch beim Neuanlauf nach einem Fehler', () => {
        expect(bundleAttachDisabledReason(null, true, null)).toBe('LOADING')
        // preCondition noch nicht erfüllt: weder Antwort noch Fehler noch laufender Abruf.
        expect(bundleAttachDisabledReason(null, false, null)).toBe('LOADING')
        // Alter Fehlerstatus während des Retries zählt nicht als Fehler.
        expect(bundleAttachDisabledReason(null, true, 500)).toBe('LOADING')
    })

    it('unterscheidet fehlende Berechtigung vom übrigen Ladefehler', () => {
        expect(bundleAttachDisabledReason(null, false, 401)).toBe('FORBIDDEN')
        expect(bundleAttachDisabledReason(null, false, 403)).toBe('FORBIDDEN')
        expect(bundleAttachDisabledReason(null, false, 500)).toBe('LOAD_FAILED')
        expect(bundleAttachDisabledReason(null, false, 404)).toBe('LOAD_FAILED')
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
