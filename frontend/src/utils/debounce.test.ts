import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {debounce} from './debounce.ts'

describe('debounce', () => {
    beforeEach(() => {
        vi.useFakeTimers()
    })
    afterEach(() => {
        vi.useRealTimers()
    })

    it('führt nach der Wartezeit genau einmal aus', () => {
        const fn = vi.fn()
        const d = debounce(fn, 500)
        d()
        expect(fn).not.toHaveBeenCalled()
        vi.advanceTimersByTime(500)
        expect(fn).toHaveBeenCalledTimes(1)
    })

    it('bündelt eine Serie schneller Aufrufe zu einem einzigen', () => {
        const fn = vi.fn()
        const d = debounce(fn, 500)
        d()
        vi.advanceTimersByTime(300)
        d()
        vi.advanceTimersByTime(300)
        d()
        // Die Wartezeit startet mit jedem Aufruf neu — bis hier darf nichts gefeuert haben.
        expect(fn).not.toHaveBeenCalled()
        vi.advanceTimersByTime(500)
        expect(fn).toHaveBeenCalledTimes(1)
    })

    it('feuert nach der Ruhephase für einen späteren Auslöser erneut', () => {
        const fn = vi.fn()
        const d = debounce(fn, 500)
        d()
        vi.advanceTimersByTime(500)
        d()
        vi.advanceTimersByTime(500)
        expect(fn).toHaveBeenCalledTimes(2)
    })

    it('cancel verwirft den ausstehenden Aufruf (Unmount-Fall)', () => {
        const fn = vi.fn()
        const d = debounce(fn, 500)
        d()
        d.cancel()
        vi.advanceTimersByTime(1000)
        expect(fn).not.toHaveBeenCalled()
        // cancel ohne ausstehenden Aufruf ist ein No-Op.
        d.cancel()
    })
})
