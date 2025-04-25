import {Grid2, Typography} from '@mui/material'
import {useUser} from '@contexts/user/UserContext.ts'
import {useTranslation} from 'react-i18next'
import {useMemo} from 'react'
import {UpcomingEventsWidget} from '@components/dashboard/UpcomingEventsWidget.tsx'
import {EventRegistrationsWidget} from '@components/dashboard/EventRegistrationsWidget.tsx'
import {readRegistrationGlobal} from '@authorization/privileges.ts'

const Dashboard = () => {
    const user = useUser()
    const {t} = useTranslation()

    const {clubId, registrationReadPrivilege} = useMemo(() => {
        return {
            clubId: user.loggedIn && user.clubId,
            registrationReadPrivilege: user.checkPrivilege(readRegistrationGlobal),
        }
    }, [user])

    return (
        <Grid2 container spacing={2} p={1} height={'100%'} alignContent={'flex-start'}>
            {/*{verein && <VereinWidget vereinId={verein} />}*/}
            <Grid2 size={{xs: 12}}>
                <Typography variant={'h5'}>{t('event.events')}</Typography>
            </Grid2>
            {registrationReadPrivilege && <EventRegistrationsWidget />}
            {clubId && <UpcomingEventsWidget />}
        </Grid2>
    )
}

export default Dashboard
