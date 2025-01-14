import {PropsWithChildren, useEffect, useRef, useState} from 'react'
import {User, UserContext} from './UserContext.ts'
import {client, LoginResponse, Privilege} from '../../api'
import {PrivilegeScope} from '../../utils/types.ts'
import {router} from '../../routes.tsx'

type UserData = LoginResponse & {loggedIn: boolean}

const defaultUserData: UserData = {
    loggedIn: false,
    privilegesGlobal: [],
    privilegesBound: [],
}

const UserProvider = ({children}: PropsWithChildren) => {
    const [userData, setUserData] = useState(defaultUserData)
    const prevLoggedIn = useRef(userData.loggedIn)

    const navigate = router.navigate

    useEffect(() => {
        const f = async (res: Response) => {
            if (res.status === 401) {
                logout()
            }
            return res
        }

        client.interceptors.response.use(f)
        return () => client.interceptors.response.eject(f)
    }, [client])

    useEffect(() => {
        if (prevLoggedIn.current !== userData.loggedIn) {
            prevLoggedIn.current = userData.loggedIn
            const redirect = router.state.resolvedLocation.search.redirect
            navigate({to: userData.loggedIn ? (redirect ? redirect : '/dashboard') : '/'})
        }
    }, [userData])

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
        getPrivilegeScope: getAuthorization,
        association: undefined,
    }

    return <UserContext.Provider value={userValue}>{children}</UserContext.Provider>
}

export default UserProvider
