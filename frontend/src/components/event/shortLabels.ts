import {useCallback, useEffect, useState} from 'react'

/**
 * Eine Wahl für zwei Oberflächen: ob Läufe am Kürzel (Rennnummer + Kurzname) oder am
 * ausgeschriebenen Wettkampfnamen hängen. Der Zeitplan-Tab schaltet am Spaltenkopf "Slot", das
 * Schiedsrichter-Board in seiner Kopfzeile — wer einmal umstellt, findet beide Seiten so vor.
 *
 * Der Zustand liegt im localStorage (überlebt Reload und Neustart des Browsers, gilt aber nur für
 * diesen Browser auf diesem Gerät) und wird zusätzlich über ein Fenster-Ereignis verteilt:
 * `storage` feuert nur in ANDEREN Tabs, zwei gleichzeitig sichtbare Umschalter im selben Tab liefen
 * sonst auseinander.
 *
 * Solange NICHT umgeschaltet wurde, entscheidet die Oberfläche selbst ([fallback]): Das Board
 * startet in der Kurzform — am Steg zählt, welches Rennen dran ist, nicht wie es ausgeschrieben
 * heißt —, der Zeitplan-Tab bei den vollen Namen. Ein Klick ist eine Ansage und gilt ab dann für
 * beide Seiten; nur die Vorbelegung ist getrennt.
 */
const STORAGE_KEY = 'schedule_short_labels'
const CHANGE_EVENT = 'r2r:short-labels'

/** Die getroffene Wahl, oder null, solange keine getroffen wurde. */
const storedChoice = (): boolean | null => {
    const value = localStorage.getItem(STORAGE_KEY)
    return value === null ? null : value === 'true'
}

export const shortLabelsOrDefault = (fallback: boolean): boolean => storedChoice() ?? fallback

export const useShortLabels = (fallback: boolean = false): [boolean, () => void] => {
    const [shortLabels, setShortLabels] = useState(() => shortLabelsOrDefault(fallback))

    useEffect(() => {
        const sync = () => setShortLabels(shortLabelsOrDefault(fallback))
        sync()
        window.addEventListener(CHANGE_EVENT, sync)
        // Aus einem zweiten Tab derselben Veranstaltung - dort feuert nur `storage`.
        window.addEventListener('storage', sync)
        return () => {
            window.removeEventListener(CHANGE_EVENT, sync)
            window.removeEventListener('storage', sync)
        }
    }, [fallback])

    // Gedreht wird, was die Oberfläche gerade zeigt, nicht was gespeichert ist: ohne getroffene
    // Wahl steht auf dem Board die Kurzform, ein Klick muss dort zur Langform führen.
    const toggle = useCallback(() => {
        localStorage.setItem(STORAGE_KEY, String(!shortLabelsOrDefault(fallback)))
        window.dispatchEvent(new Event(CHANGE_EVENT))
    }, [fallback])

    return [shortLabels, toggle]
}
