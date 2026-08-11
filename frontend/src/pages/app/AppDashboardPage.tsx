import {Alert} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useAppSession} from '@contexts/app/AppSessionContext.tsx'
import LiveDashboardPage from '../event/LiveDashboardPage.tsx'
import AppTopTitle from '@components/qrApp/AppTopTitle.tsx'

/**
 * Dasselbe Dashboard wie in der Verwaltungsoberfläche, nur im App-Layout.
 * Die Veranstaltung kommt aus der App-Sitzung statt aus der Route.
 */
const AppDashboardPage = () => {
    const {t} = useTranslation()
    const {eventId} = useAppSession()

    if (!eventId) {
        return <Alert severity="info">{t('app.dashboard.noEvent')}</Alert>
    }

    // Kopfzeile mit Zurück-Pfeil zur Funktionsauswahl: In der installierten PWA (standalone, ohne
    // Browser-Leiste) saß man sonst im Dashboard fest - der Browser-Zurück-Knopf fehlt dort
    // (beobachtet am 10.08.2026). Ziel ist die Funktionsauswahl, nicht der Scanner: von dort ist
    // man ins Dashboard gekommen.
    // `cacheReads` nur hier: Der zuletzt geladene Stand gehört aufs Telefon am Steg, nicht in den
    // Speicher eines Arbeitsplatzrechners.
    return (
        <>
            <AppTopTitle
                title={t('event.liveDashboard.title')}
                backTarget={'APP_Function_Select'}
            />
            <LiveDashboardPage eventId={eventId} cacheReads />
        </>
    )
}

export default AppDashboardPage
