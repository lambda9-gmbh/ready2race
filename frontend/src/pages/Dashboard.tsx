import {Grid2, Typography} from '@mui/material'
import {useUser} from '@contexts/user/UserContext.ts'
import {useTranslation} from 'react-i18next'
import {useMemo} from 'react'
import {UpcomingEventsWidget} from '@components/dashboard/UpcomingEventsWidget.tsx'
import {EventRegistrationsWidget} from '@components/dashboard/EventRegistrationsWidget.tsx'
import {readEventGlobal, readRegistrationGlobal} from '@authorization/privileges.ts'
import {TasksWidget} from '@components/dashboard/TasksWidget.tsx'

const Dashboard = () => {
    const user = useUser()
    const {t} = useTranslation()

    const {userId, clubId, registrationReadPrivilege, eventReadPrivilege} = useMemo(() => {
        return {
            userId: user.loggedIn && user.id,
            clubId: user.loggedIn && user.clubId,
            registrationReadPrivilege: user.checkPrivilege(readRegistrationGlobal),
            eventReadPrivilege: user.checkPrivilege(readEventGlobal),
        }
    }, [user])

    return (
        <Grid2
            container
            spacing={2}
            p={1}
            height={'100%'}
            alignContent={'flex-start'}
            alignItems={'stretch'}>
            <Grid2 size={{xs: 12}}>
                <Typography variant={'h5'}>{t('task.tasks')}</Typography>
            </Grid2>
            {userId && eventReadPrivilege && <TasksWidget userId={userId} />}
            <Grid2 size={{xs: 12}}>
                <Typography variant={'h5'}>{t('event.events')}</Typography>
            </Grid2>
            {registrationReadPrivilege && <EventRegistrationsWidget />}
            {clubId && <UpcomingEventsWidget />}
        </Grid2>
    )
}

export default Dashboard
