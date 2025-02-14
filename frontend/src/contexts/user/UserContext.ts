import {createContext, useContext} from 'react'
import {Action, LoginDto, Privilege, Resource, Scope} from '../../api'

export type AuthenticatedUser = {
    loggedIn: true
    id: string
    login: (data: LoginDto) => void
    logout: () => void
    getPrivilegeScope: (action: Action, resource: Resource) => Scope | undefined
    checkPrivilege: (privilege: Privilege) => boolean
}

export type AnonymousUser = {
    loggedIn: false
    login: (data: LoginDto) => void
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
