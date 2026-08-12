import {Box, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {BoardElement, BoardViewDto} from '@api/types.gen'
import {scaled} from '../info/athleteBoard/common'
import PlaceOrdinal from '@components/PlaceOrdinal'
import {ceremonyForElement} from './boardView'

interface BoardCeremonyElementProps {
    element: BoardElement
    view: BoardViewDto
}

/**
 * Das Podium einer Siegerehrung — dieselben Ränge wie auf dem gedruckten
 * Siegerehrungsbogen: großer Rang als Ordnungszahl, Vereinszeile, Boot, Crew mit
 * Rollen und (abweichenden) Heimatvereinen.
 */
const BoardCeremonyElement = ({element, view}: BoardCeremonyElementProps) => {
    const {t} = useTranslation()

    const ceremony = ceremonyForElement(view, element)

    const title = ceremony
        ? [
              [ceremony.competitionShortName ?? ceremony.competitionName, ceremony.competitionIdentifier]
                  .filter(Boolean)
                  .join(' · '),
              ceremony.ratingCategoryName,
          ]
              .filter(Boolean)
              .join(' — ')
        : t('event.boards.element.type.awardCeremony')

    return (
        <Box
            sx={{
                height: '100%',
                minHeight: 0,
                display: 'grid',
                gridTemplateRows: 'auto auto minmax(0, 1fr)',
                rowGap: scaled('0.25rem', '0.4vw', '0.6rem'),
                p: scaled('0.5rem', '0.9vw', '1.25rem'),
                border: '1px solid',
                borderColor: 'divider',
                borderRadius: 1,
            }}>
            <Typography
                sx={{
                    fontSize: scaled('0.75rem', '1vw', '1.8rem'),
                    fontWeight: 700,
                    textTransform: 'uppercase',
                    letterSpacing: '0.04em',
                }}
                color="text.secondary">
                {t('event.boards.element.ceremonyTitle')}
            </Typography>
            <Typography sx={{fontSize: scaled('1rem', '1.8vw', '2.6rem'), fontWeight: 800}} noWrap>
                {ceremony?.competitionName ?? title}
                {ceremony?.ratingCategoryName ? ` — ${ceremony.ratingCategoryName}` : ''}
            </Typography>

            {/* Scrollen statt Abschneiden: ein langes Siegerfeld bleibt per Scroll
                erreichbar, statt hinter der Zellkante zu verschwinden. */}
            <Box sx={{minHeight: 0, overflow: 'auto', display: 'grid', alignContent: 'start'}}>
                {!ceremony || ceremony.ranks.length === 0 ? (
                    <Typography
                        sx={{fontSize: scaled('0.85rem', '1.2vw', '1.8rem')}}
                        color="text.secondary">
                        {t('event.boards.element.ceremonyEmpty')}
                    </Typography>
                ) : (
                    ceremony.ranks.map((rank, index) => (
                        <Stack
                            key={index}
                            direction="row"
                            gap={1.5}
                            alignItems="flex-start"
                            sx={{
                                borderTop: index > 0 ? '1px solid' : 'none',
                                borderColor: 'divider',
                                py: scaled('0.3rem', '0.5vw', '0.8rem'),
                                minWidth: 0,
                            }}>
                            <Typography
                                sx={{
                                    fontSize: scaled('1.4rem', '2.8vw', '4.5rem'),
                                    fontWeight: 800,
                                    lineHeight: 1,
                                    minWidth: '2em',
                                    textAlign: 'center',
                                    flexShrink: 0,
                                }}>
                                {/* Bei geteilten Rängen trägt nur das erste Boot die große Zahl. */}
                                {rank.first ? <PlaceOrdinal place={rank.rank} /> : ''}
                            </Typography>
                            <Box sx={{flex: 1, minWidth: 0}}>
                                <Typography
                                    sx={{
                                        fontSize: scaled('0.95rem', '1.6vw', '2.4rem'),
                                        fontWeight: 700,
                                    }}>
                                    {rank.team.clubLine}
                                    {rank.shared
                                        ? ` (${t('event.boards.element.sharedRank')})`
                                        : ''}
                                </Typography>
                                <Typography
                                    sx={{fontSize: scaled('0.7rem', '1.1vw', '1.5rem')}}
                                    color="text.secondary">
                                    {[rank.team.boatLine, rank.team.time, rank.team.penalty]
                                        .filter(Boolean)
                                        .join(' · ')}
                                </Typography>
                                {rank.team.athletes.map((athlete, i) => (
                                    <Typography
                                        key={i}
                                        noWrap
                                        sx={{fontSize: scaled('0.75rem', '1.2vw', '1.6rem')}}>
                                        {[
                                            athlete.role
                                                ? `${athlete.name} (${athlete.role})`
                                                : athlete.name,
                                            athlete.club,
                                        ]
                                            .filter(Boolean)
                                            .join(' · ')}
                                    </Typography>
                                ))}
                            </Box>
                        </Stack>
                    ))
                )}
            </Box>
        </Box>
    )
}

export default BoardCeremonyElement
