/**
 * Trailing-Debounce ohne Argumente: Jeder Aufruf startet die Wartezeit neu, ausgeführt wird
 * genau einmal, sobald [waitMs] lang Ruhe war. Gedacht für Benachrichtigungen, bei denen eine
 * Serie schnell aufeinanderfolgender Auslöser nur eine Reaktion verdient — etwa „Speichern &
 * weiter" in der Durchführung, das den Zeitplan daneben nicht mit jedem Klick neu laden soll.
 *
 * `cancel()` verwirft einen noch ausstehenden Aufruf — für das Aufräumen beim Unmount, damit
 * kein Timer in eine bereits abgebaute Komponente hinein feuert.
 */
export const debounce = (
    fn: () => void,
    waitMs: number,
): (() => void) & {cancel: () => void} => {
    let timeoutId: ReturnType<typeof setTimeout> | null = null
    const debounced = () => {
        if (timeoutId != null) {
            clearTimeout(timeoutId)
        }
        timeoutId = setTimeout(() => {
            timeoutId = null
            fn()
        }, waitMs)
    }
    debounced.cancel = () => {
        if (timeoutId != null) {
            clearTimeout(timeoutId)
            timeoutId = null
        }
    }
    return debounced
}
