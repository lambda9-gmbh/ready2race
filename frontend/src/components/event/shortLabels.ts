import {useCallback, useEffect, useState} from 'react'

/**
 * Eine Wahl für zwei Oberflächen: ob Läufe am Kürzel (Rennnummer + Kurzname) oder am
 * ausgeschriebenen Wettkampfnamen hängen. Der Zeitplan-Tab schaltet am Spaltenkopf "Slot", das
 * Schiedsrichter-Board in seiner Kopfzeile — wer einmal umstellt, findet beide Seiten so vor.
 *
 * Der Zustand liegt im localStorage (überlebt Reload und Gerätewechsel innerhalb des Browsers) und
 * wird zusätzlich über ein Fenster-Ereignis verteilt: `storage` feuert nur in ANDEREN Tabs, zwei
 * gleichzeitig sichtbare Umschalter im selben Tab liefen sonst auseinander.
 */
const STORAGE_KEY = 'schedule_short_labels'
const CHANGE_EVENT = 'r2r:short-labels'

export const storedShortLabels = (): boolean => localStorage.getItem(STORAGE_KEY) === 'true'

export const useShortLabels = (): [boolean, () => void] => {
    const [shortLabels, setShortLabels] = useState(storedShortLabels)

    useEffect(() => {
        const sync = () => setShortLabels(storedShortLabels())
        window.addEventListener(CHANGE_EVENT, sync)
        // Aus einem zweiten Tab derselben Veranstaltung - dort feuert nur `storage`.
        window.addEventListener('storage', sync)
        return () => {
            window.removeEventListener(CHANGE_EVENT, sync)
            window.removeEventListener('storage', sync)
        }
    }, [])

    const toggle = useCallback(() => {
        localStorage.setItem(STORAGE_KEY, String(!storedShortLabels()))
        window.dispatchEvent(new Event(CHANGE_EVENT))
    }, [])

    return [shortLabels, toggle]
}
