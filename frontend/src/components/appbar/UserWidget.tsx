import {useUser} from '../../contexts/user/UserContext.ts'
import {IconButton} from '@mui/material'
import {Login, Logout} from '@mui/icons-material'
import {Link} from '@tanstack/react-router'

const UserWidget = () => {
    const {loggedIn, logout} = useUser()

    const handleLogout = () => {
        logout()
    }

    return loggedIn ? (
        <IconButton onClick={handleLogout}>
            <Logout />
        </IconButton>
    ) : (
        <Link to={'/login'}>
            <IconButton>
                <Login />
            </IconButton>
        </Link>
    )
}

export default UserWidget
