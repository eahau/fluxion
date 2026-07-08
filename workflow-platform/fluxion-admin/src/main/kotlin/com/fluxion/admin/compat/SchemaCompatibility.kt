package com.fluxion.admin.compat

import org.slf4j.LoggerFactory
import java.math.BigDecimal

/**
 * Schema 閸忕厧顔愰幀褍鍘规惔鏇炰紣閸?
 *
 * 閹碘偓閺堝鍙嗛崣?閸戝搫寮惃鍕悑鐎圭娴嗛幑銏㈢埠娑撯偓闁俺绻冨銈呬紣閸忓嘲顦╅悶鍡礉娣囨繆鐦夐敍?
 * 1. 缁鐎锋潪顒佸床婢惰精瑙︽禒鍛）韫囨鎲＄拃锔肩礉娑撳秳鑵戦弬顓熷复閸?
 * 2. 鎼寸喎绱旂€涙顔岄棃娆撶帛韫囩晫鏆?
 * 3. 閺嬫矮濡囬張顏嗙叀閸婄厧鍘规惔?
 */
object SchemaCompatibility {

    private val log = LoggerFactory.getLogger(SchemaCompatibility::class.java)

    // 閳光偓閳光偓閳光偓 閸忋儱寮崗鐓庮啇 閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

    /**
     * 鐎瑰鍙忛懢宄板絿閸欏倹鏆熼崐纭风窗韫囩晫鏆愭惔鐔风磾鐎涙顔岄敍灞惧ⅵ閸?WARN 閺冦儱绻旈妴?
     *
     * @param value          閸樼喎顫愰崣鍌涙殶閸婄》绱欓崣顖濆厴娑?null閿?
     * @param paramName      閸欏倹鏆熼崥宥忕礄閻劋绨弮銉ョ箶閿?
     * @param clientInfo     鐎广垺鍩涚粩顖涚垼鐠囧棔淇婇幁顖ょ礄閻劋绨弮銉ョ箶鏉╁€熼嚋閿?
     * @param deprecated     閺勵垰鎯佹稉鍝勫嚒鎼寸喎绱旂€涙顔?
     * @return 閸欏倹鏆熼崐纭风礉鎼寸喎绱旂€涙顔屾潻鏂挎礀 null
     */
    fun <T> safeGetParam(
        value: T?,
        paramName: String,
        clientInfo: String? = null,
        deprecated: Boolean = false
    ): T? {
        if (deprecated && value != null) {
            log.warn(
                "Deprecated parameter [{}] received from client [{}] - value will be ignored",
                paramName, clientInfo ?: "unknown"
            )
            return null
        }
        return value
    }

    /**
     * 閺堫亞鐓￠弸姘閸婄厧鍘规惔鏇窗闁洤鍩岄弸姘娑擃厺绗夌€涙ê婀惃鍕偓鍏兼閿涘奔绗夐幎娑樼磽鐢潻绱濇潻鏂挎礀姒涙顓婚崐绗衡偓?
     *
     * @param name          閺嬫矮濡囬崥宥囆為敍鍫㈡暏娴滃孩妫╄箛妤嬬礆
     * @param raw           閸樼喎顫愮€涙顑佹稉鎻掆偓?
     * @param values        閺嬫矮濡囬惃鍕閺堝鎮庡▔鏇炩偓鍏兼殶缂佸嫸绱欐俊?enumClass.entries.map { it.name }閿?
     * @param defaultValue  閸忔粌绨虫妯款吇閸?
     */
    fun <T> safeEnumValue(
        name: String,
        raw: String?,
        values: Array<out String>,
        defaultValue: T
    ): T {
        if (raw == null) return defaultValue
        val matched = values.firstOrNull { it.equals(raw, ignoreCase = true) }
        if (matched == null) {
            log.warn("Unknown enum value [{}] for [{}], fallback to default", raw, name)
        }
        @Suppress("UNCHECKED_CAST")
        return (matched as? T) ?: defaultValue
    }

    /**
     * 鐎硅姤婢楅弸姘鐟欙絾鐎介敍姘帒鐠佸憡妫€广垺鍩涚粩顖欑炊閸忋儳娈戦弮褎鐏囨稉鎯р偓纭风礉閺冄冣偓闂寸瑢閺傜増鐏囨稉鐐Ё鐏忓嫨鈧?
     *
     * @param raw             閸樼喎顫愰弸姘鐎涙顑佹稉?
     * @param values          瑜版挸澧犻弸姘閹碘偓閺堝鎮庡▔鏇炩偓?
     * @param legacyMapper    閺冄冣偓?閳?閺傛澘鈧偐娈戦弰鐘茬殸鐞?
     * @param defaultValue    閸忔粌绨抽崐?
     */
    fun <T> safeEnumWithLegacy(
        raw: String?,
        values: Array<out String>,
        legacyMapper: Map<String, String>,
        defaultValue: T
    ): T {
        if (raw == null) return defaultValue
        val effective = legacyMapper[raw] ?: raw
        val matched = values.firstOrNull { it.equals(effective, ignoreCase = true) }
        if (matched == null) {
            log.warn("Unknown enum value [{}] (mapped from [{}]), fallback to default", effective, raw)
        }
        @Suppress("UNCHECKED_CAST")
        return (matched as? T) ?: defaultValue
    }

    // 閳光偓閳光偓閳光偓 缁鐎锋潪顒佸床閸忔粌绨?閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

    /**
     * 鐎瑰鍙忕亸鍡曟崲閹板繐鈧壈娴嗘稉?Number閿涘湢ong閿涘鈧?
     * 閺€顖涘瘮閿涙碍鏆熺€涙ぜ鈧焦鏆熺€涙鐡х粭锔胯閵?true"/"false" 閳?1/0
     */
    fun safeToNumber(value: Any?, fieldName: String = "unknown"): Long? {
        if (value == null) return null
        return when (value) {
            is Number -> value.toLong()
            is Boolean -> if (value) 1L else 0L
            is String -> {
                val trimmed = value.trim()
                try {
                    when {
                        trimmed.equals("true", ignoreCase = true) -> 1L
                        trimmed.equals("false", ignoreCase = true) -> 0L
                        trimmed.contains('.') -> BigDecimal(trimmed).toLong()
                        else -> trimmed.toLong()
                    }
                } catch (e: NumberFormatException) {
                    log.warn("safeToNumber: cannot convert [{}] for field [{}]", trimmed, fieldName)
                    null
                }
            }
            else -> {
                log.warn("safeToNumber: unexpected type [{}] for field [{}]", value::class.java.simpleName, fieldName)
                null
            }
        }
    }

    /**
     * 鐎瑰鍙忕亸鍡曟崲閹板繐鈧壈娴嗘稉?String閵?
     */
    fun safeToString(value: Any?, fieldName: String = "unknown"): String? {
        if (value == null) return null
        return when (value) {
            is String -> value
            is Number -> value.toString()
            is Boolean -> value.toString()
            else -> {
                val str = value.toString()
                log.warn("safeToString: unexpected type [{}] for field [{}], toString result: {}",
                    value::class.java.simpleName, fieldName, str.take(100))
                str
            }
        }
    }

    /**
     * 鐎瑰鍙忕亸鍡曟崲閹板繐鈧壈娴嗘稉?Boolean閵?
     * 閺€顖涘瘮閿涙rue/false閵?true"/"false"閵?/0閵?1"/"0"
     */
    fun safeToBoolean(value: Any?, fieldName: String = "unknown"): Boolean? {
        if (value == null) return null
        return when (value) {
            is Boolean -> value
            is Number -> value.toLong() != 0L
            is String -> when (value.trim().lowercase()) {
                "true", "1", "yes", "on" -> true
                "false", "0", "no", "off" -> false
                else -> {
                    log.warn("safeToBoolean: cannot parse [{}] for field [{}]", value, fieldName)
                    null
                }
            }
            else -> {
                log.warn("safeToBoolean: unexpected type [{}] for field [{}]", value::class.java.simpleName, fieldName)
                null
            }
        }
    }

    /**
     * 鐎瑰鍙忛懢宄板絿瀹撳苯顨滈崐纭风礄閸忕厧顔?null 娑擃參妫块懞鍌滃仯閿涘鈧?
     * Kotlin 鐠嬪啰鏁ら弬鐟扮安娴兼ê鍘涙担璺ㄦ暏 `?.` 閸欘垶鈧鎽奸敍灞绢劃閺傝纭舵笟?Java 閹存牕鍙炬禒鏍ф簚閺咁垯濞囬悽銊ｂ偓?
     */
    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T> safeGetNested(root: Map<String, Any?>, vararg path: String): T? {
        var current: Any? = root
        for (key in path) {
            current = when (current) {
                is Map<*, *> -> (current as Map<String, Any?>)[key]
                else -> return null
            }
            if (current == null) return null
        }
        @Suppress("UNCHECKED_CAST")
        return current as? T
    }
}