import {useEntityAdministration} from '../../utils/hooks.ts'
import {AppUserDto} from '../../api'
import {Box} from '@mui/material'
import UserTable from '../../components/user/UserTable.tsx'
import {useTranslation} from 'react-i18next'

const UsersPage = () => {
    const {t} = useTranslation()
    const administrationProps = useEntityAdministration<AppUserDto>(t('user.user'), {entityCreate: false})

    return (
        <Box>
            <UserTable {...administrationProps.table} />
        </Box>
    )
}

export default UsersPage
