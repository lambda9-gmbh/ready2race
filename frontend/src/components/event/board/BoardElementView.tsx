import {BoardElement, BoardViewDto} from '@api/types.gen'
import BoardClockElement from './BoardClockElement'
import BoardMatchListElement from './BoardMatchListElement'
import BoardMatchSlotElement from './BoardMatchSlotElement'
import BoardTextElement from './BoardTextElement'

interface BoardElementViewProps {
    element: BoardElement
    view: BoardViewDto
    now: Date
    columns: number
    rows: number
}

/** Die Weiche über die Elementtypen — jede Kachel rendert ihr aktives Element hierüber. */
const BoardElementView = ({element, view, now, columns, rows}: BoardElementViewProps) => {
    switch (element.type) {
        case 'MATCH':
            return (
                <BoardMatchSlotElement
                    element={element}
                    view={view}
                    now={now}
                    columns={columns}
                    rows={rows}
                />
            )
        case 'MATCH_LIST':
            return <BoardMatchListElement element={element} view={view} />
        case 'CLOCK':
            return <BoardClockElement element={element} view={view} now={now} />
        case 'TEXT':
            return <BoardTextElement element={element} />
    }
}

export default BoardElementView
