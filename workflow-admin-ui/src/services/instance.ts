import { client, unwrap } from '@/sdk';
import type { InstanceInfo, AppGroup } from '@/types/api';

export async function getInstances(group?: string) {
  const params = group ? { group } : {};
  return unwrap(
    await client.GET('/api/admin/instances', { params: { query: params } }),
  ) as InstanceInfo[];
}

export async function getInstanceGroups() {
  return unwrap(
    await client.GET('/api/admin/instances/groups'),
  ) as AppGroup[];
}
