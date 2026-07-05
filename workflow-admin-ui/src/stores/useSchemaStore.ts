import { create } from 'zustand';
import type { SchemaDefinition } from '@/types/schema';

interface SchemaState {
  schemaList: SchemaDefinition[];
  schemaMap: Record<string, SchemaDefinition>;
  currentSchema: SchemaDefinition | null;
  loading: boolean;
  setSchemaList: (list: SchemaDefinition[]) => void;
  setCurrentSchema: (schema: SchemaDefinition | null) => void;
  upsertSchema: (schema: SchemaDefinition) => void;
  removeSchema: (id: string | number) => void;
  setLoading: (loading: boolean) => void;
}

export const useSchemaStore = create<SchemaState>((set, get) => ({
  schemaList: [],
  schemaMap: {},
  currentSchema: null,
  loading: false,

  setSchemaList: (list) => {
    const map: Record<string, SchemaDefinition> = {};
    list.forEach((s) => {
      const key = String(s.id || (s as any).schemaName);
      map[key] = s;
    });
    set({ schemaList: list, schemaMap: map });
  },

  setCurrentSchema: (schema) => set({ currentSchema: schema }),

  upsertSchema: (schema) => {
    const { schemaList, schemaMap } = get();
    const key = String(schema.id || (schema as any).schemaName);
    const index = schemaList.findIndex(
      (s) => String(s.id || (s as any).schemaName) === key,
    );
    const nextList =
      index >= 0
        ? schemaList.map((s, i) => (i === index ? schema : s))
        : [...schemaList, schema];
    set({
      schemaList: nextList,
      schemaMap: { ...schemaMap, [key]: schema },
    });
  },

  removeSchema: (id) => {
    const { schemaList, schemaMap } = get();
    const key = String(id);
    const nextList = schemaList.filter(
      (s) => String(s.id || (s as any).schemaName) !== key,
    );
    const nextMap = { ...schemaMap };
    delete nextMap[key];
    set({ schemaList: nextList, schemaMap: nextMap });
  },

  setLoading: (loading) => set({ loading }),
}));
