import {Link, Stack, Typography} from '@mui/material'
import TimerOutlinedIcon from '@mui/icons-material/TimerOutlined'
import {useTranslation} from 'react-i18next'

type TimingProviderSource = {
    timingProviderName?: string | null
    timingProviderUrl?: string | null
}

type Props = {
    matches: TimingProviderSource[]
    /**
     * Inline steht die Nennung in einer fremden Zeile - im Lauf-Dialog direkt hinter der Uhrzeit.
     * Sie trägt dort dieselbe Schrift und dieselbe Symbolfarbe wie die Zeitangabe daneben, statt
     * als kleingedruckte Fußzeile unter der ganzen Liste zu stehen.
     */
    inline?: boolean
}

// Attribution of the external timing provider (e.g. RaceClocker) whose terms require a visible
// reference/link wherever their timing data is published.
const TimingProviderAttribution = ({matches, inline = false}: Props) => {
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
            spacing={inline ? 1 : 0.5}
            justifyContent={inline ? undefined : 'center'}
            alignItems={'center'}
            sx={inline ? undefined : {py: 1}}>
            <TimerOutlinedIcon
                color={inline ? 'primary' : 'action'}
                fontSize={inline ? undefined : 'inherit'}
            />
            <Typography
                variant={inline ? undefined : 'body2'}
                color={inline ? undefined : 'textSecondary'}>
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
