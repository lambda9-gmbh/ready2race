import {Drawer, List} from '@mui/material'
import {SidebarContext, SidebarProps} from './SidebarContext.ts'
import {PropsWithChildren} from 'react'

type Props = SidebarProps

const Sidebar = ({children, ...props}: PropsWithChildren<Props>) => {
    return (
        <Drawer
            variant={'permanent'}
            PaperProps={{
                sx: {
                    position: 'static',
                },
            }}>
            <SidebarContext.Provider value={props}>
                <List>{children}</List>
            </SidebarContext.Provider>
        </Drawer>
    )
}

export default Sidebar
