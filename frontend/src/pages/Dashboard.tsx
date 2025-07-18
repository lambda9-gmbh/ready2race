import {Grid2, Typography} from '@mui/material'
import {useUser} from '@contexts/user/UserContext.ts'
import {useTranslation} from 'react-i18next'
import {useMemo} from 'react'
import {UpcomingEventsWidget} from '@components/dashboard/UpcomingEventsWidget.tsx'
import {EventRegistrationsWidget} from '@components/dashboard/EventRegistrationsWidget.tsx'
import {readRegistrationGlobal} from '@authorization/privileges.ts'
import {TasksWidget} from '@components/dashboard/TasksWidget.tsx'
import {ShiftWidget} from '@components/dashboard/ShiftWidget.tsx'

const Dashboard = () => {
    const user = useUser()
    const {t} = useTranslation()

    const {userId, clubId, registrationReadPrivilege, eventReadPrivilege} = useMemo(() => {
        return {
            userId: user.loggedIn && user.id,
            clubId: user.loggedIn && user.clubId,
            registrationReadPrivilege: user.checkPrivilege(readRegistrationGlobal),
            eventReadPrivilege: user.loggedIn
                ? user.clubId === undefined && user.getPrivilegeScope('READ', 'EVENT') !== undefined
                : false,
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
            {userId && eventReadPrivilege && (
                <>
                    <Grid2 size={{xs: 12}}>
                        <Typography variant={'h5'}>{t('common.organisation')}</Typography>
                    </Grid2>
                    <TasksWidget userId={userId} />
                    <ShiftWidget userId={userId} />
                </>
            )}
            <Grid2 size={{xs: 12}}>
                <Typography variant={'h5'}>{t('event.events')}</Typography>
            </Grid2>
            {registrationReadPrivilege && <EventRegistrationsWidget />}
            {clubId && <UpcomingEventsWidget />}
        </Grid2>
    )
}

export default Dashboard
