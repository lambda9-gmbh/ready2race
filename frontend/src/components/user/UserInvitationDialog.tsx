import {BaseEntityDialogProps} from '@utils/types.ts'
import {useForm} from 'react-hook-form-mui'
import {useCallback} from 'react'
import EntityDialog from '@components/EntityDialog.tsx'
import {Stack} from '@mui/material'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {AppUserInvitationDto, InviteRequest} from "@api/types.gen.ts";
import {inviteUser} from "@api/sdk.gen.ts";
import {i18nLanguage, languageMapping} from "@utils/helpers.ts";
import FormInputEmail from "@components/form/input/FormInputEmail.tsx";

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
    language: languageMapping[i18nLanguage()],
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
            <Stack spacing={4}>
                <FormInputText name={'firstname'} label={'[todo] Vorname'} required />
                <FormInputText name={'lastname'} label={'[todo] Nachname'} required />
                <FormInputEmail name={'email'} label={'[todo] E-Mail'} required />
            </Stack>
        </EntityDialog>
    )
}

export default UserInvitationDialog
