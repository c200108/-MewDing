package com.mengding.app.logic

/** 宠物图鉴。key 对应 assets/pets/<key>.png */
data class Species(val key: String, val name: String, val defaultName: String, val intro: String)

object Catalog {
    val all = listOf(
        Species("cat", "小猫", "叮叮", "奶桃色小猫，额头有虎斑纹，尾巴总在摇"),
        Species("dog", "小狗", "旺财", "柴犬色大垂耳，被摸会吐舌头"),
        Species("guinea_pig", "豚鼠", "团子", "三花毛色，圆滚滚一只"),
        Species("sugar_glider", "蜜袋鼯", "小飞", "被拎起来会张开飞膜"),
        Species("snake", "小蛇", "青青", "盘成两圈，会吐分叉的小舌头"),
        Species("lizard", "蜥蜴", "豹豹", "豹纹守宫，咧嘴笑，环纹尾巴"),
        Species("chinchilla", "龙猫", "灰灰", "灰色大毛球，圆耳朵、蓬松大尾巴，开心了会原地蹦高"),
        Species("hamster", "仓鼠", "瓜子", "金黄色小圆球，吃东西会把腮帮子塞得鼓鼓的"),
        Species("turtle", "小乌龟", "慢慢", "背着六边形花纹的壳，走得慢吞吞，害羞就缩进壳里"),
        Species("bunny", "小兔", "糯米", "长耳朵小白兔，一蹦一蹦地走，被摸时耳朵耷拉下来"),
        Species("clownfish", "小丑鱼", "尼莫", "橙白条纹，在水里摆尾游，喝水改成吐泡泡"),
        Species("hedgehog", "刺猬", "栗子", "一身小刺，被拎起来或睡觉时缩成刺球"),
        Species("swan", "天鹅", "白白", "雪白的羽毛、橙色小嘴，走路一摇一摆地扇翅膀"),
        Species("pig", "小香猪", "噜噜", "粉嘟嘟的卷尾巴小猪，吃饭拱得最起劲"),
        Species("tarantula", "塔兰图拉毒蛛", "八爪", "红膝毛蛛，8 条腿交替爬行，其实很温顺"),
    )

    fun of(key: String) = all.firstOrNull { it.key == key } ?: all[0]

    /** 领养第 n 只（已经有 owned 只）的价格：第 2 只 150，之后每只 +100 */
    fun price(owned: Int): Int = 150 + (owned - 1).coerceAtLeast(0) * 100

    /** 等级：每级需要的累计经验 */
    private val levelExp = intArrayOf(0, 60, 150, 280, 450, 660, 910, 1200, 1530, 1900)

    fun level(exp: Int): Int = levelExp.indexOfLast { exp >= it } + 1

    fun expRange(exp: Int): Pair<Int, Int> {
        val lv = level(exp)
        val lo = levelExp[lv - 1]
        val hi = if (lv < levelExp.size) levelExp[lv] else lo
        return lo to hi
    }

    /** 升级解锁的新动作（需求 E4b）。动画还在画，先列出来 */
    val unlocks = listOf(3 to "打滚", 5 to "转圈", 7 to "坐下作揖", 10 to "专属彩蛋动作")
}
