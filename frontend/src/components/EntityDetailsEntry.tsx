import {Box, Typography} from '@mui/material'
import {TypographyOwnProps} from '@mui/material/Typography/Typography'

type Props = TypographyOwnProps & {
    content: string | undefined
    label?: string
}
const EntityDetailsEntry = ({content, label, ...props}: Props) => {
    return (
        content !== undefined && (
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
