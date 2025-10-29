import BaseDialog from '@components/BaseDialog.tsx'
import {useEffect, useMemo, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {useFeedback} from '@utils/hooks.ts'
import {
    Alert,
    AlertTitle,
    Box,
    Button,
    Card,
    DialogActions,
    DialogContent,
    DialogTitle,
    Divider,
    Stack,
    Typography,
} from '@mui/material'
import {MatchResultType} from '@api/types.gen.ts'
import {FormContainer, useForm} from 'react-hook-form-mui'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import LoadingButton from '@components/form/LoadingButton.tsx'
import {submitChallengeTeamResults} from '@api/sdk.gen.ts'
import {competitionRoute, eventRoute} from '@routes'
import ChallengeResultForm from '@components/event/competition/registration/ChallengeResultForm.tsx'

export type ResultInputTeamInfo = {
    id: string
    name?: string
    clubName: string
    namedParticipants: {
        namedParticipantName: string
        participants: {
            firstname: string
            lastname: string
        }[]
    }[]
}

type Props = {
    dialogOpen: boolean
    teamDto: ResultInputTeamInfo | null
    closeDialog: () => void
    reloadTeams: () => void
    resultConfirmationImageRequired: boolean
    resultType?: MatchResultType
    outsideOfChallengeTimespan: boolean
}
type Form = {
    result: string
    files: {
        file: File
    }[]
}
const defaultValues: Form = {
    result: '',
    files: [],
}

const ChallengeResultDialog = ({teamDto, dialogOpen, ...props}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()

    const [submitting, setSubmitting] = useState(false)

    const formContext = useForm<Form>()

    const [confirming, setConfirming] = useState(false)

    const onSubmit = async (formData: Form) => {
        if (!teamDto) return
        if (formData.files.length !== 1) return

        setSubmitting(true)
        const {error} = await submitChallengeTeamResults({
            path: {
                eventId: eventId,
                competitionId: competitionId,
                competitionRegistrationId: teamDto.id,
            },
            body: {
                request: {
                    result: Number(formData.result),
                },
                files: [formData.files[0].file],
            },
        })
        setSubmitting(false)
        setConfirming(false)
        if (error) {
            feedback.error(t('event.competition.execution.results.challenge.error'))
        } else {
            feedback.success(t('event.competition.execution.results.challenge.success'))
            props.reloadTeams()
            props.closeDialog()
        }
    }

    useEffect(() => {
        if (dialogOpen) {
            formContext.reset(defaultValues)
            setConfirming(false)
        }
    }, [dialogOpen])

    const confirmationFormState = useMemo(() => formContext.getValues(), [confirming])

    const resultTypeDescriptor =
        props.resultType === 'DISTANCE'
            ? t('event.competition.execution.results.resultType.distance')
            : ''

    const resultTypeAdornment = props.resultType === 'DISTANCE' ? 'm' : undefined

    return teamDto ? (
        <BaseDialog open={dialogOpen} onClose={props.closeDialog} maxWidth={'xs'}>
            <DialogTitle>
                {t('event.competition.execution.results.challenge.challengeResults')}
            </DialogTitle>
            <FormContainer formContext={formContext} onSuccess={() => setConfirming(true)}>
                <DialogContent>
                    <Stack spacing={4}>
                        {props.outsideOfChallengeTimespan && (
                            <Alert severity={'warning'} variant={'outlined'}>
                                {t('event.competition.execution.results.challenge.outsideOfTime')}
                            </Alert>
                        )}
                        <Card sx={{p: 2}}>
                            <Typography>
                                {teamDto.clubName + (teamDto.name ? ` ${teamDto.name}` : '')}
                            </Typography>
                            {teamDto.namedParticipants.map((namedParticipant, npIdx) => (
                                <>
                                    {npIdx > 0 && <Divider />}
                                    <Stack spacing={2}>
                                        <Typography>
                                            {namedParticipant.namedParticipantName}
                                        </Typography>
                                        {namedParticipant.participants.map(participant => (
                                            <Typography>
                                                {participant.firstname} {participant.lastname}
                                            </Typography>
                                        ))}
                                    </Stack>
                                </>
                            ))}
                        </Card>
                        <Divider />

                        {!confirming ? (
                            <ChallengeResultForm
                                dialogOpen={dialogOpen}
                                proofRequired={props.resultConfirmationImageRequired}
                                resultTypeDescriptor={resultTypeDescriptor}
                                resultTypeAdornment={resultTypeAdornment}
                            />
                        ) : (
                            <>
                                <Box>
                                    <Typography>
                                        <span style={{fontWeight: 'bold'}}>
                                            {resultTypeDescriptor}:{' '}
                                        </span>
                                        {confirmationFormState.result +
                                            (resultTypeAdornment ? ` ${resultTypeAdornment}` : '')}
                                    </Typography>
                                </Box>
                                {confirmationFormState.files.length === 1 && (
                                    <Typography>
                                        <span style={{fontWeight: 'bold'}}>
                                            {t(
                                                'event.competition.execution.results.confirmationImage.confirmationImage',
                                            )}
                                            :{' '}
                                        </span>
                                        {confirmationFormState.files[0].file.name}
                                    </Typography>
                                )}
                                <Alert severity={'warning'}>
                                    <AlertTitle>
                                        {t(
                                            'event.competition.execution.results.challenge.confirmTitle',
                                        )}
                                    </AlertTitle>

                                    <Typography>
                                        {t(
                                            'event.competition.execution.results.challenge.confirmBody',
                                        )}
                                    </Typography>
                                </Alert>
                            </>
                        )}
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={props.closeDialog} disabled={submitting}>
                        {t('common.cancel')}
                    </Button>
                    {!confirming ? (
                        <SubmitButton submitting={submitting}>{t('common.continue')}</SubmitButton>
                    ) : (
                        <LoadingButton
                            variant={'contained'}
                            pending={submitting}
                            onClick={() => onSubmit(formContext.getValues())}>
                            {t('event.competition.execution.results.challenge.confirm')}
                        </LoadingButton>
                    )}
                </DialogActions>
            </FormContainer>
        </BaseDialog>
    ) : (
        <></>
    )
}

export default ChallengeResultDialog
