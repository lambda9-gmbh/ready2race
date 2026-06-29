import {Alert, AlertTitle, Grid2} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useFetch} from '@utils/hooks.ts'
import {getOwnPendingClubRepresentativeApproval} from '@api/sdk.gen.ts'
import {useUser} from '@contexts/user/UserContext.ts'

const PendingClubRepresentativeAlert = (props: {gridItem?: boolean}) => {
    const {t} = useTranslation()
    const user = useUser()

    const {data} = useFetch(signal => getOwnPendingClubRepresentativeApproval({signal}), {
        preCondition: () => user.loggedIn,
    })

    // A 204 (no pending approval) is surfaced by the fetch client as an empty object `{}`, which
    // is truthy - so guard on an actual field instead of the object itself.
    if (!data?.clubName) {
        return null
    }

    const alert = (
        <Alert severity={'info'}>
            <AlertTitle>{t('club.representative.pending.title')}</AlertTitle>
            {t('club.representative.pending.message', {club: data.clubName})}
        </Alert>
    )

    return props.gridItem ? <Grid2 size={12}>{alert}</Grid2> : alert
}

export default PendingClubRepresentativeAlert
