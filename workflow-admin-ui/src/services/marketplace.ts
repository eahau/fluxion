/**
 * 工作流市场 API 服务
 */
import { apiGet, apiPost, apiDelete } from './request';
import type {
  MarketplaceListing,
  MarketplacePublishRequest,
  MarketplaceInstallRequest,
  MarketplaceInstallRecord,
  MarketplaceInstallResult,
} from '@/types/marketplace';

/** 获取所有 ACTIVE 的市场 listing */
export async function getListings() {
  return apiGet<MarketplaceListing[]>('/api/admin/marketplace');
}

/** 搜索市场 */
export async function searchMarketplace(keyword: string) {
  return apiGet<MarketplaceListing[]>('/api/admin/marketplace/search', { keyword });
}

/** 获取单个 listing 详情 */
export async function getListing(listingId: string) {
  return apiGet<MarketplaceListing>(`/api/admin/marketplace/${listingId}`);
}

/** 发布工作流到市场 */
export async function publishWorkflow(data: MarketplacePublishRequest) {
  return apiPost<MarketplaceListing>('/api/admin/marketplace/publish/workflow', data);
}

/** 发布函数到市场 */
export async function publishFunction(data: MarketplacePublishRequest) {
  return apiPost<MarketplaceListing>('/api/admin/marketplace/publish/function', data);
}

/** 安装市场工作流/函数到指定 app_group */
export async function installFromMarketplace(data: MarketplaceInstallRequest) {
  return apiPost<MarketplaceInstallResult>('/api/admin/marketplace/install', data);
}

/** 卸载市场安装 */
export async function uninstallFromMarketplace(listingId: string, appGroup: string) {
  return apiDelete<void>(`/api/admin/marketplace/install?listingId=${listingId}&appGroup=${appGroup}`);
}

/** 获取某 app_group 的安装记录 */
export async function getInstalls(appGroup: string) {
  return apiGet<MarketplaceInstallRecord[]>('/api/admin/marketplace/installs', { appGroup });
}
