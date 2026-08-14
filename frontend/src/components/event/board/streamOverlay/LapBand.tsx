import {Box, Stack, Typography, useTheme} from '@mui/material'
import {BoardElement} from '@api/types.gen.ts'
import FlipList from '../FlipList.tsx'
import {StreamLapEntry} from '../streamOverlay.ts'
import {solidOr, streamNameForms} from './streamDisplay.ts'

interface LapBandProps {
    laps: StreamLapEntry[]
    element: BoardElement
}

/** Startversatz eines neu eintreffenden Rundeneintrags — schiebt sichtbar von rechts herein. */
const LAP_ENTER_OFFSET = 260

/**
 * Modus „Rundenanzeige": schmales Bauchband unten (~10 vh), bis zu drei zuletzt
 * eingetroffene Rundenzeiten nebeneinander — neueste links, mit Akzentrand. Eine neue
 * Rundenzeit schiebt per translateX-FLIP von rechts herein, die übrigen rücken nach
 * (FlipList mit axis="x" übernimmt beides über dieselbe Positionsmessung).
 */
const LapBand = ({laps, element}: LapBandProps) => {
    const theme = useTheme()
    const names = streamNameForms(element)

    return (
        <Box
            sx={{
                position: 'absolute',
                left: 0,
                right: 0,
                bottom: 0,
                height: '10vh',
                display: 'flex',
            }}>
            <Stack
                direction="row"
                sx={{
                    width: 1,
                    overflow: 'hidden',
                    backgroundColor: solidOr(theme.palette.text.primary, '#1d1d1d'), // dunkles, DECKENDES Band
                    color: solidOr(theme.palette.background.paper, '#ffffff'),
                }}>
                <FlipList
                    axis="x"
                    enterOffset={LAP_ENTER_OFFSET}
                    items={laps}
                    keyOf={lap => `${lap.startNumber}-${lap.lapName}-${lap.recordedAt ?? ''}`}
                    // `flex`/`minWidth` müssen auf FlipLists Wrapper-<div> sitzen — das ist
                    // das tatsächliche Flex-Item der Stack-Row, nicht diese innere Box.
                    itemStyle={{flex: '1 1 0', minWidth: '16rem'}}
                    render={(lap, index) => (
                        <Box
                            sx={{
                                height: 1,
                                display: 'flex',
                                alignItems: 'center',
                                gap: 2,
                                px: 3,
                                // Akzentrand nur am neuesten (linken) Eintrag — eine harte
                                // Kante, kein Verlauf.
                                borderLeft:
                                    index === 0
                                        ? `0.4rem solid ${solidOr(theme.palette.primary.main, '#1976d2')}`
                                        : 'none',
                            }}>
                            <Typography
                                variant="h4"
                                sx={{
                                    fontWeight: 800,
                                    fontVariantNumeric: 'tabular-nums',
                                    flexShrink: 0,
                                }}>
                                {lap.startNumber}
                            </Typography>
                            <Box sx={{minWidth: 0, flex: 1}}>
                                <Typography variant="h6" noWrap sx={{fontWeight: 700}}>
                                    {names.clubs
                                        ? (lap.clubsShort ?? lap.clubsFull)
                                        : (lap.clubsFull ?? lap.clubsShort)}
                                </Typography>
                                <Typography variant="body2" noWrap>
                                    {lap.lapName}
                                </Typography>
                            </Box>
                            <Typography
                                variant="h5"
                                sx={{
                                    fontWeight: 700,
                                    fontVariantNumeric: 'tabular-nums',
                                    flexShrink: 0,
                                }}>
                                {lap.timeString}
                            </Typography>
                        </Box>
                    )}
                />
            </Stack>
        </Box>
    )
}

export default LapBand
