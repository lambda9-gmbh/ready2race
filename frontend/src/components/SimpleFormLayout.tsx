import {Box, Card} from '@mui/material'
import {PropsWithChildren} from 'react'

const SimpleFormLayout = ({children}: PropsWithChildren) => {
    return (
        <Box sx={{display: 'flex'}}>
            <Card
                sx={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignSelf: 'center',
                    margin: 'auto',
                    maxWidth: 600,
                    padding: 4,
                }}>
                {children}
            </Card>
        </Box>
    )
}

export default SimpleFormLayout
