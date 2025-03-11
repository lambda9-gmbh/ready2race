import EntityDialog from '@components/EntityDialog.tsx'
import {AutocompleteOption, BaseEntityDialogProps} from '@utils/types.ts'
import {EventDocumentDto, EventDocumentRequest, EventDocumentTypeDto} from '@api/types.gen.ts'
import {useFieldArray, useForm, useFormContext} from 'react-hook-form-mui'
import {useCallback, useState} from 'react'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'
import FormInputAutocomplete from '@components/form/input/FormInputAutocomplete.tsx'
import {useFetch} from '@utils/hooks.ts'
import {addDocuments, getDocumentTypes, updateDocument} from '@api/sdk.gen.ts'
import {eventIndexRoute} from '@routes'
import {Box, IconButton, Stack, Typography} from '@mui/material'
import {Delete} from '@mui/icons-material'
import SelectFileButton from '@components/SelectFileButton.tsx'
import {useTranslation} from 'react-i18next'

type Form = {
    documentType: AutocompleteOption
    files: {
        file: File
    }[]
}

const defaultValues: Form = {
    documentType: {
        id: '',
        label: '',
    },
    files: [],
}

const typeDtoToOption = (dto: EventDocumentTypeDto | undefined): AutocompleteOption => ({
    id: dto?.id ?? '',
    label: dto?.name ?? '',
})

const mapFormToRequest = (form: Form): EventDocumentRequest => ({
    documentType: takeIfNotEmpty(form.documentType?.id),
})

const mapEntityToForm = (entity: EventDocumentDto): Form => ({
    documentType: typeDtoToOption(entity.documentType),
    files: [],
})

const FileSelection = () => {
    const formContext = useFormContext<Form>()
    const {t} = useTranslation()

    const {fields, append, remove} = useFieldArray({
        control: formContext.control,
        name: 'files',
        keyName: 'fieldId',
        rules: {
            validate: values => {
                if (values.length < 1) {
                    setEmptyListError(t('event.document.error.emptyList'))
                }
                return 'empty'
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
                variant={'text'}
                multiple
                onSelected={files => {
                    Array.from(files).forEach(file => append({file}))
                }}
                accept={'application/pdf'}>
                {t('event.document.add.add')}
            </SelectFileButton>
        </Box>
    )
}

const DocumentDialog = (props: BaseEntityDialogProps<EventDocumentDto>) => {
    const {eventId} = eventIndexRoute.useParams()
    const {t} = useTranslation()

    const formContext = useForm<Form>()

    const addAction = (form: Form) =>
        addDocuments({
            path: {
                eventId,
            },
            body: {
                ...mapFormToRequest(form),
                files: form.files.map(file => file.file),
            },
        })

    const editAction = (form: Form, entity: EventDocumentDto) =>
        updateDocument({
            path: {
                eventId,
                eventDocumentId: entity.id,
            },
            body: mapFormToRequest(form),
        })

    const onOpen = useCallback(() => {
        formContext.reset(props.entity ? mapEntityToForm(props.entity) : defaultValues)
    }, [props.entity])

    const {data, pending} = useFetch(signal => getDocumentTypes({signal}))

    const typeOptions: AutocompleteOption[] = data?.data.map(dto => typeDtoToOption(dto)) ?? []

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            addAction={addAction}
            editAction={editAction}>
            <Stack spacing={4}>
                <FormInputAutocomplete
                    name={'documentType'}
                    options={typeOptions}
                    label={t('event.document.type.type')}
                    autocompleteProps={{
                        getOptionKey: option => option.id,
                    }}
                    loading={pending}
                />
                {!props.entity && <FileSelection />}
            </Stack>
        </EntityDialog>
    )
}

export default DocumentDialog
