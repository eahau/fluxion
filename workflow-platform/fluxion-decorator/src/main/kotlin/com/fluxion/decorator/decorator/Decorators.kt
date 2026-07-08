package com.fluxion.decorator.decorator

import com.fluxion.core.exception.DecoratorNotFoundException
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.WorkflowNode
import org.slf4j.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 閼哄倻鍋ｇ憗鍛淬偘閸?SPI
 * 闁俺绻冮崠鍛邦棅 WorkflowFunction 鐎圭偟骞囧Ο顏勫瀼閸忚櫕鏁為悙鐧哥礄Metrics / Trace / RateLimit / Cache / Async閿? */
interface NodeDecorator {
    /** 鐟佸懘銈伴崳銊ョ穿閻劌鎮曢敍鍫濆弿鐏炩偓閸烆垯绔撮敍宀€鏁ゆ禍?WorkflowNode.decorators 闁板秶鐤嗛敍?*/
    fun name(): String

    /**
     * 閸栧懓顥?function閿涘矁绻戦崶鐐存煀閻?WorkflowFunction
     *
     * @param function 鐞氼偄瀵樼憗鍛畱閸樼喎顫愰崙鑺ユ殶閿涘牊鍨ㄦ稉濠佺鐏炲倽顥婃鏉挎珤閸栧懓顥婇崥搴ｆ畱閸戣姤鏆熼敍?     * @param node     瑜版挸澧犻懞鍌滃仯閿涘牐顥婃鏉挎珤閸欘垵顕伴崣鏍Ν閻愬湱娈?decoratorParams 閼惧嘲褰囬懛顏勭箒閻ㄥ嫰鍘ょ純顕嗙礆
     */
    fun decorate(function: WorkflowFunction<Any>, node: WorkflowNode): WorkflowFunction<Any>
}

/**
 * 鐟佸懘銈伴崳銊︽暈閸愬奔鑵戣箛?閳?缁?Kotlin閿涘矂娴傚鍡樼仸娓氭繆绂? */
class DecoratorRegistry {

    private val log = LoggerFactory.getLogger(javaClass)
    private val decorators = ConcurrentHashMap<String, NodeDecorator>()

    fun register(decorator: NodeDecorator) {
        decorators[decorator.name()] = decorator
        log.debug { "Registered decorator: ${decorator.name()}" }
    }

    fun registerAll(decoratorList: Collection<NodeDecorator>) {
        decoratorList.forEach(::register)
    }

    fun resolve(name: String): NodeDecorator =
        decorators[name] ?: throw DecoratorNotFoundException(name)

    fun contains(name: String): Boolean = decorators.containsKey(name)

    fun listNames(): List<String> = decorators.keys().toList()
}

/**
 * 瀵倹顒為崶鐐剁殶閻樿埖鈧? */
enum class AsyncCallbackStatus {
    SUCCESS,
    FAILED
}

/**
 * 瀵倹顒為崶鐐剁殶娑撳﹣绗呴弬?閳?閹佃儻娴囧鍌涱劄閼哄倻鍋ｉ幍褑顢戠€瑰本鍨氶崥搴ｆ畱鐎瑰本鏆ｆ稉濠佺瑓閺傚洣绗岀紒鎾寸亯閵? *
 * 閻?[AsyncDecorator] 閸︺劌绱撳銉ゆ崲閸斺剝澧界悰灞界暚閹存劕鎮楅弸鍕偓鐙呯礉楠炲爼鈧俺绻?[AsyncCallback.publish] 閹舵洟鈧帞绮伴崥搴ｎ伂鐎圭偟骞囬妴? */
data class AsyncCallbackContext(
    /** 閺堫剚顐煎鍌涱劄娴犺濮熼崬顖欑閺嶅洩鐦戦敍鍫ｇ殶閻劍鏌熺粩瀣祮閸欘垰绶遍敍?*/
    val asyncId: String,
    /** 瀹搞儰缍斿ù浣瑰⒔鐞涘苯鏁稉鈧弽鍥槕 */
    val executionId: String,
    /** 瀹搞儰缍斿ù浣哥暰娑?ID */
    val workflowId: String,
    /** 瀹搞儰缍斿ù浣告倳缁?*/
    val workflowName: String,
    /** 閹碘偓鐏炵偛绨查悽銊ュ瀻缂?*/
    val appGroup: String?,
    /** 閼哄倻鍋ｉ崬顖欑 ID */
    val nodeId: String,
    /** 閼哄倻鍋ｉ崥宥囆?*/
    val nodeName: String,
    /** 閼哄倻鍋ｅ鏇犳暏閻ㄥ嫬鍤遍弫?*/
    val functionRef: String,
    /** 閹笛嗩攽缂佹挻鐏夐悩鑸碘偓渚婄窗SUCCESS / FAILED */
    val status: AsyncCallbackStatus,
    /** 閹存劕濮涢弮鍓佹畱閼哄倻鍋ｆ潏鎾冲毉 */
    val output: Any?,
    /** 婢惰精瑙﹂弮鍓佹畱闁挎瑨顕ゆ穱鈩冧紖 */
    val errorMsg: String?
)

/**
 * 瀵倹顒為崶鐐剁殶 SPI 閳?AsyncDecorator 閻ㄥ嫬鎮楃粩顖欑贩鐠? * 鐎圭偟骞囬悽?workflow-admin 閹绘劒绶甸敍鍦瀉fka / HTTP 閸ョ偠鐨熼敍? */
interface AsyncCallback {
    fun publish(context: AsyncCallbackContext)
}

/**
 * 缂傛挸鐡ㄧ€涙ê鍋?SPI 閳?CacheDecorator 閻ㄥ嫬鎮楃粩顖欑贩鐠? * 姒涙顓荤€圭偟骞囬敍姝渦iltin 濡€虫健閹绘劒绶?CaffeineCacheStore
 * 閸欘垱娴涢幑銏犵杽閻滃府绱伴悽鍙樼瑐鐏炲倸绨查悽銊︽暈閸?RedisCacheStore 缁?Bean 鐟曞棛娲? */
interface CacheStore {
    fun get(key: String): Any?
    fun put(key: String, value: Any?, ttl: Long, unit: TimeUnit)
    fun evict(key: String)
}
