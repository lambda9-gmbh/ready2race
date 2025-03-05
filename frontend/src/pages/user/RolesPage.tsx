import {useEntityAdministration} from '@utils/hooks.ts'
import {RoleDto} from '@api/types.gen.ts'
import {Box} from '@mui/material'
import RoleTable from '@components/role/RoleTable.tsx'
import RoleDialog from '@components/role/RoleDialog.tsx'
import {useTranslation} from 'react-i18next'

const RolesPage = () => {
    const {t} = useTranslation()
    const administrationProps = useEntityAdministration<RoleDto>(t('role.role'))

    return (
        <Box>
            <RoleTable {...administrationProps.table} title={t('role.userRoles')} />
            <RoleDialog {...administrationProps.dialog} />
        </Box>
    )
}

export default RolesPage
