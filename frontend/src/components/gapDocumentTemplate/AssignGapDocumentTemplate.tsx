import {Trans, useTranslation} from 'react-i18next'
import {GapDocumentType} from '@api/types.gen.ts'
import {
    assignGapDocumentTemplate,
    getGapDocumentTemplates,
    getGapDocumentTemplateTypes,
} from '@api/sdk.gen.ts'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {Box, List, ListItem, MenuItem, Select, Typography} from '@mui/material'
import Throbber from '@components/Throbber.tsx'

const AssignGapDocumentTemplate = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {data: templates, reload: reloadTemplates} = useFetch(signal =>
        getGapDocumentTemplates({signal}),
    )

    const {data: types, reload: reloadTypes} = useFetch(signal =>
        getGapDocumentTemplateTypes({signal}),
    )

    const updateTemplateAssignment = async (
        gapDocumentType: GapDocumentType,
        template?: string,
    ) => {
        const {error} = await assignGapDocumentTemplate({
            path: {
                gapDocumentType,
            },
            body: {
                template,
            },
        })

        if (error) {
            feedback.error(
                error.status.value === 400
                    ? t('gap.document.template.assignments.error.typeMismatch')
                    : t('common.error.unexpected'),
            )
            return
        }

        reloadTemplates()
        reloadTypes()
    }

    return (
        <Box>
            <Typography variant={'h2'}>
                <Trans i18nKey={'gap.document.template.assignments.global'} />
                {types && templates ? (
                    <List>
                        {types.map(type => (
                            <ListItem key={type.type}>
                                <Box
                                    sx={{
                                        width: 1,
                                        display: 'flex',
                                        justifyContent: 'space-between',
                                        alignItems: 'center',
                                    }}>
                                    <Typography>
                                        <Trans
                                            i18nKey={`gap.document.template.types.${type.type}`}
                                        />
                                    </Typography>
                                    <Select
                                        value={type.assignedTemplate?.value ?? 'none'}
                                        onChange={e => {
                                            const value = e.target.value as string
                                            if (value === 'none') {
                                                updateTemplateAssignment(type.type)
                                            } else {
                                                updateTemplateAssignment(type.type, value)
                                            }
                                        }}>
                                        <MenuItem value={'none'}>
                                            <Trans i18nKey={'document.template.none'} />
                                        </MenuItem>
                                        {templates.data
                                            .filter(template => template.type === type.type)
                                            .map(template => (
                                                <MenuItem key={template.id} value={template.id}>
                                                    {template.name}
                                                </MenuItem>
                                            ))}
                                    </Select>
                                </Box>
                            </ListItem>
                        ))}
                    </List>
                ) : (
                    <Throbber />
                )}
            </Typography>
        </Box>
    )
}

export default AssignGapDocumentTemplate
