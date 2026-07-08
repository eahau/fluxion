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
 * Unit tests for [WfSchemaService].
 *
 * Covers:
 *  - Frozen state permission checks (edit vs unlock authority)
 *  - Schema reference prefix extraction (`schema:` only)
 *  - Schema reference validity checks (target existence in DB)
 *  - Self-reference tolerance when `selfName` is provided
 *  - Cascade delete protection when referenced by other schemas
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

    // ─────────────────────────────────────────────────────────────────────
    // Reference extraction tests (extractReferencedSchemaNames)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `extractReferencedSchemaNames correctly extracts schema prefixed refs`() {
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
    fun `extractReferencedSchemaNames ignores non-schema prefixed refs`() {
        // Old (incorrect) json-schema: prefix must NOT be recognized
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
    fun `extractReferencedSchemaNames supports compact and whitespace formats`() {
        val json = """
            {"props":{"x":{"${'$'}ref":"schema:First"},"y": { "${'$'}ref" : "schema:Second" }}}
        """.trimIndent()

        val refs = service.extractReferencedSchemaNames(json)
        assertEquals(setOf("First", "Second"), refs)
    }

    @Test
    fun `extractReferencedSchemaNames returns empty set when no refs`() {
        val json = """{"type":"object","properties":{"a":{"type":"string"}}}"""
        assertTrue(service.extractReferencedSchemaNames(json).isEmpty())
    }

    // ─────────────────────────────────────────────────────────────────────
    // Reference validity tests (validateSchemaReferences)
    // ─────────────────────────────────────────────────────────────────────

    private fun mockSchemasInDb(vararg names: String) {
        `when`(schemaRepository.findAll()).thenReturn(
            names.map { n ->
                WfSchema().apply { schemaName = n }
            }
        )
    }

    @Test
    fun `validateSchemaReferences passes when all targets exist`() {
        mockSchemasInDb("User", "Address", "Order")
        val json = """
            {"properties":{
              "u":{"${'$'}ref":"schema:User"},
              "a":{"${'$'}ref":"schema:Address"}
            }}
        """.trimIndent()
        // Must not throw
        service.validateSchemaReferences(json, selfName = null)
    }

    @Test
    fun `validateSchemaReferences throws when refs do not exist`() {
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
        assertTrue(ex.message!!.contains("Order"), "error message must contain missing ref Order")
        assertTrue(ex.message!!.contains("Payment"), "error message must contain missing ref Payment")
        assertFalse(ex.message!!.contains("User"), "error message must NOT contain existing User")
    }

    @Test
    fun `validateSchemaReferences allows self-ref when selfName is set`() {
        mockSchemasInDb("Other")
        val json = """
            {"properties":{
              "this":{"${'$'}ref":"schema:Tree"},
              "other":{"${'$'}ref":"schema:Other"}
            }}
        """.trimIndent()
        // selfName = Tree, so schema:Tree is tolerated as self-reference even if DB does not have it yet
        service.validateSchemaReferences(json, selfName = "Tree")
    }

    @Test
    fun `validateSchemaReferences rejects fresh self-ref on save`() {
        mockSchemasInDb() // empty DB
        val json = """{"properties":{"self":{"${'$'}ref":"schema:FreshNew"}}}"""
        // On save selfName is null, so FreshNew must NOT be tolerated as self-ref
        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.validateSchemaReferences(json, selfName = null)
        }
        assertTrue(ex.message!!.contains("FreshNew"), "self-ref during save must not be excused")
    }

    @Test
    fun `validateSchemaReferences passes through when there are no refs`() {
        mockSchemasInDb()
        service.validateSchemaReferences("""{"type":"object"}""", selfName = null)
        // Must not throw
    }

    // ─────────────────────────────────────────────────────────────────────
    // Frozen permission + service layer integration tests
    // ─────────────────────────────────────────────────────────────────────

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
    fun `update frozen schema without unlock authority throws AccessDeniedException`() {
        setCurrentUserAuthorities("schema:edit") // edit only, no unlock
        val existing = basicSchema("Locked", frozen = true)
        `when`(schemaRepository.findBySchemaName("Locked")).thenReturn(java.util.Optional.of(existing))
        `when`(schemaRepository.findAll()).thenReturn(emptyList())

        val incoming = basicSchema("Locked")
        val ex = assertThrows(AccessDeniedException::class.java) {
            service.update("Locked", incoming)
        }
        assertTrue(ex.message!!.contains("frozen"))
    }

    @Test
    fun `update frozen schema with schema unlock authority succeeds`() {
        setCurrentUserAuthorities("schema:unlock")
        val existing = basicSchema("Locked", frozen = true)
        val saved = basicSchema("Locked", frozen = false)
        `when`(schemaRepository.findBySchemaName("Locked")).thenReturn(java.util.Optional.of(existing))
        `when`(schemaRepository.save(any())).thenReturn(saved)
        `when`(schemaRepository.findAll()).thenReturn(emptyList())

        val incoming = basicSchema("Locked").apply { frozen = false }
        val result = service.update("Locked", incoming)
        assertNotNull(result)
        // schema:unlock is required to actually flip frozen
        assertFalse(result.frozen)
    }

    @Test
    fun `delete frozen schema without unlock authority throws AccessDeniedException`() {
        setCurrentUserAuthorities("schema:edit")
        val existing = basicSchema("Locked", frozen = true)
        `when`(schemaRepository.findBySchemaName("Locked")).thenReturn(java.util.Optional.of(existing))
        `when`(definitionRepository.findAll()).thenReturn(emptyList())
        `when`(schemaRepository.findAll()).thenReturn(listOf(existing))

        val ex = assertThrows(AccessDeniedException::class.java) {
            service.delete("Locked")
        }
        assertTrue(ex.message!!.contains("frozen"))
    }

    @Test
    fun `save validates that referenced schemas exist in DB`() {
        setCurrentUserAuthorities("schema:edit")
        // simulate empty DB
        mockSchemasInDb()
        `when`(schemaRepository.save(any())).thenAnswer { it.arguments[0] as WfSchema }

        val badRefSchema = WfSchema().apply {
            schemaName = "NewOne"
            schemaType = "INPUT"
            schemaFormat = "json-schema"
            // reference a schema that does not exist
            schemaJson = """{"properties":{"u":{"${'$'}ref":"schema:NotExist"}}}"""
            scope = "PLATFORM"
        }

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.save(badRefSchema)
        }
        assertTrue(ex.message!!.contains("NotExist"), "creating schema with illegal ref must be rejected")
    }

    @Test
    fun `delete throws when schema is referenced by other schemas via schema prefix`() {
        setCurrentUserAuthorities("schema:unlock", "schema:edit")

        val target = basicSchema("TargetSchema")
        // referrer uses the CORRECT schema: prefix (after the prefix fix)
        val referrer = basicSchema("Referrer").apply {
            schemaJson = """{"properties":{"t":{"${'$'}ref":"schema:TargetSchema"}}}"""
        }
        `when`(schemaRepository.findBySchemaName("TargetSchema")).thenReturn(java.util.Optional.of(target))
        `when`(schemaRepository.findAll()).thenReturn(listOf(target, referrer))
        `when`(definitionRepository.findAll()).thenReturn(emptyList())

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.delete("TargetSchema")
        }
        assertTrue(ex.message!!.contains("referenced"), "deleting referenced schema must report active refs")
        assertTrue(ex.message!!.contains("Schema[Referrer]"), "error message must include referrer name")
    }

    @Test
    fun `delete ignores legacy json-schema prefixed refs and succeeds`() {
        setCurrentUserAuthorities("schema:unlock", "schema:edit")

        val target = basicSchema("TargetSchema")
        // uses the OLD (incorrect) json-schema: prefix, should NOT trigger ref protection anymore
        val oldStyleReferrer = basicSchema("OldReferrer").apply {
            schemaJson = """{"properties":{"t":{"${'$'}ref":"json-schema:TargetSchema"}}}"""
        }
        `when`(schemaRepository.findBySchemaName("TargetSchema")).thenReturn(java.util.Optional.of(target))
        `when`(schemaRepository.findAll()).thenReturn(listOf(target, oldStyleReferrer))
        `when`(definitionRepository.findAll()).thenReturn(emptyList())
        // No valid schema: prefix references -> delete must succeed without throwing "referenced"
        service.delete("TargetSchema")
    }
}
