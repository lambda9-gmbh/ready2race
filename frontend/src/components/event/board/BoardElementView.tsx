import {BoardElement, BoardViewDto} from '@api/types.gen'
import BoardCeremonyElement from './BoardCeremonyElement'
import BoardClockElement from './BoardClockElement'
import BoardMatchListElement from './BoardMatchListElement'
import BoardMatchSlotElement from './BoardMatchSlotElement'
import BoardTextElement from './BoardTextElement'

interface BoardElementViewProps {
    element: BoardElement
    view: BoardViewDto
    now: Date
    effectiveColumns: number
    heightFraction: number
}

/** Die Weiche über die Elementtypen — jede Kachel rendert ihr aktives Element hierüber. */
const BoardElementView = ({
    element,
    view,
    now,
    effectiveColumns,
    heightFraction,
}: BoardElementViewProps) => {
    switch (element.type) {
        case 'MATCH':
            return (
                <BoardMatchSlotElement
                    element={element}
                    view={view}
                    now={now}
                    effectiveColumns={effectiveColumns}
                    heightFraction={heightFraction}
                />
            )
        case 'MATCH_LIST':
            return <BoardMatchListElement element={element} view={view} />
        case 'CLOCK':
            return <BoardClockElement element={element} view={view} now={now} />
        case 'TEXT':
            return <BoardTextElement element={element} />
        case 'AWARD_CEREMONY':
            return <BoardCeremonyElement element={element} view={view} />
    }
}

export default BoardElementView
