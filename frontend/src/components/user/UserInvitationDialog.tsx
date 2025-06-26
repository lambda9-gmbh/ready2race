import {BaseEntityDialogProps} from '@utils/types.ts'
import {useForm} from 'react-hook-form-mui'
import {useCallback} from 'react'
import EntityDialog from '@components/EntityDialog.tsx'
import {Stack} from '@mui/material'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {AppUserInvitationDto, InviteRequest, InviteUserError} from '@api/types.gen.ts'
import {getRoles, inviteUser} from '@api/sdk.gen.ts'
import {adminId, i18nLanguage, languageMapping} from '@utils/helpers.ts'
import FormInputEmail from '@components/form/input/FormInputEmail.tsx'
import {useTranslation} from 'react-i18next'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {useUser} from '@contexts/user/UserContext.ts'
import {FormInputCheckbox} from '@components/form/input/FormInputCheckbox.tsx'
import RolesSelect from './RolesSelect'

type InvitationForm = {
    email: string
    firstname: string
    lastname: string
    roles: string[]
    admin: boolean
}

const defaultValues: InvitationForm = {
    email: '',
    firstname: '',
    lastname: '',
    roles: [],
    admin: false,
}

const mapFormToRequest = (formData: InvitationForm): InviteRequest => ({
    email: formData.email,
    firstname: formData.firstname,
    lastname: formData.lastname,
    roles: formData.admin ? [] : formData.roles,
    admin: formData.admin,
    language: languageMapping[i18nLanguage()],
    callbackUrl: location.origin + '/invitation/',
})

const addAction = (formData: InvitationForm) =>
    inviteUser({
        body: mapFormToRequest(formData),
    })

const UserInvitationDialog = (props: BaseEntityDialogProps<AppUserInvitationDto>) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const formContext = useForm<InvitationForm>()

    const user = useUser()

    const {data} = useFetch(signal => getRoles({signal})) // The backend checks for the privilege READUserGlobal

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
                {'id' in user && user.id === adminId && (
                    <FormInputCheckbox name={'admin'} label={'Admin?'} />
                )}
                {!formContext.watch('admin') && <RolesSelect availableRoles={data?.data} />}
            </Stack>
        </EntityDialog>
    )
}

export default UserInvitationDialog
