import {Box} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {BoardElement, BoardViewDto} from '@api/types.gen'
import AthleteBoardColumnCard from '../info/athleteBoard/AthleteBoardColumnCard'
import AthleteBoardMatchCard from '../info/athleteBoard/AthleteBoardMatchCard'
import AthleteBoardResultCard from '../info/athleteBoard/AthleteBoardResultCard'
import {elementScale, slotForElement} from './boardView'

interface BoardMatchSlotElementProps {
    element: BoardElement
    view: BoardViewDto
    now: Date
    /** Breiten- und Höhenanteil der Kachel im Raster — bestimmen die Dichteformel. */
    effectiveColumns: number
    heightFraction: number
}

/**
 * Ein Lauf-Element: eine Position der Tages-Timeline als Karte. Die Darstellung folgt
 * dem Zustand des gelieferten Laufs, nicht dem Vorzeichen des Offsets — ein noch
 * laufender Lauf auf Offset −1 erscheint als Aufstellungs-Karte mit Zwischenständen,
 * ein beendeter als Ergebnis-Karte.
 */
const BoardMatchSlotElement = ({
    element,
    view,
    now,
    effectiveColumns,
    heightFraction,
}: BoardMatchSlotElementProps) => {
    const {t} = useTranslation()

    const offset = element.offset ?? 0
    const slot = slotForElement(view, element)
    const content = slot ? {match: slot.match ?? null, result: slot.result ?? null} : null

    const title =
        offset === 0
            ? t('event.boards.element.titleCurrent')
            : offset === 1
              ? t('event.boards.element.titleNext')
              : offset > 1
                ? t('event.boards.element.titleUpcoming', {count: offset})
                : offset === -1
                  ? t('event.boards.element.titleLastResult')
                  : t('event.boards.element.titlePast', {count: -offset})

    const emptyText =
        offset === 0
            ? t('event.boards.element.emptyCurrent')
            : offset > 0
              ? t('event.boards.element.emptyUpcoming')
              : t('event.boards.element.emptyPast')

    // Ein Lauf im Slot ist entweder in der Arena (Offset ≤ 0: laufend/in Vorbereitung,
    // Zwischenstände zeigen) oder anstehend (Offset > 0: Countdown zeigen).
    const variant = offset <= 0 ? 'running' : 'upcoming'

    return (
        <Box
            sx={{
                height: '100%',
                minHeight: 0,
                '--ab-scale': elementScale(element, content, effectiveColumns, heightFraction),
                // „Farben aus": nüchterne Schwarzweiß-Darstellung für kontrastarme
                // Umgebungen (direkte Sonne am Steg) — bewusst als Filter statt je
                // Farbstelle, damit alle Karten-Bestandteile denselben Modus tragen.
                filter: element.contrastColors === false ? 'grayscale(1)' : undefined,
                // Ohne autoFit bleibt die Schrift in voller Größe; ein volles Feld darf
                // dann abgeschnitten werden statt zu schrumpfen.
                overflow: element.autoFit === false ? 'auto' : undefined,
            }}>
            <AthleteBoardColumnCard title={title} emptyText={emptyText}>
                {content?.match ? (
                    <AthleteBoardMatchCard
                        match={content.match}
                        now={now}
                        variant={variant}
                        showCountdown={element.showCountdown !== false}
                        showCrew={element.showCrew !== false}
                        showTimes={element.showTimes !== false}
                        showCrewDetails={element.showCrewDetails === true}
                        showBirthYears={element.showBirthYears === true}
                        showAdvancement={element.showAdvancement === true}
                        showRegisteringClub={element.showRegisteringClub === true}
                    />
                ) : content?.result ? (
                    <AthleteBoardResultCard
                        result={content.result}
                        showTimes={element.showTimes !== false}
                    />
                ) : undefined}
            </AthleteBoardColumnCard>
        </Box>
    )
}

export default BoardMatchSlotElement
