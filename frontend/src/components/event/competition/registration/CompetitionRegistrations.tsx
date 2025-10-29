import CompetitionRegistrationDialog from '@components/event/competition/registration/CompetitionRegistrationDialog.tsx'
import CompetitionRegistrationTable from '@components/event/competition/registration/CompetitionRegistrationTable.tsx'
import {useEntityAdministration} from '@utils/hooks.ts'
import {CompetitionDto, CompetitionRegistrationDto, EventDto} from '@api/types.gen.ts'
import {Trans, useTranslation} from 'react-i18next'
import {useAuthenticatedUser} from '@contexts/user/UserContext.ts'
import {Alert, Stack, Typography} from '@mui/material'
import {currentlyInTimespan, getRegistrationState} from '@utils/helpers.ts'
import InlineLink from '@components/InlineLink.tsx'
import ChallengeResultDialog, {
    ResultInputTeamInfo,
} from '@components/event/competition/registration/ChallengeResultDialog.tsx'
import {useState} from 'react'

type Props = {
    eventData: EventDto
    competitionData: CompetitionDto
    reloadEvent: () => void
}
const CompetitionRegistrations = ({eventData, competitionData, reloadEvent}: Props) => {
    const {t} = useTranslation()
    const user = useAuthenticatedUser()

    const [lastChallengeRegistration, setLastChallengeRegistration] =
        useState<ResultInputTeamInfo | null>(null)

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

    const registrationInitialized = (eventData.registrationCount ?? 0) > 0

    return (
        <Stack spacing={2}>
            {registrationPossible && !registrationInitialized && !eventData.challengeEvent && (
                <Alert severity={'info'}>
                    <Typography sx={{mb: 1}}>
                        <Trans i18nKey={'event.competition.registration.noEventRegistration'} />
                        <Trans i18nKey={'event.registerHere.1'} />
                        <InlineLink
                            to={'/event/$eventId/register'}
                            params={{eventId: eventData.id}}>
                            {t('event.registerHere.2')}
                        </InlineLink>
                        <Trans i18nKey={'event.registerHere.3'} />
                    </Typography>
                </Alert>
            )}
            <CompetitionRegistrationDialog
                {...competitionRegistrationProps.dialog}
                openResultDialog={reg => setLastChallengeRegistration(reg)}
                competition={competitionData}
                eventData={eventData}
            />
            {eventData.challengeEvent && (
                <ChallengeResultDialog
                    dialogOpen={!!lastChallengeRegistration}
                    teamDto={lastChallengeRegistration}
                    closeDialog={() => setLastChallengeRegistration(null)}
                    reloadTeams={() => null}
                    resultConfirmationImageRequired={
                        competitionData.properties.challengeConfig
                            ?.resultConfirmationImageRequired ?? false
                    }
                    resultType={eventData.challengeResultType}
                    outsideOfChallengeTimespan={currentlyInTimespan(
                        competitionData.properties.challengeConfig?.startAt,
                        competitionData.properties.challengeConfig?.endAt,
                    )}
                />
            )}
            <CompetitionRegistrationTable
                {...competitionRegistrationProps.table}
                registrationState={registrationState}
                registrationInitialized={registrationInitialized}
                reloadEvent={reloadEvent}
            />
        </Stack>
    )
}
export default CompetitionRegistrations
