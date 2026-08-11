/**
 * Erkennungslogik für den Installationshinweis der Helfer-App.
 *
 * Drei Fragen entscheiden, ob auf der Login-Seite etwas angezeigt wird:
 *  1. Läuft die App schon installiert (standalone)? Dann nichts anzeigen.
 *  2. Wurde der Hinweis schon weggeklickt? Dann dauerhaft still bleiben.
 *  3. iOS-Safari ohne Install-API? Dann der Teilen-Hinweis statt des Buttons.
 *
 * Alle Abhängigkeiten (Media-Query, Navigator-Eigenschaften, Speicher) werden injiziert,
 * weil die vitest-Umgebung `node` ist und dort weder `window` noch `localStorage` existiert —
 * dasselbe Muster wie in sessionToken.ts.
 */

export type WebStorageLike = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>

export type MediaQueryLike = Pick<MediaQueryList, 'matches'>

export type DisplayModeEnv = {
    /** window.matchMedia — undefined, wenn der Browser keine Media-Queries kann */
    matchMedia?: (query: string) => MediaQueryLike
    /** navigator.standalone — nur iOS-Safari setzt das Feld überhaupt */
    navigatorStandalone?: boolean
}

export const DISMISSED_KEY = 'pwa_install_hint_dismissed'

/** Läuft die Seite bereits als installierte App? Im Zweifel (keine API): nein. */
export const isStandalone = (env: DisplayModeEnv): boolean => {
    if (env.navigatorStandalone === true) {
        return true
    }
    try {
        return env.matchMedia?.('(display-mode: standalone)').matches === true
    } catch {
        return false
    }
}

/**
 * iOS-Safari erkennen — der einzige Browser, der weder `beforeinstallprompt` feuert noch einen
 * anderen Weg als das Teilen-Menü kennt. Bewusst defensiv: Fremd-Browser auf iOS (CriOS, FxiOS,
 * EdgiOS, OPiOS) und In-App-WebViews (ohne „Safari“-Kennung) fallen durch — lieber gar kein
 * Hinweis als eine Anleitung, die auf dem Gerät nicht stimmt. iPads, die sich als Mac ausgeben
 * (iPadOS 13+ am Desktop-Modus), fallen aus demselben Grund ebenfalls durch.
 */
export const isIosSafari = (userAgent: string): boolean => {
    if (!/iPhone|iPad|iPod/.test(userAgent)) {
        return false
    }
    if (!/Safari/.test(userAgent)) {
        return false
    }
    return !/CriOS|FxiOS|EdgiOS|OPiOS|GSA/.test(userAgent)
}

/** Wurde der Hinweis auf diesem Gerät schon weggeklickt? Bei kaputtem Speicher: nein. */
export const isHintDismissed = (store: WebStorageLike): boolean => {
    try {
        return store.getItem(DISMISSED_KEY) !== null
    } catch {
        return false
    }
}

/** Merkt das Wegklicken dauerhaft. Wirft nie — Safari im privaten Modus lehnt setItem ggf. ab. */
export const dismissHint = (store: WebStorageLike): void => {
    try {
        store.setItem(DISMISSED_KEY, '1')
    } catch {
        // Ohne Speicher kommt der Hinweis beim nächsten Laden wieder - verschmerzbar.
    }
}
