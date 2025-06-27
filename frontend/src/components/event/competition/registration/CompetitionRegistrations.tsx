import CompetitionRegistrationDialog from '@components/event/competition/registration/CompetitionRegistrationDialog.tsx'
import CompetitionRegistrationTable from '@components/event/competition/registration/CompetitionRegistrationTable.tsx'
import {useEntityAdministration} from '@utils/hooks.ts'
import {CompetitionDto, CompetitionRegistrationTeamDto, EventDto} from '@api/types.gen.ts'
import {eventRegistrationPossible} from '@utils/helpers.ts'
import {useTranslation} from 'react-i18next'
import {useUser} from '@contexts/user/UserContext.ts'
import {eventRoute} from '@routes'
import {readRegistrationGlobal} from '@authorization/privileges.ts'
import {Link} from '@tanstack/react-router'
import {Box, Button, Typography} from '@mui/material'
import {Forward} from '@mui/icons-material'

type Props = {
    eventData: EventDto | null
    competitionData: CompetitionDto
}
const CompetitionRegistrations = ({eventData, competitionData}: Props) => {
    const {t} = useTranslation()
    const user = useUser()

    const {eventId} = eventRoute.useParams()

    const registrationPossible = eventRegistrationPossible(
        eventData?.registrationAvailableFrom,
        eventData?.registrationAvailableTo,
    )

    const createRegistrationScope = user.loggedIn
        ? user.getPrivilegeScope('CREATE', 'REGISTRATION')
        : undefined
    const updateRegistrationScope = user.loggedIn
        ? user.getPrivilegeScope('CREATE', 'REGISTRATION')
        : undefined

    const competitionRegistrationTeamsProps =
        useEntityAdministration<CompetitionRegistrationTeamDto>(
            t('event.registration.registration'),
            {
                entityCreate:
                    createRegistrationScope === 'GLOBAL' ||
                    (createRegistrationScope === 'OWN' && registrationPossible),
                entityUpdate:
                    updateRegistrationScope === 'GLOBAL' ||
                    (updateRegistrationScope === 'OWN' && registrationPossible),
            },
        )

    console.log(competitionData.registrationCount)

    return (
        (!user.checkPrivilege(readRegistrationGlobal) &&
            (eventData?.registrationCount ?? 0 > 0) && (
                <>
                    <CompetitionRegistrationDialog
                        {...competitionRegistrationTeamsProps.dialog}
                        competition={competitionData}
                        eventId={eventId}
                    />
                    <CompetitionRegistrationTable {...competitionRegistrationTeamsProps.table} />
                </>
            )) ||
        (eventData && (
            <>
                {eventRegistrationPossible(
                    eventData?.registrationAvailableFrom,
                    eventData?.registrationAvailableTo,
                ) && (
                    <Box>
                        <Typography sx={{mb: 1}}>
                            {t('event.competition.registration.noEventRegistration')}
                        </Typography>
                        <Link to={'/event/$eventId/register'} params={{eventId: eventData?.id}}>
                            <Button endIcon={<Forward />} variant={'contained'}>
                                {t('event.registerNow')}
                            </Button>
                        </Link>
                    </Box>
                )}
            </>
        ))
    )
}
export default CompetitionRegistrations
