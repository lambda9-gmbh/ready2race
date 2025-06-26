import {Box, Button, CardActions, CardContent, IconButton, Stack, Typography} from '@mui/material'
import {useEntityAdministration, useFeedback, useFetch} from '@utils/hooks.ts'
import {clubRoute} from '@routes'
import {useTranslation} from 'react-i18next'
import Throbber from '../../components/Throbber.tsx'
import {ClubDto, getClub, getClubUsers, ParticipantDto} from '../../api'
import ParticipantTable from '../../components/participant/ParticipantTable.tsx'
import ParticipantDialog from '../../components/participant/ParticipantDialog.tsx'
import Card from '@mui/material/Card'
import {AccountCircle, Edit, Email} from '@mui/icons-material'
import ClubDialog from "@components/club/ClubDialog.tsx";

const ClubPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {clubId} = clubRoute.useParams()

    const clubProps = useEntityAdministration<ClubDto>(t('club.club'), {entityCreate: false})

    const {data} = useFetch(signal => getClub({signal, path: {clubId: clubId}}), {
        onResponse: ({error}) => {
            if (error) {
                feedback.error(t('common.load.error.single', {entity: t('club.club')}))
                console.log(error)
            }
        },
        deps: [clubId, clubProps.table.lastRequested],
    })

    const {data: userData} = useFetch(signal => getClubUsers({signal, path: {clubId: clubId}}), {
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
                <Stack spacing={2}>
                    <Stack direction={'row'} spacing={1}>
                        <Typography variant="h2">{data.name}</Typography>
                        <IconButton onClick={() => clubProps.table.openDialog(data)}><Edit /></IconButton>
                    </Stack>
                    <Stack direction={'row'}>
                        {userData?.map(u => (
                            <Card key={u.id} sx={{background: '#FFF'}}>
                                <CardContent>
                                    <Stack direction={'row'} spacing={1}>
                                        <Typography variant={'h5'} sx={{color: 'text.secondary'}}>
                                            <AccountCircle fontSize={'large'} />
                                        </Typography>
                                        <Stack>
                                            <Typography variant="h5">
                                                {u.firstname} {u.lastname}
                                            </Typography>
                                            <Typography sx={{color: 'text.secondary'}}>
                                                {u.roles.map(r => r.name).join(', ')}
                                            </Typography>
                                        </Stack>
                                    </Stack>
                                </CardContent>
                                <CardActions sx={{justifyContent: 'end'}}>
                                    <Button
                                        startIcon={<Email />}
                                        size="small"
                                        onClick={e => {
                                            window.location.href = `mailto:${u.email}`
                                            e.preventDefault()
                                        }}>
                                        {u.email}
                                    </Button>
                                </CardActions>
                            </Card>
                        ))}
                    </Stack>
                    <Box>
                        <ParticipantTable
                            {...participantProps.table}
                            title={t('club.participants')}
                        />
                        <ParticipantDialog {...participantProps.dialog} />
                        <ClubDialog {...clubProps.dialog} />
                    </Box>
                </Stack>
            )) || <Throbber />}
        </Box>
    )
}

export default ClubPage
