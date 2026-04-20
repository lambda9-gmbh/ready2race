import {EventDayScheduleCompetitionMatchDataDto} from '@api/types.gen.ts'

const calculateOffsetDurationMinutesRoundedUp = (
    occurringTeamCount: number,
    startTimeOffsetRaw?: number,
): number => {
    const normalizedOffsetSeconds =
        !startTimeOffsetRaw || startTimeOffsetRaw <= 0
            ? 0
            : startTimeOffsetRaw >= 1000 && startTimeOffsetRaw % 1000 === 0
              ? startTimeOffsetRaw / 1000
              : startTimeOffsetRaw

    const nonNegativeOffsetSeconds = Math.max(0, normalizedOffsetSeconds)
    const additionalStartShifts = Math.max(0, occurringTeamCount - 1)
    if (nonNegativeOffsetSeconds <= 0 || additionalStartShifts <= 0) {
        return 0
    }

    const totalOffsetSeconds = additionalStartShifts * nonNegativeOffsetSeconds
    return Math.ceil(totalOffsetSeconds / 60)
}

export const calculateMatchDurationWithOffsetMinutes = (
    match: EventDayScheduleCompetitionMatchDataDto,
    baseMatchDuration: number,
): number =>
    baseMatchDuration +
    calculateOffsetDurationMinutesRoundedUp(
        match.occurringTeamCount ?? 0,
        match.startTimeOffsetSeconds,
    )

export const calculateTotalDurationMinutes = (
    matches: EventDayScheduleCompetitionMatchDataDto[],
    baseMatchDuration: number,
    matchGapDuration: number,
): number => {
    if (matches.length === 0) {
        return 0
    }

    const totalMatchDuration = matches.reduce(
        (sum, match) => sum + calculateMatchDurationWithOffsetMinutes(match, baseMatchDuration),
        0,
    )
    const totalGapDuration = Math.max(0, matches.length - 1) * matchGapDuration
    return totalMatchDuration + totalGapDuration
}
