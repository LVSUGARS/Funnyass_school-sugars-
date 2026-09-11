package com.funnyass.test.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本比较逻辑的回归测试。
 * 更新检查的准确性取决于它，所以单独覆盖。
 */
class UpdateCheckerTest {

    @Test
    fun `去掉 v 前缀与修饰后缀`() {
        assertEquals("1.0.0", UpdateChecker.normalizeVersion("v1.0.0"))
        assertEquals("1.0.0", UpdateChecker.normalizeVersion("V1.0.0"))
        assertEquals("1.0.0", UpdateChecker.normalizeVersion(" 1.0.0 "))
        assertEquals("1.0.0", UpdateChecker.normalizeVersion("v1.0.0-release"))
        assertEquals("1.0.0", UpdateChecker.normalizeVersion("v1.0.0+build5"))
    }

    @Test
    fun `远端更高时返回正数`() {
        assertTrue(UpdateChecker.compareVersion("1.0.0", "0.1965") > 0)
        assertTrue(UpdateChecker.compareVersion("1.1.0", "1.0.9") > 0)
        assertTrue(UpdateChecker.compareVersion("2.0", "1.99.99") > 0)
    }

    @Test
    fun `相同版本返回零`() {
        assertEquals(0, UpdateChecker.compareVersion("1.0.0", "1.0.0"))
        // 段数不同时缺位按 0 处理
        assertEquals(0, UpdateChecker.compareVersion("1.0", "1.0.0"))
        assertEquals(0, UpdateChecker.compareVersion("1", "1.0.0"))
    }

    @Test
    fun `本地更高时返回负数`() {
        assertTrue(UpdateChecker.compareVersion("0.1965", "1.0.0") < 0)
        assertTrue(UpdateChecker.compareVersion("1.0.0", "1.1.0") < 0)
    }

    @Test
    fun `非数字段按零处理且不抛异常`() {
        assertEquals(0, UpdateChecker.compareVersion("abc", "0.0.0"))
        assertEquals(0, UpdateChecker.compareVersion("", ""))
        assertTrue(UpdateChecker.compareVersion("1.x.0", "1.0.0") == 0)
    }
}
