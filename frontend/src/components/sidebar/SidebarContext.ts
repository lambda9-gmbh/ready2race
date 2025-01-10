import {createContext, useContext} from 'react'

export type SidebarProps = {
    open: boolean
}

export const SidebarContext = createContext<SidebarProps | null>(null)

export const useSidebar = (): SidebarProps => {
    const props = useContext(SidebarContext)
    if (props === null) {
        throw Error('Sidebar context not initialized')
    }
    return props
}
