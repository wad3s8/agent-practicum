const DAY_IN_MS = 24 * 60 * 60 * 1000;

export function formatIsoDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
}

export function formatDisplayDate(date: Date) {
  const day = String(date.getDate()).padStart(2, '0');
  const month = String(date.getMonth() + 1).padStart(2, '0');

  return `${day}.${month}`;
}

export function getStartOfWeek(date = new Date()) {
  const start = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const day = start.getDay();
  const mondayOffset = day === 0 ? -6 : 1 - day;

  start.setDate(start.getDate() + mondayOffset);

  return start;
}

export function getWeekStartForPeriod(period: string) {
  if (period !== 'week') {
    return undefined;
  }

  return formatIsoDate(getStartOfWeek());
}

export function getPeriodStart(period: string) {
  const now = new Date();

  if (period === 'week') {
    return formatIsoDate(getStartOfWeek(now));
  }

  if (period === 'month') {
    return formatIsoDate(new Date(now.getFullYear(), now.getMonth(), 1));
  }

  if (period === 'quarter') {
    const quarterStartMonth = Math.floor(now.getMonth() / 3) * 3;
    return formatIsoDate(new Date(now.getFullYear(), quarterStartMonth, 1));
  }

  return undefined;
}


export function addDays(date: Date, days: number) {
  return new Date(date.getTime() + days * DAY_IN_MS);
}

export function getPeriodEnd(period: string) {
  const now = new Date();

  if (period === 'week') {
    return formatIsoDate(addDays(getStartOfWeek(now), 6));
  }

  if (period === 'month') {
    return formatIsoDate(new Date(now.getFullYear(), now.getMonth() + 1, 0));
  }

  if (period === 'quarter') {
    const quarterStartMonth = Math.floor(now.getMonth() / 3) * 3;
    return formatIsoDate(new Date(now.getFullYear(), quarterStartMonth + 3, 0));
  }

  return undefined;
}
