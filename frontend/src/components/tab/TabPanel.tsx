import {PropsWithChildren} from 'react'
import {Box} from '@mui/material'

type Props<TabType> = {
    index: TabType
    activeTab: TabType
}

const TabPanel = <TabType extends string,> ({children, activeTab, index}: PropsWithChildren<Props<TabType>>) => {

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
