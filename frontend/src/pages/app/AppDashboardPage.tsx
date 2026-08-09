import {Alert} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useAppSession} from '@contexts/app/AppSessionContext.tsx'
import LiveDashboardPage from '../event/LiveDashboardPage.tsx'

/**
 * Dasselbe Dashboard wie in der Verwaltungsoberfläche, nur im App-Layout und ohne Chrome.
 * Die Veranstaltung kommt aus der App-Sitzung statt aus der Route.
 */
const AppDashboardPage = () => {
    const {t} = useTranslation()
    const {eventId} = useAppSession()

    if (!eventId) {
        return <Alert severity="info">{t('app.dashboard.noEvent')}</Alert>
    }

    return <LiveDashboardPage eventId={eventId} />
}

export default AppDashboardPage
