import { NODE_PARAM_SCHEMAS } from './nodeParamSchemas';

describe('condition editor schemas', () => {
  it('does not require rule field/operator for filter and branch condition editors', () => {
    const filterRuleItemSchema = NODE_PARAM_SCHEMAS.FILTER?.properties?.rules?.items;
    const branchRuleItemSchema = NODE_PARAM_SCHEMAS.CONDITION_BRANCH?.properties?.conditions?.items?.properties?.rules?.items;

    expect(filterRuleItemSchema?.required).toBeUndefined();
    expect(branchRuleItemSchema?.required).toBeUndefined();
  });
});
