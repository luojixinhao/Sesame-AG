package fansirsqi.xposed.sesame.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import fansirsqi.xposed.sesame.R
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.LanguageUtil
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ToastUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class HtmlViewerActivity : BaseActivity() {
    var mWebView: MyWebView? = null
    var progressBar: ProgressBar? = null
    private var uri: Uri? = null
    private var canClear: Boolean? = null
    var settings: WebSettings? = null

    // 倒排索引：关键词 -> 行号列表（用于快速搜索）
    private val searchIndex = mutableMapOf<String, MutableList<Int>>()

    // 保存所有日志行（用于懒加载）
    private var allLogLines: List<String> = emptyList()
    private var currentLoadedCount = 0  // 当前已加载行数
    private var dynamicBatchSize = LOAD_MORE_LINES  // 动态批次大小（前端自适应计算）

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LanguageUtil.setLocale(this)
        setContentView(R.layout.activity_html_viewer)
        // 初始化 WebView 和进度条
        mWebView = findViewById(R.id.mwv_webview)
        progressBar = findViewById(R.id.pgb_webview)

        setupWebView()
        settings = mWebView!!.getSettings()

        // 安全设置 WebView
        try {
            if (mWebView != null) {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                    try {
                        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings!!, true)
                    } catch (e: Exception) {
                        Log.error(TAG, "设置夜间模式失败: " + e.message)
                        Log.printStackTrace(TAG, e)
                    }
                }

                settings!!.javaScriptEnabled = false
                settings!!.domStorageEnabled = false
                progressBar!!.setProgressTintList(
                    ColorStateList.valueOf(
                        ContextCompat.getColor(
                            this,
                            R.color.selection_color
                        )
                    )
                )
                mWebView!!.setBackgroundColor(ContextCompat.getColor(this, R.color.background))
            }
        } catch (e: Exception) {
            Log.error(TAG, "WebView初始化异常: " + e.message)
            Log.printStackTrace(TAG, e)
        }

        val contentView = findViewById<View>(android.R.id.content)

        ViewCompat.setOnApplyWindowInsetsListener(contentView) { _, insets ->
            val systemBarsBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom

            mWebView!!.setPadding(
                mWebView!!.getPaddingLeft(),
                mWebView!!.paddingTop,
                mWebView!!.getPaddingRight(),
                systemBarsBottom
            )

            insets
        }
    }

    /**
     * 设置 WebView 的 WebChromeClient 和进度变化监听
     */
    private fun setupWebView() {
        mWebView!!.setWebChromeClient(
            object : WebChromeClient() {
                @SuppressLint("WrongConstant")
                override fun onProgressChanged(view: WebView?, progress: Int) {
                    progressBar!!.progress = progress
                    if (progress < 100) {
                        baseSubtitle = "Loading..."
                        progressBar!!.visibility = View.VISIBLE
                    } else {
                        baseSubtitle = mWebView!!.getTitle()
                        progressBar!!.visibility = View.GONE
                    }
                }
            })
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onResume() {
        super.onResume()
        // 安全设置WebView
        try {
            val intent = getIntent() // 获取传递过来的 Intent
            if (intent != null) {
                if (mWebView != null) {
                    settings!!.setSupportZoom(true) // 支持缩放
                    settings!!.builtInZoomControls = true // 启用内置缩放机制
                    settings!!.displayZoomControls = false // 不显示缩放控件
                    settings!!.useWideViewPort = true // 启用触摸缩放
                    settings!!.loadWithOverviewMode = true //概览模式加载
                    settings!!.textZoom = 85
                    // 可选夜间模式设置
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                        try {
                            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings!!, true)
                        } catch (e: Exception) {
                            Log.error(TAG, "设置夜间模式失败: " + e.message)
                            Log.printStackTrace(TAG, e)
                        }
                    }
                }
                configureWebViewSettings(intent, settings!!)
                uri = intent.data
                if (uri != null) {
//                    mWebView.loadUrl(uri.toString());
                    /** 日志实时显示 begin */
                    settings!!.javaScriptEnabled = true
                    settings!!.domStorageEnabled = true // 可选

                    // 注册 JavaScript 接口，提供索引搜索能力
                    mWebView!!.addJavascriptInterface(SearchBridge(), "SearchBridge")

                    mWebView!!.loadUrl("file:///android_asset/log_viewer.html")
                    mWebView!!.setWebChromeClient(object : WebChromeClient() {
                        @RequiresApi(api = Build.VERSION_CODES.O)
                        override fun onProgressChanged(view: WebView?, progress: Int) {
                            progressBar!!.progress = progress
                            if (progress < 100) {
                                baseSubtitle = "Loading..."
                                progressBar!!.visibility = View.VISIBLE
                            } else {
                                baseSubtitle = mWebView!!.getTitle()
                                progressBar!!.visibility = View.GONE

                                // ★★ 页面已就绪：使用 Flow 流式加载日志 ★★
                                if (uri != null && "file".equals(uri!!.scheme, ignoreCase = true)) {
                                    val path = uri!!.path
                                    if (path != null && path.endsWith(".log")) {
                                        // 使用协程 + Flow 流式加载
                                        loadLogWithFlow(path)
                                    }
                                }
                            }
                        }
                    })
                    /** 日志实时显示 end */
                }
                canClear = intent.getBooleanExtra("canClear", false)
            }
        } catch (e: Exception) {
            Log.error(TAG, "WebView设置异常: " + e.message)
            Log.printStackTrace(TAG, e)
        }
    }

    /**
     * 配置 WebView 的设置项
     *
     * @param intent   传递的 Intent
     * @param settings WebView 的设置
     */
    private fun configureWebViewSettings(intent: Intent, settings: WebSettings) {
        if (intent.getBooleanExtra("nextLine", true)) {
            settings.textZoom = 85
            settings.useWideViewPort = false
        } else {
            settings.textZoom = 85
            settings.useWideViewPort = true
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // 创建菜单选项
        menu.add(0, 1, 1, getString(R.string.export_file))
        if (canClear == true) {  // 修复：Boolean? 需要明确比较
            menu.add(0, 2, 2, getString(R.string.clear_file))
        }
        menu.add(0, 3, 3, getString(R.string.open_with_other_browser))
        menu.add(0, 4, 4, getString(R.string.copy_the_url))
        menu.add(0, 5, 5, getString(R.string.scroll_to_top))
        menu.add(0, 6, 6, getString(R.string.scroll_to_bottom))
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 ->                 // 导出文件
                exportFile()

            2 ->                 // 清空文件
                clearFile()

            3 ->                 // 使用其他浏览器打开
                openWithBrowser()

            4 ->                 // 复制 URL 到剪贴板
                copyUrlToClipboard()

            5 ->                 // 滚动到顶部（先加载全部数据）
                mWebView!!.evaluateJavascript(
                    """
                    if (typeof loadAllAndScrollToTop === 'function') {
                        loadAllAndScrollToTop();
                    } else {
                        window.scrollTo(0, 0);
                    }
                    """.trimIndent(),
                    null
                )

            6 ->                 // 滚动到底部
                mWebView!!.scrollToBottom()
        }
        return true
    }

    /**
     * 导出当前文件
     */
    private fun exportFile() {
        try {
            if (uri != null) {
                val path = uri!!.path
                Log.runtime(TAG, "URI path: $path")
                if (path != null) {
                    val exportFile = Files.exportFile(File(path), true)
                    if (exportFile != null && exportFile.exists()) {
                        ToastUtil.showToast(getString(R.string.file_exported) + exportFile.path)
                    } else {
                        Log.runtime(TAG, "导出失败，exportFile 对象为 null 或不存在！")
                    }
                } else {
                    Log.runtime(TAG, "路径为 null！")
                }
            } else {
                Log.runtime(TAG, "URI 为 null！")
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
        }
    }

    /**
     * 清空当前文件
     */
    private fun clearFile() {
        try {
            if (uri != null) {
                val path = uri!!.path
                if (path != null) {
                    val file = File(path)
                    if (Files.clearFile(file)) {
                        ToastUtil.makeText(this, "文件已清空", Toast.LENGTH_SHORT).show()
                        mWebView!!.reload()
                    }
                }
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
        }
    }

    /**
     * 使用其他浏览器打开当前 URL
     */
    private fun openWithBrowser() {
        if (uri != null) {
            val scheme = uri!!.scheme
            if ("http".equals(scheme, ignoreCase = true) || "https".equals(scheme, ignoreCase = true)) {
                val intent = Intent(Intent.ACTION_VIEW, uri)
                startActivity(intent)
            } else if ("file".equals(scheme, ignoreCase = true)) {
                ToastUtil.makeText(this, "该文件不支持用浏览器打开", Toast.LENGTH_SHORT).show()
            } else {
                ToastUtil.makeText(this, "不支持用浏览器打开", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 复制当前 WebView 的 URL 到剪贴板
     */
    private fun copyUrlToClipboard() {
        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager?
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText(null, mWebView!!.getUrl()))
            ToastUtil.makeText(this, getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        if (mWebView is MyWebView) {
            (mWebView as MyWebView).stopWatchingIncremental()
        }
    }

    override fun onStop() {
        super.onStop()
        if (mWebView is MyWebView) {
            (mWebView as MyWebView).stopWatchingIncremental()
        }
    }

    override fun onDestroy() {
        // 先停止文件监听，再做 WebView 清理，最后再 super
        if (mWebView is MyWebView) {
            (mWebView as MyWebView).stopWatchingIncremental()
        }
        if (mWebView != null) {
            try {
                mWebView!!.loadUrl("about:blank")
                mWebView!!.stopLoading()
                // 注意：Kotlin 中 webChromeClient 和 webViewClient 不接受 null
                // destroy() 会自动清理所有资源，无需手动置空
                mWebView!!.destroy()
            } catch (_: Throwable) {
            }
        }
        super.onDestroy()
    }

    /**
     * 使用 Flow 流式加载日志文件
     * 优点：
     * 1. 首次只加载500行，极速显示
     * 2. 滚动加载更多，支持无限滚动
     * 3. 内存占用低
     * 4. 同步建立倒排索引，支持秒级搜索
     */
    private fun loadLogWithFlow(path: String) {
        lifecycleScope.launch {
            try {
                // 清空旧索引和数据
                searchIndex.clear()
                currentLoadedCount = 0

                // 统计总行数和获取所有可用行
                val (totalLines, lastLines) = withContext(Dispatchers.IO) {
                    try {
                        getLastLines(path)
                    } catch (e: CancellationException) {
                        // 协程取消，直接重新抛出
                        throw e
                    } catch (e: Exception) {
                        Log.error(TAG, "文件读取失败: ${e.message}")
                        Log.printStackTrace(TAG, e)
                        Pair(0, emptyList())
                    }
                }

                // 保存所有行供懒加载使用
                allLogLines = lastLines
                // Log.record(TAG, "📂 日志文件加载成功: 总行数=$totalLines, 可用行数=${allLogLines.size}")

                // 显示统计信息
                val header = if (totalLines > MAX_DISPLAY_LINES) {
                    val skippedLines = totalLines - MAX_DISPLAY_LINES
                    """
                        === 日志文件较大，加载最后 $MAX_DISPLAY_LINES 行 ===
                        === 总计 $totalLines 行，已跳过前 $skippedLines 行 ===
                        === ⚡ 末尾读取 + 智能懒加载技术 ===
                        === 📱 自适应加载，往上滑动加载更多 ===
                        
                    """.trimIndent()
                } else {
                    """
                        === 📄 总计 $totalLines 行日志 ===
                        === 📱 智能加载，往上滑动自动加载更多 ===
                        
                    """.trimIndent()
                }

                withContext(Dispatchers.Main) {
                    mWebView?.evaluateJavascript(
                        "setFullText(${toJsString(header)})",
                        null
                    )
                }

                // 🚀 快速初始加载：只加载最后200行（约2-3屏）
                val initialLoadCount = 200.coerceAtMost(allLogLines.size)

                val initialLines = allLogLines.takeLast(initialLoadCount)
                currentLoadedCount = initialLines.size

                // 流式加载初始日志行（分批次）
                loadLinesFlow(initialLines)
                    .collect { batch ->
                        // 在主线程更新 UI
                        withContext(Dispatchers.Main) {
                            mWebView?.evaluateJavascript(
                                "appendLines(${toJsArray(batch)})",
                                null
                            )
                        }
                    }

                // 通知前端初始加载完成（索引还未构建，传0）
                withContext(Dispatchers.Main) {
                    val hasMore = currentLoadedCount < allLogLines.size
                    mWebView?.evaluateJavascript(
                        """
                        if(typeof onInitialLoadComplete === 'function') {
                            onInitialLoadComplete(0, $currentLoadedCount, ${allLogLines.size}, $hasMore);
                        } else {
                            console.error('❌ onInitialLoadComplete 函数未定义');
                        }
                        """.trimIndent(),
                        null
                    )
                }

                // 启动增量监听
                withContext(Dispatchers.Main) {
                    mWebView?.startWatchingIncremental(path)
                }

                // 🔥 后台异步构建索引（不阻塞UI，用户可以先查看日志）
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        buildSearchIndex(allLogLines)
                        // 索引构建完成后通知前端
                        withContext(Dispatchers.Main) {
                            mWebView?.evaluateJavascript(
                                "if(typeof onIndexBuilt === 'function') onIndexBuilt(${searchIndex.size})",
                                null
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.error(TAG, "索引构建失败: ${e.message}")
                        Log.printStackTrace(TAG, e)
                    }
                }

            } catch (e: CancellationException) {
                // 协程取消是正常行为（通常发生在页面关闭时），不记录错误
                Log.record(TAG, "日志加载已取消（页面已关闭）")
                throw e  // 重新抛出，让协程框架正确处理
            } catch (e: Exception) {
                Log.error(TAG, "Flow 加载日志失败: ${e.message}")
                Log.printStackTrace(TAG, e)

                // 失败后仍启动监听
                withContext(Dispatchers.Main) {
                    mWebView?.startWatchingIncremental(path)
                }
            }
        }
    }

    /**
     * 创建流式加载的 Flow
     * 每批次加载 BATCH_SIZE 行
     */
    private fun loadLinesFlow(lines: List<String>): Flow<List<String>> = flow {
        val batches = lines.chunked(BATCH_SIZE)
        for (batch in batches) {
            emit(batch)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 构建搜索索引（倒排索引）
     * 原理：关键词 -> 行号列表
     * 支持：中文、英文、数字
     */
    private fun buildSearchIndex(lines: List<String>) {
        try {
            lines.forEachIndexed { lineIndex, line ->
                try {
                    // 提取关键词
                    val keywords = extractKeywords(line)
                    keywords.forEach { keyword ->
                        try {
                            // 🔥 使用 compute 方法，线程安全且避免重载歧义
                            searchIndex.compute(keyword) { _, existingList ->
                                val list = existingList ?: ArrayList<Int>()
                                list.add(lineIndex)
                                list
                            }
                        } catch (e: Exception) {
//                            Log.error(TAG, "添加索引失败: keyword=$keyword, lineIndex=$lineIndex, ${e.message}")
//                            Log.printStackTrace(TAG, e)
                        }
                    }
                } catch (e: Exception) {
                    Log.error(TAG, "处理第${lineIndex}行失败: line.length=${line.length}, ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.error(TAG, "索引构建异常: lines.size=${lines.size}, ${e.message}")
            Log.printStackTrace(TAG, e)
        }
    }

    /**
     * 提取关键词（简化版分词）
     * 支持中英文混合、数字
     */
    private fun extractKeywords(line: String): Set<String> {
        val keywords = mutableSetOf<String>()

        try {
            // 1. 提取英文单词（2字符以上）
            Regex("[a-zA-Z]{2,}").findAll(line).forEach {
                keywords.add(it.value.lowercase())
            }

            // 2. 提取中文词（改进：提取所有2-4字的子串，避免贪婪匹配导致索引缺失）
            Regex("[\\u4e00-\\u9fa5]+").findAll(line).forEach { match ->
                val text = match.value
                if (text.isEmpty()) return@forEach

                // 只提取2-4字的词（提高搜索精度，减少噪音）
                val maxLen = minOf(4, text.length)
                for (len in 2..maxLen) {
                    val maxStartIndex = text.length - len
                    if (maxStartIndex < 0) continue

                    for (i in 0..maxStartIndex) {
                        try {
                            val endIndex = i + len
                            if (endIndex <= text.length) {
                                keywords.add(text.substring(i, endIndex))
                            }
                        } catch (e: StringIndexOutOfBoundsException) {
                            Log.error(TAG, "substring 错误: text.length=${text.length}, i=$i, len=$len, endIndex=${i+len}")
                        }
                    }
                }
            }

            // 3. 提取数字（3位以上）
            Regex("\\d{3,}").findAll(line).forEach {
                keywords.add(it.value)
            }
        } catch (e: Exception) {
            Log.error(TAG, "提取关键词失败: line.length=${line.length}, ${e.message}")
        }

        return keywords
    }

    /**
     * JavaScript 桥接类（供前端调用） 不要删除
     */
    inner class SearchBridge {
        /**
         * 快速搜索（使用倒排索引）
         * @return JSON 数组：包含关键词的行号列表
         */
        @android.webkit.JavascriptInterface
        fun search(keyword: String): String {
            if (keyword.isBlank()) return "[]"

            val lineNumbers = searchIndex[keyword.lowercase()]
                ?: searchIndex[keyword]
                ?: emptyList()

            return lineNumbers.joinToString(prefix = "[", postfix = "]")
        }

        /**
         * 搜索并返回匹配的行内容
         * @param keyword 搜索关键词
         * @return JSON 对象：包含匹配的行内容和统计信息
         */
        @android.webkit.JavascriptInterface
        fun searchLines(keyword: String?): String {
            if (keyword.isNullOrBlank()) return """{"lines": [], "total": 0}"""

            return try {
                // 尝试使用索引快速查找
                val lineNumbers = searchIndex[keyword.lowercase()]
                    ?: searchIndex[keyword]
                    ?: emptyList()

                if (lineNumbers.isNotEmpty()) {
                    // 使用索引获取匹配的行
                    val matchedLines = lineNumbers.mapNotNull { index ->
                        allLogLines.getOrNull(index)
                    }
                    val linesJson = toJsArray(matchedLines)
                    """{"lines": $linesJson, "total": ${matchedLines.size}, "source": "index"}"""
                } else {
                    // 索引未找到，回退到全文搜索
                    val matchedLines = allLogLines.filter { it.contains(keyword, ignoreCase = false) }
                    val linesJson = toJsArray(matchedLines)
                    """{"lines": $linesJson, "total": ${matchedLines.size}, "source": "fulltext"}"""
                }
            } catch (e: Exception) {
                Log.printStackTrace(TAG, e)
                """{"lines": [], "total": 0, "error": "${e.message}"}"""
            }
        }


        /**
         * 获取索引统计信息
         * @return JSON 对象：{keywords: 关键词数量, lines: 总行数}
         */
        @android.webkit.JavascriptInterface
        fun getIndexStats(): String {
            return """{"keywords": ${searchIndex.size}, "lines": ${allLogLines.size}}"""
        }

        /**
         * 设置加载批次大小（由前端自适应计算传入）
         */
        @android.webkit.JavascriptInterface
        fun setLoadBatchSize(size: Int) {
            if (size > 0) {
                dynamicBatchSize = size
            }
        }

        /**
         * 加载更多日志行
         * @param count 请求加载的行数
         * @return JSON 数组：新加载的日志行
         */
        @android.webkit.JavascriptInterface
        fun loadMore(count: Int): String {
            try {
                // 计算还有多少行未加载
                val remainingLines = allLogLines.size - currentLoadedCount

                if (remainingLines <= 0) {
                    // 已经全部加载完
                    // Log.record(TAG, "已加载全部日志，无更多数据")
                    return "[]"
                }

                // 计算实际加载的行数（不超过剩余行数）
                val actualCount = minOf(count, remainingLines)
                val startIndex = allLogLines.size - currentLoadedCount - actualCount
                val endIndex = allLogLines.size - currentLoadedCount

                val moreLines = allLogLines.subList(startIndex, endIndex)
                currentLoadedCount += moreLines.size

                // Log.record(TAG, "加载更多: ${moreLines.size} 行，已加载: $currentLoadedCount/${allLogLines.size}")
                // 转换为 JSON 数组
                return toJsArray(moreLines)
            } catch (e: Exception) {
                Log.error(TAG, "loadMore 异常: ${e.message}")
                Log.printStackTrace(TAG, e)
                return "[]"
            }
        }

        /**
         * 检查是否还有更多日志可加载
         * @return true 如果还有更多日志
         */
        @android.webkit.JavascriptInterface
        fun hasMore(): Boolean {
            return currentLoadedCount < allLogLines.size
        }

    }


    companion object {
        private const val LOAD_MORE_LINES = 500     // 每次加载更多500行
        private const val MAX_DISPLAY_LINES = 200000 // 最多显示200000行（支持大日志文件）
        private const val BATCH_SIZE = 50           // 每批次50行（减少单次渲染压力）
        private val TAG: String = HtmlViewerActivity::class.java.getSimpleName()
        private fun toJsString(s: String?): String {
            if (s == null) return "''"
            val sb = StringBuilder(s.length + 16)
            sb.append('\'')
            for (i in 0..<s.length) {
                when (val c = s[i]) {
                    '\'' -> sb.append('\\').append('\'')  // 修复：分开添加反斜杠和单引号
                    '\\' -> sb.append("\\\\")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    '\u000C' -> sb.append("\\f")  // form feed
                    '\b' -> sb.append("\\b")
                    else -> if (c.code < 0x20) {
                        sb.append(String.format("\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
            sb.append('\'')
            return sb.toString()
        }

        /**
         * 🚀 从文件末尾往前读取，获取最后 N 行（高性能版 - 完美支持中文和Emoji）
         *
         * 原理：
         * 1. 使用逐行读取，避免UTF-8多字节字符被截断
         * 2. 优化：使用环形缓冲区只保留最后N行
         * 3. 内存占用低，速度快
         *
         * @return Pair(总行数, 最后N行的列表)
         */
        private fun getLastLines(path: String): Pair<Int, List<String>> {
            val file = File(path)
            if (!file.exists() || file.length() == 0L) {
                return Pair(0, emptyList())
            }

            // 使用环形缓冲区保存最后 MAX_DISPLAY_LINES 行
            val buffer = ArrayDeque<String>(MAX_DISPLAY_LINES)
            var totalLines = 0

            BufferedReader(
                InputStreamReader(FileInputStream(file), StandardCharsets.UTF_8)
            ).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    totalLines++

                    // 添加到缓冲区
                    buffer.addLast(line!!)

                    // 如果超过限制，移除最早的行
                    if (buffer.size > MAX_DISPLAY_LINES) {
                        buffer.removeFirst()
                    }
                }
            }

            return Pair(totalLines, buffer.toList())
        }

        /**
         * 将字符串转换为标准 JSON 字符串（双引号）
         * 用于 JSON.parse() 解析
         */
        private fun toJsonString(s: String?): String {
            if (s == null) return "\"\""
            val sb = StringBuilder(s.length + 16)
            sb.append('"')
            for (i in 0..<s.length) {
                when (val c = s[i]) {
                    '"' -> sb.append("\\\"")  // JSON 中转义双引号
                    '\\' -> sb.append("\\\\")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    '\u000C' -> sb.append("\\f")  // form feed
                    '\b' -> sb.append("\\b")
                    else -> if (c.code < 0x20) {
                        sb.append(String.format("\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
            sb.append('"')
            return sb.toString()
        }

        /**
         * 将字符串列表转换为标准 JSON 数组字符串
         * 例如：["line1", "line2"] -> '["line1","line2"]'
         * 注意：返回的是符合 JSON 标准的格式（使用双引号）
         */
        private fun toJsArray(lines: List<String>): String {
            if (lines.isEmpty()) return "[]"
            val sb = StringBuilder()
            sb.append('[')
            for (i in lines.indices) {
                if (i > 0) sb.append(',')
                sb.append(toJsonString(lines[i]))  // 使用 JSON 格式的字符串
            }
            sb.append(']')
            return sb.toString()
        }

    }
}