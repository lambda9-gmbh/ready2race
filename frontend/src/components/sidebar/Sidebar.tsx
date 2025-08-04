import {Box, Divider, List} from '@mui/material'
import {SidebarContext, SidebarProps} from './SidebarContext.ts'
import {PropsWithChildren} from 'react'

type Props = SidebarProps

const Sidebar = ({children, ...props}: PropsWithChildren<Props>) => {
    return (
        <Box sx={{display: 'flex'}}>
            <SidebarContext.Provider value={props}>
                <List>{children}</List>
            </SidebarContext.Provider>
            <Divider orientation={'vertical'} />
        </Box>
    )
}

export default Sidebar
