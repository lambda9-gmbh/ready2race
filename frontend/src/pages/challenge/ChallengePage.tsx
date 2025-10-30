import {
    Box,
    Card,
    CardContent,
    Chip,
    Divider,
    Grid2,
    Stack,
    Typography,
    Alert,
    IconButton,
    DialogTitle,
    DialogContent,
    Button,
} from '@mui/material'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getChallengeInfo, downloadMatchTeamResultDocumentByToken} from '@api/sdk.gen.ts'
import {currentlyInTimespan} from '@utils/helpers.ts'
import Throbber from '@components/Throbber.tsx'
import {useTranslation} from 'react-i18next'
import {ChallengeCompetitionInfoDto} from '@api/types.gen.ts'
import {format} from 'date-fns'
import {challengeRoute} from '@routes'
import EmojiEventsIcon from '@mui/icons-material/EmojiEvents'
import GroupsIcon from '@mui/icons-material/Groups'
import PersonIcon from '@mui/icons-material/Person'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import AccessTimeIcon from '@mui/icons-material/AccessTime'
import DownloadIcon from '@mui/icons-material/Download'
import VisibilityIcon from '@mui/icons-material/Visibility'
import AddIcon from '@mui/icons-material/Add'
import BaseDialog from '@components/BaseDialog.tsx'
import {useRef, useState} from 'react'
import ChallengeResultDialog, {
    ResultInputTeamInfo,
} from '@components/event/competition/registration/ChallengeResultDialog.tsx'

const CompetitionCard = ({
    competition,
    onDownload,
    onView,
    onSubmitResult,
}: {
    competition: ChallengeCompetitionInfoDto
    onDownload: (competitionId: string, docId: string) => void
    onView: (competitionId: string, docId: string) => void
    onSubmitResult: (competition: ChallengeCompetitionInfoDto) => void
}) => {
    const {t} = useTranslation()
    const canSubmitResult =
        !competition.resultInfo &&
        currentlyInTimespan(competition.challengeStart, competition.challengeEnd)

    return (
        <Card elevation={2}>
            <CardContent>
                <Stack spacing={2}>
                    <Stack direction="row" spacing={1} alignItems="center">
                        <EmojiEventsIcon color="primary" />
                        <Typography variant="h6">{competition.name}</Typography>
                        <Chip label={competition.identifier} size="small" />
                    </Stack>

                    <Divider />

                    {/* Time Information */}
                    <Stack spacing={1}>
                        <Stack direction="row" spacing={1} alignItems="center">
                            <AccessTimeIcon fontSize="small" />
                            <Typography variant="body2" color="text.secondary">
                                {t('challenge.period')}:
                            </Typography>
                        </Stack>
                        <Typography variant="body2">
                            {format(new Date(competition.challengeStart), 'PPp')} -{' '}
                            {format(new Date(competition.challengeEnd), 'PPp')}
                        </Typography>
                    </Stack>

                    {/* Team Information */}
                    <Stack spacing={1}>
                        <Stack direction="row" spacing={1} alignItems="center">
                            <GroupsIcon fontSize="small" />
                            <Typography variant="body2" fontWeight="bold">
                                {competition.teamInfo.name || t('challenge.team')}
                            </Typography>
                        </Stack>

                        {competition.teamInfo.namedParticipants.map((np, idx) => (
                            <Box key={idx} sx={{ml: 4}}>
                                <Typography variant="body2" fontWeight="medium">
                                    {np.name}
                                </Typography>
                                {np.participants.map((p, pIdx) => (
                                    <Stack
                                        key={pIdx}
                                        direction="row"
                                        spacing={1}
                                        alignItems="center"
                                        sx={{ml: 2}}>
                                        <PersonIcon fontSize="small" />
                                        <Typography variant="body2">
                                            {p.firstname} {p.lastname} ({p.clubName})
                                        </Typography>
                                    </Stack>
                                ))}
                            </Box>
                        ))}
                    </Stack>

                    {/* Result Information */}
                    {competition.resultInfo && (
                        <Alert severity="success" icon={<CheckCircleIcon />}>
                            <Stack spacing={1}>
                                <Typography variant="body2">
                                    {t('challenge.result')}: {competition.resultInfo.result}
                                </Typography>
                                {competition.resultInfo.proofDocumentId && (
                                    <Stack direction="row" spacing={1} alignItems="center">
                                        <Typography variant="caption">
                                            {t('challenge.proofSubmitted')}
                                        </Typography>
                                        <IconButton
                                            size="small"
                                            onClick={() =>
                                                onView(
                                                    competition.id,
                                                    competition.resultInfo!.proofDocumentId!,
                                                )
                                            }
                                            title={t('common.file.view')}>
                                            <VisibilityIcon fontSize="small" />
                                        </IconButton>
                                        <IconButton
                                            size="small"
                                            onClick={() =>
                                                onDownload(
                                                    competition.id,
                                                    competition.resultInfo!.proofDocumentId!,
                                                )
                                            }
                                            title={t('common.file.download')}>
                                            <DownloadIcon fontSize="small" />
                                        </IconButton>
                                    </Stack>
                                )}
                            </Stack>
                        </Alert>
                    )}

                    {!competition.resultInfo && competition.proofRequired && (
                        <Alert severity="info">
                            <Typography variant="body2">{t('challenge.proofRequired')}</Typography>
                        </Alert>
                    )}

                    {canSubmitResult && (
                        <Button
                            variant="contained"
                            startIcon={<AddIcon />}
                            onClick={() => onSubmitResult(competition)}
                            fullWidth>
                            {t('challenge.submitResult')}
                        </Button>
                    )}
                </Stack>
            </CardContent>
        </Card>
    )
}

const ChallengePage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {accessToken} = challengeRoute.useParams()
    const downloadRef = useRef<HTMLAnchorElement>(null)

    const [viewDocumentDialogOpen, setViewDocumentDialogOpen] = useState(false)
    const [viewDocumentUrl, setViewDocumentUrl] = useState<string | null>(null)

    const [resultDialogOpen, setResultDialogOpen] = useState(false)
    const [selectedCompetition, setSelectedCompetition] =
        useState<ChallengeCompetitionInfoDto | null>(null)

    const {
        data: challengeData,
        pending: challengePending,
        reload: reloadChallengeData,
    } = useFetch(
        signal =>
            getChallengeInfo({
                signal,
                path: {accessToken},
            }),
        {
            onResponse: ({error}) => {
                if (error)
                    feedback.error(
                        t('common.load.error.single', {
                            entity: t('challenge.challenge'),
                        }),
                    )
            },
            deps: [accessToken],
        },
    )

    const handleDownloadDocument = async (competitionId: string, docId: string) => {
        if (!challengeData?.eventId) return
        const {data, error} = await downloadMatchTeamResultDocumentByToken({
            path: {
                eventId: challengeData.eventId,
                competitionId,
                accessToken,
                resultDocumentId: docId,
            },
        })
        const anchor = downloadRef.current

        if (error) {
            feedback.error(t('event.competition.execution.results.document.download.error'))
        } else if (data !== undefined && anchor) {
            anchor.href = URL.createObjectURL(data)
            anchor.download = 'proof-document.jpg'
            anchor.click()
            anchor.href = ''
            anchor.download = ''
        }
    }

    const handleViewDocument = async (competitionId: string, docId: string) => {
        if (!challengeData?.eventId) return
        const {data, error} = await downloadMatchTeamResultDocumentByToken({
            path: {
                eventId: challengeData.eventId,
                competitionId,
                accessToken,
                resultDocumentId: docId,
            },
        })

        if (error) {
            feedback.error(t('event.competition.execution.results.document.download.error'))
        } else if (data !== undefined) {
            setViewDocumentUrl(URL.createObjectURL(data))
            setViewDocumentDialogOpen(true)
        }
    }

    const closeViewDocumentDialog = () => {
        if (viewDocumentUrl) {
            URL.revokeObjectURL(viewDocumentUrl)
        }
        setViewDocumentDialogOpen(false)
        setViewDocumentUrl(null)
    }

    const handleSubmitResult = (competition: ChallengeCompetitionInfoDto) => {
        setSelectedCompetition(competition)
        setResultDialogOpen(true)
    }

    const closeResultDialog = () => {
        setResultDialogOpen(false)
        setSelectedCompetition(null)
    }

    const convertToTeamInfo = (competition: ChallengeCompetitionInfoDto): ResultInputTeamInfo => {
        // Get clubName from first participant
        const clubName = competition.teamInfo.namedParticipants[0]?.participants[0]?.clubName || ''

        return {
            id: competition.teamInfo.id,
            name: competition.teamInfo.name ?? undefined,
            clubName,
            namedParticipants: competition.teamInfo.namedParticipants.map(np => ({
                namedParticipantName: np.name,
                participants: np.participants.map(p => ({
                    firstname: p.firstname,
                    lastname: p.lastname,
                })),
            })),
        }
    }

    return (
        <>
            <a ref={downloadRef} style={{display: 'none'}} />
            {challengePending ? (
                <Stack sx={{display: 'flex', flex: 1, justifyContent: 'center'}}>
                    <Throbber />
                </Stack>
            ) : challengeData ? (
                <Grid2 container spacing={3} p={3}>
                    <Grid2 size={{xs: 12}}>
                        <Typography variant="h4" gutterBottom>
                            {challengeData.eventName}
                        </Typography>
                        <Typography variant="body1" color="text.secondary" gutterBottom>
                            {t('challenge.title')}
                        </Typography>
                    </Grid2>

                    {challengeData.competitions.map(competition => (
                        <Grid2 key={competition.id} size={{xs: 12, md: 6, lg: 4}}>
                            <CompetitionCard
                                competition={competition}
                                onDownload={handleDownloadDocument}
                                onView={handleViewDocument}
                                onSubmitResult={handleSubmitResult}
                            />
                        </Grid2>
                    ))}
                </Grid2>
            ) : (
                <Stack sx={{display: 'flex', flex: 1, justifyContent: 'center', p: 3}}>
                    <Alert severity="error">{t('challenge.notFound')}</Alert>
                </Stack>
            )}
            <BaseDialog
                open={viewDocumentDialogOpen}
                onClose={closeViewDocumentDialog}
                maxWidth={'xl'}>
                <DialogTitle>{t('challenge.proofSubmitted')}</DialogTitle>
                <DialogContent>
                    {viewDocumentUrl && (
                        <Box
                            sx={{
                                display: 'flex',
                                justifyContent: 'center',
                                alignItems: 'center',
                                minHeight: '400px',
                            }}>
                            <img
                                src={viewDocumentUrl}
                                alt={t('challenge.proofSubmitted')}
                                style={{
                                    maxWidth: '100%',
                                    maxHeight: '80vh',
                                    objectFit: 'contain',
                                }}
                            />
                        </Box>
                    )}
                </DialogContent>
            </BaseDialog>
            {selectedCompetition && challengeData && (
                <ChallengeResultDialog
                    dialogOpen={resultDialogOpen}
                    teamDto={convertToTeamInfo(selectedCompetition)}
                    closeDialog={closeResultDialog}
                    reloadTeams={reloadChallengeData}
                    resultConfirmationImageRequired={selectedCompetition.proofRequired}
                    resultType={challengeData.resultType}
                    outsideOfChallengeTimespan={
                        !currentlyInTimespan(
                            selectedCompetition.challengeStart,
                            selectedCompetition.challengeEnd,
                        )
                    }
                    accessToken={accessToken}
                    eventId={challengeData.eventId}
                    competitionId={selectedCompetition.id}
                />
            )}
        </>
    )
}

export default ChallengePage
