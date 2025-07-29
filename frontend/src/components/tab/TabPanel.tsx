import {ReactNode} from 'react'
import {Box} from '@mui/material'

const TabPanel = <TabType extends string,>(props: {children?: ReactNode; index: TabType; activeTab: TabType}) => {
    const {children, activeTab, index} = props

    return (
        <div
            role="tabpanel"
            hidden={activeTab !== index}
            id={`simple-tabpanel-${index}`}
            aria-labelledby={`simple-tab-${index}`}>
            {activeTab === index && <Box>{children}</Box>}
        </div>
    )
}
export default TabPanel
