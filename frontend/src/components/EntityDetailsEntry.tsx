import {Box, Typography} from '@mui/material'
import {TypographyOwnProps} from '@mui/material/Typography/Typography'

type Props = TypographyOwnProps & {
    content: string | number | undefined
    label?: string
}
const EntityDetailsEntry = ({content, label, ...props}: Props) => {
    // todo: content > 0 ??? -> RaceDetailsEntry for now!
    return (
        ((typeof content === 'number' && content > 0) || typeof content === 'string') && (
            <Box>
                <Typography {...props}>
                    {label ? label + ': ' : ''}
                    {content}
                </Typography>
            </Box>
        )
    )
}

export default EntityDetailsEntry
