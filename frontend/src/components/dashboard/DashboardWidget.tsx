import {Card, CardActions, CardContent, CardHeader, Grid2} from '@mui/material'
import {ReactNode} from 'react'

export const DashboardWidget = (props: {
    size: number
    color?: string
    headerAction?: ReactNode
    header?: string
    content: ReactNode
    footerAction?: ReactNode
}) => {
    return (
        <Grid2 size={{xs: props.size}}>
            <Card
                variant={'outlined'}
                sx={{
                    background:
                        'linear-gradient(62deg, rgba(255,255,255,1) 48%, ' +
                        (props.color || 'rgba(42,157,143,0.22)') +
                        ' 100%)',
                }}>
                <CardHeader action={props.headerAction} subheader={props.header} sx={{pb: 0}} />
                <CardContent>{props.content}</CardContent>
                {props.footerAction && (
                    <CardActions sx={{justifyContent: 'end'}}>{props.footerAction}</CardActions>
                )}
            </Card>
        </Grid2>
    )
}
