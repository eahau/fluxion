import { useMemo, useCallback } from 'react';
import Ajv from 'ajv';
import addFormats from 'ajv-formats';
import type { ErrorObject } from 'ajv';

const ajv = new Ajv({ allErrors: true, strict: false });
addFormats(ajv);

export interface ValidationError {
  path: string;
  message: string;
}

export function useSchemaValidation(schema?: any) {
  const validate = useMemo(() => {
    if (!schema) return null;
    try {
      return ajv.compile(schema);
    } catch (e) {
      console.error('Schema compile error:', e);
      return null;
    }
  }, [schema]);

  const validateData = useCallback(
    (data: any): { valid: boolean; errors: ValidationError[] } => {
      if (!validate) return { valid: true, errors: [] };
      const valid = validate(data) as boolean;
      const errors =
        validate.errors?.map((err: ErrorObject) => ({
          path: err.instancePath || '/',
          message: err.message || '校验失败',
        })) || [];
      return { valid, errors };
    },
    [validate],
  );

  return { validate: validateData };
}
