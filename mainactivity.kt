package com.example.instabuhbot

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import kotlin.random.Random

/**
 * Полноценное Android-приложение (один файл MainActivity.kt) для автоматизации Instagram:
 * 1. Авторизация по логину и паролю (или напрямую в встроенном окне Instagram) с сохранением сессии.
 * 2. Верхняя панель управления: поля Логин/Пароль (скрываются после входа), Поле поиска («бухгалтер»),
 *    кнопки «▶ СТАРТ» и «⏹ СТОП», а также строка статуса.
 * 3. Алгоритм после нажатия «▶ СТАРТ»:
 *    - Открывает поиск Instagram и ищет введённый запрос (или берёт уже открытый список поиска на экране).
 *    - Собирает все аккаунты из списка поиска сверху вниз.
 *    - По очереди заходит в каждый аккаунт, ставит лайки с чередованием:
 *      1-й аккаунт -> 1 лайк, 2-й -> 2 лайка, 3-й -> 3 лайка, 4-й -> 1 лайк (цикл 1 -> 2 -> 3 -> 1...).
 *    - Нажимает кнопку «Отправить сообщение» (Message), печатает:
 *      «Здравствуйте можно с Вами связаться?» и отправляет.
 *    - Возвращается обратно к списку поиска и переходит к следующему аккаунту сверху вниз,
 *      пока вы не нажмёте кнопку «⏹ СТОП».
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var loginLayout: LinearLayout
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginBtn: Button
    private lateinit var toggleAuthBtn: Button
    private lateinit var searchInput: EditText
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var statusText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    // Очередь найденных аккаунтов из списка поиска (сверху вниз)
    private val accountQueue = mutableListOf<String>()
    private val processedAccounts = mutableSetOf<String>()
    private var currentAccountIndex = 0

    // Чередование количества лайков: 1 -> 2 -> 3 -> 1 -> 2 -> 3...
    private val likePattern = intArrayOf(1, 2, 3)
    private var patternStepIndex = 0

    // Текст сообщения для отправки
    private val messageToSend = "Здравствуйте можно с Вами связаться?"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Создаём интерфейс программно (не требуется редактировать activity_main.xml)
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#121212"))
        }

        // 1. Блок авторизации (Логин + Пароль + Кнопка Входа)
        loginLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(4))
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E2E"))
        }

        usernameInput = EditText(this).apply {
            hint = "Логин Instagram"
            setHintTextColor(Color.parseColor("#888899"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setSingleLine(true)
        }

        passwordInput = EditText(this).apply {
            hint = "Пароль"
            setHintTextColor(Color.parseColor("#888899"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setSingleLine(true)
        }

        loginBtn = Button(this).apply {
            text = "Войти"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setBackgroundColor(Color.parseColor("#3B82F6"))
            setTextColor(Color.WHITE)
            setOnClickListener { performAutoLogin() }
        }

        loginLayout.addView(usernameInput)
        loginLayout.addView(passwordInput)
        loginLayout.addView(loginBtn)

        // 2. Панель поиска и кнопки СТАРТ / СТОП сверху
        val controlBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E2E"))
        }

        searchInput = EditText(this).apply {
            hint = "Поиск (напр. бухгалтер)"
            setText("бухгалтер")
            setHintTextColor(Color.parseColor("#888899"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setSingleLine(true)
        }

        startBtn = Button(this).apply {
            text = "▶ СТАРТ"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setBackgroundColor(Color.parseColor("#10B981"))
            setTextColor(Color.WHITE)
            setOnClickListener { onStartClicked() }
        }

        stopBtn = Button(this).apply {
            text = "⏹ СТОП"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setBackgroundColor(Color.parseColor("#EF4444"))
            setTextColor(Color.WHITE)
            isEnabled = false
            setOnClickListener { onStopClicked() }
        }

        toggleAuthBtn = Button(this).apply {
            text = "🔑"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setBackgroundColor(Color.parseColor("#374151"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(42), ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                loginLayout.visibility =
                    if (loginLayout.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }

        controlBar.addView(searchInput)
        controlBar.addView(startBtn)
        controlBar.addView(stopBtn)
        controlBar.addView(toggleAuthBtn)

        // 3. Строка статуса (показывает текущий шаг, сколько лайков ставится и какой аккаунт в работе)
        statusText = TextView(this).apply {
            text = "Статус: Готов. Войдите в аккаунт, введите запрос в поиск и нажмите СТАРТ."
            setTextColor(Color.parseColor("#E5E7EB"))
            setBackgroundColor(Color.parseColor("#111827"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }

        // 4. Встроенный браузер WebView с Instagram
        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        setupWebView()

        rootLayout.addView(loginLayout)
        rootLayout.addView(controlBar)
        rootLayout.addView(statusText)
        rootLayout.addView(webView)
        setContentView(rootLayout)

        // Открываем Instagram
        webView.loadUrl("https://www.instagram.com/")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            // Используем Desktop/Mobile совместимый User-Agent, чтобы стабильно работали поиск и Direct
            userAgentString =
                "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.addJavascriptInterface(BotJsBridge(), "AndroidBot")
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                // Блокируем редиректы в стороннее приложение Instagram (intent://), чтобы всё работало внутри нашего приложения
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    return true
                }
                return false
            }
        }
    }

    /**
     * 1. Автоматический ввод логина и пароля в форму Instagram
     */
    private fun performAutoLogin() {
        val user = usernameInput.text.toString().trim()
        val pass = passwordInput.text.toString().trim()
        if (user.isEmpty() || pass.isEmpty()) {
            updateStatus("Введите логин и пароль выше или авторизуйтесь прямо на экране Instagram.")
            return
        }

        updateStatus("Вводим логин и пароль...")
        val escapedUser = escapeJs(user)
        val escapedPass = escapeJs(pass)

        val js = """
            (function() {
                function setNativeValue(el, value) {
                    const valueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                    valueSetter.call(el, value);
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                }
                const u = document.querySelector('input[name="username"]');
                const p = document.querySelector('input[name="password"]');
                if (u && p) {
                    u.focus();
                    setNativeValue(u, '$escapedUser');
                    p.focus();
                    setNativeValue(p, '$escapedPass');
                    setTimeout(() => {
                        const btn = document.querySelector('button[type="submit"]') ||
                                    Array.from(document.querySelectorAll('div[role="button"], button'))
                                         .find(b => /войти|log in/i.test(b.innerText));
                        if (btn) btn.click();
                    }, 600);
                    return 'OK';
                }
                return 'NO_FIELDS';
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { res ->
            if (res?.contains("OK") == true) {
                updateStatus("Выполняется вход... После входа панель логина можно свернуть кнопкой 🔑.")
                handler.postDelayed({
                    loginLayout.visibility = View.GONE
                }, 4000)
            } else {
                updateStatus("Поля входа не найдены. Если вы уже вошли, сверните панель кнопкой 🔑.")
            }
        }
    }

    /**
     * 2. Запуск бота по кнопке «▶ СТАРТ»
     */
    private fun onStartClicked() {
        if (isRunning) return
        isRunning = true
        startBtn.isEnabled = false
        stopBtn.isEnabled = true
        loginLayout.visibility = View.GONE

        val query = searchInput.text.toString().trim().ifEmpty { "бухгалтер" }
        val nextLikes = likePattern[patternStepIndex % likePattern.size]
        updateStatus("▶ Запущено! Поиск «$query»... (След. аккаунт: $nextLikes лайк(а))")

        // Запускаем открытие поиска и сбор списка аккаунтов сверху вниз
        openSearchAndCollectAccounts(query)
    }

    /**
     * 3. Остановка бота по кнопке «⏹ СТОП»
     */
    private fun onStopClicked() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        startBtn.isEnabled = true
        stopBtn.isEnabled = false
        updateStatus("⏹ Остановлено пользователем. Нажмите СТАРТ для продолжения.")
    }

    /**
     * Открывает поиск в Instagram (если ещё не открыт), вводит запрос и собирает список аккаунтов сверху вниз
     */
    private fun openSearchAndCollectAccounts(query: String) {
        if (!isRunning) return

        // Сначала проверяем, есть ли уже на экране открытый список результатов поиска
        collectSearchAccountsFromPage { existingList ->
            if (!isRunning) return@collectSearchAccountsFromPage

            val newAccounts = existingList.filter { it !in processedAccounts }
            if (newAccounts.isNotEmpty()) {
                accountQueue.clear()
                accountQueue.addAll(newAccounts)
                currentAccountIndex = 0
                updateStatus("Найдено аккаунтов в списке: ${accountQueue.size}. Начинаем обход сверху вниз...")
                processNextAccountInQueue()
            } else {
                // Переходим на вкладку поиска и вводим запрос автоматически
                updateStatus("Открываем панель поиска и вводим «$query»...")
                webView.loadUrl("https://www.instagram.com/explore/")
                handler.postDelayed({
                    if (!isRunning) return@postDelayed
                    typeSearchQueryInWebView(query)
                }, 4000)
            }
        }
    }

    private fun typeSearchQueryInWebView(query: String) {
        if (!isRunning) return
        val escapedQuery = escapeJs(query)

        val js = """
            (function() {
                function setNativeValue(el, value) {
                    const valueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                    valueSetter.call(el, value);
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                }
                // Нажимаем на иконку или поле поиска
                const searchBtn = document.querySelector('a[href*="/explore/"], svg[aria-label*="Поиск"], svg[aria-label*="Search"]');
                if (searchBtn && !document.querySelector('input[placeholder*="Поиск"], input[placeholder*="Search"], input[type="search"]')) {
                    const clickable = searchBtn.closest('a, button, div[role="button"]') || searchBtn;
                    clickable.click();
                }
                setTimeout(() => {
                    const input = document.querySelector('input[placeholder*="Поиск"], input[placeholder*="Search"], input[aria-label*="Поиск"], input[aria-label*="Search"], input[type="search"], input[type="text"]');
                    if (input) {
                        input.focus();
                        setNativeValue(input, '$escapedQuery');
                    }
                }, 800);
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)

        // Ждём 4.5 секунды, пока подгрузится список аккаунтов в поиске
        handler.postDelayed({
            if (!isRunning) return@postDelayed
            collectSearchAccountsFromPage { accounts ->
                if (!isRunning) return@collectSearchAccountsFromPage
                val filtered = accounts.filter { it !in processedAccounts }
                if (filtered.isEmpty()) {
                    updateStatus("Не удалось найти новые аккаунты. Откройте поиск в Instagram вручную, введите запрос и нажмите СТАРТ.")
                    onStopClicked()
                } else {
                    accountQueue.clear()
                    accountQueue.addAll(filtered)
                    currentAccountIndex = 0
                    updateStatus("Собрано аккаунтов сверху вниз: ${accountQueue.size}. Заходим в первый...")
                    processNextAccountInQueue()
                }
            }
        }, 4800)
    }

    /**
     * Извлекает ссылки на аккаунты из результатов поиска строго сверху вниз (по координате Y на экране)
     */
    private fun collectSearchAccountsFromPage(callback: (List<String>) -> Unit) {
        val js = """
            (function() {
                const excluded = new Set(['explore', 'reels', 'direct', 'accounts', 'p', 'reel', 'stories', 'tv', 'about', 'legal', 'privacy']);
                const anchors = Array.from(document.querySelectorAll('a[href^="/"]'));
                const items = [];
                const seen = new Set();

                for (const a of anchors) {
                    const href = a.getAttribute('href');
                    if (!href) continue;
                    const clean = href.split('?')[0].replace(/^\/+|\/+$/g, '');
                    if (!clean || clean.includes('/')) continue;
                    if (excluded.has(clean.toLowerCase())) continue;

                    const rect = a.getBoundingClientRect();
                    if (rect.width === 0 || rect.height === 0) continue;

                    const fullUrl = 'https://www.instagram.com/' + clean + '/';
                    if (!seen.has(fullUrl)) {
                        seen.add(fullUrl);
                        items.push({ url: fullUrl, top: rect.top });
                    }
                }
                // Сортируем строго сверху вниз по положению на экране
                items.sort((a, b) => a.top - b.top);
                return JSON.stringify(items.map(i => i.url));
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { rawJson ->
            val list = mutableListOf<String>()
            try {
                val unescaped = rawJson
                    ?.trim()
                    ?.removeSurrounding("\"")
                    ?.replace("\\\"", "\"")
                    ?.replace("\\\\", "\\") ?: "[]"
                val arr = JSONArray(unescaped)
                for (i in 0 until arr.length()) {
                    list.add(arr.getString(i))
                }
            } catch (_: Exception) {
            }
            callback(list)
        }
    }

    /**
     * Обрабатывает следующий по очереди аккаунт:
     * 1) Заходит в профиль
     * 2) Ставит N лайков (чередуя 1 -> 2 -> 3 -> 1...)
     * 3) Нажимает «Сообщение», пишет «Здравствуйте можно с Вами связаться?» и отправляет
     * 4) Возвращается к списку поиска и переходит к следующему
     */
    private fun processNextAccountInQueue() {
        if (!isRunning) return

        if (currentAccountIndex >= accountQueue.size) {
            // Если дошли до конца текущего списка, возвращаемся в поиск за следующей порцией
            val query = searchInput.text.toString().trim().ifEmpty { "бухгалтер" }
            updateStatus("Список пройден. Возвращаемся в поиск за новыми аккаунтами «$query»...")
            openSearchAndCollectAccounts(query)
            return
        }

        val accountUrl = accountQueue[currentAccountIndex]
        currentAccountIndex++

        // Определяем, сколько лайков ставить в этом аккаунте: 1, затем 2, затем 3, затем снова 1...
        val likesToPut = likePattern[patternStepIndex % likePattern.size]
        patternStepIndex++

        updateStatus("Аккаунт [$currentAccountIndex/${accountQueue.size}]: открываем профиль (План: $likesToPut лайк(а) + сообщение)...")
        webView.loadUrl(accountUrl)

        // Ждём загрузки профиля и собираем последние посты
        handler.postDelayed({
            if (!isRunning) return@postDelayed
            collectLatestPostsAndLike(accountUrl, likesToPut)
        }, randomDelay(4200, 5500))
    }

    /**
     * Собирает первые [likesCount] постов в профиле и по очереди ставит на них лайки
     */
    private fun collectLatestPostsAndLike(accountUrl: String, likesCount: Int) {
        if (!isRunning) return

        val js = """
            (function() {
                const links = Array.from(document.querySelectorAll('a[href*="/p/"], a[href*="/reel/"]'));
                const urls = [];
                for (const l of links) {
                    const h = l.href;
                    if (h && !urls.includes(h)) {
                        urls.push(h);
                    }
                    if (urls.length >= $likesCount) break;
                }
                return JSON.stringify(urls);
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { rawJson ->
            if (!isRunning) return@evaluateJavascript
            val postUrls = mutableListOf<String>()
            try {
                val unescaped = rawJson
                    ?.trim()
                    ?.removeSurrounding("\"")
                    ?.replace("\\\"", "\"")
                    ?.replace("\\\\", "\\") ?: "[]"
                val arr = JSONArray(unescaped)
                for (i in 0 until arr.length()) {
                    postUrls.add(arr.getString(i))
                }
            } catch (_: Exception) {
            }

            if (postUrls.isEmpty()) {
                updateStatus("Постов нет (или профиль закрыт). Переходим сразу к отправке сообщения...")
                openMessageAndSend(accountUrl)
            } else {
                likePostsSequentially(accountUrl, postUrls, 0)
            }
        }
    }

    /**
     * Открывает каждый пост из списка (1, 2 или 3) и ставит лайк
     */
    private fun likePostsSequentially(
        accountUrl: String,
        postUrls: List<String>,
        index: Int
    ) {
        if (!isRunning) return

        if (index >= postUrls.size) {
            // Все запланированные лайки (1, 2 или 3) поставлены — возвращаемся в профиль и жмём «Сообщение»
            updateStatus("Поставлено лайков: ${postUrls.size}. Возвращаемся в профиль для отправки сообщения...")
            webView.loadUrl(accountUrl)
            handler.postDelayed({
                if (!isRunning) return@postDelayed
                openMessageAndSend(accountUrl)
            }, randomDelay(3500, 4800))
            return
        }

        val postUrl = postUrls[index]
        updateStatus("Ставим лайк ${index + 1} из ${postUrls.size}...")
        webView.loadUrl(postUrl)

        handler.postDelayed({
            if (!isRunning) return@postDelayed

            val likeJs = """
                (function() {
                    const unlikeSvg = document.querySelector('svg[aria-label="Не нравится"], svg[aria-label="Unlike"]');
                    if (unlikeSvg) return 'ALREADY_LIKED';

                    const likeSvg = document.querySelector('svg[aria-label="Нравится"], svg[aria-label="Like"]');
                    if (likeSvg) {
                        const btn = likeSvg.closest('div[role="button"], button') || likeSvg;
                        btn.click();
                        return 'LIKED';
                    }
                    return 'NOT_FOUND';
                })();
            """.trimIndent()

            webView.evaluateJavascript(likeJs) {
                handler.postDelayed({
                    likePostsSequentially(accountUrl, postUrls, index + 1)
                }, randomDelay(2200, 3400))
            }
        }, randomDelay(3200, 4500))
    }

    /**
     * Нажимает кнопку «Отправить сообщение» в профиле, вводит «Здравствуйте можно с Вами связаться?» и отправляет
     */
    private fun openMessageAndSend(accountUrl: String) {
        if (!isRunning) return
        updateStatus("Нажимаем кнопку «Сообщение»...")

        val clickMsgJs = """
            (function() {
                const buttons = Array.from(document.querySelectorAll('div[role="button"], button, a'));
                const msgBtn = buttons.find(b => {
                    const t = (b.innerText || '').trim().toLowerCase();
                    return t === 'отправить сообщение' || t === 'сообщение' || t === 'message';
                });
                if (msgBtn) {
                    msgBtn.click();
                    return 'CLICKED';
                }
                return 'NO_BTN';
            })();
        """.trimIndent()

        webView.evaluateJavascript(clickMsgJs) { res ->
            if (!isRunning) return@evaluateJavascript

            if (res?.contains("NO_BTN") == true) {
                updateStatus("Кнопка сообщения недоступна. Возвращаемся к списку поиска...")
                processedAccounts.add(accountUrl)
                returnToSearchAndContinue()
                return@evaluateJavascript
            }

            // Ждём открытия окна чата Direct
            handler.postDelayed({
                if (!isRunning) return@postDelayed
                typeAndSendMessageInDirect(accountUrl)
            }, randomDelay(4200, 5500))
        }
    }

    /**
     * Печатает «Здравствуйте можно с Вами связаться?» в поле Direct и нажимает Отправить
     */
    private fun typeAndSendMessageInDirect(accountUrl: String) {
        if (!isRunning) return
        updateStatus("Печатаем: «$messageToSend»...")

        val escapedMsg = escapeJs(messageToSend)
        val sendJs = """
            (function() {
                // Закрываем всплывающее окно уведомлений («Не сейчас»), если оно появилось
                const notNow = Array.from(document.querySelectorAll('button, div[role="button"]'))
                    .find(b => /не сейчас|not now/i.test(b.innerText || ''));
                if (notNow) notNow.click();

                const box = document.querySelector('div[role="textbox"][contenteditable="true"], textarea[placeholder], div[aria-label*="Сообщение"], div[aria-label*="Message"]');
                if (!box) return 'NO_INPUT';

                box.focus();
                if (box.tagName.toLowerCase() === 'textarea') {
                    const valueSetter = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value').set;
                    valueSetter.call(box, '$escapedMsg');
                    box.dispatchEvent(new Event('input', { bubbles: true }));
                } else {
                    document.execCommand('selectAll', false, null);
                    document.execCommand('insertText', false, '$escapedMsg');
                    box.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText', data: '$escapedMsg' }));
                }

                setTimeout(() => {
                    const sendBtn = Array.from(document.querySelectorAll('div[role="button"], button'))
                        .find(b => {
                            const t = (b.innerText || '').trim().toLowerCase();
                            const label = (b.getAttribute('aria-label') || '').toLowerCase();
                            return t === 'отправить' || t === 'send' || label.includes('отправить') || label.includes('send');
                        });
                    if (sendBtn) {
                        sendBtn.click();
                    } else {
                        const enterEvent = new KeyboardEvent('keydown', {
                            bubbles: true, cancelable: true, key: 'Enter', code: 'Enter', keyCode: 13
                        });
                        box.dispatchEvent(enterEvent);
                    }
                }, 900);

                return 'SENT';
            })();
        """.trimIndent()

        webView.evaluateJavascript(sendJs) {
            if (!isRunning) return@evaluateJavascript
            processedAccounts.add(accountUrl)
            updateStatus("✉ Сообщение отправлено! Возвращаемся в список поиска к следующему аккаунту...")

            handler.postDelayed({
                returnToSearchAndContinue()
            }, randomDelay(4500, 6800))
        }
    }

    /**
     * Возвращается к списку поиска и переходит к следующему аккаунту сверху вниз
     */
    private fun returnToSearchAndContinue() {
        if (!isRunning) return
        val query = searchInput.text.toString().trim().ifEmpty { "бухгалтер" }
        updateStatus("Возвращаемся в список поиска «$query» и выбираем следующий аккаунт...")
        handler.postDelayed({
            processNextAccountInQueue()
        }, randomDelay(2500, 4000))
    }

    private fun updateStatus(msg: String) {
        runOnUiThread {
            statusText.text = msg
        }
    }

    private fun randomDelay(minMs: Long, maxMs: Long): Long {
        return Random.nextLong(minMs, maxMs)
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun escapeJs(str: String): String {
        return str.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
    }

    inner class BotJsBridge {
        @JavascriptInterface
        fun log(message: String) {
            updateStatus(message)
        }
    }
}