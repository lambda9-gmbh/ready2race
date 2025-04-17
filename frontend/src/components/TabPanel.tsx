import {ReactNode} from 'react'
import {Box} from '@mui/material'

const TabPanel = (props: {children?: ReactNode; index: number; activeTab: number}) => {
    const {children, activeTab, index, ...other} = props

    return (
        <div
            role="tabpanel"
            hidden={activeTab !== index}
            id={`simple-tabpanel-${index}`}
            aria-labelledby={`simple-tab-${index}`}
            {...other}>
            {activeTab === index && <Box>{children}</Box>}
        </div>
    )
}
export default TabPanel
