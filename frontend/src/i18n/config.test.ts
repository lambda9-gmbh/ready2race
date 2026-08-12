import {describe, expect, it} from 'vitest'
import i18next from 'i18next'
import './config.ts'

// React escaped beim Rendern selbst — i18next darf interpolierte Variablen deshalb nicht
// zusätzlich escapen. Ohne `escapeValue: false` wurde aus „Steuerfrau/mann" in Dialogen
// „Steuerfrau&#x2F;mann" (der Schrägstrich als HTML-Entity im Klartext).
describe('i18n-Interpolation', () => {
    it('escaped interpolierte Variablen mit Schrägstrich nicht zu HTML-Entities', () => {
        const t = i18next.getFixedT('de')
        const result = t('event.timing.races.deleteConfirm', {name: 'Steuerfrau/mann'})
        expect(result).toContain('Steuerfrau/mann')
        expect(result).not.toContain('&#x2F;')
    })

    it('lässt auch andere HTML-relevante Zeichen unangetastet', () => {
        const t = i18next.getFixedT('de')
        const result = t('event.timing.races.deleteConfirm', {name: 'A & B <Achter>'})
        expect(result).toContain('A & B <Achter>')
        expect(result).not.toContain('&amp;')
        expect(result).not.toContain('&lt;')
    })
})
