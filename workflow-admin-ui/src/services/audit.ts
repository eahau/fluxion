import { apiGet } from './request';
import type { PageResponse } from '@/types/api';

export interface AuditLog {
  id: string;
  operator: string;
  action: string;
  resourceType: string;
  resourceId: string;
  detail?: string;
  ip?: string;
  timestamp: string;
}

export async function getAuditLogs(params?: {
  operator?: string;
  action?: string;
  resourceType?: string;
  startTime?: string;
  endTime?: string;
  page?: number;
  pageSize?: number;
}) {
  return apiGet<PageResponse<AuditLog>>('/api/admin/audit-logs', params);
}
