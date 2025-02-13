import {PropsWithChildren, useEffect, useRef, useState} from 'react'
import {AnonymousUser, AuthenticatedUser, User, UserContext} from './UserContext.ts'
import {
    Action,
    checkUserLogin,
    client,
    LoginDto,
    Privilege,
    Resource,
    Scope,
    userLogout,
} from '../../api'
import {router} from '../../routes.tsx'
import {useFetch} from '../../utils/hooks.ts'
import {scopeLevel} from '../../utils/helpers.ts'

type UserData = LoginDto

const UserProvider = ({children}: PropsWithChildren) => {
    const [userData, setUserData] = useState<UserData>()
    const prevLoggedIn = useRef(false)
    const loggedIn = Boolean(userData)

    const navigate = router.navigate

    useEffect(() => {
        const f = async (res: Response) => {
            if (res.status === 401) {
                await logout()
            }
            return res
        }

        client.interceptors.response.use(f)
        return () => client.interceptors.response.eject(f)
    }, [client])

    useEffect(() => {
        if (prevLoggedIn.current !== loggedIn) {
            prevLoggedIn.current = loggedIn
            const redirect = router.state.resolvedLocation.search.redirect
            navigate({to: loggedIn ? (redirect ? redirect : '/dashboard') : '/'})
        }
    }, [userData])

    //TODO: error-handling
    useFetch(signal => checkUserLogin({signal}), {
        onResponse: ({data, response}) => {
            if (response.status === 200 && data !== undefined) {
                login(data)
            }
        },
    })

    const login = (data: LoginDto) => setUserData({...data})

    const logout = async () => {
        const {error} = await userLogout()
        if (error === undefined) {
            setUserData(undefined)
        }
    }

    let userValue: User

    if (!userData) {
        userValue = {
            loggedIn: false,
            login,
        } satisfies AnonymousUser
    } else {
        const checkPrivilege = (privilege: Privilege): boolean =>
            userData.privileges.some(
                p =>
                    p.action === privilege.action &&
                    p.resource === privilege.resource &&
                    scopeLevel[p.scope] >= scopeLevel[privilege.scope],
            )

        const getPrivilegeScope = (action: Action, resource: Resource): Scope | undefined =>
            userData.privileges.find(p => p.action === action && p.resource === resource)?.scope

        userValue = {
            loggedIn: true,
            id: userData.id,
            login,
            logout,
            checkPrivilege,
            getPrivilegeScope,
        } satisfies AuthenticatedUser
    }

    return <UserContext.Provider value={userValue}>{children}</UserContext.Provider>
}

export default UserProvider
