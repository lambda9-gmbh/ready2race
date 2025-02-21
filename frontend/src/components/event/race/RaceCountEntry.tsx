import {TypographyOwnProps} from '@mui/material/Typography/Typography'
import EntityDetailsEntry from '@components/EntityDetailsEntry.tsx'

type Props = TypographyOwnProps & {
    content: string | number | undefined
    label?: string
}
const RaceCountEntry = ({content, ...props}: Props) => {
    return (
        ((typeof content === 'number' && content > 0) || typeof content === 'string') && (
            <EntityDetailsEntry content={content.toString()} {...props} />
        )
    )
}

export default RaceCountEntry
