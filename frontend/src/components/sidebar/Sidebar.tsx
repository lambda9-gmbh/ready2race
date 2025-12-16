import {Box, Divider, List} from '@mui/material'
import {SidebarContext, SidebarProps} from './SidebarContext.ts'
import {PropsWithChildren} from 'react'

type Props = SidebarProps & {
    isSmallScreen: boolean
}

const Sidebar = ({children, ...props}: PropsWithChildren<Props>) => {
    return (
        <Box
            sx={{
                display: 'flex',
                width: props.isSmallScreen ? '100%' : undefined,
                flex: props.isSmallScreen ? 1 : undefined,
            }}>
            <SidebarContext.Provider value={props}>
                <List sx={{width: '100%', flex: 1, display: 'flex', flexDirection: 'column'}}>
                    {children}
                </List>
            </SidebarContext.Provider>
            <Divider orientation={'vertical'} />
        </Box>
    )
}

export default Sidebar
