import BaseDialog from '@components/BaseDialog.tsx'
import {Button, DialogActions, DialogContent, DialogTitle, Stack, Typography} from '@mui/material'
import {Trans, useTranslation} from 'react-i18next'
import {FormContainer, useFieldArray, useForm} from 'react-hook-form-mui'
import {useEffect, useState} from 'react'
import {importClubParticipants} from '@api/sdk.gen.ts'
import {ParticipantImportRequest} from '@api/types.gen.ts'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'
import {clubIndexRoute} from '@routes'
import SelectFileButton from '@components/SelectFileButton.tsx'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {useFeedback} from '@utils/hooks.ts'

type Props = {
    open: boolean
    onClose: () => void
    reloadParticipants: () => void
}

type Form = {
    separator: string
    colFirstname: string
    colLastname: string
    colYear: string
    colGender: string
    colExternalClubname: string
    valueGenderMale: string
    valueGenderFemale: string
    valueGenderDiverse: string
    files: {
        file: File
    }[]
}

const defaultValues: Form = {
    separator: ',',
    colFirstname: '',
    colLastname: '',
    colYear: '',
    colGender: '',
    colExternalClubname: '',
    valueGenderMale: 'M',
    valueGenderFemale: 'F',
    valueGenderDiverse: 'D',
    files: [],
}

const mapFormToRequest = (formData: Form): ParticipantImportRequest => ({
    separator: formData.separator,
    colFirstname: formData.colFirstname,
    colLastname: formData.colLastname,
    colYear: formData.colYear,
    colGender: formData.colGender,
    colExternalClubname: takeIfNotEmpty(formData.colExternalClubname),
    valueGenderMale: formData.valueGenderMale,
    valueGenderFemale: formData.valueGenderFemale,
    valueGenderDiverse: formData.valueGenderDiverse,
})

const ParticipantImportDialog = ({open, onClose, reloadParticipants}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const formContext = useForm<Form>()
    const [submitting, setSubmitting] = useState(false)

    const [fileError, setFileError] = useState<string | null>(null)

    const {clubId} = clubIndexRoute.useParams()

    useEffect(() => {
        if (open) {
            formContext.reset(defaultValues)
            setFileError(null)
        }
    }, [open])

    const {fields, append, update} = useFieldArray({
        control: formContext.control,
        name: 'files',
        keyName: 'fieldId',
        rules: {
            validate: values => {
                if (values.length !== 1) {
                    setFileError(t('club.participant.upload.dialog.file.missing'))
                    return 'empty'
                } else {
                    setFileError(null)
                    return undefined
                }
            },
        },
    })

    const filename = fields[0]?.file?.name

    const handleImport = async (formData: Form) => {
        setSubmitting(true)
        const {error} = await importClubParticipants({
            path: {
                clubId,
            },
            body: {
                request: mapFormToRequest(formData),
                files: formData.files.map(o => o.file),
            },
        })
        setSubmitting(false)

        if (error) {
            if (error.status.value === 400) {
                if (error.errorCode === 'FILE_ERROR') {
                    feedback.error(t('common.error.upload.FILE_ERROR'))
                } else if (error.message === 'Unsupported file type') {
                    // TODO: replace with error code!
                    feedback.error(t('common.error.upload.unsupportedType'))
                } else {
                    feedback.error(t('common.error.unexpected'))
                }
            } else if (error.status.value === 422) {
                const details = 'details' in error && error.details
                switch (error.errorCode) {
                    case 'SPREADSHEET_NO_HEADERS':
                        feedback.error(t('common.error.upload.NO_HEADERS'))
                        break
                    case 'SPREADSHEET_MALFORMED':
                        feedback.error(t('common.error.upload.SPREADSHEET_MALFORMED'))
                        break
                    case 'SPREADSHEET_COLUMN_UNKNOWN':
                        feedback.error(
                            t('common.error.upload.COLUMN_UNKNOWN', details as {expected: string}),
                        )
                        break
                    case 'SPREADSHEET_CELL_BLANK':
                        feedback.error(
                            t(
                                'common.error.upload.CELL_BLANK',
                                details as {row: number; column: string},
                            ),
                        )
                        break
                    case 'SPREADSHEET_WRONG_CELL_TYPE':
                        feedback.error(
                            t(
                                'common.error.upload.WRONG_CELL_TYPE',
                                details as {
                                    row: number
                                    column: string
                                    actual: string
                                    expected: string
                                },
                            ),
                        )
                        break
                    case 'SPREADSHEET_UNPARSABLE_STRING':
                        feedback.error(
                            t(
                                'common.error.upload.UNPARSABLE_STRING',
                                details as {
                                    row: number
                                    column: string
                                    value: string
                                },
                            ),
                        )
                        break
                    default:
                        feedback.error(t('common.error.unexpected'))
                        break
                }
            }
        } else {
            onClose()
            reloadParticipants()
        }
    }

    return (
        <BaseDialog open={open} onClose={onClose}>
            <DialogTitle>
                <Trans i18nKey={'club.participant.import'} />
            </DialogTitle>
            <FormContainer formContext={formContext} onSuccess={handleImport}>
                <DialogContent>
                    <Stack spacing={4}>
                        <FormInputText
                            name={'separator'}
                            label={t('club.participant.upload.dialog.separator')}
                            required
                            rules={{
                                validate: val => {
                                    if (val.length > 1) {
                                        return t(
                                            'club.participant.upload.dialog.error.separatorTooLong',
                                        )
                                    }
                                },
                            }}
                        />
                        <Stack spacing={2}>
                            <Typography variant={'subtitle1'}>
                                <Trans i18nKey={'club.participant.upload.dialog.cols'} />
                            </Typography>
                            <FormInputText
                                name={'colFirstname'}
                                label={t('club.participant.upload.dialog.col.firstname')}
                                required
                            />
                            <FormInputText
                                name={'colLastname'}
                                label={t('club.participant.upload.dialog.col.lastname')}
                                required
                            />
                            <FormInputText
                                name={'colYear'}
                                label={t('club.participant.upload.dialog.col.year')}
                                required
                            />
                            <FormInputText
                                name={'colGender'}
                                label={t('club.participant.upload.dialog.col.gender')}
                                required
                            />
                            <FormInputText
                                name={'colExternalClubname'}
                                label={t('club.participant.upload.dialog.col.external')}
                            />
                        </Stack>
                        <Stack spacing={2}>
                            <Typography variant={'subtitle1'}>
                                <Trans i18nKey={'club.participant.upload.dialog.genderValues'} />
                            </Typography>
                            <FormInputText
                                name={'valueGenderMale'}
                                label={t('club.participant.upload.dialog.value.gender.male')}
                                required
                            />
                            <FormInputText
                                name={'valueGenderFemale'}
                                label={t('club.participant.upload.dialog.value.gender.female')}
                                required
                            />
                            <FormInputText
                                name={'valueGenderDiverse'}
                                label={t('club.participant.upload.dialog.value.gender.diverse')}
                                required
                            />
                        </Stack>
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
                                accept={'.csv'}>
                                {filename
                                    ? t('club.participant.upload.dialog.file.change')
                                    : t('club.participant.upload.dialog.file.choose')}
                            </SelectFileButton>
                            {fileError && <Typography color={'error'}>{fileError}</Typography>}
                        </Stack>
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={onClose} disabled={submitting}>
                        <Trans i18nKey={'common.cancel'} />
                    </Button>
                    <SubmitButton submitting={submitting}>
                        <Trans i18nKey={'club.participant.upload.dialog.submit'} />
                    </SubmitButton>
                </DialogActions>
            </FormContainer>
        </BaseDialog>
    )
}

export default ParticipantImportDialog
