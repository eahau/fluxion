package com.fluxion.decorator.engine

import kotlinx.coroutines.CoroutineDispatcher
import java.util.concurrent.Executor

/**
 * 鐠恒劎鍤庣粙瀣╃瑐娑撳鏋冩导鐘虫尡閹碘晛鐫嶉悙? *
 * 鐠佹崘顓搁崣鍌濃偓?OTel Context.wrap()閿? *   wrap 娑撯偓濞喡ょ箲閸ョ偛瀵樼憗鍛倵閻ㄥ嫭澧界悰灞芥珤/鐠嬪啫瀹抽崳顭掔礉鐠嬪啰鏁ら弬瑙勬￥闂団偓閹扮喓鐓℃稉濠佺瑓閺傚洣绱堕幘顓溾偓? *
 * ```
 * // OTel: Context.wrap(Executor)
 * Executor wrapped = context.wrap(executor);
 * wrapped.execute(task);  // context auto-propagated
 *
 * // Fluxion: TaskInterceptor.wrap(CoroutineDispatcher / Executor)
 * async(interceptor.wrap(dispatcher)) { ... }
 * interceptor.wrap(executor).execute { ... }
 * ```
 *
 * 鐟曞棛娲婇崷鐑樻珯閿? *   - [wrap] `CoroutineDispatcher` 閳?DagExecutor async(Dispatchers.IO)
 *   - [wrap] `Executor` 閳?RetryScheduler / TIMEOUT_EXECUTOR / AsyncDecorator
 *
 * fluxion-core 閺堫剝闊╂稉宥勭贩鐠ф牔鎹㈡担鏇℃嫹闊亝顢嬮弸璁圭礉
 * 閸忚渹缍嬬€圭偟骞囬悽?OTel 濡€虫健閿涘牆顩?fluxion-decorator-impl閿涘褰佹笟娑栤偓? */
interface TaskInterceptor {

    /** 閸栧懓顥?CoroutineDispatcher閿涘苯婀?dispatch 鐏炲倿娼伴懛顏勫З娴肩姵鎸辨稉濠佺瑓閺?*/
    fun wrap(dispatcher: CoroutineDispatcher): CoroutineDispatcher

    /** 閸栧懓顥?Executor閿涘苯婀?execute 鐏炲倿娼伴懛顏勫З娴肩姵鎸辨稉濠佺瑓閺?*/
    fun wrap(executor: Executor): Executor = executor

    companion object {
        /** 缁屽搫鐤勯悳?閳?娑撳秳绱堕幘顓濇崲娴ｆ洑绗傛稉瀣瀮閿涘矁绻戦崶鐐插斧婵澧界悰灞芥珤/鐠嬪啫瀹抽崳?*/
        val NOOP: TaskInterceptor = object : TaskInterceptor {
            override fun wrap(dispatcher: CoroutineDispatcher): CoroutineDispatcher = dispatcher
        }
    }
}
