package com.fluxion.di

import com.fluxion.core.function.WorkflowFunction

/**
 * [FunctionInstanceProvider] 閻ㄥ嫬鍙忕仦鈧▔銊ュ斀鐞涖劊鈧? *
 * 瀹搞儰缍斿ù浣哥穿閹垮骸婀▔銊ュ斀閸戣姤鏆熺紒鍕閺冭绱濋柅姘崇箖濮濄倖鏁為崘宀冦€冮幐鎴︹偓澶岊儑娑撯偓娑擃亝鏁幐浣烘窗閺嶅洤鍤遍弫鎵閻ㄥ嫭褰佹笟娑溾偓鍜冪礉
 * 鐎瑰本鍨氶崙鑺ユ殶鐎圭偘绶ラ惃鍕灡瀵よ桨绗屾笟婵婄濞夈劌鍙嗛妴? */
object FunctionInstanceProviderRegistry {

    private val providers = mutableListOf<FunctionInstanceProvider>()

    /**
     * 濞夈劌鍞芥稉鈧稉顏勫毐閺佹澘鐤勬笟瀣絹娓氭稖鈧懌鈧?     */
    fun register(provider: FunctionInstanceProvider) {
        providers.add(provider)
    }

    /**
     * 閼惧嘲褰囩粭顑跨娑擃亝鏁幐浣瑰瘹鐎规艾鍤遍弫鎵閻ㄥ嫭褰佹笟娑溾偓鍛偓?     */
    fun getProvider(functionClass: Class<out WorkflowFunction<*>>): FunctionInstanceProvider? {
        return providers.firstOrNull { it.supports(functionClass) }
    }

    /**
     * 濞撳懐鈹栧鍙夋暈閸愬本褰佹笟娑溾偓鍜冪礄娑撴槒顩﹂悽銊ょ艾濞村鐦敍澶堚偓?     */
    fun clear() {
        providers.clear()
    }
}
