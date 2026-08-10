import {addClub, ClubDto, ClubShortNameDto, ClubUpsertDto, updateClub} from '../../api'
import {getClubShortNameForName} from '@api/sdk.gen.ts'
import {BaseEntityDialogProps} from '@utils/types.ts'
import {useTranslation} from 'react-i18next'
import EntityDialog from '../EntityDialog.tsx'
import {Stack} from '@mui/material'
import {FormInputText} from '../form/input/FormInputText.tsx'
import {useForm} from 'react-hook-form-mui'
import {useCallback, useRef, useState} from 'react'
import {useUser} from '@contexts/user/UserContext.ts'
import {readClubGlobal, updateClubGlobal} from '@authorization/privileges.ts'
import {clubShortNameForRequest} from './shortName/clubShortNames.ts'

type ClubForm = {
    name: string
    shortName: string
}

const RECOMMENDED_CLUB_NAME_LENGTH = 30

const ClubDialog = (props: BaseEntityDialogProps<ClubDto>) => {
    const {t} = useTranslation()
    const user = useUser()

    // Die Kurzform ist keine Spalte am Verein, sondern hängt am Vereins*namen* - sie fällt
    // deshalb unter dieselben Rechte wie die Pflegeseite und nicht unter die des Vereins.
    // Wer den Dialog öffnen darf, aber die Kurzform nicht ändern darf, sieht sie gesperrt.
    const maySeeShortName = user.checkPrivilege(readClubGlobal)
    const mayUpdateShortName = user.checkPrivilege(updateClubGlobal)

    const defaultValues: ClubForm = {
        name: '',
        shortName: '',
    }

    const formContext = useForm<ClubForm>()

    // Die aufgelöste Kurzform zum Namen, mit dem der Dialog aufging: der Vergleichswert, an dem
    // sich "unangetastet" von "geändert" und von "geleert" unterscheiden lässt. Ohne ihn würde
    // jedes Öffnen und Speichern die automatische Kurzform als gepflegt festschreiben.
    const [resolved, setResolved] = useState<ClubShortNameDto | null>(null)
    // Ein zweites Öffnen darf nicht die Antwort des ersten ins Feld bekommen.
    const pendingFor = useRef<string | null>(null)

    // Ab dieser Länge kürzen Listen den Vereinsnamen — der Hinweis hilft, das beim
    // Anlegen zu berücksichtigen, statt es erst in der Anzeige zu merken.
    const nameValue = formContext.watch('name') ?? ''

    const onOpen = useCallback(() => {
        formContext.reset(props.entity ? mapDtoToForm(props.entity) : defaultValues)
        setResolved(null)

        const name = props.entity?.name
        pendingFor.current = name ?? null

        if (!maySeeShortName || !name) return

        getClubShortNameForName({query: {name}}).then(({data}) => {
            if (!data || pendingFor.current !== name) return
            setResolved(data)
            // Vorbelegt mit der automatisch erzeugten Kurzform, nicht leer - wer nichts anfasst,
            // verliert nichts, und leeren heißt weiterhin "zurück zur Automatik".
            formContext.setValue('shortName', data.shortName)
        })
    }, [props.entity, maySeeShortName])

    const toRequest = (formData: ClubForm): ClubUpsertDto => ({
        name: formData.name,
        shortName: clubShortNameForRequest(resolved, formData.shortName ?? '', mayUpdateShortName),
    })

    const addAction = (formData: ClubForm) => addClub({body: toRequest(formData)})

    const editAction = (formData: ClubForm, entity: ClubDto) =>
        updateClub({path: {clubId: entity.id}, body: toRequest(formData)})

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            addAction={addAction}
            editAction={editAction}>
            <Stack spacing={2}>
                <FormInputText
                    name={'name'}
                    label={t('entity.name')}
                    required
                    helperText={t('club.nameLengthHint', {
                        length: nameValue.length,
                        max: RECOMMENDED_CLUB_NAME_LENGTH,
                    })}
                />
                {maySeeShortName && (
                    <FormInputText
                        name={'shortName'}
                        label={t('club.shortName.shortName')}
                        disabled={!mayUpdateShortName}
                        helperText={t('club.shortName.dialogHint')}
                    />
                )}
            </Stack>
        </EntityDialog>
    )
}

function mapDtoToForm(dto: ClubDto): ClubForm {
    return {
        name: dto.name,
        shortName: '',
    }
}

export default ClubDialog
