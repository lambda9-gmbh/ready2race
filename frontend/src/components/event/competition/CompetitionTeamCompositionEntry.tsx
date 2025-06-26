import {ListItem, ListItemText, Stack, Typography} from '@mui/material'
import {HtmlTooltip} from '@components/HtmlTooltip.tsx'
import {Info} from '@mui/icons-material'
import {NamedParticipantForCompetitionDto} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'

type Props = {
    np: NamedParticipantForCompetitionDto
    gender: 'male' | 'female' | 'nonBinary' | 'mixed'
}
const CompetitionTeamCompositionEntry = ({np, gender}: Props) => {
    const {t} = useTranslation()

    const count =
        gender === 'male'
            ? np.countMales
            : gender === 'female'
              ? np.countFemales
              : gender === 'nonBinary'
                ? np.countNonBinary
                : np.countMixed

    return count > 0 ? (
        <ListItem>
            <Stack
                spacing={1}
                direction={'row'}
                key={np.id + '-m'}
                sx={{flexWrap: 'wrap', alignItems: 'center'}}>
                <ListItemText>
                    {count} {np.name}{' '}
                    {gender === 'male'
                        ? t('event.competition.gender.male')
                        : gender === 'female'
                          ? t('event.competition.gender.female')
                          : gender === 'nonBinary'
                            ? t('event.competition.gender.nonBinary')
                            : t('event.competition.gender.mixed')}
                </ListItemText>
                {np.description && (
                    <HtmlTooltip
                        title={
                            <>
                                <Typography fontWeight={'bold'}>{np.name}</Typography>
                                <Typography>{np.description}</Typography>
                            </>
                        }>
                        <Info color={'info'} fontSize={'small'} />
                    </HtmlTooltip>
                )}
            </Stack>
        </ListItem>
    ) : (
        <></>
    )
}

export default CompetitionTeamCompositionEntry
