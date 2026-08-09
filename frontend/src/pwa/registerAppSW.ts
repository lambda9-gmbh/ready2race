import {useCallback, useEffect, useRef, useState} from 'react'

/**
 * Registriert den Service Worker der Helfer-App - und nur sie. Aufgerufen wird der Hook
 * ausschließlich aus dem AppLayout, damit die Verwaltungsoberfläche keinen bekommt.
 *
 * Registriert wird von Hand statt über `virtual:pwa-register`: Das virtuelle Modul registriert
 * den Worker unter seinem Standardpfad, und der Pfad ist hier die tragende Eigenschaft - nur
 * '/app/sw.js' ergibt den Scope '/app/'.
 */
export const useRegisterAppSW = () => {
    const [needRefresh, setNeedRefresh] = useState(false)
    const registrationRef = useRef<ServiceWorkerRegistration | null>(null)

    useEffect(() => {
        if (!('serviceWorker' in navigator)) {
            return
        }
        let cancelled = false

        const watchInstalling = (registration: ServiceWorkerRegistration) => {
            const installing = registration.installing
            if (installing === null) {
                return
            }
            installing.addEventListener('statechange', () => {
                // Ein Controller ist nur vorhanden, wenn schon eine Fassung lief - beim
                // allerersten Besuch ist das kein Update, sondern die Erstinstallation.
                if (installing.state === 'installed' && navigator.serviceWorker.controller) {
                    setNeedRefresh(true)
                }
            })
        }

        navigator.serviceWorker
            .register('/app/sw.js', {scope: '/app/'})
            .then(registration => {
                if (cancelled) {
                    return
                }
                registrationRef.current = registration
                if (registration.waiting && navigator.serviceWorker.controller) {
                    setNeedRefresh(true)
                }
                registration.addEventListener('updatefound', () => watchInstalling(registration))
            })
            .catch(() => {
                // Kein HTTPS, privater Modus, Browser ohne Unterstuetzung: Die App laeuft
                // unveraendert weiter, nur ohne Offline. Kein Fehlerdialog.
            })

        return () => {
            cancelled = true
        }
    }, [])

    const updateApp = useCallback(() => {
        const waiting = registrationRef.current?.waiting
        if (!waiting) {
            window.location.reload()
            return
        }
        // Erst wenn der neue Worker das Ruder uebernommen hat, wird neu geladen - sonst zeigt die
        // frisch geladene Seite wieder die alte Fassung.
        navigator.serviceWorker.addEventListener(
            'controllerchange',
            () => window.location.reload(),
            {once: true},
        )
        waiting.postMessage({type: 'SKIP_WAITING'})
    }, [])

    return {needRefresh, updateApp}
}

/**
 * Notausstieg für ein einzelnes klemmendes Gerät: Registrierung und Caches weg, dann neu laden.
 *
 * Bewusst eine freie Funktion und nicht Teil des Hooks - sonst müsste jede Stelle, die nur den
 * Notausstieg braucht, den Worker mitregistrieren.
 */
export const resetAppInstallation = async (): Promise<void> => {
    if ('serviceWorker' in navigator) {
        const registrations = await navigator.serviceWorker.getRegistrations()
        await Promise.all(registrations.map(r => r.unregister()))
    }
    if ('caches' in window) {
        const keys = await caches.keys()
        await Promise.all(keys.map(k => caches.delete(k)))
    }
    window.location.reload()
}
