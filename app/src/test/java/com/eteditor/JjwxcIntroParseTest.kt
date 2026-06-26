package com.eteditor

import org.junit.Assert.assertTrue
import org.junit.Test

class JjwxcIntroParseTest {
    // 复现晋江新版“内容标签”smallreadbody 块内部嵌套 div（主角/配角图片角色卡）的结构。
    // 旧实现用非贪婪 (.*?)</div> 截取，会停在第一个嵌套 </div> 处，丢掉块尾的“一句话简介”“立意”。
    // 修复后改用平衡标签匹配取完整块，这两行应能被抓到。
    @Test
    fun parseJjwxcIntroKeepsOneLineAndThemeAfterNestedDivs() {
        val html = """
            <div id="novelintro" itemprop="description">示例正文第一行<br>示例正文第二行</div>
            <div class="smallreadbody"><span style="color:red">内容标签：</span>
                <span><a href='//www.jjwxc.net/bookbase.php?bq=33' target='_blank'>豪门世家</a></span>
                <span><a href='//www.jjwxc.net/bookbase.php?bq=58' target='_blank'>破镜重圆</a></span>
                <br/>
                <div style="width: 100%"></div>
                <span class="role_pic_frame"><span class="role_pic"><span class="role_pic_img_1"><div class="character_name ">角色甲</div></span></span></span>
                <span class="role_pic_frame"><span class="role_pic"><span class="role_pic_img_1"><div class="character_name character_name_animate">角色乙</div></span></span></span>
                <div style="clear: left;"></div>
                <br/><br/>
                <span style="color:#F98C4D">一句话简介：示例一句话简介</span>
                <br/><br/>
                <span style="color:#F98C4D">立意：示例立意内容</span>
            </div>
        """.trimIndent()

        val intro = JjwxcFetcher().parseJjwxcIntro(html)

        assertTrue("应包含内容标签，实际：$intro", intro.contains("内容标签：豪门世家 破镜重圆"))
        assertTrue("应包含一句话简介，实际：$intro", intro.contains("一句话简介：示例一句话简介"))
        assertTrue("应包含立意，实际：$intro", intro.contains("立意：示例立意内容"))
    }
}
