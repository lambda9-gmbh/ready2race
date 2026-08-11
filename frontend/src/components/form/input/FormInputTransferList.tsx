import * as React from 'react'
import {ReactNode, useEffect, useState} from 'react'
import List from '@mui/material/List'
import Card from '@mui/material/Card'
import CardHeader from '@mui/material/CardHeader'
import ListItemText from '@mui/material/ListItemText'
import ListItemIcon from '@mui/material/ListItemIcon'
import Checkbox from '@mui/material/Checkbox'
import Button from '@mui/material/Button'
import Divider from '@mui/material/Divider'
import {Box, ListItem, Stack, TextField, Typography} from '@mui/material'
import {Control, FieldPath, FieldValues} from 'react-hook-form'
import {useController, useFormContext} from 'react-hook-form-mui'
import {Trans, useTranslation} from 'react-i18next'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import {ParticipantRequirementApproveManuallyForEventForm} from '@components/event/participantRequirement/ParticipantRequirementApproveManuallyForEventDialog.tsx'

type RecordWithId = Record<string, any> & {id: string}

type TransferListProps<
    TFieldValues extends FieldValues = FieldValues,
    TName extends FieldPath<TFieldValues> = FieldPath<TFieldValues>,
    TValue extends RecordWithId = {id: string},
> = {
    name: TName
    control?: Control<TFieldValues>
    options: TValue[]
    loading?: boolean
    labelLeft: string
    labelRight: string
    renderValue: (value: TValue) => {primary: ReactNode; secondary: ReactNode}
}

export default function FormInputTransferList<
    TFieldValues extends FieldValues = FieldValues,
    TName extends FieldPath<TFieldValues> = FieldPath<TFieldValues>,
    TValue extends RecordWithId = {id: string},
>(props: TransferListProps<TFieldValues, TName, TValue>) {
    const {t} = useTranslation()
    const {name, control, labelRight, labelLeft, options, renderValue} = props
    const {field} = useController({
        name,
        control,
    })

    const formContext = useFormContext()

    const {value: right} = field

    const not = (a: TValue[], b: TValue[]): TValue[] => {
        return a.filter(value => !b.some(bValue => bValue.id === value.id))
    }

    const intersection = (a: TValue[], b: TValue[]) => {
        return a.filter(value => b.some(bValue => bValue.id === value.id))
    }

    const union = (a: TValue[], b: TValue[]) => {
        return [...a, ...not(b, a)]
    }

    const [checked, setChecked] = useState<TValue[]>([])
    const [left, setLeft] = useState<TValue[]>(not(options, right))

    useEffect(() => {
        setLeft(not(options, right))
    }, [options, right])

    const leftChecked = intersection(checked, left)
    const rightChecked = intersection(checked, right)

    const handleToggle = (value: TValue) => () => {
        const currentIndex = checked.indexOf(value)
        const newChecked = [...checked]

        if (currentIndex === -1) {
            newChecked.push(value)
        } else {
            newChecked.splice(currentIndex, 1)
        }

        setChecked(newChecked)
    }

    const numberOfChecked = (items: TValue[]) => intersection(checked, items).length

    const handleToggleAll = (items: TValue[]) => () => {
        if (numberOfChecked(items) === items.length) {
            setChecked(not(checked, items))
        } else {
            setChecked(union(checked, items))
        }
    }

    const handleCheckedRight = () => {
        const newRight = right.concat(leftChecked)
        setChecked(not(checked, leftChecked))
        field.onChange(newRight)
    }

    const handleCheckedLeft = () => {
        const newRight = not(right, rightChecked)
        setChecked(not(checked, rightChecked))
        field.onChange(newRight)
    }

    // Feste Breite der Bemerkungs-Spalte ab „sm", damit die Spaltenüberschrift („Bemerkung")
    // in der Kopfzeile mit den Eingabefeldern in der Liste fluchtet. Unterhalb von „lg"
    // schmaler, damit neben der Bemerkung genug Platz für die Namen bleibt. Auf „xs"
    // rutscht das Feld in eine eigene Zeile unter den Namen (Platzhalter statt Überschrift).
    const noteColumnWidth = {sm: 140, lg: 200}

    const customList = (title: React.ReactNode, items: TValue[], isRight: boolean) => (
        <Card elevation={2} sx={{height: '100%', display: 'flex', flexDirection: 'column'}}>
            <Stack direction={'row'} justifyContent={'space-between'} alignItems={'end'}>
                <CardHeader
                    sx={{px: 2, py: 1, minWidth: 0}}
                    avatar={
                        <Checkbox
                            onClick={handleToggleAll(items)}
                            checked={numberOfChecked(items) === items.length && items.length !== 0}
                            indeterminate={
                                numberOfChecked(items) !== items.length &&
                                numberOfChecked(items) !== 0
                            }
                            disabled={items.length === 0}
                            inputProps={{
                                'aria-label': 'all items selected',
                            }}
                        />
                    }
                    title={title}
                    subheader={`${numberOfChecked(items)}/${items.length} ${t('common.selected')}`}
                />
                {isRight && (
                    <Typography
                        sx={{
                            py: 1,
                            pr: 2,
                            // Breite = Bemerkungs-Spalte + rechtes Listen-Padding (16px),
                            // damit die Überschrift bündig über den Feldern steht
                            display: {xs: 'none', sm: 'block'},
                            width: {
                                sm: `${noteColumnWidth.sm + 16}px`,
                                lg: `${noteColumnWidth.lg + 16}px`,
                            },
                            flexShrink: 0,
                        }}>
                        <Trans i18nKey={'event.participantRequirement.checkedNote'} />
                    </Typography>
                )}
            </Stack>

            <Divider />
            <List
                sx={{
                    // Keine festen vw-Breiten mehr — die Liste füllt ihre Karte,
                    // gescrollt wird innerhalb der Liste. flexGrow gleicht aus,
                    // wenn die Kopfzeilen der beiden Karten unterschiedlich hoch sind.
                    width: '100%',
                    height: {xs: '35vh', md: '60vh'},
                    flexGrow: 1,
                    bgcolor: 'background.paper',
                    overflow: 'auto',
                }}
                dense
                component="div"
                role="list">
                {items.map((value: TValue) => {
                    const labelId = `transfer-list-all-item-${value.id}-label`
                    const {primary, secondary} = renderValue(value)

                    return (
                        <ListItem
                            key={value.id}
                            role="listitem"
                            sx={{
                                display: 'flex',
                                justifyContent: 'space-between',
                                alignItems: 'center',
                                gap: 1,
                                // Auf „xs" bricht das Bemerkungsfeld in eine eigene Zeile um
                                flexWrap: {xs: 'wrap', sm: 'nowrap'},
                            }}>
                            {/* minWidth: 0 (auch für das innere label und den Text) lässt den
                                Namensblock schrumpfen und umbrechen, statt die Liste zu sprengen */}
                            <Box
                                sx={{
                                    minWidth: 0,
                                    flex: 1,
                                    '& > label': {minWidth: 0, width: '100%'},
                                    '& .MuiListItemText-root': {minWidth: 0},
                                }}>
                                <FormInputLabel
                                    label={
                                        <ListItemText
                                            id={labelId}
                                            primary={primary}
                                            secondary={secondary}
                                        />
                                    }
                                    required
                                    horizontal
                                    reverse>
                                    <ListItemIcon>
                                        <Checkbox
                                            onClick={handleToggle(value)}
                                            checked={checked.includes(value)}
                                            tabIndex={-1}
                                            disableRipple
                                            inputProps={{
                                                'aria-labelledby': labelId,
                                            }}
                                        />
                                    </ListItemIcon>
                                </FormInputLabel>
                            </Box>
                            {isRight && (
                                <TextField
                                    sx={{
                                        width: {
                                            xs: '100%',
                                            sm: noteColumnWidth.sm,
                                            lg: noteColumnWidth.lg,
                                        },
                                        flexShrink: 0,
                                    }}
                                    placeholder={t('event.participantRequirement.checkedNote')}
                                    value={value.note}
                                    onChange={e => {
                                        const val = e.target.value
                                        const currentList: ParticipantRequirementApproveManuallyForEventForm['approvedParticipants'] =
                                            formContext.getValues('approvedParticipants')
                                        const currentIndex = currentList.findIndex(
                                            c => c.id === value.id,
                                        )!
                                        const newList = [...currentList]
                                        const p = newList[currentIndex]
                                        newList.splice(currentIndex, 1, {
                                            ...p,
                                            note: val,
                                        })
                                        formContext.setValue('approvedParticipants', newList)
                                    }}
                                />
                            )}
                        </ListItem>
                    )
                })}
            </List>
        </Card>
    )

    // Robustes Flex-Layout statt zentriertem Grid mit Umbruch: beide Listen teilen
    // sich die volle Breite (flex: 1, minWidth: 0), die Pfeilspalte bleibt schmal
    // dazwischen. Auf schmalen Viewports (unter „md") stapeln die Listen bewusst
    // untereinander, die Pfeile drehen sich dann um 90° und stehen mittig.
    return (
        <Stack
            direction={{xs: 'column', md: 'row'}}
            spacing={2}
            alignItems="stretch"
            sx={{width: '100%'}}>
            <Box sx={{flex: 1, minWidth: 0}}>{customList(labelLeft, left, false)}</Box>
            <Stack
                direction={{xs: 'row', md: 'column'}}
                spacing={1}
                alignSelf="center"
                justifyContent="center"
                sx={{flexShrink: 0}}>
                <Button
                    sx={{transform: {xs: 'rotate(90deg)', md: 'none'}}}
                    variant="outlined"
                    size="small"
                    onClick={handleCheckedRight}
                    disabled={leftChecked.length === 0}
                    aria-label="move selected right">
                    &gt;
                </Button>
                <Button
                    sx={{transform: {xs: 'rotate(90deg)', md: 'none'}}}
                    variant="outlined"
                    size="small"
                    onClick={handleCheckedLeft}
                    disabled={rightChecked.length === 0}
                    aria-label="move selected left">
                    &lt;
                </Button>
            </Stack>
            <Box sx={{flex: 1, minWidth: 0}}>{customList(labelRight, right, true)}</Box>
        </Stack>
    )
}
