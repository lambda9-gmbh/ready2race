import {BaseEntityDialogProps} from '@utils/types.ts'
import {useForm} from 'react-hook-form-mui'
import {useCallback} from 'react'
import EntityDialog from '@components/EntityDialog.tsx'
import {Stack} from '@mui/material'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {AppUserInvitationDto, InviteRequest, InviteUserError} from '@api/types.gen.ts'
import {inviteUser} from '@api/sdk.gen.ts'
import {i18nLanguage, languageMapping} from '@utils/helpers.ts'
import FormInputEmail from '@components/form/input/FormInputEmail.tsx'
import {useTranslation} from 'react-i18next'
import {useFeedback} from '@utils/hooks.ts'

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
    const {t} = useTranslation()
    const feedback = useFeedback()
    const formContext = useForm<InvitationForm>()

    const onOpen = useCallback(() => {
        formContext.reset(defaultValues)
    }, [props.entity])

    const onAddError = (error: InviteUserError) => {
        if (error.status.value === 409) {
            if (error.errorCode === 'EMAIL_IN_USE') {
                formContext.setError('email', {
                    type: 'validate',
                    message:
                        t('user.email.inUse.statement') +
                        ' ' +
                        t('user.email.inUse.callToAction.invitation'),
                })
            } else if (error.errorCode === 'CANNOT_ASSIGN_ROLES') {
                feedback.error(t('role.error.cannotAssign'))
            }
        } else {
            feedback.error(t('entity.add.error', {entity: props.entityName}))
        }
    }

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            addAction={addAction}
            onAddError={onAddError}>
            <Stack spacing={4}>
                <FormInputText name={'firstname'} label={t('user.firstname')} required />
                <FormInputText name={'lastname'} label={t('user.lastname')} required />
                <FormInputEmail name={'email'} label={t('user.email.email')} required />
            </Stack>
        </EntityDialog>
    )
}

export default UserInvitationDialog
