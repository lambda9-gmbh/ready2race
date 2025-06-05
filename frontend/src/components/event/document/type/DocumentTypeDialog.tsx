import EntityDialog from '@components/EntityDialog.tsx'
import {BaseEntityDialogProps} from '@utils/types.ts'
import {EventDocumentTypeDto, EventDocumentTypeRequest} from '@api/types.gen.ts'
import {useForm} from 'react-hook-form-mui'
import {addDocumentType, updateDocumentType} from '@api/sdk.gen.ts'
import {useCallback} from 'react'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {Stack} from '@mui/material'
import {useTranslation} from 'react-i18next'
import FormInputSwitch from '@components/form/input/FormInputSwitch.tsx'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'

type Form = EventDocumentTypeRequest

const defaultValues: Form = {
    name: '',
    description: '',
    required: false,
    confirmationRequired: false,
}

const mapFormToRequest = (formData: Form): EventDocumentTypeRequest => formData

const mapEntityToForm = (dto: EventDocumentTypeDto): Form => {
    const {id, ...rest} = dto
    return {...rest, description: takeIfNotEmpty(rest.description)}
}

const addAction = (formData: Form) =>
    addDocumentType({
        body: mapFormToRequest(formData),
    })

const editAction = (formData: Form, entity: EventDocumentTypeDto) =>
    updateDocumentType({
        path: {
            eventDocumentTypeId: entity.id,
        },
        body: mapFormToRequest(formData),
    })

const DocumentTypeDialog = (props: BaseEntityDialogProps<EventDocumentTypeDto>) => {
    const formContext = useForm<Form>()
    const {t} = useTranslation()

    const onOpen = useCallback(() => {
        formContext.reset(props.entity ? mapEntityToForm(props.entity) : defaultValues)
    }, [props.entity])

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            addAction={addAction}
            editAction={editAction}>
            <Stack spacing={4}>
                <FormInputText name={'name'} label={t('event.document.type.name')} required />
                <FormInputText name={'description'} label={t('event.document.type.description')} />
                <FormInputSwitch
                    name={'required'}
                    label={t('event.document.type.required.forEvent')}
                    horizontal
                    reverse
                />
                <FormInputSwitch
                    name={'confirmationRequired'}
                    label={t('event.document.type.required.confirmation')}
                    horizontal
                    reverse
                />
                {/*todo: change slider required, shows optional, doesnt force true when required*/}
            </Stack>
        </EntityDialog>
    )
}

export default DocumentTypeDialog
