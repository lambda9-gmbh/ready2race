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
            <Card>
                <CardHeader action={props.headerAction} subheader={props.header} sx={{pb: 0}} />
                <CardContent sx={{maxHeight: 300, overflowY: 'scroll'}}>
                    {props.content}
                </CardContent>
                {props.footerAction && (
                    <CardActions sx={{justifyContent: 'end'}}>{props.footerAction}</CardActions>
                )}
            </Card>
        </Grid2>
    )
}
