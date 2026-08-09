import {PropsWithChildren, useEffect, useRef} from 'react'
import {Box, Tabs, TabsActions} from '@mui/material'

type Props<TabType extends string> = PropsWithChildren<{
    activeTab: TabType
    setActiveTab: (value: TabType) => void
}>
const TabSelectionContainer = <TabType extends string>({children, ...props}: Props<TabType>) => {
    // Ist beim ersten Rendern nicht der erste Reiter ausgewählt — etwa weil der Einstieg über
    // den QR-Code am Teilnehmerband direkt auf "Mein Event" zeigt —, vermisst MUI den
    // Unterstrich, bevor Beschriftungen und Symbole ihre endgültige Breite haben: er bleibt
    // dann unter dem ersten Reiter stehen, während ein anderer als aktiv eingefärbt ist.
    // Ein Nachmessen nach dem Einhängen rückt ihn an die richtige Stelle.
    const tabsActions = useRef<TabsActions>(null)
    useEffect(() => {
        tabsActions.current?.updateIndicator()
    }, [props.activeTab])

    return (
        <Box sx={{borderBottom: 1, borderColor: 'divider'}}>
            <Tabs
                action={tabsActions}
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
