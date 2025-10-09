import {PropsWithChildren, useEffect, useState} from 'react'
import {AnonymousUser, AuthenticatedUser, User, UserContext} from './UserContext.ts'
import {router} from '@routes'
import {useFetch} from '@utils/hooks.ts'
import {scopeLevel} from '@utils/helpers.ts'
import {Action, LoginDto, Privilege, Resource, Scope} from '@api/types.gen.ts'
import {checkUserLogin, client, userLogout} from '@api/sdk.gen.ts'
import i18next from 'i18next'
import {fallbackLng, isLanguage, Language} from '@i18n/config.ts'
import PanicPage from '../../pages/PanicPage.tsx'

type Session = {
    token: string
}

type UserData = {
    userInfo: LoginDto
    token: string
    isInApp: boolean
    authStatus: 'authenticated'
} | {
    userInfo: LoginDto | undefined
    token: string | null
    isInApp: boolean
    authStatus: 'pending' | 'anonymous'
}

const UserProvider = ({children}: PropsWithChildren) => {
    const [language, setLanguage] = useState(
        isLanguage(i18next.language) ? i18next.language : fallbackLng,
    )
    const [userData, setUserData] = useState<UserData>({
        userInfo: undefined,
        token: sessionStorage.getItem('session'),
        isInApp: router.state.resolvedLocation.pathname.startsWith('/app'),
        authStatus: "pending",
    })
    const [error, setError] = useState<string | null>(null)

    const navigate = router.navigate

    useEffect(() => {
        const f = async (res: Response) => {
            if (res.status === 401) {
                const isInApp = router.state.resolvedLocation.pathname.startsWith('/app')
                await logout(isInApp)
            }
            return res
        }

        client.interceptors.response.use(f)
        return () => client.interceptors.response.eject(f)
    }, [])

    useEffect(() => {
        console.log(userData)
        if (userData.authStatus !== "pending") {
            const loggedIn = userData.userInfo !== undefined
            if (userData.isInApp) {
                navigate({to: loggedIn ? '/app' : '/app/login'})
            } else {
                const redirect = router.state.resolvedLocation.search.redirect
                navigate({to: loggedIn ? (redirect ? redirect : '/dashboard') : '/'})
            }
        } else {
            if (userData.userInfo && userData.token) {
                setUserData({
                    userInfo: userData.userInfo,
                    token: userData.token,
                    isInApp: userData.isInApp,
                    authStatus: 'authenticated'
                })
            } else {
                setUserData(prevState => ({
                    userInfo: undefined,
                    token: null,
                    isInApp: prevState.isInApp,
                    authStatus: "anonymous"
                }))
            }
        }
        if (userData.token) {
            const f = async (req: Request) => {
                req.headers.set('X-Api-Session', JSON.stringify({token: userData.token}))
                return req
            }

            client.interceptors.request.use(f)
            return () => client.interceptors.request.eject(f)
        }

    }, [userData])

    useFetch(signal => checkUserLogin({signal}), {
        onResponse: ({data, response}) => {
            if (response.status === 200 && data !== undefined) {
                setAuth(data, undefined, userData.isInApp)
            } else {
                sessionStorage.removeItem('session')
                setUserData(prevState => ({
                    userInfo: undefined,
                    token: null,
                    isInApp: prevState.isInApp,
                    authStatus: "anonymous"
                }))
            }
        },
        onPanic: error => {
            setError(`${error}`)
        },
    })

    const setAuth = (data: LoginDto, headers?: Headers, isInApp: boolean = false) => {
        let token = userData.token

        if (headers) {
            const sessionHeader = headers.get('X-Api-Session')
            if (sessionHeader === null) {
                throw Error('Missing header on login response')
            }
            token = (JSON.parse(sessionHeader) as Session).token
            sessionStorage.setItem('session', token)
        }
        if (!token) {
            throw Error('Missing session token on login')
        }
        setUserData({
            userInfo: data,
            token,
            isInApp,
            authStatus: "pending"
        })
    }

    const logout = async (isInApp: boolean = false) => {
        const {error} = await userLogout()
        if (error === undefined) {
            sessionStorage.removeItem('session')
            setUserData({
                userInfo: undefined,
                isInApp,
                token: null,
                authStatus: "anonymous"
            })
        }
    }

    const changeLanguage = async (language: Language) => {
        await i18next.changeLanguage(language)
        setLanguage(language)
    }

    let userValue: User
    if (userData.authStatus !== 'authenticated') {
        userValue = {
            language,
            changeLanguage,
            loggedIn: false,
            login: setAuth,
            checkPrivilege: () => false,
            getPrivilegeScope: () => undefined,
        } satisfies AnonymousUser
    } else {
        const checkPrivilege = (privilege: Privilege): boolean =>
            userData.userInfo.privileges.some(
                p =>
                    p.action === privilege.action &&
                    p.resource === privilege.resource &&
                    scopeLevel[p.scope] >= scopeLevel[privilege.scope],
            )

        const getPrivilegeScope = (action: Action, resource: Resource): Scope | undefined =>
            userData.userInfo.privileges
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
            id: userData.userInfo.id,
            clubId: userData.userInfo.clubId,
            login: setAuth,
            logout,
            checkPrivilege,
            getPrivilegeScope,
        } satisfies AuthenticatedUser
    }

    return (
        <UserContext.Provider value={userValue}>
            {error !== null ? <PanicPage /> : userData.authStatus !== "pending" && children}
        </UserContext.Provider>
    )
}

export default UserProvider
