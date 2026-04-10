import type { ProviderMetricItem } from '@/types'
import { request } from './http'

export const getMetrics = () => request<ProviderMetricItem[]>('/api/system/metrics')
