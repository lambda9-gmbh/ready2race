import CompetitionRegistrationDialog from '@components/event/competition/registration/CompetitionRegistrationDialog.tsx'
import CompetitionRegistrationTable from '@components/event/competition/registration/CompetitionRegistrationTable.tsx'
import {useEntityAdministration} from '@utils/hooks.ts'
import {CompetitionDto, CompetitionRegistrationDto, EventDto} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'
import {useAuthenticatedUser} from '@contexts/user/UserContext.ts'
import {Link} from '@tanstack/react-router'
import {Box, Button, Stack, Typography} from '@mui/material'
import {Forward} from '@mui/icons-material'
import {getRegistrationState} from '@utils/helpers.ts'

type Props = {
    eventData: EventDto
    competitionData: CompetitionDto
}
const CompetitionRegistrations = ({eventData, competitionData}: Props) => {
    const {t} = useTranslation()
    const user = useAuthenticatedUser()

    const registrationState = getRegistrationState(
        eventData,
        competitionData.properties.lateRegistrationAllowed,
    )

    const registrationPossible = registrationState !== 'CLOSED'

    const registrationScope = user.getPrivilegeScope('UPDATE', 'REGISTRATION')
    const actionsAllowed =
        registrationScope === 'GLOBAL' || (registrationScope === 'OWN' && registrationPossible)

    const competitionRegistrationProps = useEntityAdministration<CompetitionRegistrationDto>(
        t('event.registration.registration'),
        {
            entityCreate: actionsAllowed,
            entityUpdate: actionsAllowed,
            entityDelete: actionsAllowed,
        },
    )

    return (
        (((eventData.registrationCount ?? 0 > 0) || !user.clubId) && (
            <Stack spacing={2}>
                <CompetitionRegistrationDialog
                    {...competitionRegistrationProps.dialog}
                    competition={competitionData}
                    eventData={eventData}
                />
                <CompetitionRegistrationTable
                    {...competitionRegistrationProps.table}
                    registrationState={registrationState}
                />
            </Stack>
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
