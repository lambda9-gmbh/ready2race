import {PropsWithChildren, useState} from 'react'
import {User, UserContext} from './UserContext.ts'
import {LoginResponse, Privilege} from '../../api'
import {PrivilegeScope} from '../../utils/types.ts'

type UserData = LoginResponse & {loggedIn: boolean}

const defaultUserData: UserData = {
    loggedIn: false,
    privilegesGlobal: [],
    privilegesBound: [],
}

const UserProvider = ({children}: PropsWithChildren) => {
    const [userData, setUserData] = useState(defaultUserData)

    const login = (data: LoginResponse) => setUserData({...data, loggedIn: true})

    const logout = () => setUserData(defaultUserData)

    const getAuthorization = (privilege: Privilege): PrivilegeScope | null =>
        userData.privilegesGlobal.includes(privilege)
            ? 'global'
            : userData.privilegesBound.includes(privilege)
              ? 'association-bound'
              : null

    const userValue: User = {
        loggedIn: userData.loggedIn,
        login,
        logout,
        getAuthorization,
        association: userData.association,
    }

    return <UserContext.Provider value={userValue}>{children}</UserContext.Provider>
}

export default UserProvider
