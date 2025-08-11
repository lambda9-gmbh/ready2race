import {Paper, Stack, Typography} from '@mui/material'
import {Person, Business, EmojiEvents} from '@mui/icons-material'
import {useTranslation} from 'react-i18next'
import {CheckQrCodeResponse} from '@api/types.gen.ts'
import {useAppSession} from '@contexts/app/AppSessionContext.tsx'
import {updateAppCompetitionCheckGlobal} from '@authorization/privileges.ts'

interface QrAssignmentInfoProps {
    response: CheckQrCodeResponse
}

export const QrAssignmentInfo = ({response}: QrAssignmentInfoProps) => {
    const {t} = useTranslation()
    const {appFunction} = useAppSession()

    return (
        <Paper elevation={2} sx={{p: 2, width: '100%'}}>
            <Stack spacing={1.5}>
                <Typography variant="h6" color="primary">
                    {'type' in response && response.type === 'Participant'
                        ? t('qrParticipant.assignmentInfo')
                        : t('qrAppuser.assignmentInfo')}
                </Typography>

                <Stack direction="row" spacing={1} alignItems="center">
                    <Person color="action" />
                    <Typography>
                        <strong>{t('common.name')}:</strong> {response.firstname}{' '}
                        {response.lastname}
                    </Typography>
                </Stack>

                {response.clubName && (
                    <Stack direction="row" spacing={1} alignItems="center">
                        <Business color="action" />
                        <Typography>
                            <strong>{t('club.club')}:</strong> {response.clubName}
                        </Typography>
                    </Stack>
                )}

                {'type' in response &&
                    response.type === 'Participant' &&
                    'competitions' in response &&
                    response.competitions &&
                    response.competitions.length > 0 &&
                    appFunction !== updateAppCompetitionCheckGlobal.resource && (
                        <Stack spacing={1}>
                            <Stack direction="row" spacing={1} alignItems="center">
                                <EmojiEvents color="action" />
                                <Typography>
                                    <strong>{t('event.competition.competitions')}:</strong>
                                </Typography>
                            </Stack>
                            <Stack sx={{pl: 4}}>
                                {response.competitions.map((comp, index) => (
                                    <Typography key={index} variant="body2">
                                        • {comp}
                                    </Typography>
                                ))}
                            </Stack>
                        </Stack>
                    )}
            </Stack>
        </Paper>
    )
}
