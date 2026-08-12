import {createContext, useContext, useEffect} from 'react'

/**
 * RootLayout beschneidet die Seitenbreite mit einem MUI-Container (maxWidth 'xl'). Für Ansichten,
 * die am Renntag die volle Browserbreite brauchen — derzeit nur der Veranstaltungs-Modus des
 * Zeitplan-Tabs mit seinem Split aus Zeitplan und Durchführung — stellt RootLayout hierüber einen
 * Schalter bereit, der die Beschränkung vorübergehend aufhebt. Als Kontext statt als Prop-Kette,
 * weil zwischen Layout und Zeitplan-Tab mehrere Ebenen liegen (Route, EventPage, TabPanel), die
 * von der Breite nichts wissen müssen.
 */
export const FullWidthLayoutContext = createContext<(active: boolean) => void>(() => {})

/**
 * Hebt die Breitenbeschränkung des Seitencontainers auf, solange [active] gilt und die
 * aufrufende Komponente eingehängt ist. Das Aufräumen übernimmt der Effekt — auch beim
 * Tab-Wechsel, der die Komponente aushängt (TabPanel rendert inaktive Tabs nicht).
 */
export const useFullWidthLayout = (active: boolean): void => {
    const setFullWidth = useContext(FullWidthLayoutContext)
    useEffect(() => {
        if (!active) {
            return
        }
        setFullWidth(true)
        return () => setFullWidth(false)
    }, [active, setFullWidth])
}
