package com.fluxion.di

import com.fluxion.core.function.WorkflowFunction

/**
 * 閸戣姤鏆熺€圭偘绶ラ幓鎰返閼?閳ユ柡鈧?DI 鐎圭懓娅掗柅鍌炲帳閸?SPI
 *
 * 鐠愮喕鐭楃亸?[WorkflowFunction] 閻ㄥ嫬鐤勯悳鎵鐎圭偘绶ラ崠鏍电礉楠炴湹绮犳惔鏇炵湴 DI 鐎圭懓娅掗敍鍦玴ring閵嗕笩uice 缁涘绱? * 濞夈劌鍙嗛崗鏈电贩鐠ф牓鈧倸鐤勯悳鎵閸欘亪娓舵竟鐗堟閼奉亜绻侀弨顖涘瘮閻ㄥ嫬鍤遍弫鎵閸ㄥ绱漑FunctionInstanceProviderRegistry]
 * 娴兼碍瀵滄い鍝勭碍閹告垿鈧顑囨稉鈧稉?`supports()` 鏉╂柨娲?true 閻ㄥ嫭褰佹笟娑溾偓鍛偓? */
interface FunctionInstanceProvider {

    /**
     * 閸掋倖鏌囪ぐ鎾冲閹绘劒绶甸懓鍛板厴閸氾箑鐤勬笟瀣閹稿洤鐣鹃惃鍕毐閺佹壆琚妴?     */
    fun supports(functionClass: Class<*>): Boolean

    /**
     * 鐎圭偘绶ラ崠鏍у毐閺佹壆琚獮鑸垫暈閸忋儰绶风挧鏍モ偓?     *
     * 鐎圭偟骞囨惔鏂剧箽鐠囦浇绻戦崶鐐垫畱鐎圭偘绶ュ鎻掔暚閹?DI 濞夈劌鍙嗛敍娑滃閺冪姵纭堕崚娑樼紦閿涘苯绨查幎娑樺毉瀵倸鐖堕妴?     */
    fun getInstance(functionClass: Class<out WorkflowFunction<*>>): WorkflowFunction<*>
}
