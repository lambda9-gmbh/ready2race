import CompetitionRegistrationTeamTable from '@components/event/competition/registration/CompetitionRegistrationTeamTable.tsx'
import {useEntityAdministration} from '@utils/hooks.ts'
import {CompetitionRegistrationTeamDto, EventDto} from '@api/types.gen.ts'
import {useAuthenticatedUser} from '@contexts/user/UserContext.ts'
import {useTranslation} from 'react-i18next'

type Props = {
    eventData: EventDto
}
const CompetitionRegistrationTeams = ({eventData}: Props) => {
    const {t} = useTranslation()
    const user = useAuthenticatedUser()

    const competitionRegistrationTeamProps =
        useEntityAdministration<CompetitionRegistrationTeamDto>(t('event.registration.teams'), {
            entityCreate: false,
            entityUpdate: false,
            entityDelete: false,
        })

    return (
        ((eventData.registrationCount ?? 0 > 0) || !user.clubId) && (
            <CompetitionRegistrationTeamTable
                {...competitionRegistrationTeamProps.table}
                eventData={eventData}
            />
        )
    )
}

export default CompetitionRegistrationTeams
