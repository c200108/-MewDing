package com.mengding.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 两款小游戏的规则测试：不经过界面，直接驱动游戏逻辑 */
class GamesTest {
    private fun asset(name: String) = File("src/main/assets/games/$name").readText()
    private fun adv() = AdvGame((1..3).map { asset("adv_$it.txt") }).also { it.load(1); it.update(1.7f) }
    private fun AdvGame.sim(sec: Float, each: AdvGame.() -> Unit = {}) {
        var t = 0f
        while (t < sec) { each(); update(1 / 60f); t += 1 / 60f }
    }
    private fun AdvGame.place(x: Float, bottom: Float) { p.x = x; p.y = bottom - p.h; p.vx = 0f; p.vy = 0f; camX = maxOf(0f, x - 6f) }

    @Test fun headBumpGiftGivesCoin() {
        val g = adv(); g.enemies.clear()
        g.place(14.15f, 13f)
        g.jumpPress(true); g.sim(0.8f)
        assertEquals('X', g.lv.g[9][14])
        assertEquals(1, g.coins)
    }

    @Test fun mushroomMakesBigThenBigBreaksCookie() {
        val g = adv(); g.enemies.clear()
        g.place(21.15f, 13f)
        g.jumpPress(true); g.sim(0.5f); g.jumpPress(false)
        assertEquals('X', g.lv.g[9][21])
        assertEquals(1, g.items.size)
        // 菇往右走，宠物追上去
        g.sim(4f) { right = true }
        assertTrue("吃到萌萌菇变大", g.big)
        assertEquals(1.75f, g.p.h, 0.001f)
        g.right = false
        g.sim(1f)
        g.place(18.15f, 13f)
        g.jumpPress(true); g.sim(0.6f); g.jumpPress(false)
        assertEquals("变大后顶碎饼干砖", '.', g.lv.g[9][18])
    }

    @Test fun smallPetOnlyBumpsCookie() {
        val g = adv(); g.enemies.clear()
        g.place(18.15f, 13f)
        g.jumpPress(true); g.sim(0.6f)
        assertEquals('B', g.lv.g[9][18])
    }

    @Test fun stompChestnutAndKickShell() {
        val g = adv(); g.enemies.clear()
        g.enemies += Enemy('g', 30.1f, 12.2f, 0.8f)
        g.place(30f, 9f)
        g.sim(0.4f)
        assertEquals(E_FLAT, g.enemies.firstOrNull()?.state ?: E_FLAT)
        assertTrue(g.score >= 100)
        assertEquals(3, g.lives)

        val g2 = adv(); g2.enemies.clear()
        val bug = Enemy('k', 30.1f, 12.15f, 0.85f)
        g2.enemies += bug
        g2.place(30f, 9f)
        g2.sim(0.35f)
        assertEquals("踩瓢瓢虫变成壳", E_SHELL, bug.state)
        // 清掉树桩，免得壳撞回来
        for (r in 0 until 15) for (c in 0 until g2.lv.w) if (g2.lv.g[r][c] == 'P') g2.lv.g[r][c] = '.'
        g2.place(28f, 13f)
        g2.sim(1.2f) { right = bug.state != E_SLIDE }
        assertEquals("碰一下把壳踢飞", E_SLIDE, bug.state)
        assertTrue(bug.vx > 0)
        assertEquals(AP.PLAY, g2.phase)
    }

    @Test fun touchingEnemyWhenSmallLosesLife() {
        val g = adv(); g.enemies.clear()
        g.enemies += Enemy('g', 31f, 12.2f, 0.8f)
        g.place(29f, 13f)
        g.sim(1.5f) { right = true }
        assertEquals(AP.DYING, g.phase)
        g.right = false
        g.sim(3f)
        assertEquals(2, g.lives)
    }

    @Test fun pitKills() {
        val g = adv(); g.enemies.clear()
        g.place(69.1f, 13f)
        g.sim(1f)
        assertEquals(AP.DYING, g.phase)
    }

    @Test fun flagClearsAndLoadsNextLevel() {
        val g = adv(); g.enemies.clear()
        var cleared = 0
        g.onClear = { cleared = it }
        g.place(g.lv.flagX - 1.2f, 8f)
        g.sim(1.5f) { right = true }
        assertEquals(AP.CLEAR, g.phase)
        g.right = false
        g.sim(25f)
        assertEquals(1, cleared)
        assertEquals(2, g.levelNo)
    }

    /** 每一关都要能跳过去：一个只会"往右跑、前面有坑或障碍就跳"的机器人（无敌）要能走到终点 */
    @Test fun everyLevelIsPassable() {
        for (n in 1..3) {
            val g = AdvGame((1..3).map { asset("adv_$it.txt") })
            g.load(n); g.update(1.7f)
            var cleared = false
            g.onClear = { cleared = true }
            var t = 0f
            var stuckX = 0f; var stuckT = 0f
            while (t < 120f && !cleared && g.phase != AP.DYING) {
                g.star = 100f
                g.right = true; g.run = n != 3
                val fx = (g.p.x + g.p.w).toInt()
                val bottom = (g.p.y + g.p.h).toInt()
                val wallAhead = (1..2).any { d -> g.cell(fx + d, bottom - 1).let { it in "#SB?M*XPF" } }
                val gapAhead = (1..(if (g.run) 4 else 1)).any { d -> (bottom until 15).none { r -> g.cell(fx + d, r) in "#SB?M*XPFu" } }
                if (g.p.onGround && (wallAhead || gapAhead)) g.jumpPress(true)
                if (g.p.vy > 0) g.jumpPress(false)
                g.update(1 / 60f); t += 1 / 60f
                if (g.p.x > stuckX + 0.5f) { stuckX = g.p.x; stuckT = t }
                if (t - stuckT > 6f) break
                if (g.phase == AP.CLEAR) { g.right = false }
            }
            assertTrue("第 $n 关走不通，卡在 x=${g.p.x} y=${g.p.y} phase=${g.phase}", cleared || g.phase == AP.CLEAR)
        }
    }

    // ---------------- 吃豆

    private fun pac() = PacGame(asset("pac_maze.txt").lines().filter { it.isNotEmpty() })

    @Test fun pacEatsBeansAndGhostsLeaveHouse() {
        val g = pac()
        val before = g.left
        g.update(2.1f)
        g.press(-1, 0)        // 一直按住左
        var t = 0f
        while (t < 12f && g.phase == Phase.PLAY) { g.update(1 / 60f); t += 1 / 60f }
        assertTrue("吃到了豆", g.left < before)
        assertTrue(g.score > 0)
        assertTrue("云都出窝了", g.ghosts.count { it.st == GS.ACTIVE } >= 3 || g.phase != Phase.PLAY)
    }

    @Test fun ghostsCatchIdlePet() {
        val g = pac()
        g.update(2.1f)
        g.want(0, 0)
        var t = 0f
        while (t < 90f && g.phase != Phase.OVER) {
            g.wantDx = 0; g.wantDy = 0
            g.update(1 / 60f); t += 1 / 60f
        }
        assertEquals("不动的宠物迟早被抓 3 次", Phase.OVER, g.phase)
    }

    @Test fun bellBeanLetsPetEatGhost() {
        val g = pac()
        g.update(2.1f)
        g.update(1 / 60f)
        // 把铃铛豆放在宠物脚下
        g.bean[g.pet.ty][g.pet.tx] = 2
        g.pet.set(g.pet.tx, g.pet.ty, 0, 0)
        g.update(1 / 60f)
        assertTrue(g.fright > 0)
        val red = g.ghosts[0]
        assertTrue(red.scared)
        // 让雷雷就站在宠物身上
        red.m.set(g.pet.tx, g.pet.ty, 0, 0)
        g.update(1 / 60f)
        assertEquals(GS.EATEN, red.st)
        // 小水滴回到窝里再出来
        var t = 0f
        while (t < 10f && red.st != GS.ACTIVE) { g.update(1 / 60f); t += 1 / 60f }
        assertEquals(GS.ACTIVE, red.st)
    }

    @Test fun clearingAllBeansGoesToNextLevel() {
        val g = pac()
        g.update(2.1f)
        for (y in 0 until 21) for (x in 0 until 19) g.bean[y][x] = 0
        g.bean[g.pet.ty][g.pet.tx] = 1
        g.left = 1
        g.pet.set(g.pet.tx, g.pet.ty, 0, 0)
        g.update(1 / 60f)
        assertEquals(Phase.CLEAR, g.phase)
        g.update(1.3f); g.update(1.3f)
        assertEquals(2, g.level)
        assertTrue(g.left > 100)
    }

    // ---------------- 连连看

    private fun link(rows: List<String>): LinkGame {
        val g = LinkGame(LinkDiff.EASY, listOf("cat", "dog", "pig"), kotlin.random.Random(1))
        // 用指定的小棋盘覆盖：'.' 空，字母是种类
        for (r in 0 until g.R) for (c in 0 until g.C) g.grid[r][c] = null
        rows.forEachIndexed { r, line -> line.forEachIndexed { c, ch -> if (ch != '.') g.grid[r][c] = ch.toString() } }
        return g
    }

    @Test fun linkAdjacentAndStraight() {
        val g = link(listOf("AA....", "B.B..."))
        assertTrue(g.path(Cell(0, 0), Cell(0, 1)) != null)
        assertTrue("中间空着可以直连", g.path(Cell(1, 0), Cell(1, 2)) != null)
        assertTrue("不同种类不能连", g.path(Cell(0, 0), Cell(1, 0)) == null)
    }

    @Test fun linkAroundOutside() {
        // 两只 A 被 B 围住，只能从棋盘外面绕
        val g = link(listOf("ABBBBA", "BBBBBB"))
        val p = g.path(Cell(0, 0), Cell(0, 5))
        assertTrue("可以从上面外圈绕过去", p != null)
        assertTrue(p!!.size <= 4)
    }

    @Test fun linkTooManyTurns() {
        // A 在左上被包住，B 墙挡着，三个拐弯才能到 → 连不上
        val g = link(listOf(
            ".BBBBB",
            "BA.B..",
            "BBBB.B",
            "B....A",
            "BBBBBB",
        ))
        assertTrue(g.path(Cell(1, 1), Cell(3, 5)) == null)
    }

    @Test fun linkMatchShuffleBombWin() {
        val g = LinkGame(LinkDiff.EASY, listOf("cat", "dog", "pig", "bunny", "swan", "hamster"), kotlin.random.Random(7))
        val total = g.left
        assertEquals(48, total)
        val counts = { g.grid.flatten().filterNotNull().groupingBy { it }.eachCount() }
        val before = counts()
        g.useShuffle()
        assertEquals("打乱不改变每种的数量", before, counts())
        assertTrue(g.useBomb())
        assertEquals(total - 2, g.left)
        // 一直按提示消，必须能全部消完
        var guard = 0
        while (g.state == LinkState.PLAY && guard++ < 100) {
            val (a, b) = g.findPair() ?: break
            g.tap(a); g.tap(b)
        }
        assertEquals(LinkState.WIN, g.state)
        assertEquals(0, g.left)
    }

    @Test fun linkTimeAndFreeze() {
        val g = LinkGame(LinkDiff.DOOM, listOf("cat", "dog", "pig", "bunny", "swan", "hamster", "turtle", "hedgehog",
            "clownfish", "tarantula", "chinchilla", "snake", "lizard", "guinea_pig", "sugar_glider"), kotlin.random.Random(3))
        assertEquals(96, g.left)
        g.useFreeze()
        g.update(5f)
        assertEquals(100f, g.timeLeft, 0.01f)
        g.update(10f); g.update(200f)
        assertEquals(LinkState.LOSE, g.state)
    }

    // ---------------- 0.3.7：吃豆按住才走、点一下走一格；吃完换下一关；连连看连错扣时间

    @Test fun pacTapMovesOneTileHoldKeepsMoving() {
        val g = pac()
        g.update(2.1f)
        val x0 = g.pet.tx
        // 什么都不按：不动
        repeat(60) { g.update(1 / 60f) }
        assertEquals(x0, g.pet.tx)
        assertTrue(!g.pet.moving)
        // 点一下左：只走一格就停
        g.want(-1, 0)
        repeat(120) { g.update(1 / 60f) }
        assertEquals(x0 - 1, g.pet.tx)
        assertTrue(!g.pet.moving)
        // 按住左：一直走，直到松手或碰墙
        g.press(-1, 0)
        repeat(30) { g.update(1 / 60f) }
        assertTrue(g.pet.tx <= x0 - 3)
        g.release()
        repeat(60) { g.update(1 / 60f) }
        assertTrue("松手后停在格子上", !g.pet.moving)
    }

    @Test fun pacClearingCyclesMazeSkins() {
        val g = pac()
        g.update(2.1f)
        for (lv in 1..4) {
            assertEquals(lv, g.level)
            if (g.phase == Phase.READY) g.update(2.1f)
            for (y in 0 until 21) for (x in 0 until 19) g.bean[y][x] = 0
            g.bean[g.pet.ty][g.pet.tx] = 1
            g.left = 1
            g.pet.set(g.pet.tx, g.pet.ty, 0, 0)
            g.update(1 / 60f)
            assertEquals(Phase.CLEAR, g.phase)
            g.update(1.3f); g.update(1.3f)
            assertTrue("新的一关豆子又摆满了", g.left > 100)
        }
        assertEquals(5, g.level)
    }

    @Test fun linkWrongCostsTwoPercent() {
        val g = link(listOf("AB....", "......"))
        g.timeLeft = 100f
        g.tap(Cell(0, 0)); g.tap(Cell(0, 1))     // 不是同一种：连错
        assertEquals(98f, g.timeLeft, 0.01f)
        assertTrue(g.sel == null)
        val g2 = link(listOf("ABBBBA", "BBBBBB", "BBBBBB"))
        g2.timeLeft = 50f
        // 两只 A 在第一行两头，上面外圈能绕，能连上：不扣时间
        g2.tap(Cell(0, 0)); g2.tap(Cell(0, 5))
        assertEquals(50f, g2.timeLeft, 0.01f)
    }
}
