package com.mengding.app.data

import kotlinx.serialization.Serializable

/**
 * 数据分三层（需求 A5）：
 *  - [ConfigState]  配置：个人档案、设置、宠物、萌点。以后可以上云同步。明文存在 config.json。
 *  - [PrivateState] 个人敏感数据：喝水吃饭睡觉明细、经期、关心 TA。默认只在本机，加密存在 private.bin。
 *  - 公共资源：宠物精灵图等，在 assets 里，以后可以改成从服务器下载。
 */

@Serializable
enum class Gender(val label: String) { UNSET("未填写"), FEMALE("女"), MALE("男"), PRIVATE("不愿透露") }

@Serializable
enum class SoundMode(val label: String) { SOFT("轻柔提示音"), DING("萌叮铃铛声"), PET("宠物叫声"), SILENT("静音（只震动）") }

@Serializable
data class Meal(val label: String, val time: String)

@Serializable
data class Profile(
    val nickname: String = "",
    val gender: Gender = Gender.UNSET,
    val birthday: String? = null,
    val weightKg: Int? = null,
    val wake: String = "07:30",
    val sleep: String = "23:00",
    val meals: List<Meal> = listOf(Meal("早餐", "08:00"), Meal("午餐", "12:00"), Meal("晚餐", "18:30")),
    val onboarded: Boolean = false,
)

@Serializable
data class CloudPrefs(
    val enabled: Boolean = false,
    val config: Boolean = true,
    val water: Boolean = false,
    val sleep: Boolean = false,
    val meals: Boolean = false,
    val period: Boolean = false,
)

@Serializable
data class Settings(
    val waterGoalMl: Int = 2000,
    val cupMl: Int = 250,
    val waterIntervalMin: Int = 90,
    val waterOn: Boolean = true,
    val mealOn: Boolean = true,
    val sleepOn: Boolean = true,
    val quietOn: Boolean = false,
    val quietStart: String = "12:30",
    val quietEnd: String = "13:30",
    val sound: SoundMode = SoundMode.SOFT,
    /** null 表示跟随性别：女性默认打开，其他默认关闭 */
    val periodOn: Boolean? = null,
    val ovulationShown: Boolean = false,
    val periodAdvanceDays: Int = 2,
    val careTips: Boolean = true,
    val careModeOn: Boolean = false,
    /** 关心 TA 功能开关（提醒设置里）：null 跟随性别——女性默认开，男性和不愿透露默认关；开了便签里才有「关心 TA」 */
    val careOn: Boolean? = null,
    val persistentPet: Boolean = true,
    val floatingPet: Boolean = false,
    val stepWalk: Boolean = false,
    val privacyLock: Boolean = false,
    val disguise: Boolean = false,
    val iconAlias: String = "default",
    val petStyle: String = "pixel",
    val cloud: CloudPrefs = CloudPrefs(),
    /** 点宠物、按住宠物时发出叫声 */
    val petSoundOn: Boolean = true,
    /** 桌面宠物说话（文字气泡） */
    val speechOn: Boolean = true,
    val speechIntervalMin: Int = 30,
    val phrases: List<String> = DEFAULT_PHRASES,
    /** 界面主题，见 ui/Themes.kt */
    val uiTheme: String = "classic",
    /** 饭点时桌面宠物冒气泡提醒（点气泡就记这一餐） */
    val mealBubble: Boolean = true,
    /** 桌面宠物怎么跑：off 不跑；cross 横穿屏幕；edges 绕屏幕四条边跑 */
    val petRunMode: String = "cross",
    /** 横穿方向：auto 来回；left 一直向左；right 一直向右 */
    val petRunDir: String = "auto",
    /** 绕边方向：cw 顺时针；ccw 逆时针 */
    val petLapDir: String = "cw",
    /** 小游戏的音效和背景音乐 */
    val gameSound: Boolean = true,
    /** 小游戏方向键、操作键按下时震一下 */
    val gameVibrate: Boolean = true,
    /** 显示字体（ui/AppFonts.kt）：system = 系统默认，其他的要先联网下载 */
    val fontKey: String = "system",
    /** 本地天气：用户主动同意获取大致位置后才打开（ui/Weather.kt） */
    val localWeather: Boolean = false,
    /** 首页动态背景：auto = 按时间 / 天气 / 节日自动换，否则固定某个场景（ui/LiveScene.kt 的 Scenes.ALL） */
    val homeScene: String = "auto",
)

/** 宠物默认会说的话（用户可以改） */
val DEFAULT_PHRASES = listOf(
    "今天也要加油呀",
    "记得好好喝水哦",
    "坐久了，起来伸个懒腰吧",
    "别忘了按时吃饭",
    "你已经很棒啦",
    "累了就歇一会儿，我陪着你",
    "深呼吸，放松一下肩膀",
    "今天也要对自己好一点",
    "早点睡，明天会更好",
)

@Serializable
data class Pet(
    val id: String,
    val species: String,
    val name: String,
    val stamina: Float = 85f,
    val satiety: Float = 80f,
    val hydration: Float = 80f,
    val exp: Int = 0,
    val sleeping: Boolean = false,
    val adoptedAt: Long = 0,
    /** 旧版（0.3.0 开发中）的单件服装字段，读到后会迁移到 wearing */
    val outfit: String? = null,
    /** 正在穿的服装 key，每个分类最多一件，见 assets/outfits/outfits.json */
    val wearing: List<String> = emptyList(),
) {
    /** 实际穿着的：兼容旧字段 */
    val worn: List<String> get() = wearing.ifEmpty { listOfNotNull(outfit) }
}

/** 每天清零的计数 */
@Serializable
data class Daily(
    val date: String = "",
    val reminderPts: Int = 0,
    val gamePts: Int = 0,
    val gameSeconds: Int = 0,
    val companionMinutes: Int = 0,
    val companionPts: Int = 0,
    val waterGoalAwarded: Boolean = false,
    val sleepAwarded: Boolean = false,
    val recordAwarded: Boolean = false,
    val petExp: Int = 0,
    val waterOff: Boolean = false,
)

@Serializable
data class Economy(
    val points: Int = 0,
    val lastCheckIn: String? = null,
    val checkInStreak: Int = 0,
    val waterStreak: Int = 0,
    val lastWaterGoalDay: String? = null,
    val sleepStreak: Int = 0,
    val lastSleepDay: String? = null,
    /** 打卡领养得到的免费领养次数 */
    val freeAdopts: Int = 0,
    val waterAdoptClaimed: Boolean = false,
    val sleepAdoptClaimed: Boolean = false,
    /** 买过的服装 */
    val ownedOutfits: List<String> = emptyList(),
    /** 小游戏最高分：pac = 萌宠吃豆，adv = 萌叮大冒险 */
    val gameBest: Map<String, Int> = emptyMap(),
    /** 萌叮大冒险已解锁到第几关 */
    val advUnlocked: Int = 1,
    /** 库存：商店买的食物、饮料，key -> 个数 */
    val pantry: Map<String, Int> = emptyMap(),
    /** 小游戏道具：key -> 个数（logic/GameItems.kt） */
    val gameItems: Map<String, Int> = emptyMap(),
)

@Serializable
data class Snooze(val kind: String, val at: Long, val extra: String = "")

@Serializable
data class ConfigState(
    val profile: Profile = Profile(),
    val settings: Settings = Settings(),
    val pets: List<Pet> = emptyList(),
    val outingId: String? = null,
    val economy: Economy = Economy(),
    val daily: Daily = Daily(),
    val lastSimAt: Long = 0,
    val doubleRecoveryUntil: Long = 0,
    val lastAlarmCheck: Long = 0,
    val snoozes: List<Snooze> = emptyList(),
)

// ---------------- 个人敏感数据 ----------------

@Serializable
data class WaterLog(val t: Long, val ml: Int)

@Serializable
data class MealLog(val day: String, val label: String, val t: Long, val note: String = "")

/** day 是"哪一晚"：晚上睡觉那天的日期 */
@Serializable
data class SleepLog(val day: String, val bedAt: Long? = null, val wakeAt: Long? = null, val quality: Int = 0)

@Serializable
data class DayNote(
    val day: String,
    val flow: Int = 0,
    val pain: Int = 0,
    val moods: List<String> = emptyList(),
    val symptoms: List<String> = emptyList(),
)

@Serializable
data class CarePerson(
    val id: String,
    val name: String,
    val lastStart: String? = null,
    val cycle: Int = 28,
    val length: Int = 5,
)

@Serializable
data class PrivateState(
    val water: List<WaterLog> = emptyList(),
    val meals: List<MealLog> = emptyList(),
    val sleeps: List<SleepLog> = emptyList(),
    /** 标记为经期的日期（yyyy-MM-dd） */
    val periodDays: List<String> = emptyList(),
    val notes: List<DayNote> = emptyList(),
    val care: List<CarePerson> = emptyList(),
)

// ---------------- 便签 ----------------

@Serializable
enum class BlockType { TEXT, IMAGE, AUDIO, TABLE }

/** 段落格式：整段生效 */
@Serializable
data class TextSpec(
    /** 0 小、1 正文、2 大、3 标题 */
    val size: Int = 1,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    /** 颜色序号，见 NoteColors */
    val color: Int = 0,
    /** 0 左、1 中、2 右 */
    val align: Int = 0,
    /** 0 无、1 圆点、2 数字、3 待办 */
    val list: Int = 0,
    val quote: Boolean = false,
)

@Serializable
data class Block(
    val id: String,
    val type: BlockType = BlockType.TEXT,
    val text: String = "",
    val spec: TextSpec = TextSpec(),
    val checked: Boolean = false,
    /** 图片、音频：存在 filesDir/notes_media/ 下的文件名 */
    val file: String? = null,
    val durationMs: Long = 0,
    /** 音频是录音还是插入的文件 */
    val recorded: Boolean = false,
    val table: List<List<String>> = emptyList(),
)

@Serializable
enum class Repeat(val label: String) {
    ONCE("仅一次"), DAILY("每天"), WORKDAYS("工作日"), WEEKENDS("休息日"), DATES("特定日期")
}

@Serializable
enum class RemindMode(val label: String) { BUBBLE("仅弹气泡"), BUBBLE_SOUND("气泡 + 叫声 + 震动") }

@Serializable
data class NoteReminder(
    val enabled: Boolean = true,
    val time: String = "09:00",
    val repeat: Repeat = Repeat.ONCE,
    /** 仅一次的日期 */
    val date: String? = null,
    /** 特定日期 */
    val dates: List<String> = emptyList(),
    val mode: RemindMode = RemindMode.BUBBLE_SOUND,
    /** 气泡里显示的话，空就用便签标题 */
    val text: String = "",
)

@Serializable
data class Note(
    val id: String,
    val title: String = "",
    val blocks: List<Block> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val pinned: Boolean = false,
    val reminder: NoteReminder? = null,
)

@Serializable
data class NotesState(val notes: List<Note> = emptyList())
