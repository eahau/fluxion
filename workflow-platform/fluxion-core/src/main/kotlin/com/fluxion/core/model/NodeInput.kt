package com.fluxion.core.model

import com.fluxion.core.exception.InvalidParamException
import com.fluxion.schema.api.SchemaBackedMap
import com.fluxion.schema.api.SchemaDataProviderRegistry
import com.fluxion.core.value.ExecutionMeta
import com.fluxion.schema.model.SchemaFormat

/**
 * 闁煎搫鍊婚崑锝夋儍閸曨偆鏆氶柡浣侯棎缁额參宕楅妷銈囩憪濞戞挸顑嗛弸?闁?缂侇偆绮惁?闁告垼濮ら弳鐔兼儍閸曨喚娈堕柣顫妼瀵剟寮?+ 闁告瑯鍨甸～鍡涙儍閸曨偒妯嗛梺顔哄妿楠炲棙鏅?
 */
data class NodeInput(
    /** 闁烩晛鐡ㄧ敮鎾礂閵夈儱妫橀柨娑欑煯缁楀倹绋夐埀顒勬嚍閸屾粌浠柣銊ュ缁绘垿宕堕悙娴嬪亾绾绀勭紒鐐閳ь儸鍕偊闁挎稑顦伴崹?DAG 闁艰鲸鑹鹃幃搴ｇ磼閹惧浜?*/
    val directInput: Any?,
    /** 闁哄嫭鍎崇槐鈩冪珶閻楀牊顫栭柣銊ュ缁堕鎸ч弽顒€螡闁绘劗顢婄欢顓㈠礄閻氬绀勯悗鐢垫嚀缁ㄦ煡鎳為崒婊冧化闂佹澘绉堕悿?dependsOn 閻庢稒顨嗛宀勬晬?*/
    val declaredDeps: Map<String, Any> = emptyMap(),
    /** 鐎规悶鍎扮紞鏂棵规担绋挎枾濠殿喖顑呴崣鍡涘矗閸岋妇绀勫☉鎾崇Т瑜版煡宕ｅ鍫㈢ */
    val workflowInput: Map<String, Any> = emptyMap(),
    /** 闁煎搫鍊婚崑锝夋煀瀹ュ洨鏋傞柛娆忓€归弳鐔兼晬閸喐闄嶉柤?WorkflowNode.params闁挎稑鐭傚顏呮交閹邦垼鏀介柡鍐煐閺嗙喖骞戦鍡欑 */
    val nodeParams: Map<String, Any> = emptyMap(),
    /** 闁圭瑳鍡╂斀闁稿繐鍟╂穱濠囧箒椤栥倗绀別xecutionId/traceId 缂佹稑顧€缁辨繈宕ｉ鍥跺殺闁?*/
    val meta: ExecutionMeta? = null,
    /**
     * 闁轰胶澧楀畵渚€寮介悡搴ｇ闁哄秴娲╅惁鎴︽晬?json-schema" / "protobuf" / "avro"闁挎稑顦埀?     *
     * 濞?null 闁哄啳娉涘ú鏍焻閳ь剟宕氭０浣虹倞缂?Map 閻犱礁娼″Λ璺何熼垾宕囩闁挎稑鐗嗛幃婊堝触鎼粹€虫倯閻庡湱娅㈢槐姘跺Υ?     * 濞?[providerRegistry] 闂佹澘绉撮幃搴㈡媴鐠恒劍鏆忛柨娑樼焸閳瑰秹宕?Schema 闁规壆鍠撻悡锟犳儍閸曨偆鎽熸繛鍫㈡暩妤犲洭寮悧鍫濈ウ閻犱礁娼″Λ鍫曞Υ?     */
    val schemaFormat: String? = null,
    /** Schema 闁告艾绉惰ⅷ闁挎稑鐗嗚ぐ鏌ユ焻婢舵稓绀夐柣顫妺缁剚绂?SchemaRegistry 闁哄被鍎叉竟妯尖偓鐟版湰閺?Schema 閻庤鐭粻鐔兼晬?*/
    val schemaName: String? = null,
    /** Schema 闁绘鐗婂﹢浼村矗閸戙倗绀勯柛娆樺灦閳ь剙顧€缁辨棘ull 閻炴稏鍔庨妵姘跺嫉閳ь剟寮幍顔碱暭闁哄牜鍓ㄧ槐?*/
    val schemaVersion: Long? = null,
    /**
     * Schema 闁轰胶澧楀畵浣烘媼閸ф锛栭柟缁樺姃缁剁敻鎳撻崨顔芥殘闁告劕鐭侀妴鍐Υ?     *
     * 濞?[schemaFormat] 闂佹澘绉撮幃搴ㄦ晬鐏炶姤韬弶鈺傚姌椤㈡垿寮捄鍝勑楅柟顑跨窔閳ь剙顦扮€氥劎鈧數鎳撶花鏌ュ冀閻撳海纭€闁汇劌瀚弳鐔煎箲椤旀鍟忛梻鍌ゅ枤閻°儵鎮鹃妷顖滅獥
     * - JSON 闁?[com.fluxion.schema.json.JsonSchemaDataProvider]
     * - Protobuf 闁?ProtobufSchemaDataProvider闁挎稑娼歭uxion-schema:protobuf 婵☆垪鈧櫕鍋ラ柨?     * - Avro 闁?AvroSchemaDataProvider闁挎稑娼歭uxion-schema:avro 婵☆垪鈧櫕鍋ラ柨?     */
    val providerRegistry: SchemaDataProviderRegistry? = null
) {
    companion object {
        /** `${variable}` 婵☆垪鍓濆姗€宕￠悩杈╃Т缂佹闄勯婊堝礆?*/
        private val DOLLAR_BRACE_PATTERN = Regex("""\$\{([a-zA-Z_][a-zA-Z0-9_.]*)}""")
    }

    // 闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩?    // 闁烩晛鐡ㄧ敮鎾礂閵夈儱妫橀柨娑樻綈irectInput闁挎稑顦悾銊╁礂閵婎煈鍟忛梻?    // 闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩?
    /** 闁烩晛鐡ㄧ敮鎾礂閵夈儱妫橀悗鐟邦槸閸欏繘宕ｉ弽褉鍋撶涵椋庣nullable闁?*/
    inline fun <reified T> input(): T? = directInput as? T

    /** 闁烩晛鐡ㄧ敮鎾礂閵夈儱妫橀煫鍥ф噸缁卞爼鏁嶅畝鈧杈ㄥ緞鏉堛劌顫旂€殿喖鍊搁悥?*/
    inline fun <reified T> requireInput(): T =
        input<T>() ?: throw InvalidParamException("directInput is required")

    /** 闁烩晛鐡ㄧ敮鎾礂閵夈儱妫橀悽顖ょ畵缁垳鎷嬮妶鍛亾?*/
    inline fun <reified T> input(default: T): T = input<T>() ?: default

    /** 闁烩晛鐡ㄧ敮鎾礂閵夈儱妫橀柤濂変簻閻ｇ偓绋婃径搴㈢ギ闁?*/
    inline fun <reified T> input(mapper: (Any) -> T): T? = directInput?.let(mapper)

    /** 闁烩晛鐡ㄧ敮鎾礂閵夈儱妫橀柤濂変簻閻ｇ偓绋婃径搴㈢ギ闁?+ 濮掓稒顭堥濠氬磹?*/
    inline fun <reified T> input(default: T, mapper: (Any) -> T): T =
        input(mapper) ?: default

    fun inputAsString(default: String = ""): String = input(default) { it.toString() }
    fun inputAsInt(default: Int = 0): Int = input(default, ::convertToInt)
    fun inputAsLong(default: Long = 0L): Long = input(default, ::convertToLong)
    fun inputAsBoolean(default: Boolean = false): Boolean = input(default, ::convertToBoolean)

    // 闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩?    // Schema 闁规壆鍠撻悡锟犳儍?directInput 閻庢稒顨嗛宀€鎷嬮崸妤侊紪闁挎稑鐗嗘慨鈺呭箑娴ｈ娈堕柟璇″枦椤旀牠姊婚鍓ф憸闁伙絻鍎荤槐?    // 闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩?
    /**
     * 閻熸瑱绲鹃悗浠嬪触鎼达絾鐣?[SchemaFormat]闁挎稑鑻紞?[schemaFormat] 濞?null 闁哄啯鍎肩换鎴﹀炊?null闁?     */
    val resolvedSchemaFormat: SchemaFormat?
        get() = schemaFormat?.let { SchemaFormat.fromCode(it) }

    /**
     * directInput 闁?Map 閻熸瑥妫楀ù姗€鏁嶉崷鐜瀐ema 闁哄秶鍘х槐锟犲箛閻旇櫣鍙€闁挎稑顦埀?     *
     * - 鐟滅増鎹侀鏇犵磾椤旇崵鍟?[schemaFormat] + [providerRegistry] 闁哄啳顔愮槐婵囨交閺傛寧绀€ [SchemaBackedMap]闁?     *   闁?`get()` / `containsKey()` 闁烩晛鐡ㄧ敮瀛樻叏閺冣偓婢ь厾绱掑▎蹇ｅ殸閹煎瓨姊诲▓?[com.fluxion.schema.api.SchemaDataProvider]闁?     *   闁哄啰濞€濞撳爼宕楅崼锝囨闁?`toMap()` 闁稿鑹鹃崣蹇涙煂韫囨碍绁柟璇℃娇閳?     * - 闁告熬绠戦崹顖炲炊閻愯　鍋撻埀顒勫礆妫颁胶鐐婄紓?`Map<String, Any?>` 鐎殿噣缂氬ù鍡涘Υ?     * - directInput 濞?null 闁哄啯鍎肩换鎴﹀炊閻愮鏁?Map闁?     *
     * ```kotlin
     * // 闁稿秴绻戝▍姗€鏌?Map 濞戞挴鍋撻柡宥堟腹婵炲洭鎮介…鎺旂閹煎瓨娲栭惇浼存嚊椤忓嫬袟閻?SchemaDataProvider
     * val name = nodeInput.directInputView["name"]
     * val hasAge = nodeInput.directInputView.containsKey("age")
     * nodeInput.directInputView.forEach { (k, v) -> ... }
     * ```
     */
    val directInputView: Map<String, Any?> by lazy {
        val data = directInput ?: return@lazy emptyMap<String, Any?>()
        val format = resolvedSchemaFormat
        val registry = providerRegistry
        if (format != null && registry != null) {
            SchemaBackedMap(data, registry.getProvider(format))
        } else {
            @Suppress("UNCHECKED_CAST")
            (data as? Map<String, Any?>) ?: emptyMap()
        }
    }

    /**
     * 闁兼儳鍢茶ぐ鍥箰閸パ呮毎濞撴碍绻嗙粋鍡涙嚍閸屾粌浠弶鍫熸尭閸ゎ參鎯?Map 閻熸瑥妫楀ù姗€鏁嶉崷鐜瀐ema 闁哄秶鍘х槐锟犲箛閻旇櫣鍙€闁挎稑顦埀?     *
     * 濞?[directInputView] 闁告艾鐬奸幃濠囨晬鐏炶偐绋诲ù锝嗙矌閺併倖绂?declaredDeps 濞戞搩鍘惧▓鎴﹀蓟閹邦亪鍤嬮柤鍝勫€婚崑锝嗘綇閹惧啿姣夐柕?     * 濞撴碍绻嗙粋鍡樻綇閹惧啿姣夐柛娆樺灥閸忔﹢寮?Map闁挎稑婀濻ON闁挎稑顦伴崹?DynamicMessage闁挎稑婀otobuf闁挎稑顦伴崹?GenericRecord闁挎稑婀弙ro闁挎稑顧€缁?     * 鐟滅増鎹囬崢銈囩磾椤旇崵鍟?Schema 濞ｅ洠鍓濇导鍛村籍閹澘娈伴柛鏂诲姂閳ь剚淇虹换?provider 閻犱礁娼″Λ鍓佲偓娑欘殕椤斿矂濡?     *
     * @param nodeId 濞撴碍绻嗙粋鍡涙嚍閸屾粌浠?ID
     * @return Map 閻熸瑥妫楀ù姗€鏁嶇仦鑲╃憹閻庢稒锚濠€顏堝籍閹壆绠查柛銉у仧閳?Map
     */
    fun depView(nodeId: String): Map<String, Any?> {
        val data = declaredDeps[nodeId] ?: return emptyMap()
        val format = resolvedSchemaFormat
        val registry = providerRegistry
        if (format != null && registry != null) {
            return SchemaBackedMap(data, registry.getProvider(format))
        }
        @Suppress("UNCHECKED_CAST")
        return (data as? Map<String, Any?>) ?: emptyMap()
    }

    /**
     * 濞?directInput 濞戞搩鍘界€垫粎鈧稒顨嗛宀勫触瀹ュ牆绠柛娆愮墪閳ь剛銆嬬槐姗瞔hema 闁哄秶鍘х槐锟犲箛閻旇櫣鍙€闁挎稑顦埀?     *
     * 濠殿喗姊规晶顓犵磼?[directInputView]闁挎稑鑻紞瀣嫉?Schema 濞ｅ洠鍓濇导鍛村籍閹澘娈伴柛鏂诲姀閾斿鈧數鎳撶花鏌ユ儍?SchemaDataProvider闁?     * 闁告熬绠戦崹顖炲炊閻愯　鍋撻埀顒勫礆妫颁胶鐐婄紓?Map 閻犱礁娼″Λ鍫曞Υ?     */
    fun inputField(field: String): Any? = directInputView[field]

    /**
     * 濞?directInput 濞戞搩鍙€楠炲繘宕ｉ弽褏绠戝┑澶樺亜閻⊙冣枔闂堟稈鍋撶涵椋庣缂傚倸鎼妵鎴﹀籍閼搁潧顫旂€殿喖鍊搁悥鍫曞Υ?     */
    fun requireInputField(field: String): Any =
        inputField(field) ?: throw InvalidParamException("directInput.$field is required")

    /**
     * 濞?directInput 濞戞搩鍙€楠炲繘宕ｉ弽褏鎽熸繛鍫ユ涧閳ь剛銆嬬槐婵堟暜閿曞倻甯涢悹浣靛€曢埀顒傤儠閳?     */
    fun inputFieldOrDefault(field: String, default: Any? = null): Any? =
        inputField(field) ?: default

    /**
     * 闁告帇鍊栭弻?directInput 濞戞搩鍘藉Σ鎼佸触閿曗偓閻°劑宕烽妸锕€鐦归悗瑙勮壘閻⊙冣枔绾板绀凷chema 闁哄秶鍘х槐锟犲箛閻旇櫣鍙€闁挎稑顦埀?     */
    fun hasInputField(field: String): Boolean = directInputView.containsKey(field)

    /**
     * 閻?directInput 閺夌儐鍓氬畷鍙夌▔濞差亖鍋撳杈ㄦ殢 Map 閻炴稏鍔庨妵姘舵晬閸︾帪hema 闁哄秶鍘х槐锟犲箛閻旇櫣鍙€闁挎稑顦埀?     *
     * 濠殿喗姊规晶顓犵磼?[directInputView]闁?     */
    fun inputAsMap(): Map<String, Any?> = directInputView

    /**
     * 闁兼儳鍢茶ぐ?directInput 濞戞搩鍘芥晶宥夊嫉婢跺﹤璁查柣顫妼閻⊙冣枔闂堟稒鍊抽柨娑樻汞chema 闁哄秶鍘х槐锟犲箛閻旇櫣鍙€闁挎稑顦埀?     */
    fun inputFieldNames(): Set<String> = directInputView.keys

    // 闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩?    // 闁煎搫鍊婚崑锝夋煀瀹ュ洨鏋傞柛娆忓€归弳鐔兼晬閸ь柕deParams闁挎稑顦悾銊╁礂閵婎煈鍟忛梻?闁?閻熸洖妫涘ú?90% 閻犲鍟伴弫銈夊棘?    // 闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩?
    /** 闁煎搫鍊婚崑锝夊矗閸屾稒娈堕悗鐟邦槸閸欏繘宕ｉ弽褉鍋撶涵椋庣nullable闁?*/
    inline fun <reified T> param(key: String): T? = nodeParams[key] as? T

    /** 闁煎搫鍊婚崑锝夊矗閸屾稒娈堕煫鍥ф噸缁卞爼鏁嶅畝鈧杈ㄥ緞鏉堛劌顫旂€殿喖鍊搁悥?*/
    inline fun <reified T> requireParam(key: String): T =
        param<T>(key) ?: throw InvalidParamException("nodeParams.$key is required")

    /** 闁煎搫鍊婚崑锝夊矗閸屾稒娈堕悽顖ょ畵缁垳鎷嬮妶鍛亾?*/
    inline fun <reified T> param(key: String, default: T): T = param<T>(key) ?: default

    /** 闁煎搫鍊婚崑锝夊矗閸屾稒娈堕柤濂変簻閻ｇ偓绋婃径搴㈢ギ闁?*/
    inline fun <reified T> param(key: String, mapper: (Any) -> T): T? =
        nodeParams[key]?.let(mapper)

    /** 闁煎搫鍊婚崑锝夊矗閸屾稒娈堕柤濂変簻閻ｇ偓绋婃径搴㈢ギ闁?+ 濮掓稒顭堥濠氬磹?*/
    inline fun <reified T> param(key: String, default: T, mapper: (Any) -> T): T =
        param(key, mapper) ?: default

    fun paramAsString(key: String, default: String = ""): String =
        param(key, default) { it.toString() }

    fun paramAsInt(key: String, default: Int = 0): Int =
        param(key, default, ::convertToInt)

    fun paramAsLong(key: String, default: Long = 0L): Long =
        param(key, default, ::convertToLong)

    fun paramAsBoolean(key: String, default: Boolean = false): Boolean =
        param(key, default, ::convertToBoolean)

    // 闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩?    // 鐎规悶鍎扮紞鏂棵规担绋挎枾濠殿喖顑呴崣鍡涘矗閸岋妇绀剋orkflowInput闁挎稑顦悾銊╁礂閵婎煈鍟忛梻?    // 闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩?
    /** 鐎规悶鍎扮紞鏂棵规担绋挎枾濠殿喖顑呴崣鍡涘矗閸屾氨鏆旈柛蹇嬪妼瑜板洭宕愮涵椋庣nullable闁?*/
    inline fun <reified T> wfInput(key: String): T? = workflowInput[key] as? T

    /** 鐎规悶鍎扮紞鏂棵规担绋挎枾濠殿喖顑呴崣鍡涘矗閸屾氨绠戝ù鑲╁缁辨繄绱撻崫鍕╀杭闁硅埖绋戠槐鎾舵暜?*/
    inline fun <reified T> requireWfInput(key: String): T =
        wfInput<T>(key) ?: throw InvalidParamException("workflowInput.$key is required")

    /** 鐎规悶鍎扮紞鏂棵规担绋挎枾濠殿喖顑呴崣鍡涘矗閸屾氨鏁ㄥ娑欘焾椤撳宕?*/
    inline fun <reified T> wfInput(key: String, default: T): T = wfInput<T>(key) ?: default

    /** 鐎规悶鍎扮紞鏂棵规担绋挎枾濠殿喖顑呴崣鍡涘矗閸屾繂娈伴悗瑙勭煯缁犵喐娼浣稿簥 */
    inline fun <reified T> wfInput(key: String, mapper: (Any) -> T): T? =
        workflowInput[key]?.let(mapper)

    /** 鐎规悶鍎扮紞鏂棵规担绋挎枾濠殿喖顑呴崣鍡涘矗閸屾繂娈伴悗瑙勭煯缁犵喐娼浣稿簥 + 濮掓稒顭堥濠氬磹?*/
    inline fun <reified T> wfInput(key: String, default: T, mapper: (Any) -> T): T =
        wfInput(key, mapper) ?: default

    /** 鐎规悶鍎扮紞鏂棵规担绋垮汲闁告瑥鍊藉ù?String */
    fun wfInputAsString(key: String, default: String = ""): String =
        wfInput(key, default) { it.toString() }

    /** 鐎规悶鍎扮紞鏂棵规担绋垮汲闁告瑥鍊藉ù?Int */
    fun wfInputAsInt(key: String, default: Int = 0): Int =
        wfInput(key, default, ::convertToInt)

    /** 鐎规悶鍎扮紞鏂棵规担绋垮汲闁告瑥鍊藉ù?Long */
    fun wfInputAsLong(key: String, default: Long = 0L): Long =
        wfInput(key, default, ::convertToLong)

    /** 鐎规悶鍎扮紞鏂棵规担绋垮汲闁告瑥鍊藉ù?Boolean */
    fun wfInputAsBoolean(key: String, default: Boolean = false): Boolean =
        wfInput(key, default, ::convertToBoolean)

    // 闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩?    // 濠㈠湱澧楀Σ鎴炵瑹濠靛﹦顩柨娑樻綈eclaredDeps闁挎稑顦悾銊╁礂閵婎煈鍟忛梻?    // 闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩?
    /** 濠㈠湱澧楀Σ鎴炵瑹濠靛﹦顩弶鍫熸尭閸ゎ厾鈧懓顦崣蹇涘矗閺嵮€鍋撶涵椋庣nullable闁?*/
    inline fun <reified T> dep(nodeId: String): T? = declaredDeps[nodeId] as? T

    /** 濠㈠湱澧楀Σ鎴炵瑹濠靛﹦顩弶鍫熸尭閸ゎ叀绠涢崨顒傜倞闁挎稑鐬煎杈ㄥ緞鏉堛劌顫旂€殿喖鍊搁悥?*/
    inline fun <reified T> requireDep(nodeId: String): T =
        dep<T>(nodeId) ?: throw InvalidParamException("declaredDeps.$nodeId is required")

    /** 濠㈠湱澧楀Σ鎴炵瑹濠靛﹦顩弶鍫熸尭閸ゎ厾鏁敃鍌滃笡閻犱降鍊曢埀?*/
    inline fun <reified T> dep(nodeId: String, default: T): T = dep<T>(nodeId) ?: default

    /** 濠㈠湱澧楀Σ鎴炵瑹濠靛﹦顩弶鍫熸尭閸ゎ參鎳涢鍕毎濞戞柨顦冲ù鍡涘箲?*/
    inline fun <reified T> dep(nodeId: String, mapper: (Any) -> T): T? =
        declaredDeps[nodeId]?.let(mapper)

    /** 濠㈠湱澧楀Σ鎴炵瑹濠靛﹦顩弶鍫熸尭閸ゎ參鎳涢鍕毎濞戞柨顦冲ù鍡涘箲?+ 濮掓稒顭堥濠氬磹?*/
    inline fun <reified T> dep(nodeId: String, default: T, mapper: (Any) -> T): T =
        dep(nodeId, mapper) ?: default

    /** 濠㈠湱澧楀Σ鎴炵瑹濠靛﹦顩弶鍫熸尭閸ゎ厽娼?String */
    fun depAsString(nodeId: String, default: String = ""): String =
        dep(nodeId, default) { it.toString() }

    /** 濠㈠湱澧楀Σ鎴炵瑹濠靛﹦顩弶鍫熸尭閸ゎ厽娼?Int */
    fun depAsInt(nodeId: String, default: Int = 0): Int =
        dep(nodeId, default, ::convertToInt)

    /** 濠㈠湱澧楀Σ鎴炵瑹濠靛﹦顩弶鍫熸尭閸ゎ厽娼?Long */
    fun depAsLong(nodeId: String, default: Long = 0L): Long =
        dep(nodeId, default, ::convertToLong)

    /** 濠㈠湱澧楀Σ鎴炵瑹濠靛﹦顩弶鍫熸尭閸ゎ厽娼?Boolean */
    fun depAsBoolean(nodeId: String, default: Boolean = false): Boolean =
        dep(nodeId, default, ::convertToBoolean)

    // 闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩?    // Schema 闁规壆鍠撻悡锟犳儍?declaredDeps 閻庢稒顨嗛宀€鎷嬮崸妤侊紪
    // 闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩?
    /**
     * 濞寸姴瀛╃€垫氨鈧鐭欢椋庢導閺嶎剙螡闁绘劕婀卞▓鎴炴綇閹惧啿姣夊☉鎿冨幗鐎垫粎鈧稒顨嗛宀勫触瀹ュ牆绠柛娆愮墪閳ь剛銆嬬槐姗瞔hema 闁哄秶鍘х槐锟犲箛閻旇櫣鍙€闁挎稑顦埀?     *
     * 濠殿喗姊规晶顓犵磼?[depView]闁挎稑鐭侀崵婊堝礉閵娾斁鍋撳宕囩畺閻庣數鎳撶花鏌ユ儍?SchemaDataProvider 閻犱礁娼″Λ鍫曞Υ?     */
    fun depField(nodeId: String, field: String): Any? = depView(nodeId)[field]

    /**
     * 闁兼儳鍢茶ぐ鍥箰閸パ呮毎濞撴碍绻嗙粋鍡涙嚍閸屾粌浠弶鍫熸尭閸ゎ厽绋夐鐔奉暡闁哄牆顦ぐ鏌ユ偨閵娿儳鎽熸繛鍫ユ涧閹洟鏁嶉崷鐜瀐ema 闁哄秶鍘х槐锟犲箛閻旇櫣鍙€闁挎稑顦埀?     */
    fun depFieldNames(nodeId: String): Set<String> = depView(nodeId).keys

    /**
     * 濞寸姴瀛╃€垫氨鈧鐭欢椋庢導閺嶎剙螡闁绘劕婀卞▓鎴炴綇閹惧啿姣夊☉鎿冨弨楠炲繘宕ｉ弽褏绠戝┑澶樺亜閻⊙冣枔闂堟稈鍋撶涵椋庣缂傚倸鎼妵鎴﹀籍閼搁潧顫旂€殿喖鍊搁悥鍫曞Υ?     */
    fun requireDepField(nodeId: String, field: String): Any =
        depField(nodeId, field) ?: throw InvalidParamException("declaredDeps.$nodeId.$field is required")

    // 闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩?    // 缂備焦鎸婚悗顖炲礌閺嵮冩闁轰焦澹嗙划锔锯偓瑙勭啲缁辨┎tructured Binding闁?    // 闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩?
    /**
     * 閻熸瑱绲鹃悗浠嬪矗閸屾稒娈堕柛濠勩€嬬槐婵嬪绩椤栨稑鐦悗娑欘殜濞间即鏌岃箛搴ｇ憿缂備焦鎸婚悗顖炲礌閺嶎偆鎷ㄩ悗瑙勭煯鐞氳京绮斿鍠ｄ礁顕ｈ箛瀣у亾?     *
     * 闁圭鍋撻柡鍫濐槸閻⊙呯箔閿旇儻顩柨娑樼墕鐎垫﹢骞忛鍌滄尝闁哄瀚€佃尙绱掗幋婵堟毎闁?prefix/suffix闁挎稑顦搴ㄥ绩椤栨稑鐦?`${variable}` 婵☆垪鍓濆姗€骞撻幒鎴斿亾绾绐?     * ```json
     * "user:${username}:${password}"                        // 缂佺虎鍨伴悺褏绮敂鑳洬婵☆垪鍓濆?     * { "$ref": "input.userId" }                             // 鐎殿喗娲滈弫銈咁啅閵夈倗绋婃繛缈犵閸欏棝宕?     * { "$ref": "n2.username" }                              // 鐎殿喗娲滈弫銈嗙▔婵犲啰鍩楅柤鍝勫€婚崑锝嗘綇閹惧啿姣?     * { "$ref": "n2.username", "prefix": "user:" }          // 閻㈩垽绠戞晶鐘电磽閳ь剟骞忛崗鐓庡
     * { "$ref": "n2.username", "prefix": "user:${env}:" }   // prefix 濞戞梻鍠愰弫顕€骞愭担鐟扮祷闁?     * ```
     *
     * `$ref` 闁哄秶鍘х槐锟犳晬?     * - `input.{field}` 闁?濞?workflowInput 闁告瑦鐗曢埀?     * - `{nodeId}.{field}` 闁?濞?declaredDeps[nodeId] 闁告瑦鐗曢悺娆戔偓娑欘殕椤?     * - 閻熶胶顭堥悺褍鈻撻棃娑欏€?闁?濞撴碍绻冮濂稿蓟閵夛箑顥?workflowInput 闁?directInput 闁?nodeParams
     */
    fun resolveBinding(value: Any?): String? {
        if (value == null) return null

        // 闁冲厜鍋撻柍鍏夊亾 1. 閻庢稒顨堥浣圭▔鐠囇呯獥閻熸瑱绲鹃悗?${var} 婵☆垪鍓濆姗€骞撻幒鎴斿亾?闁冲厜鍋撻柍鍏夊亾
        if (value is String) return interpolateTemplate(value)

        // 闁冲厜鍋撻柍鍏夊亾 2. 缂備焦鎸婚悗顖炲礌閺嶎偆鎷ㄩ悗瑙勮壘椤曨喚鎸?闁冲厜鍋撻柍鍏夊亾
        if (value is Map<*, *>) {
            val ref = value["\$ref"]?.toString()
                ?: return null  // 闁?$ref 閻庢稒顨嗛宀勬晬瀹€鍕缂備焦鍨甸悾鍓р偓鐢殿攰閽?
            val resolved: Any? = when {
                // input.{field} 闁?workflowInput
                ref.startsWith("input.") -> {
                    val field = ref.removePrefix("input.")
                    workflowInput[field]
                }
                // {nodeId}.{field} 闁?declaredDeps
                ref.contains('.') -> {
                    val dot = ref.indexOf('.')
                    val nodeId = ref.substring(0, dot)
                    val field = ref.substring(dot + 1)
                    depView(nodeId)[field]
                }
                // 閻熶胶顭堥悺褍鈻撻棃娑欏€?闁?濞撴碍绻冮濂稿蓟閵夛箑顥?workflowInput 闁?directInput 闁?nodeParams
                else -> lookupVariable(ref)
            }

            val resolvedStr = resolved?.toString() ?: return null
            // prefix/suffix also support ${var} template interpolation
            val prefix = interpolateTemplate(value["prefix"]?.toString() ?: "")
            val suffix = interpolateTemplate(value["suffix"]?.toString() ?: "")
            return "$prefix$resolvedStr$suffix"
        }

        // 闁冲厜鍋撻柍鍏夊亾 3. 闁稿繑婀圭划顒傜尵鐠囪尙鈧兘鏁嶅绁嘢tring 闁稿繑绮岀花?闁冲厜鍋撻柍鍏夊亾
        return value.toString()
    }

    /**
     * 閻熸瑱绲鹃悗鐣屸偓娑欘殘椤戜焦绋夐煫顓″幀闁?`${variable}` 婵☆垪鍓濆姗€宕￠悩杈╃Т缂佹璐熼埀?     *
     * 闁告瑦锕㈤崳娲蓟閵夛箑顥濆銈呮惈缁參鏁嶅绔渞kflowInput 闁?directInput (Map) 闁?nodeParams 闁?declaredDeps (闁瑰吀绀侀柦鈺冧沪閺囩偟纾?闁?     * 闁哄牜浜ｈ闁哄鍔楀▓鎴﹀础閻樿京绉寸紒妤嬬細缁绘岸骞愭担绋挎枾闁哄秵鐏氶埀?     */
    private fun interpolateTemplate(template: String): String {
        if (!template.contains("\${")) return template
        return DOLLAR_BRACE_PATTERN.replace(template) { match ->
            val varName = match.groupValues[1]
            lookupVariable(varName)?.toString() ?: match.value
        }
    }

    /** 闁圭顦ぐ澶愭煂韫囨挻鍊冲〒姘箖椤愬ジ寮婚妷锕€顥?workflowInput 闁?directInput 闁?nodeParams 闁?declaredDeps */
    private fun lookupVariable(name: String): Any? {
        workflowInput[name]?.let { return it }
        directInputView[name]?.let { return it }
        nodeParams[name]?.let { return it }
        if (name.contains('.')) {
            val dot = name.indexOf('.')
            val nodeId = name.substring(0, dot)
            val field = name.substring(dot + 1)
            depView(nodeId)[field]?.let { return it }
        }
        declaredDeps[name]?.let { return it }
        return null
    }

    /**
     * 闁归潧缍婇崳铏规喆閿濆棛鈧粙宕ｉ崒娑欐闁告帗顨夐妴鍐晬鐏炲墽妲ㄥ☉鎿冧簻閸樻挾妲愰悩鎻掕濞寸姰鍎插Σ鍝モ偓娑欘殜濞间即鏌岃箛鏂跨仐缂備焦鍨甸悾鍓р偓鐢殿攰閽栧嫰濡?     */
    fun resolveBindingList(values: List<*>): List<String> =
        values.map { resolveBinding(it) ?: "" }

    // 闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩?    // 濞戞挸绉磋ぐ鏌ュ矗濡粯绾柡鍌氬簻缁辨瑩寮存径绋挎暕 Java record 闁?withXxx闁?    // 闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩￠幇銊︽珳闁崇儤鍔忛弲鏌ュ煛閹般劍娅滈柍鐑樺姀閺呮煡鍩?
    /** 闁哄洦瀵у畷?directInput闁挎稑鑻崣鐐閺嵮呮憻婵炲牆鍚€缁绘岸骞愭担椋庣憹闁?*/
    fun withDirectInput(newDirectInput: Any?): NodeInput = copy(directInput = newDirectInput)

    /** 闁哄洦瀵у畷?nodeParams闁挎稑鑻崣鐐閺嵮呮憻婵炲牆鍚€缁绘岸骞愭担椋庣憹闁?*/
    fun withNodeParams(newNodeParams: Map<String, Any>?): NodeInput =
        copy(nodeParams = newNodeParams?.toMap() ?: emptyMap())

    /** 闁哄洦瀵у畷?workflowInput闁挎稑鑻崣鐐閺嵮呮憻婵炲牆鍚€缁绘岸骞愭担椋庣憹闁?*/
    fun withWorkflowInput(newWorkflowInput: Map<String, Any>?): NodeInput =
        copy(workflowInput = newWorkflowInput?.toMap() ?: emptyMap())

    /** 闁哄洦瀵у畷?declaredDeps闁挎稑鑻崣鐐閺嵮呮憻婵炲牆鍚€缁绘岸骞愭担椋庣憹闁?*/
    fun withDeclaredDeps(newDeclaredDeps: Map<String, Any>?): NodeInput =
        copy(declaredDeps = newDeclaredDeps?.toMap() ?: emptyMap())

    /** 閻犱礁澧介悿?Schema 闁稿繐鍟╂穱濠囧箒椤栥倗绀勯柡宥囧帶缁?+ 闁告艾绉惰ⅷ + 闁绘鐗婂﹢浼存晬婢舵稓绀夐柛蹇旀构缁剛鈧稒顨嗛灞剧┍濠靛洤鐦☉鎾崇Т瑜?*/
    fun withSchema(
        format: String,
        name: String? = null,
        version: Long? = null
    ): NodeInput = copy(schemaFormat = format, schemaName = name, schemaVersion = version)

    /** 閻犱礁澧介悿?Schema 闁轰胶澧楀畵浣烘媼閸ф锛栭柟缁樺姃缁剁敻鎳撻崨顔芥殘闁告劕鐭侀妴鍐晬鐏炶棄寰撳ù鐘崇墪閻⊙冣枔閸忓摜绠介柟闀愭缁楀宕?*/
    fun withProviderRegistry(registry: SchemaDataProviderRegistry): NodeInput =
        copy(providerRegistry = registry)
}

/** 濞寸姷绮崜鎵尵鐠囪尙鈧?闁?Int 闁汇劌瀚悾銊╁礂閵娿劍绁柟?*/
private fun convertToInt(value: Any): Int = when (value) {
    is Int -> value
    is Long -> value.toInt()
    is Number -> value.toInt()
    is String -> value.toInt()
    else -> throw InvalidParamException("cannot convert to Int: `$value")
}

/** 濞寸姷绮崜鎵尵鐠囪尙鈧?闁?Long 闁汇劌瀚悾銊╁礂閵娿劍绁柟?*/
private fun convertToLong(value: Any): Long = when (value) {
    is Long -> value
    is Int -> value.toLong()
    is Number -> value.toLong()
    is String -> value.toLong()
    else -> throw InvalidParamException("cannot convert to Long: `$value")
}

/** 濞寸姷绮崜鎵尵鐠囪尙鈧?闁?Boolean 闁汇劌瀚悾銊╁礂閵娿劍绁柟?*/
private fun convertToBoolean(value: Any): Boolean = when (value) {
    is Boolean -> value
    is Number -> value.toInt() != 0
    is String -> value.lowercase().let {
        when (it) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> throw InvalidParamException("cannot convert to Boolean: `$value")
        }
    }
    else -> throw InvalidParamException("cannot convert to Boolean: `$value")
}
