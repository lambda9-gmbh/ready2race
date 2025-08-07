import CompetitionRegistrationDialog from '@components/event/competition/registration/CompetitionRegistrationDialog.tsx'
import CompetitionRegistrationTable from '@components/event/competition/registration/CompetitionRegistrationTable.tsx'
import {useEntityAdministration} from '@utils/hooks.ts'
import {CompetitionDto, CompetitionRegistrationTeamDto, EventDto, OpenForRegistrationType} from '@api/types.gen.ts'
import {eventRegistrationPossible} from '@utils/helpers.ts'
import {useTranslation} from 'react-i18next'
import {useAuthenticatedUser} from '@contexts/user/UserContext.ts'
import {Link} from '@tanstack/react-router'
import {Box, Button, Typography} from '@mui/material'
import {Forward} from '@mui/icons-material'

type Props = {
    eventData: EventDto
    competitionData: CompetitionDto
}
const CompetitionRegistrations = ({eventData, competitionData}: Props) => {
    const {t} = useTranslation()
    const user = useAuthenticatedUser()

    const registrationState: OpenForRegistrationType =
        eventRegistrationPossible(
            eventData.registrationAvailableFrom,
            eventData.registrationAvailableTo,
        ) ? 'REGULAR' : competitionData.properties.lateRegistrationAllowed && eventRegistrationPossible(
            eventData.registrationAvailableTo,
            eventData.lateRegistrationAvailableTo,
        ) ? 'LATE' : 'CLOSED'

    const registrationPossible = registrationState !== 'CLOSED'

    const registrationScope = user.getPrivilegeScope('UPDATE', 'REGISTRATION')
    const actionsAllowed = registrationScope === 'GLOBAL' || (registrationScope === 'OWN' && registrationPossible)

    const competitionRegistrationTeamsProps =
        useEntityAdministration<CompetitionRegistrationTeamDto>(
            t('event.registration.registration'),
            {
                entityCreate: actionsAllowed,
                entityUpdate: actionsAllowed,
                entityDelete: actionsAllowed,
            },
        )

    return (
        (((eventData.registrationCount ?? 0 > 0) || !user.clubId) && (
            <>
                <CompetitionRegistrationDialog
                    {...competitionRegistrationTeamsProps.dialog}
                    competition={competitionData}
                    eventData={eventData}
                />
                <CompetitionRegistrationTable
                    {...competitionRegistrationTeamsProps.table}
                    competition={competitionData}
                    registrationState={registrationState}
                />
            </>
        )) ||
        (registrationPossible && (
            <Box>
                <Typography sx={{mb: 1}}>
                    {t('event.competition.registration.noEventRegistration')}
                </Typography>
                <Link to={'/event/$eventId/register'} params={{eventId: eventData.id}}>
                    <Button endIcon={<Forward />} variant={'contained'}>
                        {t('event.registerNow')}
                    </Button>
                </Link>
            </Box>
        ))
    )
}
export default CompetitionRegistrations
