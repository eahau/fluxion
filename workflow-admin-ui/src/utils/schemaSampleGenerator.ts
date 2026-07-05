/**
 * 根据 JSON Schema 自动生成示例数据。
 * 共享 conditionSchema 的生成逻辑，保证各测试入口的示例数据一致。
 */
export { generateSampleFromSchema } from '@/utils/conditionSchema';
