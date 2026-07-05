import { apiGet } from './request';
import type { InstanceInfo, AppGroup } from '@/types/api';

export async function getInstances(group?: string) {
  // 未指定分组时不传递 group 参数，避免 Umi request 将 undefined 序列化为字符串 "undefined"，
  // 导致后端按 group="undefined" 精确匹配返回空列表。
  const params = group ? { group } : undefined;
  return apiGet<InstanceInfo[]>('/api/admin/instances', params);
}

export async function getInstanceGroups() {
  return apiGet<AppGroup[]>('/api/admin/instances/groups');
}
