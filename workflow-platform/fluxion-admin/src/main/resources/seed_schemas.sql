-- 批量插入 10 个实用 Schema 到 wf_schema 表
-- 执行方式: docker exec -i mysql mysql -u root -ppassword --default-character-set=utf8mb4 workflow_platform < seed_schemas.sql

DELETE FROM `wf_schema` WHERE `schema_name` IN (
  'common.PageRequest','common.PageResponse','common.ErrorResponse',
  'user.UserInfo','user.LoginRequest',
  'order.OrderInfo','order.CreateRequest',
  'payment.PaymentResult',
  'notification.NotifyPayload',
  'cache.CacheEntry'
);

INSERT INTO `wf_schema` (`schema_name`, `schema_type`, `schema_json`, `description`) VALUES

-- 1. 通用分页请求
('common.PageRequest', 'INPUT', '{"type":"object","required":["page","pageSize"],"properties":{"page":{"type":"integer","minimum":0,"default":0,"description":"页码（从0开始）"},"pageSize":{"type":"integer","minimum":1,"maximum":200,"default":20,"description":"每页条数"},"keyword":{"type":"string","description":"搜索关键字"},"sortBy":{"type":"string","description":"排序字段"},"sortDesc":{"type":"boolean","default":false,"description":"是否降序"}}}', '通用分页查询请求参数'),

-- 2. 通用分页响应
('common.PageResponse', 'OUTPUT', '{"type":"object","required":["total","page","pageSize","list"],"properties":{"total":{"type":"integer","description":"总记录数"},"page":{"type":"integer","description":"当前页码"},"pageSize":{"type":"integer","description":"每页条数"},"list":{"type":"array","items":{"type":"object"},"description":"数据列表"}}}', '通用分页查询响应包装'),

-- 3. 标准错误响应
('common.ErrorResponse', 'OUTPUT', '{"type":"object","required":["code","message"],"properties":{"code":{"type":"integer","description":"错误码，0=成功"},"message":{"type":"string","description":"错误描述"},"detail":{"type":"string","description":"详细错误信息（仅开发环境）"},"traceId":{"type":"string","description":"链路追踪 ID"}}}', '标准错误响应结构'),

-- 4. 用户信息
('user.UserInfo', 'INPUT,OUTPUT', '{"type":"object","required":["userId","username"],"properties":{"userId":{"type":"integer","description":"用户 ID"},"username":{"type":"string","minLength":1,"maxLength":64,"description":"用户名"},"nickname":{"type":"string","maxLength":128,"description":"昵称"},"email":{"type":"string","format":"email","description":"邮箱"},"phone":{"type":"string","pattern":"^1[3-9]\\\\d{9}$","description":"手机号"},"status":{"type":"string","enum":["ACTIVE","DISABLED","LOCKED"],"description":"账号状态"},"roles":{"type":"array","items":{"type":"string"},"description":"角色列表"},"createdAt":{"type":"string","format":"date-time","description":"注册时间"}}}', '用户基本信息，入参出参复用'),

-- 5. 登录请求
('user.LoginRequest', 'INPUT', '{"type":"object","required":["username","password"],"properties":{"username":{"type":"string","minLength":1,"maxLength":64,"description":"用户名"},"password":{"type":"string","minLength":6,"maxLength":128,"description":"密码"},"captcha":{"type":"string","description":"验证码"},"remember":{"type":"boolean","default":false,"description":"记住登录"}}}', '用户登录请求参数'),

-- 6. 订单信息
('order.OrderInfo', 'INPUT,OUTPUT', '{"type":"object","required":["orderId","userId","totalAmount","status"],"properties":{"orderId":{"type":"string","description":"订单号"},"userId":{"type":"integer","description":"用户 ID"},"totalAmount":{"type":"number","minimum":0,"description":"订单总金额（元）"},"status":{"type":"string","enum":["PENDING","PAID","SHIPPED","COMPLETED","CANCELLED"],"description":"订单状态"},"items":{"type":"array","description":"订单商品列表","items":{"type":"object","required":["productId","quantity","price"],"properties":{"productId":{"type":"string","description":"商品 ID"},"name":{"type":"string","description":"商品名称"},"quantity":{"type":"integer","minimum":1,"description":"数量"},"price":{"type":"number","minimum":0,"description":"单价（元）"}}}},"createdAt":{"type":"string","format":"date-time","description":"下单时间"}}}', '订单完整信息，含商品明细'),

-- 7. 创建订单请求
('order.CreateRequest', 'INPUT', '{"type":"object","required":["items"],"properties":{"items":{"type":"array","minItems":1,"description":"商品列表","items":{"type":"object","required":["productId","quantity"],"properties":{"productId":{"type":"string","description":"商品 ID"},"quantity":{"type":"integer","minimum":1,"description":"数量"}}}},"addressId":{"type":"string","description":"收货地址 ID"},"remark":{"type":"string","maxLength":500,"description":"订单备注"}}}', '创建订单请求参数'),

-- 8. 支付结果
('payment.PaymentResult', 'OUTPUT', '{"type":"object","required":["orderId","paymentId","amount","success"],"properties":{"orderId":{"type":"string","description":"订单号"},"paymentId":{"type":"string","description":"支付流水号"},"amount":{"type":"number","description":"支付金额（元）"},"success":{"type":"boolean","description":"是否支付成功"},"payChannel":{"type":"string","enum":["ALIPAY","WECHAT","UNIONPAY"],"description":"支付渠道"},"paidAt":{"type":"string","format":"date-time","description":"支付时间"},"message":{"type":"string","description":"支付结果描述"}}}', '第三方支付回调结果'),

-- 9. 通知消息体
('notification.NotifyPayload', 'INPUT,OUTPUT', '{"type":"object","required":["type","recipient","title","content"],"properties":{"type":{"type":"string","enum":["EMAIL","SMS","PUSH","WEBHOOK"],"description":"通知渠道"},"recipient":{"type":"string","description":"接收者（邮箱/手机号/URL）"},"title":{"type":"string","maxLength":200,"description":"通知标题"},"content":{"type":"string","description":"通知内容"},"priority":{"type":"string","enum":["LOW","NORMAL","HIGH","URGENT"],"default":"NORMAL","description":"优先级"},"extras":{"type":"object","description":"附加参数（KV）"}}}', '通用通知消息体，支持多渠道'),

-- 10. 缓存操作条目
('cache.CacheEntry', 'INPUT,OUTPUT', '{"type":"object","required":["key"],"properties":{"key":{"type":"string","minLength":1,"maxLength":256,"description":"缓存键"},"value":{"description":"缓存值（任意类型）"},"ttl":{"type":"integer","minimum":-1,"description":"过期时间（秒），-1 表示永不过期"},"namespace":{"type":"string","maxLength":64,"description":"缓存命名空间"},"hitCount":{"type":"integer","description":"命中次数（仅输出）"},"createdAt":{"type":"string","format":"date-time","description":"创建时间"}}}', '缓存读写操作条目，支持多命名空间');
