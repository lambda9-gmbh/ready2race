import {RacePropertiesDto, RacePropertiesRequestDto} from '../../../api'
import {takeIfNotEmpty} from '../../../utils/ApiUtils.ts'
import {AutocompleteOption} from '../../../utils/types.ts'

export type RaceForm = {
    identifier: string
    name: string
    shortName: string
    description: string
    countMales: string
    countFemales: string
    countNonBinary: string
    countMixed: string
    participationFee: string
    rentalFee: string
    raceCategory: AutocompleteOption
    namedParticipants: {
        namedParticipant: AutocompleteOption
        required: boolean
        countMales: string
        countFemales: string
        countNonBinary: string
        countMixed: string
    }[]
}

export const raceFormDefaultValues: RaceForm = {
    identifier: '',
    name: '',
    shortName: '',
    description: '',
    countMales: '0',
    countFemales: '0',
    countNonBinary: '0',
    countMixed: '0',
    participationFee: '',
    rentalFee: '',
    raceCategory: {id: '', label: ''},
    namedParticipants: [],
}

export function mapRaceFormToRacePropertiesRequest(formData: RaceForm): RacePropertiesRequestDto {
    return {
        identifier: formData.identifier,
        name: formData.name,
        shortName: takeIfNotEmpty(formData.shortName),
        description: takeIfNotEmpty(formData.description),
        countMales: Number(formData.countMales),
        countFemales: Number(formData.countFemales),
        countNonBinary: Number(formData.countNonBinary),
        countMixed: Number(formData.countMixed),
        participationFee: formData.participationFee.replace(',', '.'),
        rentalFee: formData.rentalFee.replace(',', '.'),
        raceCategory: takeIfNotEmpty(formData.raceCategory.id),
        namedParticipants: formData.namedParticipants.map(value => ({
            namedParticipant: value.namedParticipant.id,
            required: value.required,
            countMales: Number(value.countMales),
            countFemales: Number(value.countFemales),
            countNonBinary: Number(value.countNonBinary),
            countMixed: Number(value.countMixed),
        })),
    }
}

export function mapRacePropertiesToRaceForm(
    dto: RacePropertiesDto,
    decimalPoint: string,
): RaceForm {
    return {
        identifier: dto.identifier,
        name: dto.name,
        shortName: dto.shortName ?? '',
        description: dto.description ?? '',
        countMales: dto.countMales.toString(),
        countFemales: dto.countFemales.toString(),
        countNonBinary: dto.countNonBinary.toString(),
        countMixed: dto.countMixed.toString(),
        participationFee: dto.participationFee.replace('.', decimalPoint),
        rentalFee: dto.rentalFee.replace('.', decimalPoint),
        raceCategory: dto.raceCategory
            ? {
                  id: dto.raceCategory?.id,
                  label: dto.raceCategory.name,
              }
            : {id: '', label: ''},
        namedParticipants: dto.namedParticipants.map(value => ({
            namedParticipant: {id: value.id, label: value.name},
            required: value.required,
            countMales: value.countMales.toString(),
            countFemales: value.countFemales.toString(),
            countNonBinary: value.countNonBinary.toString(),
            countMixed: value.countMixed.toString(),
        })),
    }
}
