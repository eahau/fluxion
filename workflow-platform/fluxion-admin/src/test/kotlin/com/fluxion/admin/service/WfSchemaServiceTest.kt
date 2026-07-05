package com.fluxion.admin.service

import com.fluxion.admin.entity.WfSchema
import com.fluxion.admin.repository.WfDefinitionRepository
import com.fluxion.admin.repository.WfSchemaRepository
import com.fluxion.admin.security.SecurityContextHelper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

/**
 * WfSchemaService 单元测试：
 *  - 冻结状态权限校验
 *  - Schema 引用前缀匹配（schema:）
 *  - Schema 引用合法性校验（引用目标存在性）
 */
class WfSchemaServiceTest {

    private lateinit var schemaRepository: WfSchemaRepository
    private lateinit var definitionRepository: WfDefinitionRepository
    private lateinit var securityContext: SecurityContextHelper
    private lateinit var service: WfSchemaService

    @BeforeEach
    fun setUp() {
        @Suppress("UNCHECKED_CAST")
        schemaRepository = mock(WfSchemaRepository::class.java)
        @Suppress("UNCHECKED_CAST")
        definitionRepository = mock(WfDefinitionRepository::class.java)
        @Suppress("UNCHECKED_CAST")
        securityContext = mock(SecurityContextHelper::class.java)
        service = WfSchemaService(
            repository = schemaRepository,
            definitionRepository = definitionRepository,
            securityContext = securityContext,
            schemaConfigPublisher = null
        )
    }

    // ───────────────────────────────────────────────
    // 引用提取测试（extractReferencedSchemaNames）
    // ───────────────────────────────────────────────

    @Test
    fun `extractReferencedSchemaNames 能正确提取 schema 前缀的引用`() {
        val json = """
            {
              "type": "object",
              "properties": {
                "user": { "${'$'}ref": "schema:UserInfo" },
                "addresses": {
                  "type": "array",
                  "items": { "${'$'}ref": "schema:AddressItem" }
                },
                "nested": {
                  "type": "object",
                  "properties": {
                    "child": { "${'$'}ref": "schema:ChildSchema" }
                  }
                },
                "plain": { "type": "string" }
              }
            }
        """.trimIndent()

        val refs = service.extractReferencedSchemaNames(json)
        assertEquals(setOf("UserInfo", "AddressItem", "ChildSchema"), refs)
    }

    @Test
    fun `extractReferencedSchemaNames 不提取非 schema 前缀的引用`() {
        // 旧的（错误的） json-schema: 前缀不应当被识别
        val json = """
            {
              "type": "object",
              "properties": {
                "a": { "${'$'}ref": "json-schema:OldStyle" },
                "b": { "${'$'}ref": "#/definitions/Local" },
                "c": { "${'$'}ref": "schema:RealOne" }
              }
            }
        """.trimIndent()

        val refs = service.extractReferencedSchemaNames(json)
        assertEquals(setOf("RealOne"), refs)
    }

    @Test
    fun `extractReferencedSchemaNames 支持紧凑格式和空格格式`() {
        val json = """
            {"props":{"x":{"${'$'}ref":"schema:First"},"y": { "${'$'}ref" : "schema:Second" }}}
        """.trimIndent()

        val refs = service.extractReferencedSchemaNames(json)
        assertEquals(setOf("First", "Second"), refs)
    }

    @Test
    fun `extractReferencedSchemaNames 无引用时返回空集合`() {
        val json = """{"type":"object","properties":{"a":{"type":"string"}}}"""
        assertTrue(service.extractReferencedSchemaNames(json).isEmpty())
    }

    // ───────────────────────────────────────────────
    // 引用合法性校验测试（validateSchemaReferences）
    // ───────────────────────────────────────────────

    private fun mockSchemasInDb(vararg names: String) {
        `when`(schemaRepository.findAll()).thenReturn(
            names.map { n ->
                WfSchema().apply { schemaName = n }
            }
        )
    }

    @Test
    fun `validateSchemaReferences 所有引用都存在时通过`() {
        mockSchemasInDb("User", "Address", "Order")
        val json = """
            {"properties":{
              "u":{"${'$'}ref":"schema:User"},
              "a":{"${'$'}ref":"schema:Address"}
            }}
        """.trimIndent()
        // 不应抛出异常
        service.validateSchemaReferences(json, selfName = null)
    }

    @Test
    fun `validateSchemaReferences 引用不存在时抛出异常`() {
        mockSchemasInDb("User")
        val json = """
            {"properties":{
              "u":{"${'$'}ref":"schema:User"},
              "o":{"${'$'}ref":"schema:Order"},
              "p":{"${'$'}ref":"schema:Payment"}
            }}
        """.trimIndent()

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.validateSchemaReferences(json, selfName = null)
        }
        assertTrue(ex.message!!.contains("Order"), "错误信息应包含不存在的 Order")
        assertTrue(ex.message!!.contains("Payment"), "错误信息应包含不存在的 Payment")
        assertFalse(ex.message!!.contains("User"), "错误信息不应包含已存在的 User")
    }

    @Test
    fun `validateSchemaReferences 自引用在指定 selfName 时允许`() {
        mockSchemasInDb("Other")
        val json = """
            {"properties":{
              "this":{"${'$'}ref":"schema:Tree"},
              "other":{"${'$'}ref":"schema:Other"}
            }}
        """.trimIndent()
        // selfName = Tree，所以 schema:Tree 作为自引用是允许的（即使 DB 中尚不存在也 OK）
        service.validateSchemaReferences(json, selfName = "Tree")
    }

    @Test
    fun `validateSchemaReferences 自引用在 save 时不允许`() {
        mockSchemasInDb() // 空库
        val json = """{"properties":{"self":{"${'$'}ref":"schema:FreshNew"}}}"""
        // save 时 selfName 传 null，因此 FreshNew 不被识别
        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.validateSchemaReferences(json, selfName = null)
        }
        assertTrue(ex.message!!.contains("FreshNew"), "save 时自引用不应被豁免")
    }

    @Test
    fun `validateSchemaReferences 没有引用时直接通过`() {
        mockSchemasInDb()
        service.validateSchemaReferences("""{"type":"object"}""", selfName = null)
        // 不应抛出
    }

    // ───────────────────────────────────────────────
    // 冻结权限与服务层入口集成测试
    // ───────────────────────────────────────────────

    private fun setCurrentUserAuthorities(vararg authorities: String) {
        val auth = UsernamePasswordAuthenticationToken(
            "tester",
            "pwd",
            authorities.map { SimpleGrantedAuthority(it) }
        )
        SecurityContextHolder.getContext().authentication = auth
    }

    private fun basicSchema(name: String, frozen: Boolean = false) = WfSchema().apply {
        schemaName = name
        schemaType = "INPUT"
        schemaFormat = "json-schema"
        schemaJson = """{"type":"object","properties":{"a":{"type":"string"}}}"""
        scope = "PLATFORM"
        this.frozen = frozen
    }

    @Test
    fun `update 冻结的 schema 无权限时抛出 AccessDeniedException`() {
        setCurrentUserAuthorities("schema:edit") // 有编辑权限但无解锁权限
        val existing = basicSchema("Locked", frozen = true)
        `when`(schemaRepository.findBySchemaName("Locked")).thenReturn(java.util.Optional.of(existing))
        `when`(schemaRepository.findAll()).thenReturn(emptyList())

        val incoming = basicSchema("Locked")
        val ex = assertThrows(AccessDeniedException::class.java) {
            service.update("Locked", incoming)
        }
        assertTrue(ex.message!!.contains("已冻结"))
    }

    @Test
    fun `update 冻结的 schema 有 schema unlock 权限时通过`() {
        setCurrentUserAuthorities("schema:unlock")
        val existing = basicSchema("Locked", frozen = true)
        val saved = basicSchema("Locked", frozen = false)
        `when`(schemaRepository.findBySchemaName("Locked")).thenReturn(java.util.Optional.of(existing))
        `when`(schemaRepository.save(any())).thenReturn(saved)
        `when`(schemaRepository.findAll()).thenReturn(emptyList())

        val incoming = basicSchema("Locked").apply { frozen = false }
        val result = service.update("Locked", incoming)
        assertNotNull(result)
        // 具备 schema:unlock 才能成功修改 frozen
        assertFalse(result.frozen)
    }

    @Test
    fun `delete 冻结的 schema 无权限时抛出 AccessDeniedException`() {
        setCurrentUserAuthorities("schema:edit")
        val existing = basicSchema("Locked", frozen = true)
        `when`(schemaRepository.findBySchemaName("Locked")).thenReturn(java.util.Optional.of(existing))
        `when`(definitionRepository.findAll()).thenReturn(emptyList())
        `when`(schemaRepository.findAll()).thenReturn(listOf(existing))

        val ex = assertThrows(AccessDeniedException::class.java) {
            service.delete("Locked")
        }
        assertTrue(ex.message!!.contains("已冻结"))
    }

    @Test
    fun `save 创建 schema 时校验引用的目标必须存在`() {
        setCurrentUserAuthorities("schema:edit")
        // 模拟空库
        mockSchemasInDb()
        `when`(schemaRepository.save(any())).thenAnswer { it.arguments[0] as WfSchema }

        val badRefSchema = WfSchema().apply {
            schemaName = "NewOne"
            schemaType = "INPUT"
            schemaFormat = "json-schema"
            // 引用不存在的 Schema
            schemaJson = """{"properties":{"u":{"${'$'}ref":"schema:NotExist"}}}"""
            scope = "PLATFORM"
        }

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.save(badRefSchema)
        }
        assertTrue(ex.message!!.contains("NotExist"), "创建 Schema 时非法引用应被拦截")
    }

    @Test
    fun `delete 被其他 Schema 以 schema 前缀引用时抛出引用异常`() {
        setCurrentUserAuthorities("schema:unlock", "schema:edit")

        val target = basicSchema("TargetSchema")
        // 引用者使用正确的 schema: 前缀（修正后的前缀）
        val referrer = basicSchema("Referrer").apply {
            schemaJson = """{"properties":{"t":{"${'$'}ref":"schema:TargetSchema"}}}"""
        }
        `when`(schemaRepository.findBySchemaName("TargetSchema")).thenReturn(java.util.Optional.of(target))
        `when`(schemaRepository.findAll()).thenReturn(listOf(target, referrer))
        `when`(definitionRepository.findAll()).thenReturn(emptyList())

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.delete("TargetSchema")
        }
        assertTrue(ex.message!!.contains("正在被引用"), "删除被引用的 Schema 应提示正在被引用")
        assertTrue(ex.message!!.contains("Schema[Referrer]"), "错误信息应包含引用者名称")
    }

    @Test
    fun `delete 旧 json-schema 前缀的引用不再识别为真正引用`() {
        setCurrentUserAuthorities("schema:unlock", "schema:edit")

        val target = basicSchema("TargetSchema")
        // 使用旧（错误）的 json-schema: 前缀，不应再触发引用保护
        val oldStyleReferrer = basicSchema("OldReferrer").apply {
            schemaJson = """{"properties":{"t":{"${'$'}ref":"json-schema:TargetSchema"}}}"""
        }
        `when`(schemaRepository.findBySchemaName("TargetSchema")).thenReturn(java.util.Optional.of(target))
        `when`(schemaRepository.findAll()).thenReturn(listOf(target, oldStyleReferrer))
        `when`(definitionRepository.findAll()).thenReturn(emptyList())
        // 没有正确的 schema: 前缀引用，delete 应该能正常通过（不抛出"正在被引用"）
        service.delete("TargetSchema")
    }
}
