package com.fluxion.admin.entity

import com.fluxion.admin.generated.model.FunctionStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 工作流函数注册实体。
 *
 * 源表：`wf_function`
 */
@Entity
@Table(
    name = "wf_function",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_scope_app_function", columnNames = ["scope", "app_group", "function_name"])
    ]
)
class WfFunction : BaseEntity() {

    /** 函数引用名（列：`function_name`） */
    @Column(name = "function_name", nullable = false, length = 128)
    var functionName: String = ""

    /** 作用域：PLATFORM/PRIVATE/MARKETPLACE（列：`scope`） */
    @Column(name = "scope", nullable = false, length = 16)
    var scope: String = "PRIVATE"

    /** 所属应用分组（scope=PRIVATE 时必填）（列：`app_group`） */
    @Column(name = "app_group", length = 128)
    var appGroup: String? = null

    /** 来源引用（安装市场函数时指向原始 functionName）（列：`source_ref`） */
    @Column(name = "source_ref", length = 128)
    var sourceRef: String? = null

    /** 函数类型：BUILTIN/SCRIPT_GROOVY/SCRIPT_JS/JAVA_CLASS/EXTERNAL/CUSTOM（列：`function_type`） */
    @Column(name = "function_type", nullable = false, length = 32)
    var functionType: String = "BUILTIN"

    /** 函数所属领域（列：`domain`） */
    @Column(name = "domain", length = 32)
    var domain: String? = null

    /** 统一配置 JSON（列：`config`） */
    @Column(name = "config", columnDefinition = "JSON")
    var config: String? = null

    /** 函数状态：ACTIVE/INACTIVE（列：`status`） */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: FunctionStatus = FunctionStatus.ACTIVE
}
