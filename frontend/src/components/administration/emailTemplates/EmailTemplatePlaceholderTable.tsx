import {EmailTemplaplatePlaceholder} from '@api/types.gen.ts'
import {Table, TableBody, TableCell, TableRow, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'

type Props = {
    title: string
    placeholders: EmailTemplaplatePlaceholder[]
}

export function EmailTemplateTemplatePlaceholderTable({title, placeholders}: Props) {
    const {t} = useTranslation()
    return (
        <>
            <Typography sx={{width: '100%'}}>{title}</Typography>
            <Table padding={'none'}>
                <TableBody>
                    {placeholders &&
                        placeholders.map(placeholder => {
                            return (
                                <TableRow key={placeholder}>
                                    <TableCell
                                        align={'left'}
                                        sx={{
                                            width: '1%',
                                            whiteSpace: 'nowrap',
                                            pr: 4,
                                            pb: 0.5,
                                            pl: 2,
                                            borderBottom: 'none',
                                        }}>
                                        {`##${placeholder}##`}
                                    </TableCell>
                                    <TableCell align={'left'} sx={{borderBottom: 'none'}}>
                                        {t(
                                            `administration.emailTemplates.placeholders.${placeholder}`,
                                        )}
                                    </TableCell>
                                </TableRow>
                            )
                        })}
                </TableBody>
            </Table>
        </>
    )
}
