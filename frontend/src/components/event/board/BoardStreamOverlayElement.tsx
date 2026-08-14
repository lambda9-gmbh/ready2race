import {useMemo} from 'react'
import {Box} from '@mui/material'
import {BoardElement, BoardViewDto} from '@api/types.gen.ts'
import {streamOverlayContent, STREAM_DEFAULT_BACKGROUND} from './streamOverlay.ts'
import ClockPlate from './streamOverlay/ClockPlate.tsx'
import LapBand from './streamOverlay/LapBand.tsx'
import ResultPanel from './streamOverlay/ResultPanel.tsx'
import RunningLowerThird from './streamOverlay/RunningLowerThird.tsx'
import UpcomingListPanel from './streamOverlay/UpcomingListPanel.tsx'
import UpcomingPanel from './streamOverlay/UpcomingPanel.tsx'

type Props = {
    view: BoardViewDto
    element: BoardElement
}

/**
 * Das Livestream-Overlay: vollflächige Key-Farbe, darüber je nach Modus ein
 * Lower-Third (laufend), ein zentriertes TV-Grafik-Panel (Ergebnis/Als Nächstes/
 * Nächste Läufe) oder ein Rundenband (Rundenanzeige) im r2r-Design.
 *
 * Chroma-Regeln: JEDE sichtbare Fläche ist vollständig deckend — keine Halbtransparenz,
 * keine weichen Schatten, kein Blur. Halbtransparente Kanten mischen sich mit der
 * Key-Farbe und erzeugen Farbsäume beim Keying. Bewegung läuft deshalb ausschließlich
 * über CSS-Transforms (`FlipList`), niemals über eine Flächen-Opacity; die einzige
 * erlaubte Opacity-Fade ist die Laufuhr-Text auf dem bereits deckenden Panel (siehe
 * `streamOverlay/useStreamClockDisplay.ts`).
 */
const BoardStreamOverlayElement = ({view, element}: Props) => {
    const content = streamOverlayContent(view, element.streamMode)
    const keyColor = element.backgroundColor ?? STREAM_DEFAULT_BACKGROUND

    // Serverzeitversatz: einmal je frisch eingetroffener Antwort gemessen (serverTime
    // ändert sich nur, wenn ein Poll wirklich eine neue Antwort brachte) — nicht bei
    // jedem Render, sonst würde jeder Uhr-/Countdown-Tick die Messung verfälschen.
    const clockOffsetMs = useMemo(() => Date.now() - Date.parse(view.serverTime), [view.serverTime])

    // Leerzustand: reine Key-Farbe — das Overlay verschwindet im Stream von selbst.
    // Ausnahme „Nur Laufuhr": Die Platte bleibt dauerhaft stehen (0:00.0 bis zum
    // nächsten Start), damit die Regie die gecroppte Quelle nie verliert.
    if (content === null) {
        return (
            <Box sx={{position: 'fixed', inset: 0, backgroundColor: keyColor}}>
                {element.streamMode === 'CLOCK' && (
                    <ClockPlate match={null} clockOffsetMs={clockOffsetMs} />
                )}
            </Box>
        )
    }

    return (
        <Box sx={{position: 'fixed', inset: 0, backgroundColor: keyColor}}>
            {content.kind === 'running' && (
                <RunningLowerThird match={content.match} element={element} clockOffsetMs={clockOffsetMs} />
            )}
            {content.kind === 'result' && <ResultPanel result={content.result} element={element} />}
            {content.kind === 'upcoming' && (
                <UpcomingPanel match={content.match} element={element} clockOffsetMs={clockOffsetMs} />
            )}
            {content.kind === 'upcomingList' && (
                <UpcomingListPanel matches={content.matches} element={element} />
            )}
            {content.kind === 'laps' && <LapBand laps={content.laps} element={element} />}
            {content.kind === 'clock' && (
                <ClockPlate match={content.match} clockOffsetMs={clockOffsetMs} />
            )}
        </Box>
    )
}

export default BoardStreamOverlayElement
