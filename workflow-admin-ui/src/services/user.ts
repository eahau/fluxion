import { client, unwrap } from '@/sdk';
import type { PageResponse } from '@/types/api';

export interface User {
  id?: string;
  username: string;
  nickname?: string;
  email?: string;
  phone?: string;
  status?: 'ACTIVE' | 'INACTIVE';
  roles?: string[];
  createdAt?: string;
  updatedAt?: string;
}

export interface UserRequest {
  username: string;
  nickname?: string;
  email?: string;
  phone?: string;
  status?: 'ACTIVE' | 'INACTIVE';
  roleIds?: string[];
}

export async function listUsers(params?: { keyword?: string; page?: number; pageSize?: number }) {
  return unwrap(
    await client.GET('/api/admin/users', { params: { query: params ?? {} } }),
  ) as PageResponse<User>;
}

export async function getUser(userId: string) {
  return unwrap(
    await client.GET('/api/admin/users/{userId}', { params: { path: { userId } } }),
  ) as User;
}

export async function createUser(data: UserRequest) {
  return unwrap(
    await client.POST('/api/admin/users', { body: data as any }),
  ) as User;
}

export async function updateUser(userId: string, data: UserRequest) {
  return unwrap(
    await client.PUT('/api/admin/users/{userId}', {
      params: { path: { userId } },
      body: data as any,
    }),
  ) as User;
}

export async function deleteUser(userId: string) {
  unwrap(
    await client.DELETE('/api/admin/users/{userId}', { params: { path: { userId } } }),
  );
  return undefined as void;
}
