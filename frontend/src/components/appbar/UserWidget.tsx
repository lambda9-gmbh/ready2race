import {useUser} from '@contexts/user/UserContext.ts'
import {IconButton, Stack} from '@mui/material'
import {Login, Logout, Person} from '@mui/icons-material'
import {Link} from '@tanstack/react-router'

const UserWidget = () => {
    const user = useUser()

    if (user.loggedIn) {
        const handleLogout = () => {
            user.logout()
        }

        return (
            <Stack direction='row' spacing={1}>
                <Link to={'/user/$userId'} params={{userId: user.id}}>
                    <IconButton>
                        <Person />
                    </IconButton>
                </Link>
                <IconButton onClick={handleLogout}>
                    <Logout />
                </IconButton>
            </Stack>
        )
    } else {
        return (
            <Link to={'/login'}>
                <IconButton>
                    <Login />
                </IconButton>
            </Link>
        )
    }
}

export default UserWidget
