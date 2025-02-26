import {Box, Card} from '@mui/material'
import {PropsWithChildren} from 'react'

type Props = PropsWithChildren & {
    maxWidth: number
}

const SimpleFormLayout = ({children, maxWidth}: Props) => {
    return (
        <Box sx={{display: 'flex'}}>
            <Card
                sx={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignSelf: 'center',
                    margin: 'auto',
                    maxWidth: maxWidth,
                    padding: 4,
                    flex:1
                }}>
                {children}
            </Card>
        </Box>
    )
}

export default SimpleFormLayout
