import {Typography} from '@mui/material'

type Props = {
    place: number
}

const SinglePlaceColored = ({place}: Props) => {
    return (
        <Typography
            variant={'h6'}
            sx={{
                fontWeight: place === 1 ? 'bold' : place <= 3 ? 'medium' : 'normal',
                color:
                    place === 1
                        ? 'gold'
                        : place === 2
                          ? 'silver'
                          : place === 3
                            ? '#CD7F32'
                            : 'inherit',
            }}>
            {place === 0 ? '/' : place}
        </Typography>
    )
}

export default SinglePlaceColored
