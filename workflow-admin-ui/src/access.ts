import type { CurrentUser } from '@/types/api';

export default function access(initialState: { currentUser?: CurrentUser } | undefined) {
  const { currentUser } = initialState ?? {};
  const access = currentUser?.access ?? {};

  const permissions = currentUser?.permissions ?? [];

  return {
    // 基础权限
    canView: !!access.canView,
    canEdit: !!access.canEdit,
    canPublish: !!access.canPublish,
    canAdmin: !!access.canAdmin,

    // Schema 冻结/锁定解锁权限（ADMIN 隐式拥有）
    canUnlockSchema: permissions.includes('schema:unlock') || !!access.canAdmin,

    // 细粒度编辑权限（按模块）
    canEditSchema: permissions.includes('schema:edit') || !!access.canAdmin,
    canEditWorkflow: permissions.includes('workflow:edit') || !!access.canAdmin,
    canEditFunction: permissions.includes('function:edit') || !!access.canAdmin,

    // 角色
    isAdmin: currentUser?.roles?.includes('ADMIN') ?? false,
    isEditor: currentUser?.roles?.includes('Editor') ?? false,
  };
}
