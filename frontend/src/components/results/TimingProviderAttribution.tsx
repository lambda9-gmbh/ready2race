import {Link, Stack, Typography} from '@mui/material'
import TimerOutlinedIcon from '@mui/icons-material/TimerOutlined'
import {useTranslation} from 'react-i18next'

type TimingProviderSource = {
    timingProviderName?: string | null
    timingProviderUrl?: string | null
}

type Props = {
    matches: TimingProviderSource[]
}

// Attribution of the external timing provider (e.g. RaceClocker) whose terms require a visible
// reference/link wherever their timing data is published.
const TimingProviderAttribution = ({matches}: Props) => {
    const {t} = useTranslation()

    const providers = [
        ...new Map(
            matches
                .filter(match => match.timingProviderName)
                .map(match => [match.timingProviderName!, match.timingProviderUrl ?? null]),
        ).entries(),
    ]

    if (providers.length === 0) {
        return null
    }

    return (
        <Stack
            direction={'row'}
            spacing={0.5}
            justifyContent={'center'}
            alignItems={'center'}
            sx={{py: 1}}>
            <TimerOutlinedIcon color={'action'} fontSize={'inherit'} />
            <Typography variant={'body2'} color={'textSecondary'}>
                {t('results.timing.attribution')}{' '}
                {providers.map(([name, url], index) => (
                    <span key={name}>
                        {index > 0 && ', '}
                        {url ? (
                            <Link href={url} target={'_blank'} rel={'noopener noreferrer'}>
                                {name}
                            </Link>
                        ) : (
                            name
                        )}
                    </span>
                ))}
            </Typography>
        </Stack>
    )
}

export default TimingProviderAttribution
