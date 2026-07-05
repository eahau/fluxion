export interface DiffItem {
  path: string;
  type: 'added' | 'removed' | 'modified';
  oldValue?: any;
  newValue?: any;
}

function isPlainObject(value: any): boolean {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function compareArrays(oldArr: any[], newArr: any[], path: string): DiffItem[] {
  const results: DiffItem[] = [];
  const maxLen = Math.max(oldArr.length, newArr.length);

  for (let i = 0; i < maxLen; i++) {
    const childPath = `${path}[${i}]`;
    if (i >= oldArr.length) {
      results.push({ path: childPath, type: 'added', newValue: newArr[i] });
    } else if (i >= newArr.length) {
      results.push({ path: childPath, type: 'removed', oldValue: oldArr[i] });
    } else {
      results.push(...diffJson(oldArr[i], newArr[i], childPath));
    }
  }

  return results;
}

export function diffJson(oldObj: any, newObj: any, path = ''): DiffItem[] {
  const results: DiffItem[] = [];

  if (oldObj === newObj) {
    return results;
  }

  if (typeof oldObj !== typeof newObj || Array.isArray(oldObj) !== Array.isArray(newObj)) {
    results.push({ path, type: 'modified', oldValue: oldObj, newValue: newObj });
    return results;
  }

  if (Array.isArray(oldObj) && Array.isArray(newObj)) {
    return compareArrays(oldObj, newObj, path);
  }

  if (!isPlainObject(oldObj) || !isPlainObject(newObj)) {
    results.push({ path, type: 'modified', oldValue: oldObj, newValue: newObj });
    return results;
  }

  const oldKeys = Object.keys(oldObj);
  const newKeys = Object.keys(newObj);
  const allKeys = new Set([...oldKeys, ...newKeys]);

  allKeys.forEach((key) => {
    const childPath = path ? `${path}.${key}` : key;
    if (!(key in oldObj)) {
      results.push({ path: childPath, type: 'added', newValue: newObj[key] });
    } else if (!(key in newObj)) {
      results.push({ path: childPath, type: 'removed', oldValue: oldObj[key] });
    } else {
      results.push(...diffJson(oldObj[key], newObj[key], childPath));
    }
  });

  return results;
}
