import {useEntityAdministration} from '../utils/hooks.ts'
import {AppUserDto} from '../api'
import {Box} from '@mui/material'
import UserTable from '../components/user/UserTable.tsx'

const UsersPage = () => {
    const administrationProps = useEntityAdministration<AppUserDto>()

    return (
        <Box>
            <UserTable {...administrationProps} />
        </Box>
    )
}

export default UsersPage
