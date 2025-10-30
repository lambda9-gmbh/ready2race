import BaseDialog from '@components/BaseDialog.tsx'
import {Fragment, useEffect, useMemo, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {useFeedback} from '@utils/hooks.ts'
import {
    Alert,
    AlertTitle,
    Box,
    Button,
    Card,
    Container,
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
import {submitChallengeTeamResults, submitChallengeTeamResultsByToken} from '@api/sdk.gen.ts'
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
    accessToken?: string
    eventId: string
    competitionId: string
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

    const [submitting, setSubmitting] = useState(false)

    const formContext = useForm<Form>()

    const [confirming, setConfirming] = useState(false)

    const onSubmit = async (formData: Form) => {
        if (!teamDto) return

        setSubmitting(true)

        const {error} = props.accessToken
            ? await submitChallengeTeamResultsByToken({
                  path: {
                      eventId: props.eventId,
                      competitionId: props.competitionId,
                      competitionRegistrationId: teamDto.id,
                      accessToken: props.accessToken,
                  },
                  body: {
                      request: {
                          result: Number(formData.result),
                      },
                      files: formData.files.map(({file}) => file),
                  },
              })
            : await submitChallengeTeamResults({
                  path: {
                      eventId: props.eventId,
                      competitionId: props.competitionId,
                      competitionRegistrationId: teamDto.id,
                  },
                  body: {
                      request: {
                          result: Number(formData.result),
                      },
                      files: formData.files.map(({file}) => file),
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
        formContext.reset(defaultValues)
        if (dialogOpen) {
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
        <BaseDialog
            open={dialogOpen}
            onClose={props.closeDialog}
            maxWidth={confirming ? 'md' : 'xs'}>
            <DialogTitle>
                {t('event.competition.execution.results.challenge.challengeResults')}
            </DialogTitle>
            <FormContainer formContext={formContext} onSuccess={() => setConfirming(true)}>
                <DialogContent>
                    <Box>
                        <Container maxWidth={'xs'}>
                            <Stack spacing={4}>
                                {props.outsideOfChallengeTimespan && (
                                    <Alert severity={'warning'} variant={'outlined'}>
                                        {t(
                                            'event.competition.execution.results.challenge.outsideOfTime',
                                        )}
                                    </Alert>
                                )}
                                <Card sx={{p: 2}}>
                                    <Typography>
                                        {teamDto.clubName +
                                            (teamDto.name ? ` ${teamDto.name}` : '')}
                                    </Typography>
                                    {teamDto.namedParticipants.map((namedParticipant, npIdx) => (
                                        <Fragment
                                            key={namedParticipant.namedParticipantName + npIdx}>
                                            {npIdx > 0 && <Divider />}
                                            <Stack spacing={2}>
                                                <Typography>
                                                    {namedParticipant.namedParticipantName}
                                                </Typography>
                                                {namedParticipant.participants.map(participant => (
                                                    <Typography>
                                                        {participant.firstname}{' '}
                                                        {participant.lastname}
                                                    </Typography>
                                                ))}
                                            </Stack>
                                        </Fragment>
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
                                    <Typography variant={'h6'}>
                                        <span style={{fontWeight: 'bold'}}>
                                            {resultTypeDescriptor}:{' '}
                                        </span>
                                        {confirmationFormState.result +
                                            (resultTypeAdornment ? ` ${resultTypeAdornment}` : '')}
                                    </Typography>
                                )}
                            </Stack>
                        </Container>
                        {confirming && confirmationFormState.files.length === 1 && (
                            <Box
                                sx={{
                                    flex: 1,
                                    display: 'flex',
                                    justifyContent: 'center',
                                    alignItems: 'center',
                                    flexDirection: 'column',
                                    gap: 1,
                                    my: 4,
                                }}>
                                {formContext.watch('files')?.[0]?.file && (
                                    <img
                                        src={URL.createObjectURL(
                                            formContext.watch('files')[0].file,
                                        )}
                                        alt="Challenge confirmation"
                                        style={{
                                            maxWidth: '100%',
                                            maxHeight: '600px',
                                            objectFit: 'contain',
                                        }}
                                    />
                                )}
                            </Box>
                        )}
                    </Box>
                    {confirming && (
                        <>
                            <Divider sx={{mb: 2}} />
                            <Box sx={{display: 'flex', justifyContent: 'center'}}>
                                <Alert severity={'error'} sx={{maxWidth: 400}}>
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
                            </Box>
                        </>
                    )}
                </DialogContent>
                <DialogActions>
                    {!confirming ? (
                        <Button onClick={props.closeDialog} disabled={submitting}>
                            {t('common.cancel')}
                        </Button>
                    ) : (
                        <Button onClick={() => setConfirming(false)} disabled={submitting}>
                            {t('common.back')}
                        </Button>
                    )}
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
