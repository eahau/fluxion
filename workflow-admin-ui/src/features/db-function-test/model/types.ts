// features/db-function-test/model/types.ts
// DB Function Test Feature 私有类型（外部不直接 import）

export interface DbDatasourceBrief {
  id?: string;
  name?: string;
  domain?: 'db';
  type?: string;
}
export interface DbTableColumn {
  name: string;
  dataType?: string;
  comment?: string;
}
