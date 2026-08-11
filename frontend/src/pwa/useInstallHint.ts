import {useCallback, useEffect, useState} from 'react'
import {dismissHint, isHintDismissed, isIosSafari, isStandalone} from './installHint.ts'

/**
 * `beforeinstallprompt` ist in den DOM-Typen nicht deklariert (Chromium-only), daher hier
 * die schmale Eigenbeschreibung der beiden Felder, die gebraucht werden.
 */
type BeforeInstallPromptEvent = Event & {
    prompt: () => Promise<void>
    userChoice: Promise<{outcome: 'accepted' | 'dismissed'}>
}

/**
 * Das Ereignis feuert genau einmal pro Seitenladen — unter Umständen bevor React die
 * Login-Seite montiert hat. Deshalb fängt ein Listener auf Modulebene es ab und hält es fest;
 * der Hook holt es sich beim Mounten oder wird benachrichtigt, falls es später eintrifft.
 */
let capturedPrompt: BeforeInstallPromptEvent | null = null
const promptArrivedListeners = new Set<() => void>()

if (typeof window !== 'undefined') {
    window.addEventListener('beforeinstallprompt', event => {
        // Chromes Mini-Infobar unterdrücken - wir zeigen stattdessen den eigenen Button
        event.preventDefault()
        capturedPrompt = event as BeforeInstallPromptEvent
        promptArrivedListeners.forEach(notify => notify())
    })
}

/**
 * Liefert der Login-Seite alles für den Installationshinweis:
 *
 *  - `showInstallButton`: Android/Chromium hat `beforeinstallprompt` geliefert — ohne das
 *    Ereignis gibt es keinen Button (kein toter Knopf).
 *  - `showIosHint`: iOS-Safari ohne Install-API — dezenter Teilen-Hinweis stattdessen.
 *  - Beides ist false, wenn die App schon installiert läuft oder der Hinweis weggeklickt wurde.
 */
export const useInstallHint = () => {
    // Einmal beim Mounten entschieden - der Anzeigemodus ändert sich während der Sitzung nicht
    const [standalone] = useState(() =>
        isStandalone({
            matchMedia: typeof window !== 'undefined' ? window.matchMedia.bind(window) : undefined,
            navigatorStandalone: (navigator as Navigator & {standalone?: boolean}).standalone,
        }),
    )
    const [dismissed, setDismissed] = useState(() => isHintDismissed(localStorage))
    const [canInstall, setCanInstall] = useState(capturedPrompt !== null)
    const [iosSafari] = useState(() => isIosSafari(navigator.userAgent))

    useEffect(() => {
        const notify = () => setCanInstall(true)
        promptArrivedListeners.add(notify)
        return () => void promptArrivedListeners.delete(notify)
    }, [])

    const promptInstall = useCallback(async () => {
        const prompt = capturedPrompt
        if (prompt === null) {
            return
        }
        // Aufräumen vor dem Dialog: Das Ereignis ist verbraucht, egal wie die Wahl ausfällt -
        // ein zweites prompt() auf demselben Ereignis würfe nur eine Exception.
        capturedPrompt = null
        setCanInstall(false)
        await prompt.prompt()
        await prompt.userChoice
    }, [])

    const dismiss = useCallback(() => {
        dismissHint(localStorage)
        setDismissed(true)
    }, [])

    const visible = !standalone && !dismissed

    return {
        showInstallButton: visible && canInstall,
        showIosHint: visible && !canInstall && iosSafari,
        promptInstall,
        dismiss,
    }
}
