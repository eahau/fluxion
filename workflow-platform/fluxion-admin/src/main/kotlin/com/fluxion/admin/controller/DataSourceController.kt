package com.fluxion.admin.controller

import com.fluxion.admin.generated.api.DatasourcesApi
import com.fluxion.admin.generated.model.DatasourceColumnInfo
import com.fluxion.admin.generated.model.DatasourceInfo
import com.fluxion.admin.service.TableMetadataService
import org.slf4j.LoggerFactory
import org.slf4j.warn
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

/**
 * DB 閺佺増宓佸┃鎰帗閺佺増宓侀弻銉嚄 REST API閵?
 *
 * 鐎圭偟骞?OpenAPI 閻㈢喐鍨氶惃?[DatasourcesApi] 閹恒儱褰涢敍宀€鈥樻穱婵嗗閸氬海顏總鎴犲娑撯偓閼锋番鈧?
 * 娓氭稑澧犵粩?`db-function-test` Feature閿涘牆鍤遍弫鐗堢ゴ鐠囨洟銆夐棃顫礆娴ｈ法鏁ら敍?
 * 閻劋绨崝銊︹偓渚€鈧瀚ㄩ弫鐗堝祦濠ф劕鑻熷ù蹇氼潔鐞涖劎绮ㄩ弸?閸掓ぞ淇婇幁顖樷偓?
 *
 * 婵傛垹瀹崇€规矮绠熼崷?`doc/openapi.yaml`閿涘湒atasources tag閿涘绱濋悽?OpenAPI Generator 閻㈢喐鍨?
 * [DatasourcesApi] / [DatasourceInfo] / [DatasourceColumnInfo]閵?
 */
@RestController
class DataSourceController(
    private val tableMetadataService: TableMetadataService
) : DatasourcesApi {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 閼惧嘲褰囬幍鈧張澶婂讲閻劎娈戦弫鐗堝祦濠ф劕鍨悰銊ｂ偓?
     */
    override fun listDatasources(): ResponseEntity<List<DatasourceInfo>> {
        val names = tableMetadataService.listDataSources()
        val result = names.map { dsName ->
            DatasourceInfo().apply {
                id = dsName
                name = dsName
                domain = "db"
                type = "admin"
            }
        }
        return ResponseEntity.ok(result)
    }

    /**
     * 閼惧嘲褰囬幐鍥х暰閺佺増宓佸┃鎰畱閹碘偓閺堝鏁ら幋鐤€冮崥宥冣偓?
     */
    override fun listDatasourceTables(datasourceId: String): ResponseEntity<List<String>> {
        return try {
            val tables = tableMetadataService.listTables(datasourceId)
            ResponseEntity.ok(tables)
        } catch (e: Exception) {
            log.warn("Failed to list tables for datasource [{}]: {}", datasourceId, e.message)
            ResponseEntity.ok(emptyList())
        }
    }

    /**
     * 閼惧嘲褰囬幐鍥х暰閺佺増宓佸┃鎰瘹鐎规俺銆冮惃鍕閺堝鍨崗鍐╂殶閹诡喓鈧?
     */
    override fun listDatasourceTableColumns(
        datasourceId: String,
        tableName: String
    ): ResponseEntity<List<DatasourceColumnInfo>> {
        return try {
            val columns = tableMetadataService.getColumns(tableName, datasourceId)
            val result = columns.map { col ->
                DatasourceColumnInfo().apply {
                    name = col.name
                    columnName = col.name
                    dataType = col.dataType
                    type = col.dataType
                    comment = col.comment
                    description = col.comment
                }
            }
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            log.warn(e) { "Failed to list columns for [$datasourceId.$tableName]: ${e.message}" }
            ResponseEntity.ok(emptyList())
        }
    }
}
