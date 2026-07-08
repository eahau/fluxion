import { client, unwrap } from '@/sdk';
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
  return unwrap(
    await client.GET('/api/admin/roles', { params: { query: params ?? {} } }),
  ) as PageResponse<Role>;
}

export async function getRole(roleId: string) {
  return unwrap(
    await client.GET('/api/admin/roles/{roleId}', { params: { path: { roleId } } }),
  ) as Role;
}

export async function createRole(data: RoleRequest) {
  return unwrap(
    await client.POST('/api/admin/roles', { body: data as any }),
  ) as Role;
}

export async function updateRole(roleId: string, data: RoleRequest) {
  return unwrap(
    await client.PUT('/api/admin/roles/{roleId}', {
      params: { path: { roleId } },
      body: data as any,
    }),
  ) as Role;
}

export async function deleteRole(roleId: string) {
  unwrap(
    await client.DELETE('/api/admin/roles/{roleId}', { params: { path: { roleId } } }),
  );
  return undefined as void;
}
