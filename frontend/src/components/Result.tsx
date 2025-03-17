import {Container, Stack, Typography} from '@mui/material'
import {ReactNode} from 'react'
import {CheckCircle, Error, Info} from '@mui/icons-material'

type ResultStatus = 'SUCCESS' | 'ERROR' | 'INFO'

export const Result = (props: {
    status: ResultStatus
    title: string
    subtitle: string
    extra?: ReactNode
}) => {
    const getIcon = (status: ResultStatus) => {
        switch (status) {
            case 'SUCCESS':
                return <CheckCircle color={'success'} sx={{fontSize: '6em'}} />
            case 'ERROR':
                return <Error color={'error'} />
            case 'INFO':
                return <Info color={'info'} />
        }
    }

    return (
        <Container sx={{height: '100%'}}>
            <Stack alignItems={'center'} spacing={1} p={2}>
                {getIcon(props.status)}
                <Typography variant={'h4'}>{props.title}</Typography>
                <Typography variant={'subtitle1'}>{props.subtitle}</Typography>
                {props.extra}
            </Stack>
        </Container>
    )
}
