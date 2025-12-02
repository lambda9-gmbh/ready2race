import {PropsWithChildren} from 'react'
import {Box, Tabs} from '@mui/material'

type Props<TabType extends string> = PropsWithChildren<{
    activeTab: TabType
    setActiveTab: (value: TabType) => void
}>
const TabSelectionContainer = <TabType extends string>({children, ...props}: Props<TabType>) => {
    return (
        <Box sx={{borderBottom: 1, borderColor: 'divider'}}>
            <Tabs
                value={props.activeTab}
                onChange={(_, v) => props.setActiveTab(v)}
                variant="scrollable"
                scrollButtons="auto"
                allowScrollButtonsMobile
                sx={{
                    '& .MuiTabScrollButton-root.Mui-disabled': {
                        display: 'none',
                    },
                }}>
                {children}
            </Tabs>
        </Box>
    )
}
export default TabSelectionContainer
