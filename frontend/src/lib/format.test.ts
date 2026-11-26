import { describe, expect, it } from 'vitest';
import { formatMinorMoney, formatUtcTimestamp } from './format';

describe('formatMinorMoney', () => {
  it('formats INR minor units (paise) accurately', () => {
    const formatted = formatMinorMoney(125050, 'INR');
    expect(formatted).toContain('1,250.50');
  });

  it('formats zero minor units', () => {
    expect(formatMinorMoney(0, 'INR')).toContain('0.00');
  });
});

describe('formatUtcTimestamp', () => {
  it('returns em dash for null or undefined', () => {
    expect(formatUtcTimestamp(null)).toBe('—');
    expect(formatUtcTimestamp(undefined)).toBe('—');
  });

  it('formats valid ISO timestamp', () => {
    expect(formatUtcTimestamp('2026-11-26T04:00:00Z')).not.toBe('—');
  });
});
