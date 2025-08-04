import {PropsWithChildren, useEffect, useRef, useState} from 'react'
import {AnonymousUser, AuthenticatedUser, User, UserContext} from './UserContext.ts'
import {router} from '@routes'
import {useFetch} from '@utils/hooks.ts'
import {scopeLevel} from '@utils/helpers.ts'
import {Action, LoginDto, Privilege, Resource, Scope} from '@api/types.gen.ts'
import {checkUserLogin, client, userLogout} from '@api/sdk.gen.ts'
import i18next from 'i18next'
import {fallbackLng, isLanguage, Language} from '@i18n/config.ts'

type Session = {
    token: string
}

type UserData = LoginDto

const UserProvider = ({children}: PropsWithChildren) => {
    const [language, setLanguage] = useState(
        isLanguage(i18next.language) ? i18next.language : fallbackLng,
    )
    const [userData, setUserData] = useState<UserData>()
    const [token, setToken] = useState<string | null>(sessionStorage.getItem('session'))
    const [ready, setReady] = useState(false)
    const prevLoggedIn = useRef(false)
    const autoLogin = useRef(false)
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
        if (token) {
            const f = async (req: Request) => {
                req.headers.set('X-Api-Session', JSON.stringify({token}))
                return req
            }

            client.interceptors.request.use(f)
            return () => client.interceptors.request.eject(f)
        }
    }, [token, client])

    useEffect(() => {
        if (!autoLogin.current && prevLoggedIn.current !== loggedIn) {
            prevLoggedIn.current = loggedIn
            if (ready) {
                const redirect = router.state.resolvedLocation.search.redirect
                navigate({to: loggedIn ? (redirect ? redirect : '/dashboard') : '/'})
            } else {
                setReady(true)
            }
        }
        autoLogin.current = false
    }, [userData])

    //TODO: error-handling
    useFetch(signal => checkUserLogin({signal}), {
        onResponse: ({data, response}) => {
            if (response.status === 200 && data !== undefined) {
                autoLogin.current = true
                login(data)
            } else {
                sessionStorage.removeItem('session')
                setReady(true)
            }
        },
    })

    const login = (data: LoginDto, headers?: Headers) => {
        autoLogin.current = false
        if (headers) {
            const sessionHeader = headers.get('X-Api-Session')
            if (sessionHeader === null) {
                throw Error('Missing header on login response')
            }
            const token = (JSON.parse(sessionHeader) as Session).token
            sessionStorage.setItem('session', token)
            setToken(token)
        }
        setUserData({...data})
    }

    const logout = async () => {
        autoLogin.current = false
        const {error} = await userLogout()
        if (error === undefined) {
            sessionStorage.removeItem('session')
            setUserData(undefined)
            setToken(null)
        }
    }

    const changeLanguage = async (language: Language) => {
        await i18next.changeLanguage(language)
        setLanguage(language)
    }

    let userValue: User

    if (!userData) {
        userValue = {
            language,
            changeLanguage,
            loggedIn: false,
            login,
            checkPrivilege: () => false,
            getPrivilegeScope: () => undefined,
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
            userData.privileges
                .filter(p => p.action === action && p.resource === resource)
                .reduce<Scope | undefined>((scope, privilege) => {
                    if (scope === undefined) {
                        return privilege.scope
                    } else {
                        return scopeLevel[scope] >= scopeLevel[privilege.scope]
                            ? scope
                            : privilege.scope
                    }
                }, undefined)

        userValue = {
            language,
            changeLanguage,
            loggedIn: true,
            id: userData.id,
            clubId: userData.clubId,
            login,
            logout,
            checkPrivilege,
            getPrivilegeScope,
        } satisfies AuthenticatedUser
    }

    return <UserContext.Provider value={userValue}>{ready && children}</UserContext.Provider>
}

export default UserProvider

//todo: add "ready" (with Loading animation?) or better solution?
