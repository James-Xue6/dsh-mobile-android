package com.dsh.mobile.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * [V3.74 · 用户确认口径] **极简 Markdown 渲染** —— 不引第三方库（本工程依赖是手工 jar，加库成本高），
 * 自己写最小实现，覆盖用户点名的子集：
 *
 * **标题（#~###）/ 加粗（`**x**`）/ 行内代码（`` `x` ``）/ 无序列表 / 有序列表 / 表格 / 引用（>）/ 代码块（```）**
 * 另外顺带支持 `---` 分隔线与斜体 `*x*`。
 *
 * <p>为什么要它：PC 端把 Markdown 排版成标题/表格/列表，而手机端一直**原样显示字符**
 * （表格显示成 `| 节 | 内容 |`、加粗显示成 `**x**`）⇒ 用户说「和 PC 端对不上、不如 PC 端规整」。
 */
private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Para(val text: String) : MdBlock
    data class Bullet(val items: List<String>) : MdBlock
    data class Ordered(val items: List<String>) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val lines: List<String>) : MdBlock
    data class Table(val head: List<String>, val rows: List<List<String>>) : MdBlock
    data object Rule : MdBlock
}

private val BULLET = Regex("^\\s*[-*+]\\s+")
private val ORDERED = Regex("^\\s*\\d+[.)]\\s+")

private fun splitRow(l: String): List<String> =
    l.trim().trim('|').split('|').map { it.trim() }

/** 行级解析（够用就行，不做嵌套列表/HTML）。 */
private fun parseMarkdown(src: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val lines = src.replace("\r\n", "\n").split('\n')
    val para = mutableListOf<String>()
    fun flush() {
        if (para.isNotEmpty()) { out.add(MdBlock.Para(para.joinToString("\n"))); para.clear() }
    }
    var i = 0
    while (i < lines.size) {
        val line = lines[i].trimEnd()
        val t = line.trimStart()
        when {
            t.startsWith("```") -> {
                flush(); i++
                val body = mutableListOf<String>()
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) { body.add(lines[i]); i++ }
                if (i < lines.size) i++
                out.add(MdBlock.Code(body))
            }
            t.startsWith("#") -> {
                flush()
                val lvl = t.takeWhile { it == '#' }.length.coerceIn(1, 3)
                out.add(MdBlock.Heading(lvl, t.drop(lvl).trim())); i++
            }
            t.startsWith(">") -> {
                flush()
                val body = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    body.add(lines[i].trimStart().removePrefix(">").trim()); i++
                }
                out.add(MdBlock.Quote(body.joinToString("\n")))
            }
            t.startsWith("|") && i + 1 < lines.size && lines[i + 1].trimStart().startsWith("|") -> {
                flush()
                val head = splitRow(line); i++
                if (i < lines.size && lines[i].contains("---")) i++
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trimStart().startsWith("|")) { rows.add(splitRow(lines[i])); i++ }
                out.add(MdBlock.Table(head, rows))
            }
            BULLET.containsMatchIn(line) -> {
                flush()
                val items = mutableListOf<String>()
                while (i < lines.size && BULLET.containsMatchIn(lines[i])) {
                    items.add(lines[i].replaceFirst(BULLET, "")); i++
                }
                out.add(MdBlock.Bullet(items))
            }
            ORDERED.containsMatchIn(line) -> {
                flush()
                val items = mutableListOf<String>()
                while (i < lines.size && ORDERED.containsMatchIn(lines[i])) {
                    items.add(lines[i].replaceFirst(ORDERED, "")); i++
                }
                out.add(MdBlock.Ordered(items))
            }
            t in setOf("---", "***", "___") -> { flush(); out.add(MdBlock.Rule); i++ }
            t.isEmpty() -> { flush(); i++ }
            else -> { para.add(line); i++ }
        }
    }
    flush()
    return out
}

/** 行内样式：`code` / **bold** / *italic* ⇒ AnnotatedString。 */
private fun inlineMarkdown(
    src: String,
    ink: Color,
    codeFg: Color,
    codeBg: Color,
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < src.length) {
        when {
            src.startsWith("[", i) -> {
                // [V3.89] `[名字](路径)`：正文里只显示**名字**（品牌色 + 下划线），路径由下面的胶囊承接
                val b = src.indexOf(']', i + 1)
                if (b > 0 && b + 1 < src.length && src[b + 1] == '(') {
                    val c = src.indexOf(')', b + 2)
                    if (c > b) {
                        withStyle(SpanStyle(color = codeFg, textDecoration = TextDecoration.Underline)) {
                            append(src.substring(i + 1, b))
                        }
                        i = c + 1
                    } else { append(src[i]); i++ }
                } else { append(src[i]); i++ }
            }
            src.startsWith("`", i) -> {
                val e = src.indexOf('`', i + 1)
                if (e > i) {
                    // [2026-10-20 · 用户「一堆白字看不清」] 行内代码原来用 `codeFg`（= brand 紫），
                    // 压在 SELECT_BG 这种很浅的底上几乎看不见。代码用**正文墨色**（深浅主题都够对比），
                    // 链接仍用 brand（那是"可点"的语义，两者不该共用同一个颜色）。
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = ink, background = codeBg)) {
                        append(src.substring(i + 1, e))
                    }
                    i = e + 1
                } else { append(src[i]); i++ }
            }
            src.startsWith("**", i) -> {
                val e = src.indexOf("**", i + 2)
                if (e > i) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ink)) {
                        append(src.substring(i + 2, e))
                    }
                    i = e + 2
                } else { append(src[i]); i++ }
            }
            src.startsWith("*", i) -> {
                val e = src.indexOf('*', i + 1)
                if (e > i) {
                    withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                        append(src.substring(i + 1, e))
                    }
                    i = e + 1
                } else { append(src[i]); i++ }
            }
            else -> { append(src[i]); i++ }
        }
    }
}

/**
 * 渲染入口：与普通 `Text` 同义，但会把 Markdown 排版出来。
 * @param codeBg 行内代码/代码块的底色（调用处按明暗主题给）。
 */
/** 取一个块的纯文本（给"链接胶囊"扫描用）。 */
private fun mdRaw(b: MdBlock): String = when (b) {
    is MdBlock.Heading -> b.text
    is MdBlock.Para -> b.text
    is MdBlock.Bullet -> b.items.joinToString("\n")
    is MdBlock.Ordered -> b.items.joinToString("\n")
    is MdBlock.Quote -> b.text
    is MdBlock.Code -> b.lines.joinToString("\n")
    is MdBlock.Table -> (b.head + b.rows.flatten()).joinToString("\n")
    MdBlock.Rule -> ""
}

/**
 * [V3.89 · 用户「生成的报告 `GlassUI_Report_V3.86.md` 在聊天对话里面**还是无法直接点击**」]
 *
 * 把这一块里的 `[名字](路径)` 提取出来，渲染成一排**可点的胶囊**：
 * 点一下 ⇒ `HostHolder.host.openPath(url, label)`（宿主按相对路径打开预览；绝对路径也能兜底）。
 */
@Composable
private fun MarkdownLinkChips(raw: String, brand: Color) {
    if (raw.isEmpty() || !raw.contains("](")) return
    val links = remember(raw) {
        val out = mutableListOf<Pair<String, String>>()
        var i = 0
        while (i < raw.length) {
            val a = raw.indexOf('[', i)
            if (a < 0) break
            val b = raw.indexOf(']', a + 1)
            if (b < 0 || b + 1 >= raw.length || raw[b + 1] != '(') { i = a + 1; continue }
            val c = raw.indexOf(')', b + 2)
            if (c < 0) break
            val label = raw.substring(a + 1, b).trim()
            val url = raw.substring(b + 2, c).trim()
            // 只把"文件/路径"类链接做成胶囊（http 链接交给正文，不乱开浏览器）
            if (label.isNotEmpty() && url.isNotEmpty() && !url.startsWith("http")) out.add(label to url)
            i = c + 1
        }
        out
    }
    if (links.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        links.take(3).forEach { (label, url) ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.22f))
                    .border(0.5.dp, brand.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
                    .glassTap(corner = 999.dp) { HostHolder.host?.openPath(url, label) },
            ) {
                Text("打开 $label", color = brand, fontSize = com.dsh.mobile.ui.Ui.S_CAP1.sp,
                    maxLines = 1, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
            Spacer(Modifier.width(6.dp))
        }
    }
}

@Composable
fun MarkdownText(
    text: String,
    ink: Color,
    sub: Color,
    brand: Color,
    codeBg: Color,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = com.dsh.mobile.ui.Ui.S_BODY.sp,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
) {
    val blocks = remember(text) { parseMarkdown(text) }
    Column(modifier) {
        blocks.forEachIndexed { idx, b ->
            if (idx > 0) Spacer(Modifier.height(8.dp))
            when (b) {
                is MdBlock.Heading -> Text(
                    inlineMarkdown(b.text, ink, brand, codeBg),
                    color = ink,
                    fontSize = when (b.level) {
                        1 -> com.dsh.mobile.ui.Ui.S_HEAD.sp
                        2 -> com.dsh.mobile.ui.Ui.S_TITLE3.sp
                        else -> fontSize
                    },
                    fontWeight = FontWeight.Bold,
                    lineHeight = lineHeight,
                )
                is MdBlock.Para -> Text(
                    inlineMarkdown(b.text, ink, brand, codeBg),
                    color = ink, fontSize = fontSize, lineHeight = lineHeight,
                )
                is MdBlock.Bullet -> Column {
                    b.items.forEach { Text(
                        inlineMarkdown("• " + it, ink, brand, codeBg),
                        color = ink, fontSize = fontSize, lineHeight = lineHeight,
                        modifier = Modifier.padding(start = 2.dp, top = 3.dp),
                    ) }
                }
                is MdBlock.Ordered -> Column {
                    b.items.forEachIndexed { k, it -> Text(
                        inlineMarkdown("${k + 1}. " + it, ink, brand, codeBg),
                        color = ink, fontSize = fontSize, lineHeight = lineHeight,
                        modifier = Modifier.padding(start = 2.dp, top = 3.dp),
                    ) }
                }
                is MdBlock.Quote -> Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    Box(Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(brand))
                    Spacer(Modifier.width(8.dp))
                    Text(inlineMarkdown(b.text, sub, brand, codeBg), color = sub,
                        fontSize = fontSize, lineHeight = lineHeight)
                }
                is MdBlock.Code -> Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(codeBg)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(b.lines.joinToString("\n"), color = ink,
                        fontSize = com.dsh.mobile.ui.Ui.S_FOOT.sp, fontFamily = FontFamily.Monospace)
                }
                // [V3.76 · 用户「Markdown 里的**结构部分排版**建议优化下，根据手机尺寸优化，**现在显示太长了**」]
                //   ⚠️ 原来按列**等分宽度** ⇒ 手机上每格很窄、疯狂折行 ⇒ 又长又难读。
                //   ⇒ 两列表格渲染成**键值行**（左 34% 标签 / 右 66% 值，各自换行但行高紧凑）；
                //     三列及以上渲染成**逐行紧凑文本**（用 ` · ` 分隔，表头单独一行小字当标题）。
                is MdBlock.Table -> Column(Modifier.fillMaxWidth()) {
                    if (b.rows.isNotEmpty() && b.rows[0].size <= 2) {
                        b.rows.forEach { r ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(
                                    inlineMarkdown(r.getOrElse(0) { "" }, ink, brand, codeBg),
                                    color = sub, fontSize = com.dsh.mobile.ui.Ui.S_CAP1.sp,
                                    modifier = Modifier.weight(0.34f),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    inlineMarkdown(r.getOrElse(1) { "" }, ink, brand, codeBg),
                                    color = ink, fontSize = com.dsh.mobile.ui.Ui.S_FOOT.sp,
                                    modifier = Modifier.weight(0.66f),
                                )
                            }
                            Box(Modifier.fillMaxWidth().height(0.5.dp).background(codeBg))
                        }
                    } else {
                        if (b.head.isNotEmpty()) {
                            Text(b.head.joinToString(" · "), color = sub,
                                fontSize = com.dsh.mobile.ui.Ui.S_CAP1.sp,
                                modifier = Modifier.padding(bottom = 3.dp))
                            Box(Modifier.fillMaxWidth().height(0.5.dp).background(codeBg))
                        }
                        b.rows.forEach { r ->
                            Text(
                                inlineMarkdown(r.joinToString("  ·  "), ink, brand, codeBg),
                                color = ink, fontSize = com.dsh.mobile.ui.Ui.S_FOOT.sp,
                                modifier = Modifier.padding(vertical = 3.dp),
                            )
                        }
                    }
                }
                MdBlock.Rule -> Box(
                    Modifier.fillMaxWidth().height(0.5.dp).background(codeBg),
                )
            }
            // [V3.89 · 用户「生成的报告 GlassUI_Report_V3.86.md 在聊天对话里面**还是无法直接点击**」]
            //   这一块里若有 `[名字](路径)` ⇒ 在下面补一排**可点的胶囊**（消息里的文件名终于点得动）。
            MarkdownLinkChips(mdRaw(b), brand)
        }
    }
}

