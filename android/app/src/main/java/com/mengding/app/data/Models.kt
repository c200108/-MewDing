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
    /** 新用户默认萌叮铃铛声；老用户 config.json 里已经写着自己的选择，不受影响 */
    val sound: SoundMode = SoundMode.DING,
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
    /** 弹窗、提示、按钮、开关等控件的画风：cream 奶油气泡 / comic 漫画贴纸 / line 清新线条 / pixel 像素游戏（ui/Md.kt） */
    val uiStyle: String = "cream",
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
    /** 首页 HD-2D 小镇的背景音乐（场景右下角小喇叭开关） */
    val homeMusic: Boolean = true,
    /** 小游戏方向键、操作键按下时震一下 */
    val gameVibrate: Boolean = true,
    /** 显示字体（ui/AppFonts.kt）：system = 系统默认，其他的要先联网下载 */
    val fontKey: String = "system",
    /** 本地天气：用户主动同意获取大致位置后才打开（ui/Weather.kt） */
    val localWeather: Boolean = false,
    /** 首页动态背景（2D）：auto = 按时间 / 天气 / 节日自动换，否则固定某个场景（ui/LiveScene.kt 的 Scenes.ALL） */
    val homeScene: String = "auto",
    /** 首页用 3D 小屋（Unity）；关掉或手机不支持时用上面的 2D 场景。新字段，老用户升级后也默认打开 */
    val home3d: Boolean = true,
    /** 3D 小屋的美术风格：voxel 微体素 / hd2d（3D 低模 + 像素贴图）。Unity 一个进程只能起一次，换了要重启 App 才生效 */
    val sceneStyle: String = "voxel",
    /** 首页 2D 场景说话气泡的样式：cream 奶油可爱 / comic 卡通漫画 / line 简约线条 / ink 国漫水墨 / pixel 像素复古（ui/SceneBubble.kt） */
    val bubbleStyle: String = "cream",
    /** 已经看过的新手轻提示（按页面记：home / notes / shop / games / room / floating） */
    val tipsSeen: List<String> = emptyList(),
    /** 自启动、后台锁定没有接口能查，只能用户去系统页设好后自己打勾 */
    val autostartChecked: Boolean = false,
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
    /** 今天已经领过萌点的小游戏（adv / pac / link），每种每天一次 */
    val gamesAwarded: List<String> = emptyList(),
    /** 今日小任务：0 点后第一次打开时从池里抽 3 条（logic/Tasks.kt），领过的记在 tasksClaimed，全勤奖领过记 tasksBonus */
    val tasks: List<String> = emptyList(),
    val tasksClaimed: List<String> = emptyList(),
    val tasksBonus: Boolean = false,
    /** 今天的小任务进度：摸摸次数、玩了几局游戏、改了几次便签、喂了几次部屋宠物、完成了几次自定义提醒 */
    val pets: Int = 0,
    val games: Int = 0,
    val notes: Int = 0,
    val feeds: Int = 0,
    val customs: Int = 0,
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
    /** 成就徽章用的累计数：签到天数、摸摸次数、小游戏领到萌点的次数（摸摸和游戏从徽章上线起才开始记） */
    val checkInDays: Int = 0,
    val petCount: Int = 0,
    val gameClaims: Int = 0,
    /** 每个徽章已经领过奖的等级（0 = 还没解锁） */
    val badges: Map<String, Int> = emptyMap(),
    /** 徽章上线时用已有记录补算过了（只补一次累计签到天数） */
    val badgesSeeded: Boolean = false,
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
    /** 最近一次排好的闹钟时间（提醒体检里显示「下一个闹钟」） */
    val alarmAt: Long = 0,
    /** 最近一次排闹钟是什么时候（判断「应响未响」时，这之前改过的提醒不算漏） */
    val scheduledAt: Long = 0,
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
    /** 自定义提醒（吃药、久坐、护眼、护肤……）和它们的完成 / 跳过记录 */
    val customs: List<CustomReminder> = emptyList(),
    val customLog: List<CustomLog> = emptyList(),
)

/**
 * 自定义提醒（需求 C）。时间方式二选一：fixed 每天几个固定时刻（times）；interval 在 from–to 时段里每隔 everyMin 分钟。
 * 重复：daily 每天 / workdays 工作日 / weekdays 自选周几（1 = 周一 … 7 = 周日）。endDate 之后不再提醒。
 */
@Serializable
data class CustomReminder(
    val id: String,
    val name: String,
    /** 小图标：pill 药丸 / stretch 伸展 / eye 眼睛 / skin 护肤 / bell 铃铛（ui/Icons.kt） */
    val icon: String = "bell",
    val mode: String = "fixed",
    val times: List<String> = listOf("09:00"),
    val everyMin: Int = 60,
    val from: String = "09:00",
    val to: String = "18:00",
    val repeat: String = "daily",
    val weekdays: List<Int> = emptyList(),
    val endDate: String? = null,
    val enabled: Boolean = true,
)

/** 自定义提醒的一次完成（slot 是到点的时刻 "08:00"，首页提前打勾也记对应时刻）；skipped = 今天跳过 */
@Serializable
data class CustomLog(val id: String, val day: String, val t: Long, val slot: String = "", val skipped: Boolean = false)

// ---------------- 便签 ----------------

@Serializable
enum class BlockType { TEXT, IMAGE, AUDIO, TABLE, VIDEO }

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
