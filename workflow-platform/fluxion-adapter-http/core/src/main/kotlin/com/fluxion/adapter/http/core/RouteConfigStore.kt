package com.fluxion.adapter.http.core

/**
 * HTTP 路由配置存储 SPI
 *
 * 与具体 HTTP 框架无关，由配置中心实现：
 *   - NacosRouteConfigStore  (workflow-adapter-http-springmvc-nacos)
 *   - ApolloRouteConfigStore (workflow-adapter-http-springmvc-apollo)
 *   - 开发者可自行实现（Zookeeper / Redis pub-sub / 数据库轮询等）
 *
 * 装配链路：
 *   配置中心 → RouteConfigStore 实现 → HttpRouteRegistry（框架实现）
 *     → 具体框架路由注册/注销（如 Spring MVC 的 RequestMappingHandlerMapping）
 *
 * admin 后台发布流程：
 *   1. admin 保存 wf_definition（含 bindKey / protocol=HTTP）
 *   2. admin 将路由配置写入 Nacos/Apollo（JSON 格式）
 *   3. 本 SPI 监听到变更，通知 HttpRouteRegistry
 *   4. 由具体 HTTP 框架实现动态注册/注销路由
 */
interface RouteConfigStore {

    /**
     * 启动时加载所有已激活路由（用于初始化注册）
     */
    fun loadAll(): List<HttpRouteDefinition>

    /**
     * 注册变更监听（配置中心推送时回调）
     * 每次回调传入当前配置的完整快照，由 RouteRegistry 实现负责 diff
     *
     * @param listener 路由变更监听器
     */
    fun watch(listener: RouteChangeListener)
}

/**
 * 路由变更监听器
 * RouteRegistry 实现类实现此接口，由 RouteConfigStore 回调
 */
fun interface RouteChangeListener {
    /**
     * 配置变更时触发，传入最新的完整路由快照
     * RouteRegistry 实现负责 diff（新增/删除/修改）
     */
    fun onRoutesChanged(snapshot: List<HttpRouteDefinition>)
}
