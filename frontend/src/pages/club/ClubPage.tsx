import {Box, Typography} from '@mui/material'
import {useEntityAdministration, useFeedback, useFetch} from '../../utils/hooks.ts'
import {clubRoute} from '../../routes.tsx'
import {useTranslation} from 'react-i18next'
import Throbber from '../../components/Throbber.tsx'
import {getClub, ParticipantDto} from '../../api'
import ParticipantTable from '../../components/participant/ParticipantTable.tsx'
import ParticipantDialog from '../../components/participant/ParticipantDialog.tsx'

const ClubPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {clubId} = clubRoute.useParams()

    const {data} = useFetch(signal => getClub({signal, path: {clubId: clubId}}), {
        onResponse: ({error}) => {
            if (error) {
                feedback.error(t('common.load.error.single', {entity: t('club.club')}))
                console.log(error)
            }
        },
        deps: [clubId],
    })

    const participantProps = useEntityAdministration<ParticipantDto>(t('club.participant.title'))

    return (
        <Box sx={{display: 'flex', flexDirection: 'column'}}>
            {(data && (
                <>
                    <Typography variant="h1">{data.name}</Typography>
                    <Box sx={{mt: 4}}>
                        <ParticipantTable
                            {...participantProps.table}
                            title={t('club.participant.title')}
                        />
                        <ParticipantDialog {...participantProps.dialog} />
                    </Box>
                </>
            )) || <Throbber />}
        </Box>
    )
}

export default ClubPage
