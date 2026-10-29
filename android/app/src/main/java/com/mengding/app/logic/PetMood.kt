package com.mengding.app.logic

import com.mengding.app.data.Pet
import kotlin.random.Random

/** 宠物此刻的心情（从体力、饱食、水分、睡觉推出来，不单独存） */
enum class Mood { HAPPY, CALM, SAD, PAIN, TIRED, HUNGRY, THIRSTY }

/** 气泡里的像素表情：col 是 assets/emotes/<物种>.png 精灵表里的第几列（6 列表情 × 4 行帧，每格 56×56） */
enum class Emote(val col: Int) { LOVE(0), COOL(1), ANGRY(2), SMUG(3), LAUGH(4), CRY(5) }

object PetMood {
    /** 先看最要紧的：又饿又渴到见底是难受，再是渴、饿、困，其余按整体状态分高兴 / 难过 / 平淡 */
    fun of(p: Pet): Mood = when {
        p.satiety < 10 && p.hydration < 10 || p.stamina < 5 && (p.satiety < 15 || p.hydration < 15) -> Mood.PAIN
        p.hydration < 30 -> Mood.THIRSTY
        p.satiety < 30 -> Mood.HUNGRY
        p.sleeping || p.stamina < 30 -> Mood.TIRED
        p.stamina >= 70 && p.satiety >= 60 && p.hydration >= 60 -> Mood.HAPPY
        p.stamina < 50 || p.satiety < 45 || p.hydration < 45 -> Mood.SAD
        else -> Mood.CALM
    }

    val LINES: Map<Mood, List<String>> = mapOf(
        Mood.HAPPY to listOf(
            "今天心情超好！", "和你在一起最开心啦", "感觉浑身都是劲儿", "要不要一起玩一会儿？", "今天的阳光好舒服",
            "我好喜欢你呀", "吃饱喝足，开心转圈圈", "你今天也很棒哦", "嘿嘿，夸夸我嘛", "想给你一个大大的抱抱",
        ),
        Mood.CALM to listOf(
            "嗯，今天还不错", "我在这儿陪着你呢", "慢慢来，不着急", "发会儿呆也挺好的", "你在忙什么呀？",
            "记得抬头看看窗外", "坐久了起来走走吧", "喝口水歇一歇", "今天也平平安安的", "有我在，别担心",
        ),
        Mood.SAD to listOf(
            "有点提不起劲……", "你能多陪陪我吗？", "今天有点闷闷的", "摸摸我就好啦", "感觉少了点什么",
            "唉，想你了", "能不能抱抱我", "我是不是被忘记了", "有点小委屈", "想听你说说话",
        ),
        Mood.PAIN to listOf(
            "好难受……救救我", "又饿又渴，快撑不住了", "肚子空空，嗓子冒烟", "我没力气了……", "求求你快来照顾我",
            "眼前都在冒金星", "再不吃东西要晕倒了", "呜呜，好辛苦", "能先给我点吃的喝的吗", "我真的好难受",
        ),
        Mood.TIRED to listOf(
            "好困啊……", "眼皮在打架", "让我眯一会儿", "哈——欠——", "想窝进被子里",
            "今天好累呀", "走不动了，歇歇脚", "陪我睡个午觉吧", "电量不足，需要充电", "等我睡醒再玩",
        ),
        Mood.HUNGRY to listOf(
            "肚子咕咕叫了", "好饿呀，有好吃的吗？", "想吃饭饭", "闻到香味了吗？我饿了", "饭点到了没呀",
            "饿得没力气撒娇了", "给我一点点零食嘛", "空空的肚子在抗议", "能喂我吃饭吗？", "想吃好多好多东西",
        ),
        Mood.THIRSTY to listOf(
            "好渴呀……", "想喝水水", "嗓子干干的", "给我倒杯水好吗", "水碗是不是空了？",
            "渴得舌头都伸出来了", "咕嘟咕嘟，想喝水", "你也记得喝水哦", "一起喝口水吧", "口好干，要水水",
        ),
    )

    /** 心情对应能用的表情：高兴→大笑 / 爱心眼，平淡→墨镜 / 斜眼，饿渴→生气，难过 / 难受 / 困→大哭 */
    fun emotes(m: Mood): List<Emote> = when (m) {
        Mood.HAPPY -> listOf(Emote.LAUGH, Emote.LOVE)
        Mood.CALM -> listOf(Emote.COOL, Emote.SMUG)
        Mood.HUNGRY, Mood.THIRSTY -> listOf(Emote.ANGRY)
        Mood.SAD, Mood.PAIN, Mood.TIRED -> listOf(Emote.CRY)
    }
}

/** 每种心情一副「牌」：洗好按顺序发，发完再洗；新一副的第一张不会和上一句一样，所以不会连着重复 */
class LineDeck(private val lines: Map<Mood, List<String>> = PetMood.LINES, private val rnd: Random = Random.Default) {
    private val decks = HashMap<Mood, ArrayDeque<String>>()
    private var last: String? = null

    fun next(m: Mood): String {
        val all = lines[m].orEmpty().ifEmpty { return "" }
        val deck = decks.getOrPut(m) { ArrayDeque() }
        if (deck.isEmpty()) {
            val s = all.shuffled(rnd).toMutableList()
            if (s.size > 1 && s.first() == last) s.add(s.removeAt(0))
            deck.addAll(s)
        }
        return deck.removeFirst().also { last = it }
    }
}

/**
 * 气泡文字里可以带一个表情：开头写 "\u0001表情名\u0001"，后面是正文（正文可以为空 = 只发表情）。
 * 用字符串传是因为气泡一路都是 String（首页、Unity、悬浮窗），不认得表情的地方用 plain() 去掉标记就行。
 */
object BubbleText {
    private const val MARK = '\u0001'

    fun encode(text: String, emote: Emote?): String = if (emote == null) text else "$MARK${emote.name}$MARK$text"

    fun parse(s: String?): Pair<Emote?, String> {
        if (s == null) return null to ""
        if (s.isEmpty() || s[0] != MARK) return null to s
        val end = s.indexOf(MARK, 1)
        if (end < 0) return null to s
        val e = Emote.entries.firstOrNull { it.name == s.substring(1, end) }
        return e to s.substring(end + 1)
    }

    fun plain(s: String): String = parse(s).second

    /**
     * 按心情说一句，按概率带表情：emoteChance 的概率带表情，带表情时再有 onlyEmoteChance 的概率只发表情不说话。
     * 只发表情时也把这句话留着备用：画的那边没有这个物种的表情图，就显示这句话（fallback）。
     */
    fun compose(p: Pet, deck: LineDeck, rnd: Random = Random.Default, emoteChance: Double = .45, onlyEmoteChance: Double = .25): Composed {
        val m = PetMood.of(p)
        val line = deck.next(m)
        if (rnd.nextDouble() >= emoteChance) return Composed(line, line)
        val e = PetMood.emotes(m).random(rnd)
        return Composed(encode(if (rnd.nextDouble() < onlyEmoteChance) "" else line, e), line)
    }

    /** bubble：交给气泡的字符串（可能带表情标记）；fallback：画不出表情时显示的话 */
    data class Composed(val bubble: String, val fallback: String)

    /** 只发表情、但这个物种没有表情图时，换成备用的话；有表情图或本来就有字时原样返回 */
    fun forSpecies(c: Composed, hasEmotes: Boolean): String {
        val (e, text) = parse(c.bubble)
        return when {
            e == null -> c.bubble
            !hasEmotes -> text.ifEmpty { c.fallback }
            else -> c.bubble
        }
    }
}
