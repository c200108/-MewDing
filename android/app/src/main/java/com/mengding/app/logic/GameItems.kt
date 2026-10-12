package com.mengding.app.logic

import com.mengding.app.data.Store
import kotlin.math.ceil

/**
 * 小游戏道具（目前给萌宠连连看用）。在商店"游戏"里用萌点买，放在 Economy.gameItems；
 * 游戏里用完了可以临时买，价格是商店的 1.2 倍（向上取整）。
 */
object GameItems {
    data class Item(val key: String, val name: String, val desc: String, val price: Int, val icon: String)

    val all = listOf(
        // 0.3.7 下调：炸弹和提示一样便宜
        Item("hint", "提示", "标出一对能连上的萌宠", 4, "link_hint"),
        Item("shuffle", "打乱", "把剩下的萌宠重新洗一遍", 5, "link_shuffle"),
        Item("freeze", "冻结", "时间停住 10 秒", 6, "link_freeze"),
        Item("bomb", "炸弹", "直接炸掉一对萌宠", 4, "link_bomb"),
    )

    fun tempPrice(i: Item): Int = ceil(i.price * 1.2).toInt()

    fun count(key: String): Int = Store.cfg.economy.gameItems[key] ?: 0

    /** 在商店买 n 个 */
    fun buy(i: Item, n: Int): String {
        var msg = ""
        Store.update { st ->
            val cost = Economy.bulkPrice(i.price, n)
            val paid = Economy.spend(st, cost) ?: run { msg = "萌点不够了，还差 ${cost - st.economy.points}"; return@update st }
            val have = (paid.economy.gameItems[i.key] ?: 0) + n
            msg = "买了 $n 个「${i.name}」（-$cost 萌点），现在有 $have 个"
            paid.copy(economy = paid.economy.copy(gameItems = paid.economy.gameItems + (i.key to have)))
        }
        return msg
    }

    /** 用掉一个；没有返回 false */
    fun take(key: String): Boolean {
        var ok = false
        Store.update { st ->
            val have = st.economy.gameItems[key] ?: 0
            if (have <= 0) return@update st
            ok = true
            st.copy(economy = st.economy.copy(gameItems = st.economy.gameItems + (key to have - 1)))
        }
        return ok
    }

    /** 游戏里临时买一个并马上用（不进背包） */
    fun buyTemp(i: Item): Boolean {
        var ok = false
        Store.update { st ->
            val paid = Economy.spend(st, tempPrice(i)) ?: return@update st
            ok = true
            paid
        }
        return ok
    }
}
