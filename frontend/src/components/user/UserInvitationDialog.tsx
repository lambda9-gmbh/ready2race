import {BaseEntityDialogProps} from '@utils/types.ts'
import {useForm} from 'react-hook-form-mui'
import {useCallback} from 'react'
import EntityDialog from '@components/EntityDialog.tsx'
import {Stack} from '@mui/material'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {AppUserInvitationDto, InviteRequest} from '@api/types.gen.ts'
import {inviteUser} from '@api/sdk.gen.ts'
import {i18nLanguage, languageMapping} from '@utils/helpers.ts'
import FormInputEmail from '@components/form/input/FormInputEmail.tsx'
import {useTranslation} from "react-i18next";

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
    inviteUser({ // todo: This can result in Error 409: "Email in use" or 409: "Cannot assign roles". That should be displayed
        body: mapFormToRequest(formData),
    })

const UserInvitationDialog = (props: BaseEntityDialogProps<AppUserInvitationDto>) => {
    const {t} = useTranslation()
    const formContext = useForm<InvitationForm>()

    const onOpen = useCallback(() => {
        formContext.reset(defaultValues)
    }, [props.entity])

    return (
        <EntityDialog {...props} formContext={formContext} onOpen={onOpen} addAction={addAction}>
            <Stack spacing={4}>
                <FormInputText name={'firstname'} label={t('user.firstname')} required />
                <FormInputText name={'lastname'} label={t('user.lastname')} required />
                <FormInputEmail name={'email'} label={t('user.email')} required />
            </Stack>
        </EntityDialog>
    )
}

export default UserInvitationDialog
