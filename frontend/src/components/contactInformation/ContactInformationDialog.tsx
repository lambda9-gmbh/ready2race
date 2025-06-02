import {BaseEntityDialogProps} from "@utils/types.ts";
import {ContactInformationDto, ContactInformationRequest} from "@api/types.gen.ts";
import EntityDialog from "@components/EntityDialog.tsx";
import {useForm} from "react-hook-form-mui";
import {useCallback} from "react";
import {addContact, updateContact} from "@api/sdk.gen.ts";
import {Stack} from "@mui/material";
import {FormInputText} from "@components/form/input/FormInputText.tsx";
import {useTranslation} from "react-i18next";

type Form = ContactInformationRequest

const defaultValues: Form = {
    name: '',
    addressZip: '',
    addressCity: '',
    addressStreet: '',
    email: '',
}

const mapDtoToForm = (dto: ContactInformationDto): Form => {
    const {id, ...rest} = dto
    return rest
}

const addAction = (formData: Form) =>
    addContact({
        body: formData
    })

const editAction = (formData: Form, entity: ContactInformationDto) =>
    updateContact({
        path: {contactId: entity.id},
        body: formData
    })

const ContactInformationDialog = (props: BaseEntityDialogProps<ContactInformationDto>) => {

    const {t} = useTranslation()

    const formContext = useForm<Form>()
    
    const onOpen = useCallback(() => {
        formContext.reset(props.entity ? mapDtoToForm(props.entity) : defaultValues)
    }, [props.entity])
    
    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            addAction={addAction}
            editAction={editAction}>
            <Stack spacing={4}>
                <FormInputText name={'name'} label={t('contact.name')} required />
                <FormInputText name={'addressZip'} label={t('contact.zip')} required />
                <FormInputText name={'addressCity'} label={t('contact.city')} required />
                <FormInputText name={'addressStreet'} label={t('contact.street')} required />
                <FormInputText name={'email'} label={t('contact.email')} required />
            </Stack>
        </EntityDialog>
    )
}

export default ContactInformationDialog