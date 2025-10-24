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
    InputAdornment,
    Stack,
    Typography,
} from '@mui/material'
import {CompetitionRegistrationTeamDto, MatchResultType} from '@api/types.gen.ts'
import {FormContainer, useFieldArray, useForm} from 'react-hook-form-mui'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import SelectFileButton from '@components/SelectFileButton.tsx'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import LoadingButton from '@components/form/LoadingButton.tsx'
import {submitChallengeTeamResults} from '@api/sdk.gen.ts'
import {competitionRoute, eventRoute} from '@routes'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'

type Props = {
    dialogOpen: boolean
    teamDto: CompetitionRegistrationTeamDto | null
    closeDialog: () => void
    reloadTeams: () => void
    resultConfirmationImageRequired: boolean
    resultType?: MatchResultType
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

    const [fileError, setFileError] = useState<string | null>(null)

    const {fields, append, update} = useFieldArray({
        control: formContext.control,
        name: 'files',
        keyName: 'fieldId',
        rules: {
            validate: values => {
                if (values.length < 1 && props.resultConfirmationImageRequired) {
                    setFileError(
                        t('event.competition.execution.results.confirmationImage.error.empty'),
                    )
                    return 'empty'
                } else if (values.length > 1) {
                    setFileError(
                        t('event.competition.execution.results.confirmationImage.error.tooMany'),
                    )
                    return 'tooMany'
                } else {
                    setFileError(null)
                    return undefined
                }
            },
        },
    })

    const filename = fields[0]?.file?.name

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
            props.reloadTeams()
            props.closeDialog()
        }
    }

    useEffect(() => {
        if (dialogOpen) {
            formContext.reset(defaultValues)
            setFileError(null)
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
                            <>
                                <FormInputNumber
                                    name={'result'}
                                    integer
                                    required
                                    label={resultTypeDescriptor}
                                    slotProps={{
                                        input: {
                                            endAdornment: resultTypeAdornment ? (
                                                <InputAdornment position={'end'}>
                                                    {resultTypeAdornment}
                                                </InputAdornment>
                                            ) : undefined,
                                        },
                                    }}
                                />

                                <Stack spacing={2}>
                                    <FormInputLabel
                                        label={t(
                                            'event.competition.execution.results.confirmationImage.confirmationImage',
                                        )}
                                        required={props.resultConfirmationImageRequired}>
                                        <Typography variant={'body2'}>{filename}</Typography>
                                        <SelectFileButton
                                            variant={'text'}
                                            onSelected={file => {
                                                if (fields.length < 1) {
                                                    append({file})
                                                } else {
                                                    update(0, {file})
                                                }
                                            }}
                                            accept={'.png, .jpg, .jpeg'}>
                                            {filename
                                                ? t(
                                                      'event.competition.execution.results.confirmationImage.change',
                                                  )
                                                : t(
                                                      'event.competition.execution.results.confirmationImage.select',
                                                  )}
                                        </SelectFileButton>
                                    </FormInputLabel>
                                    {fileError && (
                                        <Typography color={'error'}>{fileError}</Typography>
                                    )}
                                </Stack>
                            </>
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
