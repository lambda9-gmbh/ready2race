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
    userInfo: LoginDto | undefined
    isInApp: boolean
}

const UserProvider = ({children}: PropsWithChildren) => {
    const [language, setLanguage] = useState(
        isLanguage(i18next.language) ? i18next.language : fallbackLng,
    )
    const [userData, setUserData] = useState<UserData>()
    const [token, setToken] = useState<string | null>(sessionStorage.getItem('session'))
    const [ready, setReady] = useState(false)
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
        if (token) {
            const f = async (req: Request) => {
                req.headers.set('X-Api-Session', JSON.stringify({token}))
                return req
            }

            client.interceptors.request.use(f)
            return () => client.interceptors.request.eject(f)
        }
    }, [token])

    // TODO: @Refactor: should be possible without this useEffect
    useEffect(() => {
        if (userData) {
            if (ready) {
                const loggedIn = userData.userInfo !== undefined
                if (userData.isInApp) {
                    navigate({to: loggedIn ? '/app' : '/app/login'})
                } else {
                    const redirect = router.state.resolvedLocation.search.redirect
                    navigate({to: loggedIn ? (redirect ? redirect : '/dashboard') : '/'})
                }
            } else {
                setReady(true)
            }
        }
    }, [userData])

    useFetch(signal => checkUserLogin({signal}), {
        onResponse: ({data, response}) => {
            if (response.status === 200 && data !== undefined) {
                login(data)
            } else {
                sessionStorage.removeItem('session')
                setReady(true)
            }
        },
        onPanic: error => {
            setError(`${error}`)
        },
    })

    const login = (data: LoginDto, headers?: Headers, isInApp: boolean = false) => {
        if (headers) {
            const sessionHeader = headers.get('X-Api-Session')
            if (sessionHeader === null) {
                throw Error('Missing header on login response')
            }
            const token = (JSON.parse(sessionHeader) as Session).token
            sessionStorage.setItem('session', token)
            setToken(token)
        }
        setUserData({userInfo: data, isInApp})
    }

    const logout = async (isInApp: boolean = false) => {
        const {error} = await userLogout()
        if (error === undefined) {
            sessionStorage.removeItem('session')
            setUserData({userInfo: undefined, isInApp})
            setToken(null)
        }
    }

    const changeLanguage = async (language: Language) => {
        await i18next.changeLanguage(language)
        setLanguage(language)
    }

    let userValue: User
    const userInfo = userData?.userInfo
    if (!userInfo) {
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
            userInfo.privileges.some(
                p =>
                    p.action === privilege.action &&
                    p.resource === privilege.resource &&
                    scopeLevel[p.scope] >= scopeLevel[privilege.scope],
            )

        const getPrivilegeScope = (action: Action, resource: Resource): Scope | undefined =>
            userInfo.privileges
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
            id: userInfo.id,
            clubId: userInfo.clubId,
            login,
            logout,
            checkPrivilege,
            getPrivilegeScope,
        } satisfies AuthenticatedUser
    }

    return (
        <UserContext.Provider value={userValue}>
            {error !== null ? <PanicPage /> : ready && children}
        </UserContext.Provider>
    )
}

export default UserProvider
