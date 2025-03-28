import {ParticipantRequirementCheckForEventConfigDto} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'
import {
    checkParticipantRequirementsForEvent,
    getActiveParticipantRequirementsForEvent,
} from '@api/sdk.gen.ts'
import {useFieldArray, useForm, useFormContext} from 'react-hook-form-mui'
import {useCallback, useMemo, useState} from 'react'
import EntityDialog from '@components/EntityDialog.tsx'
import {Alert, Box, IconButton, Stack, Typography} from '@mui/material'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {AutocompleteOption, BaseEntityDialogProps} from '@utils/types.ts'
import {eventRoute} from '@routes'
import {Delete, Info} from '@mui/icons-material'
import SelectFileButton from '@components/SelectFileButton.tsx'
import FormInputAutocomplete from '@components/form/input/FormInputAutocomplete.tsx'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'

type ParticipantRequirementCheckForEventForm = {
    requirementId: string
    separator?: string
    charset?: string
    firstnameColName: string
    lastnameColName: string
    yearsColName?: string
    clubColName?: string
    requirementColName?: string
    requirementIsValidValue?: string
    files: {
        file: File
    }[]
}

const FileSelection = () => {
    const formContext = useFormContext<ParticipantRequirementCheckForEventForm>()
    const {t} = useTranslation()

    const {fields, append, remove} = useFieldArray({
        control: formContext.control,
        name: 'files',
        keyName: 'fieldId',
        rules: {
            validate: values => {
                if (values.length < 1) {
                    setEmptyListError(t('event.document.error.emptyList'))
                    return 'empty'
                }
            },
        },
    })

    const [emptyListError, setEmptyListError] = useState<string | null>(null)

    return (
        <Box
            sx={{
                display: 'flex',
                flexDirection: 'column',
            }}>
            {emptyListError && <Typography color={'error'}>{emptyListError}</Typography>}
            {fields.map((field, index) => (
                <Stack
                    direction={'row'}
                    spacing={4}
                    justifyContent={'space-between'}
                    key={field.fieldId}>
                    <Typography>{field.file.name}</Typography>
                    <IconButton onClick={() => remove(index)}>
                        <Delete />
                    </IconButton>
                </Stack>
            ))}
            <SelectFileButton
                variant={'outlined'}
                multiple
                onSelected={files => {
                    Array.from(files).forEach(file => append({file}))
                }}
                accept={'.csv'}>
                {t('common.form.selectFile')}
            </SelectFileButton>
        </Box>
    )
}

const ParticipantRequirementCheckForEventUploadFileDialog = (
    props: BaseEntityDialogProps<ParticipantRequirementCheckForEventConfigDto>,
) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()

    const addAction = (formData: ParticipantRequirementCheckForEventForm) => {
        let {files, ...config} = formData

        return checkParticipantRequirementsForEvent({
            path: {eventId},
            body: {
                config,
                files: files.map(file => file.file),
            },
        })
    }

    const {data: requirementsData, pending: requirementsPending} = useFetch(
        signal =>
            getActiveParticipantRequirementsForEvent({
                signal,
                path: {eventId},
                query: {sort: JSON.stringify([{field: 'NAME', direction: 'ASC'}])},
            }),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('participantRequirement.participantRequirement'),
                        }),
                    )
                }
            },
        },
    )

    const requirements: AutocompleteOption[] = useMemo(
        () =>
            requirementsData?.data.map(dto => ({
                id: dto.id,
                label: dto.name,
            })) ?? [],
        [requirementsData?.data],
    )

    const defaultValues: ParticipantRequirementCheckForEventForm = {
        requirementId: requirements[0]?.id ?? '',
        separator: ';',
        charset: 'UTF-8',
        firstnameColName: t('entity.firstname'),
        lastnameColName: t('entity.lastname'),
        files: [],
    }

    const formContext = useForm<ParticipantRequirementCheckForEventForm>()

    const onOpen = useCallback(() => {
        formContext.reset(defaultValues)
    }, [requirements])

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            addAction={addAction}
            title={t('event.participantRequirement.checkUpload')}>
            <Stack spacing={4}>
                <FormInputAutocomplete
                    loading={requirementsPending}
                    required={true}
                    name={'requirementId'}
                    options={requirements}
                    matchId={true}
                    label={t('participantRequirement.participantRequirement')}
                />
                <FormInputLabel label={t('event.participantRequirement.file')} required={true}>
                    <FileSelection />
                </FormInputLabel>
                <FormInputText name="charset" label={t('event.participantRequirement.charset')} />
                <FormInputText
                    name="separator"
                    label={t('event.participantRequirement.separator')}
                />
                <FormInputText
                    name="firstnameColName"
                    label={t('event.participantRequirement.firstnameColName')}
                    required
                />
                <FormInputText
                    name="lastnameColName"
                    label={t('event.participantRequirement.lastnameColName')}
                    required
                />
                <FormInputText
                    name="yearsColName"
                    label={t('event.participantRequirement.yearsColName')}
                />
                <FormInputText
                    name="clubColName"
                    label={t('event.participantRequirement.clubColName')}
                />
                <FormInputText
                    name="requirementColName"
                    label={t('event.participantRequirement.requirementColName')}
                />
                <Alert icon={<Info />} severity={'info'}>
                    {t('event.participantRequirement.info')}
                </Alert>
                <FormInputText
                    name="requirementIsValidValue"
                    label={t('event.participantRequirement.requirementIsValidValue')}
                />
            </Stack>
        </EntityDialog>
    )
}

export default ParticipantRequirementCheckForEventUploadFileDialog
