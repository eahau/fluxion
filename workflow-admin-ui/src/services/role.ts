import { apiGet, apiPost, apiPut, apiDelete } from './request';
import type { PageResponse } from '@/types/api';

export interface Role {
  id?: string;
  name: string;
  code?: string;
  description?: string;
  permissions?: string[];
  createdAt?: string;
  updatedAt?: string;
}

export interface RoleRequest {
  name: string;
  code: string;
  description?: string;
  permissions?: string[];
}

export async function listRoles(params?: { keyword?: string; page?: number; pageSize?: number }) {
  return apiGet<PageResponse<Role>>('/api/admin/roles', params);
}

export async function getRole(roleId: string) {
  return apiGet<Role>(`/api/admin/roles/${roleId}`);
}

export async function createRole(data: RoleRequest) {
  return apiPost<Role>('/api/admin/roles', data);
}

export async function updateRole(roleId: string, data: RoleRequest) {
  return apiPut<Role>(`/api/admin/roles/${roleId}`, data);
}

export async function deleteRole(roleId: string) {
  return apiDelete<void>(`/api/admin/roles/${roleId}`);
}
