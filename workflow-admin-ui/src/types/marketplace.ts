/**
 * 市场相关类型定义
 */

export interface MarketplaceListing {
  listingId: string;
  sourceType: 'WORKFLOW' | 'FUNCTION';
  sourceId: string;
  sourceVersion?: number;
  title: string;
  description?: string;
  tags?: string;
  author?: string;
  status: string;
  installCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface MarketplacePublishRequest {
  listingId: string;
  sourceId: string;
  title: string;
  description?: string;
  tags?: string[];
}

export interface MarketplaceInstallRequest {
  listingId: string;
  appGroup: string;
}

export interface MarketplaceInstallRecord {
  listingId: string;
  appGroup: string;
  installedBy: string;
  createdAt: string;
}

export interface MarketplaceInstallResult {
  type: 'WORKFLOW' | 'FUNCTION';
  id: string;
  name: string;
}
