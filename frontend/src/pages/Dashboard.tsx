import {Grid2} from '@mui/material'
import {useUser} from '@contexts/user/UserContext.ts'
import {useMemo} from 'react'
import {UpcomingEventsWidget} from '@components/dashboard/UpcomingEventsWidget.tsx'
import {EventRegistrationsWidget} from '@components/dashboard/EventRegistrationsWidget.tsx'
import {readRegistrationGlobal} from '@authorization/privileges.ts'
import {TasksWidget} from '@components/dashboard/TasksWidget.tsx'
import {ShiftWidget} from '@components/dashboard/ShiftWidget.tsx'

const Dashboard = () => {
    const user = useUser()

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
            spacing={{xs: 1.5, lg: 4}}
            p={{xs: 1, lg: 2}}
            height={'100%'}
            alignContent={'flex-start'}
            alignItems={'stretch'}>
            {userId && eventReadPrivilege && (
                <>
                    <TasksWidget userId={userId} />
                    <ShiftWidget userId={userId} />
                </>
            )}
            {registrationReadPrivilege && <EventRegistrationsWidget />}
            {clubId && <UpcomingEventsWidget />}
        </Grid2>
    )
}

export default Dashboard
