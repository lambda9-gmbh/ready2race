import {useFetch} from "@utils/hooks.ts";
import {assignDocumentTemplate, getDocumentTemplates, getDocumentTemplateTypes} from "@api/sdk.gen.ts";
import {Box, List, ListItem, MenuItem, Select, Stack, Typography} from "@mui/material";
import Throbber from "@components/Throbber.tsx";
import {useTranslation} from "react-i18next";
import {DocumentType} from "@api/types.gen.ts";
import {useState} from "react";

const AssignDocumentTemplate = () => {
    const {t} = useTranslation()

    const [lastRequested, setLastRequested] = useState(Date.now())

    const updateTemplateAssignment = async (documentType: DocumentType, template?: string) => {
        await assignDocumentTemplate({
            path: {
                documentType
            },
            body: {
                template
            }
        })

        setLastRequested(Date.now())
    }

    const {data: templates} = useFetch(
        signal => getDocumentTemplates({
            signal
        }),
        {
            deps: [lastRequested]
        }
    )

    const {data: types} = useFetch(
        signal => getDocumentTemplateTypes({signal}),
        {
            deps: [lastRequested]
        }
    )

    return (
        <Box>
            <Typography variant={'h2'}>{t('document.template.assignments.global')}</Typography>
            { types && templates ? ( // todo: @Incomplete: add error case
                <List>
                    {types.map(type => (
                        <ListItem key={type.type}>
                            <Stack direction={'row'}>
                                <Typography>
                                    {t(`document.template.type.${type.type}`)}
                                </Typography>
                                <Select
                                    value={type.assignedTemplate?.value ?? 'none'}
                                    onChange={(e) => {
                                        const value = e.target.value as string
                                        if (value === 'none') {
                                            updateTemplateAssignment(type.type)
                                        } else {
                                            updateTemplateAssignment(type.type, e.target.value as string)
                                        }
                                    }}
                                >
                                    <MenuItem value={'none'}>
                                        {t('document.template.none')}
                                    </MenuItem>
                                    {templates.data.map(template => (
                                        <MenuItem key={template.id} value={template.id}>
                                            {template.name}
                                        </MenuItem>
                                    ))}
                                </Select>
                            </Stack>
                        </ListItem>
                    ))}
                </List>
            ) : (
                <Throbber />
            )}
        </Box>
    )
}

export default AssignDocumentTemplate