import {PropsWithChildren} from "react";
import {Box, Tabs} from "@mui/material";

type Props = PropsWithChildren<{
    activeTab: number
    setActiveTab: (index: number) => void
}>
const TabSelectionContainer = ({children, ...props}: Props) => {
    return (
        <Box sx={{borderBottom: 1, borderColor: 'divider'}}>
        <Tabs value={props.activeTab} onChange={(_, v) => props.setActiveTab(v)}>
            {children}
        </Tabs>
    </Box>
    )
}
export default TabSelectionContainer