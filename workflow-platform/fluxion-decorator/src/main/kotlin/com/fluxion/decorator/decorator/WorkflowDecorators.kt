package com.fluxion.decorator.decorator

import com.fluxion.core.exception.DecoratorNotFoundException
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.value.EngineResult
import org.slf4j.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 瀹搞儰缍斿ù浣洪獓鐟佸懘銈伴崳?SPI閵? *
 * 娑?[NodeDecorator] 閻ㄥ嫬灏崚顐窗
 * - 娴ｆ粎鏁ら崺鐕傜窗閸栧懓锛欓弫瀛橆唽瀹搞儰缍斿ù浣瑰⒔鐞涘矉绱橻DagExecutor.execute] 缁狙冨焼閿涘绱濋懓宀勬姜閸楁洑閲滈懞鍌滃仯閵? * - 鏉堟挸鍙嗘稉濠佺瑓閺傚浄绱伴崢鐔奉潗瀹搞儰缍斿ù浣稿弳閸?+ [WorkflowDefinition]閿涘矁鈧矂娼?[com.fluxion.core.model.NodeInput]閵? * - 瀵倸鐖剁拠顓濈疅閿涙艾浼愭担婊勭ウ缁狙嗩棅妤楁澘娅掗幎娑樺毉閻ㄥ嫬绱撶敮鎼佲偓姘埗閻╁瓨甯寸€佃壈鍤ч弫缈犻嚋瀹搞儰缍斿ù浣搞亼鐠愩儻绱濇稉宥呭綀閼哄倻鍋?errorStrategy 瑜板崬鎼烽妴? *
 * 閸忕鐎烽悽銊┾偓鏃撶窗
 * - 瀹搞儰缍斿ù浣洪獓閸掑棗绔峰蹇涙敚閿涘溂com.fluxion.decorator.lock.WorkflowLockDecorator]閿? * - 瀹搞儰缍斿ù浣洪獓娴滃濮熼敍鍦糲om.fluxion.builtin.db.transaction.WorkflowTransactionDecorator]閿? * - 瀹搞儰缍斿ù浣洪獓闂勬劖绁﹂敍鍦糲om.fluxion.decorator.ratelimit.WorkflowRateLimitDecorator]閿? */
interface WorkflowDecorator {
    /** 鐟佸懘銈伴崳銊ョ穿閻劌鎮曢敍鍫濆弿鐏炩偓閸烆垯绔撮敍宀€鏁ゆ禍?WorkflowDefinition.workflowDecorators 闁板秶鐤嗛敍?*/
    fun name(): String

    /**
     * 鐟佸懘銈伴弫瀛橆唽瀹搞儰缍斿ù浣瑰⒔鐞涘被鈧?     *
     * 鐎圭偟骞囩猾璇茬安閸?[execute] 閸撳秴鎮楅幓鎺戝弳濡亜鍨忛柅鏄忕帆閿涘本娓剁紒鍫濈箑妞ゆ槒鐨熼悽?[execute] 鐎瑰本鍨氱€圭偤妾銉ょ稊濞翠焦澧界悰灞烩偓?     *
     * @param def      瀹搞儰缍斿ù浣哥暰娑?     * @param rawInput 瀹搞儰缍斿ù浣稿斧婵鍙嗛崣?     * @param execute  鐞氼偉顥婃鎵畱鐎圭偤妾幍褑顢戦柅鏄忕帆閿涘牐鐨熼悽銊ょ濞嗏槄绱?     * @return 瀹搞儰缍斿ù浣瑰⒔鐞涘瞼绮ㄩ弸?     */
    suspend fun decorate(
        def: WorkflowDefinition,
        rawInput: Map<String, Any>,
        execute: suspend () -> EngineResult
    ): EngineResult
}

/**
 * 瀹搞儰缍斿ù浣洪獓鐟佸懘銈伴崳銊︽暈閸愬奔鑵戣箛?閳?缁?Kotlin閿涘矂娴傚鍡樼仸娓氭繆绂嗛妴? */
class WorkflowDecoratorRegistry {

    private val log = LoggerFactory.getLogger(javaClass)
    private val decorators = ConcurrentHashMap<String, WorkflowDecorator>()

    fun register(decorator: WorkflowDecorator) {
        decorators[decorator.name()] = decorator
        log.debug { "Registered workflow decorator: ${decorator.name()}" }
    }

    fun registerAll(decoratorList: Collection<WorkflowDecorator>) {
        decoratorList.forEach(::register)
    }

    fun resolve(name: String): WorkflowDecorator =
        decorators[name] ?: throw DecoratorNotFoundException(name)

    fun contains(name: String): Boolean = decorators.containsKey(name)

    fun listNames(): List<String> = decorators.keys().toList()
}
