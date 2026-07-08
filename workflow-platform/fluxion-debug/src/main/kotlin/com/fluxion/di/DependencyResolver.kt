package com.fluxion.di

/**
 * 娓氭繆绂嗙憴锝嗙€介崳?閳ユ柡鈧?閼存碍婀伴崙鑺ユ殶鐠佸潡妫?DI 鐎圭懓娅掗惃鍕弳閸? *
 * Groovy / JS 缁涘鍓奸張顒€鍤遍弫浼粹偓姘崇箖濮?SPI 閹稿鎮曠粔鐗堝灗缁鐎烽懢宄板絿瀹稿弶鏁為崗銉ф畱 Bean/閺堝秴濮熺€圭偘绶ラ敍? * 閺冪娀娓堕崗鍐茬妇鎼存洖鐪伴弰?Spring閵嗕笩uice 鏉╂ɑ妲搁崗鏈电铂 IoC 鐎圭懓娅掗妴? */
interface DependencyResolver {

    /**
     * 閹稿鎮曠粔鎷屝掗弸鎰贩鐠ф牕鐤勬笟瀣ㄢ偓?     *
     * @param name 娓氭繆绂嗛崷?DI 鐎圭懓娅掓稉顓犳畱閺嶅洩鐦戦敍鍫濐洤 Spring Bean name閿?     * @return 娓氭繆绂嗙€圭偘绶ラ敍灞肩瑝鐎涙ê婀弮鎯扮箲閸?null
     */
    fun resolve(name: String): Any?

    /**
     * 閹稿琚崹瀣掗弸鎰贩鐠ф牕鐤勬笟瀣ㄢ偓?     *
     * @param type 娓氭繆绂嗙猾璇茬€?     * @return 娓氭繆绂嗙€圭偘绶ラ敍灞肩瑝鐎涙ê婀弮鎯扮箲閸?null
     */
    fun <T> resolveByType(type: Class<T>): T?
}
