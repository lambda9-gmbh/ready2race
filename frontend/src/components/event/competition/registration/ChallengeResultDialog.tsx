import BaseDialog from '@components/BaseDialog.tsx'
import {useEffect, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {useFeedback} from '@utils/hooks.ts'
import {
    Button,
    DialogActions,
    DialogContent,
    DialogTitle,
    Divider,
    Stack,
    Typography,
} from '@mui/material'
import {CompetitionRegistrationTeamDto} from '@api/types.gen.ts'
import {FormContainer, useFieldArray, useForm} from 'react-hook-form-mui'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import SelectFileButton from '@components/SelectFileButton.tsx'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import LoadingButton from '@components/form/LoadingButton.tsx'
import {submitChallengeTeamResults} from '@api/sdk.gen.ts'
import {competitionRoute, eventRoute} from '@routes'

type Props = {
    dialogOpen: boolean
    teamDto: CompetitionRegistrationTeamDto | null
    closeDialog: () => void
    reloadTeams: () => void
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
                if (values.length !== 1) {
                    setFileError(t('event.competition.execution.results.dialog.file.missing'))
                    return 'empty'
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

        setConfirming(false)

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
        if (error) {
        } else {
            props.reloadTeams()
            props.closeDialog()
        }
        // TODO ERROR HANDLING
    }

    useEffect(() => {
        if (dialogOpen) {
            formContext.reset(defaultValues)
            setFileError(null)
            setConfirming(false)
        }
    }, [dialogOpen])

    return teamDto ? (
        <BaseDialog open={dialogOpen} onClose={props.closeDialog} maxWidth={'sm'}>
            <DialogTitle>{'[todo] Challenge results'}</DialogTitle>
            <FormContainer formContext={formContext} onSuccess={() => setConfirming(true)}>
                <DialogContent>
                    {!confirming ? (
                        <Stack spacing={4}>
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

                            <FormInputNumber
                                name={'result'}
                                integer
                                required
                                label={'[todo] Result'}
                            />

                            <Stack spacing={2}>
                                <Typography>{filename}</Typography>
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
                                        ? '[todo] Change image file'
                                        : '[todo] Select an image'}
                                </SelectFileButton>
                                {fileError && <Typography color={'error'}>{fileError}</Typography>}
                            </Stack>
                        </Stack>
                    ) : (
                        <Typography>HALLO</Typography>
                    )}
                </DialogContent>
                <DialogActions>
                    <Button onClick={props.closeDialog} disabled={submitting}>
                        {t('common.cancel')}
                    </Button>
                    {!confirming ? (
                        <SubmitButton submitting={submitting}>{t('common.save')}</SubmitButton>
                    ) : (
                        <LoadingButton
                            variant={'contained'}
                            pending={submitting}
                            onClick={() => onSubmit(formContext.getValues())}>
                            {'[todo] Confirm!'}
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
