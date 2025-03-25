export const getWeightings = (matchCount: number) => {
    const rounds = Math.ceil(Math.log(matchCount) / Math.log(2))

    let matches: number[][] = [[1, 2]]

    // If that weighting does not exist in the maxMatchIndex range, it is replaced by -1
    const getIfInRange = (weighting: number) => {
        if (weighting > matchCount) return -1

        return weighting
    }

    for (let round = 1; round < rounds; round++) {
        const roundMatches: number[][] = []
        const sum = Math.pow(2, round + 1) + 1

        for (let i = 0; i < matches.length; i++) {
            let home = getIfInRange(matches[i][0])
            let away = getIfInRange(sum - matches[i][0])
            roundMatches.push([home, away])
            home = getIfInRange(sum - matches[i][1])
            away = getIfInRange(matches[i][1])
            roundMatches.push([home, away])
        }
        matches = roundMatches
    }

    return matches.flat()
}
