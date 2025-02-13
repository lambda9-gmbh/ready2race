import {useEntityAdministration} from '../utils/hooks.ts'
import {RoleDto} from '../api'
import {Box} from '@mui/material'
import RoleTable from '../components/role/RoleTable.tsx'
import RoleDialog from '../components/role/RoleDialog.tsx'

const RolesPage = () => {
    const administrationProps = useEntityAdministration<RoleDto>()

    return (
        <Box>
            <RoleTable {...administrationProps} />
            <RoleDialog {...administrationProps} />
        </Box>
    )
}

export default RolesPage
