import {useEntityAdministration} from '@utils/hooks.ts'
import {AppUserDto, AppUserInvitationDto, AppUserRegistrationDto} from '@api/types.gen.ts'
import {Stack} from '@mui/material'
import UserTable from '@components/user/UserTable.tsx'
import {useTranslation} from 'react-i18next'
import UserInvitationTable from '@components/user/UserInvitationTable.tsx'
import UserRegistrationTable from '@components/user/UserRegistrationTable.tsx'
import UserInvitationDialog from '@components/user/UserInvitationDialog.tsx'

const UsersPage = () => {
    const {t} = useTranslation()
    const userAdministrationProps = useEntityAdministration<AppUserDto>(t('user.user'), {
        entityCreate: false,
        entityUpdate: false,
    })
    const invitationAdministrationProps = useEntityAdministration<AppUserInvitationDto>(
        t('user.invitation.invitation'),
        {
            entityUpdate: false,
        },
    )
    const registrationAdministrationProps = useEntityAdministration<AppUserRegistrationDto>(
        t('user.user'),
        {
            entityCreate: false,
            entityUpdate: false,
        },
    )

    return (
        <Stack spacing={2}>
            <UserTable {...userAdministrationProps.table} title={t('user.users')} />
            <UserInvitationTable
                {...invitationAdministrationProps.table}
                title={t('user.invitation.pendingInvitations')}
            />
            <UserInvitationDialog {...invitationAdministrationProps.dialog} />
            <UserRegistrationTable
                {...registrationAdministrationProps.table}
                title={t('user.registration.pendingRegistrations')}
            />
        </Stack>
    )
}

export default UsersPage
