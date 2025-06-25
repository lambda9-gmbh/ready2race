import {createContext, useContext} from 'react'
import {Action, LoginDto, Privilege, Resource, Scope} from '@api/types.gen.ts'
import {Language} from "@i18n/config.ts";

export type AuthenticatedUser = {
    language: Language
    changeLanguage: (language: Language) => Promise<void>
    loggedIn: true
    id: string
    clubId?: string
    login: (data: LoginDto, headers: Headers) => void
    logout: () => void
    getPrivilegeScope: (action: Action, resource: Resource) => Scope | undefined
    checkPrivilege: (privilege: Privilege) => boolean
}

export type AnonymousUser = {
    language: Language
    changeLanguage: (language: Language) => Promise<void>
    loggedIn: false
    login: (data: LoginDto, headers: Headers) => void
    getPrivilegeScope: (action: Action, resource: Resource) => undefined
    checkPrivilege: (privilege: Privilege) => false
}

export type User = AuthenticatedUser | AnonymousUser

export const UserContext = createContext<User | null>(null)

export const useUser = (): User => {
    const user = useContext(UserContext)
    if (user === null) {
        throw Error('User context not initialized')
    }
    return user
}
