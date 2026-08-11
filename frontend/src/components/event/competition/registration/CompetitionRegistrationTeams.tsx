import CompetitionRegistrationTeamTable from '@components/event/competition/registration/CompetitionRegistrationTeamTable.tsx'
import {useEntityAdministration} from '@utils/hooks.ts'
import {CompetitionDto, CompetitionRegistrationTeamDto, EventDto} from '@api/types.gen.ts'
import {useAuthenticatedUser} from '@contexts/user/UserContext.ts'
import {useTranslation} from 'react-i18next'

type Props = {
    eventData: EventDto
    competitionData: CompetitionDto
}
const CompetitionRegistrationTeams = ({eventData, competitionData}: Props) => {
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
            /* Der Wettkampf-Wechsler ändert nur den Routen-Parameter, die Seite bleibt gemountet.
               Die geänderte competitionId steckt dann nur in der dataRequest-Closure der Tabelle —
               die useFetch-Deps in EntityTable (paginationModel, sortModel, …) sehen sie nicht,
               der paginierte Request würde also nie neu gefeuert. Der key erzwingt beim Wechsel
               einen Remount samt frischem Fetch; dass Pagination und Suche dabei zurückspringen,
               ist gewollt. */
            <CompetitionRegistrationTeamTable
                key={competitionData.id}
                {...competitionRegistrationTeamProps.table}
                eventData={eventData}
                competitionData={competitionData}
            />
        )
    )
}

export default CompetitionRegistrationTeams
