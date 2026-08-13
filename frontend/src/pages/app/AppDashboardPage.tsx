import {Alert} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useAppSession} from '@contexts/app/AppSessionContext.tsx'
import LiveDashboardPage from '../event/LiveDashboardPage.tsx'

/**
 * Dasselbe Dashboard wie in der Verwaltungsoberfläche, nur im App-Layout.
 * Die Veranstaltung kommt aus der App-Sitzung statt aus der Route.
 */
const AppDashboardPage = () => {
    const {t} = useTranslation()
    const {eventId, navigateTo} = useAppSession()

    if (!eventId) {
        return <Alert severity="info">{t('app.dashboard.noEvent')}</Alert>
    }

    // Kein eigener AppTopTitle mehr: das Dashboard zeigt seinen Titel selbst, ein zweiter
    // darüber verschwendete nur Platz. Der Zurück-Pfeil wandert per `onBack` in die
    // Dashboard-Kopfzeile — nötig bleibt er, weil in der installierten PWA (standalone, ohne
    // Browser-Leiste) der Browser-Zurück-Knopf fehlt und man sonst im Dashboard festsäße
    // (beobachtet am 10.08.2026). Ziel ist die Funktionsauswahl, nicht der Scanner: von dort
    // ist man ins Dashboard gekommen.
    // `cacheReads` nur hier: Der zuletzt geladene Stand gehört aufs Telefon am Steg, nicht in den
    // Speicher eines Arbeitsplatzrechners.
    return (
        <LiveDashboardPage
            eventId={eventId}
            cacheReads
            onBack={() => navigateTo('APP_Function_Select')}
        />
    )
}

export default AppDashboardPage
