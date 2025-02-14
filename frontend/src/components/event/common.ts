export function eventDayName(date: string, name?: string) {
    return name ? `${date} | ${name}` : date
} // todo: used only in one component?
