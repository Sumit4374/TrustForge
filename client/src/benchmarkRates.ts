import type { BenchmarkRate } from './types'
export function rateText(rate: BenchmarkRate) { return rate.denominator === 0 || rate.percentage == null ? '— (no eligible sample)' : `${rate.numerator}/${rate.denominator} (${Math.round(rate.percentage)}%)` }
