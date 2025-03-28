import EntityDialog from '@components/EntityDialog.tsx'
import {BaseEntityDialogProps} from '@utils/types.ts'
import {EventDocumentTypeDto, EventDocumentTypeRequest} from '@api/types.gen.ts'
import {SwitchElement, useForm} from 'react-hook-form-mui'
import {addDocumentType, updateDocumentType} from '@api/sdk.gen.ts'
import {useCallback} from 'react'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {Stack} from '@mui/material'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import {useTranslation} from 'react-i18next'

type Form = EventDocumentTypeRequest

const defaultValues: Form = {
    name: '',
    required: false,
    confirmationRequired: false,
}

const mapFormToRequest = (formData: Form): EventDocumentTypeRequest => formData

const mapEntityToForm = (dto: EventDocumentTypeDto): Form => {
    const {id, ...rest} = dto
    return rest
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
                <SwitchElement
                    name={'required'}
                    label={<FormInputLabel label={t('event.document.type.required.forEvent')} />}
                />
                <SwitchElement
                    name={'confirmationRequired'}
                    label={
                        <FormInputLabel label={t('event.document.type.required.confirmation')} />
                    }
                />
                {/*todo: change slider required, shows optional, doesnt force true when required*/}
            </Stack>
        </EntityDialog>
    )
}

export default DocumentTypeDialog
