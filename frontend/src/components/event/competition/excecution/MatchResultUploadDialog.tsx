import BaseDialog from '@components/BaseDialog.tsx'
import {Button, DialogActions, DialogContent, DialogTitle, Stack, Typography} from '@mui/material'
import {Trans, useTranslation} from 'react-i18next'
import {FormContainer, useFieldArray, useForm} from 'react-hook-form-mui'
import {useEffect, useState} from 'react'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import SelectFileButton from '@components/SelectFileButton.tsx'

type Props = {
    open: boolean
    onSuccess: (file: File) => Promise<void>
    onClose: () => void
}

type Form = {
    files: {
        file: File
    }[]
}

const defaultValues: Form = {
    files: [],
}

const MatchResultUploadDialog = ({open, onSuccess, onClose}: Props) => {
    const {t} = useTranslation()
    const formContext = useForm<Form>()
    const [submitting, setSubmitting] = useState(false)

    const [fileError, setFileError] = useState<string | null>(null)

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

    return (
        <BaseDialog open={open} onClose={onClose}>
            <DialogTitle>
                <Trans i18nKey={'event.competition.execution.results.dialog.title'} />
            </DialogTitle>
            <FormContainer
                formContext={formContext}
                onSuccess={async (data: Form) => {
                    setSubmitting(true)
                    await onSuccess(data.files[0].file)
                    setSubmitting(false)
                    onClose()
                }}>
                <DialogContent>
                    <Stack spacing={4}>
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
                                accept={'.xls, .xlsx'}>
                                {filename
                                    ? t('event.competition.execution.results.dialog.file.change')
                                    : t('event.competition.execution.results.dialog.file.choose')}
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
                        <Trans i18nKey={'event.competition.execution.results.upload'} />
                    </SubmitButton>
                </DialogActions>
            </FormContainer>
        </BaseDialog>
    )
}

export default MatchResultUploadDialog
