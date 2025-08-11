import BaseDialog from "@components/BaseDialog.tsx";
import {Alert, Button, DialogActions, DialogContent, DialogTitle, Stack, Typography} from "@mui/material";
import {Trans, useTranslation} from "react-i18next";
import {FormContainer, useFieldArray, useForm} from "react-hook-form-mui";
import {useEffect, useState} from "react";
import {AutocompleteOption} from "@utils/types.ts";
import {useFetch} from "@utils/hooks.ts";
import {getMatchResultImportConfigs} from "@api/sdk.gen.ts";
import {SubmitButton} from "@components/form/SubmitButton.tsx";
import InlineLink from "@components/InlineLink.tsx";
import FormInputAutocomplete from "@components/form/input/FormInputAutocomplete.tsx";
import SelectFileButton from "@components/SelectFileButton.tsx";

type Props = {
    open: boolean
    onSuccess: (config: string, file: File) => Promise<void>
    onClose: () => void
}

type Form = {
    config: AutocompleteOption
    files: {
        file: File
    }[]
}

const defaultValues: Form = {
    config: null,
    files: [],
}

const MatchResultUploadDialog = ({open, onSuccess, onClose}: Props) => {
    const {t} = useTranslation()
    const formContext = useForm<Form>()
    const [submitting, setSubmitting] = useState(false)

    const [fileError, setFileError] = useState<string | null>(null)

    const {data, pending} = useFetch(
        signal => getMatchResultImportConfigs({signal}),
        {
            mapData: (data) => data.data.map(dto => ({
                id: dto.id,
                label: dto.name,
            }))
        },
    )

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
            }
        }
    })

    const filename = fields[0]?.file?.name

    return (
        <BaseDialog
            open={open}
            onClose={onClose}
        >
            <DialogTitle>
                <Trans i18nKey={'event.competition.execution.results.dialog.title'} />
            </DialogTitle>
            <FormContainer
                formContext={formContext}
                onSuccess={async (data: Form) => {
                    setSubmitting(true)
                    await onSuccess(data.config!.id, data.files[0].file)
                    setSubmitting(false)
                    onClose()
                }}
            >
                <DialogContent>
                    <Stack spacing={4}>
                        <Alert variant={'outlined'} severity={'info'}>
                            <Trans i18nKey={'event.competition.execution.results.dialog.alert.1'} />
                            <InlineLink to={'/config'} search={{tab: 'competition-elements'}} hash={'matchResults'}>
                                <Trans i18nKey={'event.competition.execution.results.dialog.alert.2'} />
                            </InlineLink>
                            <Trans i18nKey={'event.competition.execution.results.dialog.alert.3'} />
                        </Alert>

                        <FormInputAutocomplete
                            name={'config'}
                            options={data ?? []}
                            label={t('event.competition.execution.results.dialog.config')}
                            loading={pending}
                            required
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
                                accept={'application/vnd.ms-excel'}
                            >
                                {filename
                                    ? t('event.competition.execution.results.dialog.file.change')
                                    : t('event.competition.execution.results.dialog.file.choose')
                                }
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