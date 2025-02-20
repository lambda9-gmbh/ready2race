import {useUser} from '@contexts/user/UserContext.ts'
import {IconButton} from '@mui/material'
import {Login, Logout} from '@mui/icons-material'
import {Link} from '@tanstack/react-router'

const UserWidget = () => {
    const user = useUser()

    if (user.loggedIn) {
        const handleLogout = () => {
            user.logout()
        }

        return (
            <IconButton onClick={handleLogout}>
                <Logout />
            </IconButton>
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
