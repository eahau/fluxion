import { apiGet, apiPost, apiPut, apiDelete } from './request';
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
  return apiGet<PageResponse<User>>('/api/admin/users', params);
}

export async function getUser(userId: string) {
  return apiGet<User>(`/api/admin/users/${userId}`);
}

export async function createUser(data: UserRequest) {
  return apiPost<User>('/api/admin/users', data);
}

export async function updateUser(userId: string, data: UserRequest) {
  return apiPut<User>(`/api/admin/users/${userId}`, data);
}

export async function deleteUser(userId: string) {
  return apiDelete<void>(`/api/admin/users/${userId}`);
}
