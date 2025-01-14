import {createContext, useContext} from 'react'
import {LoginResponse, Privilege} from '../../api'
import {PrivilegeScope} from '../../utils/types.ts'

export type User = {
    loggedIn: boolean
    login: (data: LoginResponse) => void
    logout: () => void
    getPrivilegeScope: (privilege: Privilege) => PrivilegeScope | null
    association: string | undefined
}

export const UserContext = createContext<User | null>(null)

export const useUser = (): User => {
    const user = useContext(UserContext)
    if (user === null) {
        throw Error('User context not initialized')
    }
    return user
}
