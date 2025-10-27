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
    files: [],
}

const mapFormToRequest = (formData: Form): ParticipantImportRequest => ({
    separator: formData.separator,
    colFirstname: formData.colFirstname,
    colLastname: formData.colLastname,
    colYear: formData.colYear,
    colGender: formData.colGender,
    colExternalClubname: takeIfNotEmpty(formData.colExternalClubname),
})

const ParticipantImportDialog = ({open, onClose, reloadParticipants}: Props) => {
    const {t} = useTranslation()
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
        await importClubParticipants({
            path: {
                clubId,
            },
            body: {
                request: mapFormToRequest(formData),
                files: formData.files.map(o => o.file),
            },
        })
        setSubmitting(false)
        onClose()
        reloadParticipants()
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
