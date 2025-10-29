import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import {InputAdornment, Stack, Typography} from '@mui/material'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import SelectFileButton from '@components/SelectFileButton.tsx'
import {useEffect, useState} from 'react'
import {useFieldArray, useFormContext} from 'react-hook-form-mui'
import {useTranslation} from 'react-i18next'

type Form = {
    result: string
    files: {
        file: File
    }[]
}

type Props = {
    dialogOpen: boolean
    proofRequired: boolean
    resultTypeDescriptor: string
    resultTypeAdornment: string | undefined
}

const ChallengeResultForm = ({
    dialogOpen,
    proofRequired,
    resultTypeDescriptor,
    resultTypeAdornment,
}: Props) => {
    const {t} = useTranslation()

    const formContext = useFormContext<Form>()

    const [fileError, setFileError] = useState<string | null>(null)

    useEffect(() => {
        if (dialogOpen) {
            setFileError(null)
        }
    }, [dialogOpen])

    const {fields, append, update} = useFieldArray({
        control: formContext.control,
        name: 'files',
        keyName: 'fieldId',
        rules: {
            validate: values => {
                if (values.length < 1 && proofRequired) {
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

    return (
        <>
            <FormInputNumber
                name={'result'}
                required
                label={resultTypeDescriptor}
                slotProps={{
                    input: {
                        endAdornment: resultTypeAdornment ? (
                            <InputAdornment position={'end'}>{resultTypeAdornment}</InputAdornment>
                        ) : undefined,
                    },
                }}
            />

            <Stack spacing={2}>
                <FormInputLabel
                    label={t(
                        'event.competition.execution.results.confirmationImage.confirmationImage',
                    )}
                    required={proofRequired}>
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
                            ? t('event.competition.execution.results.confirmationImage.change')
                            : t('event.competition.execution.results.confirmationImage.select')}
                    </SelectFileButton>
                </FormInputLabel>
                {fileError && <Typography color={'error'}>{fileError}</Typography>}
            </Stack>
        </>
    )
}

export default ChallengeResultForm
