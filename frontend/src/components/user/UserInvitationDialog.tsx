import {BaseEntityDialogProps} from '../../utils/types.ts'
import {AppUserInvitationDto, EmailLanguage, InviteRequest, inviteUser} from '../../api'
import i18next from 'i18next'
import {useTranslation} from 'react-i18next'
import {useForm} from 'react-hook-form-mui'
import {useCallback} from 'react'
import EntityDialog from '../EntityDialog.tsx'
import {Stack} from '@mui/material'
import {FormInputText} from '../form/input/FormInputText.tsx'

type InvitationForm = {
    email: string
    firstname: string
    lastname: string
    roles: string[] // todo: implement me
}

const defaultValues: InvitationForm = {
    email: '',
    firstname: '',
    lastname: '',
    roles: [],
}

const mapFormToRequest = (formData: InvitationForm): InviteRequest => ({
    email: formData.email,
    firstname: formData.firstname,
    lastname: formData.lastname,
    roles: formData.roles,
    language: i18next.language as EmailLanguage,
    callbackUrl: location.origin + location.pathname + '/',
})

const addAction = (formData: InvitationForm) =>
    inviteUser({
        body: mapFormToRequest(formData),
    })

const UserInvitationDialog = (props: BaseEntityDialogProps<AppUserInvitationDto>) => {
    const formContext = useForm<InvitationForm>()

    const onOpen = useCallback(() => {
        formContext.reset(defaultValues)
    }, [])

    return (
        <EntityDialog {...props} formContext={formContext} onOpen={onOpen} addAction={addAction}>
            <Stack spacing={2}>
                <FormInputText name={'firstname'} label={'[todo] Vorname'} required />
                <FormInputText name={'lastname'} label={'[todo] Nachname'} required />
                <FormInputText name={'email'} label={'[todo] E-Mail'} required />
            </Stack>
        </EntityDialog>
    )
}

export default UserInvitationDialog
