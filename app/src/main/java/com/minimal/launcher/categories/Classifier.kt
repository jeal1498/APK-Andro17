package com.minimal.launcher.categories

import java.text.Normalizer
import java.util.Locale

/**
 * Local, offline, deterministic app classifier.
 *
 * An app can get several automatic categories ([classifyAll]); the first one is the primary.
 *
 * Special rules (applied first, in this priority):
 *  1. Conducción  — driver apps (Uber Driver, DiDi Conductor) and the user's work apps
 *                   "Checa", "Finanzas", "Garaje". Exclusive: no other automatic category,
 *                   so "Finanzas" never lands in Finanzas personales and Uber Driver never in Transporte.
 *  2. Inteligencia artificial — AI assistants (ChatGPT, Claude, Gemini, Copilot, DeepSeek, NotebookLM, …).
 *  3. Google      — every official Google app (com.google.*, Chrome, Play Store).
 *  Apps matched by 2/3 only get a secondary category from the curated package table (e.g. Gmail ->
 *  Comunicación), never from loose keywords.
 *
 * General scoring (highest wins, one category):
 *  1. Known package names (exact or prefix match)             -> 100
 *  2. User keywords configured on a category                  -> 90
 *  3. Built-in keyword rules on package tokens                -> 60
 *  4. Built-in keyword rules on label words                   -> 50
 *  5. Android's ApplicationInfo.category declared by the app  -> 40
 *
 * Manual assignments are resolved elsewhere ([CategoryResolver]) and always take precedence.
 */
object Classifier {

    /** A category the classifier can target. */
    data class Target(val id: Long, val builtinKey: String?, val userKeywords: List<String>)

    data class AppSignals(val label: String, val packageName: String, val systemCategory: Int = CATEGORY_UNDEFINED)

    // Mirrors android.content.pm.ApplicationInfo.CATEGORY_* so this stays a pure JVM class.
    const val CATEGORY_UNDEFINED = -1
    const val CATEGORY_GAME = 0
    const val CATEGORY_AUDIO = 1
    const val CATEGORY_VIDEO = 2
    const val CATEGORY_IMAGE = 3
    const val CATEGORY_SOCIAL = 4
    const val CATEGORY_NEWS = 5
    const val CATEGORY_MAPS = 6
    const val CATEGORY_PRODUCTIVITY = 7
    const val CATEGORY_ACCESSIBILITY = 8

    private val systemCategoryMap = mapOf(
        CATEGORY_GAME to BuiltinCategory.ENTERTAINMENT,
        CATEGORY_AUDIO to BuiltinCategory.ENTERTAINMENT,
        CATEGORY_VIDEO to BuiltinCategory.ENTERTAINMENT,
        CATEGORY_IMAGE to BuiltinCategory.TOOLS,
        CATEGORY_SOCIAL to BuiltinCategory.SOCIAL,
        CATEGORY_NEWS to BuiltinCategory.EDUCATION,
        CATEGORY_MAPS to BuiltinCategory.NAVIGATION,
        CATEGORY_PRODUCTIVITY to BuiltinCategory.WORK,
        CATEGORY_ACCESSIBILITY to BuiltinCategory.TOOLS,
    )

    // ---- Special rules ---------------------------------------------------------------------

    /** Driver-side apps of ride-hailing platforms (passenger apps are com.ubercab / com.didiglobal.passenger). */
    val drivingPackages: Set<String> = setOf("com.ubercab.driver", "com.didiglobal.driver")

    /** Labels (normalised) of the user's own driving work apps, matched as whole label or first word. */
    val drivingLabels: Set<String> = setOf("checa", "garaje", "finanzas")

    /** Platform words that, combined with a driver word in the label, identify a driver app. */
    private val drivingPlatforms = setOf("uber", "didi")
    private val driverWords = setOf("driver", "conductor", "conductores")

    val aiPackages: Set<String> = setOf(
        "com.openai.chatgpt", "com.anthropic.claude", "com.google.android.apps.bard",
        "com.microsoft.copilot", "com.deepseek.chat", "com.google.android.apps.labs.language.tailwind",
        "ai.perplexity.app.android", "ai.x.grok",
    )

    /** Whole-word keywords (label or package tokens) that identify an AI app. */
    val aiKeywords: Set<String> = setOf(
        "ai", "ia", "chatgpt", "gpt", "openai", "claude", "anthropic", "gemini", "copilot", "deepseek",
        "perplexity", "notebooklm", "grok", "llm", "chatbot",
    )

    /** Official Google apps outside the com.google.* namespace. */
    private val googleExtraPackages = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary", "com.android.vending",
    )

    fun isGoogleApp(packageName: String): Boolean =
        packageName.startsWith("com.google.") || packageName in googleExtraPackages ||
            googleExtraPackages.any { packageName.startsWith("$it.") }

    fun isDrivingApp(app: AppSignals): Boolean {
        if (app.packageName in drivingPackages || drivingPackages.any { app.packageName.startsWith("$it.") }) return true
        val label = normalize(app.label).trim()
        val words = tokenize(app.label)
        if (label in drivingLabels || words.firstOrNull() in drivingLabels.minus("finanzas")) return true
        // "Finanzas" alone is ambiguous with banking: only exact label, or together with a driver word.
        if ("finanzas" in words && words.any { it in driverWords || it in drivingPlatforms }) return true
        val pkgTokens = tokenize(app.packageName)
        val all = words + pkgTokens
        return all.any { it in drivingPlatforms } && all.any { it in driverWords }
    }

    fun isAiApp(app: AppSignals): Boolean {
        if (app.packageName in aiPackages) return true
        // Compact label catches CamelCase names such as "DeepSeek" or "NotebookLM".
        val compactLabel = normalize(app.label).replace(Regex("[^a-z]+"), "")
        if (compactLabel in aiKeywords) return true
        return (tokenize(app.label) + tokenize(app.packageName)).any { it in aiKeywords }
    }

    // ---- General rules ---------------------------------------------------------------------

    /** Package name (or package prefix ending with '.') -> category. */
    val knownPackages: Map<String, BuiltinCategory> = buildMap {
        fun put(category: BuiltinCategory, vararg packages: String) = packages.forEach { put(it, category) }
        put(
            BuiltinCategory.COMMUNICATION,
            "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thoughtcrime.securesms",
            "com.facebook.orca", "com.facebook.mlite", "com.google.android.gm", "com.google.android.apps.messaging",
            "com.google.android.dialer", "com.android.dialer", "com.google.android.contacts", "com.android.contacts",
            "com.android.mms", "com.samsung.android.messaging", "com.samsung.android.dialer",
            "com.samsung.android.app.contacts", "com.discord",
            "com.viber.voip", "com.skype.raider", "jp.naver.line.android", "com.google.android.apps.tachyon",
            "com.microsoft.office.outlook", "com.yahoo.mobile.client.android.mail", "com.kakao.talk",
            "com.tencent.mm", "ch.protonmail.android", "com.android.email", "com.google.android.apps.googlevoice",
        )
        put(
            BuiltinCategory.SOCIAL,
            "com.facebook.katana", "com.facebook.lite", "com.instagram.android", "com.instagram.barcelona",
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.twitter.android", "com.snapchat.android",
            "com.reddit.frontpage", "com.pinterest", "com.linkedin.android", "com.tumblr", "org.joinmastodon.android",
            "com.bereal.ft", "com.bsky.app", "com.vkontakte.android", "com.sina.weibo",
        )
        put(
            BuiltinCategory.WORK,
            "com.google.android.apps.docs", "com.google.android.apps.docs.editors.docs",
            "com.google.android.apps.docs.editors.sheets", "com.google.android.apps.docs.editors.slides",
            "com.google.android.calendar", "com.google.android.keep", "com.google.android.apps.tasks",
            "com.microsoft.teams", "com.microsoft.office.word", "com.microsoft.office.excel",
            "com.microsoft.office.powerpoint", "com.microsoft.office.officehubrow", "com.microsoft.skydrive",
            "com.microsoft.todos", "com.Slack", "us.zoom.videomeetings", "notion.id", "com.dropbox.android",
            "com.trello", "com.asana.app", "com.todoist", "com.evernote", "com.atlassian.android.jira.core",
            "com.adobe.reader", "com.google.android.apps.meetings", "com.samsung.android.calendar",
            "com.google.android.apps.dynamite", "com.samsung.android.app.notes", "com.github.android",
            "com.termux", "ru.iiec.pydroid3", "com.google.android.apps.adwords", "com.google.android.apps.playconsole",
            "com.canva.editor",
        )
        put(
            BuiltinCategory.FINANCE,
            "com.paypal.android.p2pmobile", "com.google.android.apps.walletnfcrel", "com.google.android.apps.nbu.paisa.user",
            "com.binance.dev", "com.coinbase.android", "com.revolut.revolut", "com.nu.production",
            "com.mercadopago.wallet", "com.bbva.bbvacontigo", "com.bancomer.mbanking", "com.santander.app",
            "com.bitso.wallet", "com.squareup.cash", "com.venmo", "com.wise.android", "com.samsung.android.spay",
        )
        put(
            BuiltinCategory.ENTERTAINMENT,
            "com.google.android.youtube", "com.google.android.apps.youtube.music", "com.netflix.mediaclient",
            "com.spotify.music", "com.disney.disneyplus", "com.amazon.avod.thirdpartyclient", "com.hbo.hbonow",
            "com.wbd.stream", "tv.twitch.android.app", "com.deezer.android.app", "com.soundcloud.android",
            "com.crunchyroll.crunchyroid", "com.plexapp.android", "org.videolan.vlc", "com.google.android.videos",
            "com.google.android.apps.youtube.kids", "com.apple.android.music", "com.google.android.apps.podcasts",
            "com.pandora.android", "com.google.android.apps.youtube.unplugged", "com.mxtech.videoplayer.ad",
            "com.lemon.lvoverseas", "com.niksoftware.snapseed", "com.adobe.lrmobile", "com.picsart.studio",
            "com.vsco.cam",
            // Games (publisher prefixes).
            "com.supercell.", "com.king.", "com.rovio.", "com.mojang.", "com.ea.", "com.gameloft.", "com.miHoYo.",
            "com.HoYoverse.", "com.roblox.", "com.epicgames.", "com.activision.", "com.nintendo.", "com.playrix.",
            "com.zynga.", "com.innersloth.", "com.kiloo.", "com.tencent.ig", "com.dts.freefireth",
            "com.google.android.play.games", "com.outfit7.",
        )
        put(
            BuiltinCategory.TOOLS,
            "com.android.settings", "com.google.android.deskclock", "com.android.deskclock",
            "com.google.android.calculator", "com.android.calculator2", "com.google.android.apps.nbu.files",
            "com.android.documentsui", "com.google.android.documentsui", "com.android.chrome",
            "org.mozilla.firefox", "com.brave.browser", "com.opera.browser", "com.microsoft.emmx",
            "com.sec.android.app.sbrowser", "com.android.vending",
            "com.google.android.googlequicksearchbox", "com.google.android.apps.recorder",
            "com.google.android.apps.authenticator2", "com.google.android.apps.weather", "com.android.stk",
            "com.google.android.apps.safetyhub", "com.google.android.apps.wellbeing",
            "com.google.android.apps.adm", "com.samsung.android.app.galaxyfinder",
            "com.sec.android.app.myfiles", "com.sec.android.app.popupcalculator", "com.sec.android.app.clockpackage",
            "com.google.android.apps.searchlite", "com.rarlab.rar", "com.adobe.scan.android",
            "com.google.android.GoogleCamera", "com.android.camera", "com.android.camera2",
            "com.sec.android.app.camera", "com.android.gallery3d", "com.sec.android.gallery3d",
            "com.sec.android.app.voicenote", "com.android.soundrecorder",
        )
        put(
            BuiltinCategory.SHOPPING,
            "com.amazon.mShop.android.shopping", "com.mercadolibre", "com.alibaba.aliexpresshd", "com.zzkko",
            "com.einnovation.temu", "com.ebay.mobile", "com.walmart.android", "com.etsy.android", "com.contextlogic.wish",
            "com.grability.rappi", "com.ubercab.eats", "com.shopee.", "com.lazada.android", "com.target.ui",
            "com.ikea.kompis", "mx.com.liverpool.shoppingapp", "com.coppel.coppelapp",
        )
        put(
            BuiltinCategory.NAVIGATION,
            "com.google.android.apps.maps", "com.waze", "com.ubercab", "com.didiglobal.passenger",
            "com.cabify.rider", "me.lyft.android", "com.tranzmate", "com.citymapper.app.release", "net.osmand",
            "com.here.app.maps", "ee.mtakso.client", "com.airbnb.android", "com.booking", "com.tripadvisor.tripadvisor",
            "com.google.android.apps.mapslite", "com.google.earth", "com.google.android.projection.gearhead",
        )
        put(
            BuiltinCategory.HEALTH,
            "com.google.android.apps.fitness", "com.google.android.apps.healthdata", "com.strava",
            "com.sec.android.app.shealth", "com.nike.plusgps", "com.fitbit.FitbitMobile", "com.myfitnesspal.android",
        )
        put(
            BuiltinCategory.EDUCATION,
            "com.duolingo", "com.google.android.apps.classroom", "org.coursera.android", "com.udemy.android",
            "org.khanacademy.android", "com.quizlet.quizletandroid", "co.brainly", "org.wikipedia",
            "com.amazon.kindle", "com.google.android.apps.books", "com.babbel.mobile.android.en",
            "com.photomath.photomath", "com.google.android.apps.education.bloom", "com.deepl.mobiletranslator",
            "com.microsoft.translator", "com.google.android.apps.magazines",
        )
    }

    /** Keyword (normalised, no accents) -> category. Matched against whole tokens only. */
    val keywords: Map<String, BuiltinCategory> = buildMap {
        fun put(category: BuiltinCategory, vararg words: String) = words.forEach { put(it, category) }
        put(
            BuiltinCategory.COMMUNICATION,
            "whatsapp", "telegram", "messenger", "messages", "messaging", "mensajes", "sms", "mms", "mail", "email",
            "gmail", "correo", "dialer", "phone", "telefono", "contacts", "contactos", "signal", "chat", "viber",
            "skype", "talk", "call", "llamadas", "voip", "inbox",
        )
        put(
            BuiltinCategory.SOCIAL,
            "facebook", "instagram", "twitter", "tiktok", "snapchat", "reddit", "pinterest", "linkedin", "threads",
            "mastodon", "tumblr", "social", "dating", "tinder", "bumble", "hinge", "community",
        )
        put(
            BuiltinCategory.WORK,
            "office", "docs", "sheets", "slides", "word", "excel", "powerpoint", "drive", "dropbox", "onedrive",
            "notion", "slack", "teams", "zoom", "meet", "calendar", "calendario", "keep", "notes", "notas",
            "evernote", "trello", "asana", "todoist", "jira", "pdf", "productivity", "tasks", "tareas",
            "work", "trabajo", "business", "crm", "workspace", "agenda", "github", "gitlab", "git", "terminal",
            "termux", "python", "pydroid", "ssh", "developer", "programming", "programacion", "coding", "admin",
            "documentos", "documents", "editor",
        )
        put(
            BuiltinCategory.FINANCE,
            "bank", "banco", "banking", "bancomer", "bbva", "santander", "paypal", "wallet", "cartera", "pay",
            "finance", "finanzas", "money", "dinero", "crypto", "bitcoin", "binance", "coinbase", "revolut",
            "nubank", "mercadopago", "invest", "inversiones", "broker", "stocks", "trading", "bitso", "banamex",
            "banorte", "hsbc", "citi", "credit", "credito", "budget", "presupuesto", "cash", "tax", "impuestos",
        )
        put(
            BuiltinCategory.ENTERTAINMENT,
            "netflix", "youtube", "spotify", "music", "musica", "video", "videos", "tv", "disney", "hbo", "twitch",
            "podcast", "podcasts", "radio", "deezer", "soundcloud", "player", "crunchyroll", "plex", "vlc", "cinema",
            "movies", "peliculas", "series", "stream", "streaming", "anime", "comics", "media",
            "game", "games", "juego", "juegos", "puzzle", "arcade", "casino", "chess", "ajedrez", "solitaire",
            "sudoku", "racing", "minecraft", "roblox", "fortnite", "pubg", "clash", "candy", "craft",
            "snapseed", "lightroom", "picsart", "vsco", "capcut",
        )
        put(
            BuiltinCategory.TOOLS,
            "settings", "ajustes", "configuracion", "calculator", "calculadora", "clock", "reloj", "files",
            "archivos", "filemanager", "flashlight", "linterna", "recorder", "grabadora", "weather", "clima",
            "vpn", "authenticator", "keyboard", "teclado", "backup", "cleaner", "compass", "brujula",
            "browser", "navegador", "chrome", "firefox", "opera", "brave", "tools", "herramientas",
            "utility", "utilities", "qr", "barcode", "launcher", "security", "seguridad", "antivirus",
            "search", "store", "downloads", "descargas", "winrar", "rar", "zip", "unzip", "scanner", "escaner",
            "camera", "camara", "gallery", "galeria", "photos", "fotos", "voice", "voz",
        )
        put(
            BuiltinCategory.SHOPPING,
            "amazon", "shop", "shopping", "tienda", "compras", "mercadolibre", "aliexpress", "shein", "temu", "ebay",
            "walmart", "etsy", "wish", "rappi", "ubereats", "delivery", "food", "comida", "market", "mercado",
            "coupons", "cupones", "deals", "ofertas", "supermarket", "supermercado",
        )
        put(
            BuiltinCategory.NAVIGATION,
            "maps", "mapas", "waze", "uber", "didi", "cabify", "lyft", "transit", "transporte", "moovit", "gps",
            "navigation", "navegacion", "citymapper", "osmand", "taxi", "travel", "viajes", "flights", "vuelos",
            "booking", "hotel", "hotels", "airbnb", "trip", "metro", "bus", "parking", "indrive", "bolt",
        )
        put(
            BuiltinCategory.HEALTH,
            "fit", "fitness", "smartfit", "gym", "gimnasio", "health", "salud", "workout", "entrenamiento", "training",
            "running", "strava", "steps", "pasos", "yoga", "meditation", "meditacion", "sleep", "calories",
            "calorias", "nutrition", "nutricion", "exercise", "ejercicio", "wellness", "bienestar",
        )
        put(
            BuiltinCategory.EDUCATION,
            "duolingo", "classroom", "coursera", "udemy", "khan", "edu", "education", "educacion", "learn",
            "learning", "aprender", "school", "escuela", "dictionary", "diccionario", "books", "libros", "kindle",
            "quizlet", "brainly", "wikipedia", "language", "idiomas", "math", "matematicas", "university",
            "universidad", "course", "cursos", "study", "estudio", "epub", "ebook", "ebooks", "reader", "lector",
            "lectura", "translate", "translator", "traductor", "deepl", "news", "noticias",
        )
    }

    /** Tokens too generic to be classified from a package name. */
    private val ignoredPackageTokens = setOf("com", "org", "net", "android", "app", "apps", "mobile", "google", "co", "io", "me", "lite")

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)

    /** Splits on anything that isn't a letter and on camelCase boundaries. */
    fun tokenize(text: String): List<String> =
        text.replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .let(::normalize)
            .split(Regex("[^a-z]+"))
            .filter { it.length >= 2 }

    fun knownPackageCategory(packageName: String): BuiltinCategory? {
        knownPackages[packageName]?.let { return it }
        // Longest matching prefix (entries ending with '.' are prefixes, others match sub-packages too).
        return knownPackages.entries
            .filter { (pkg, _) ->
                if (pkg.endsWith(".")) packageName.startsWith(pkg) else packageName.startsWith("$pkg.")
            }
            .maxByOrNull { it.key.length }
            ?.value
    }

    /** Primary automatic category, or null (= unclassified). */
    fun classify(app: AppSignals, targets: List<Target>): Long? = classifyAll(app, targets).firstOrNull()

    /** Every automatic category of [app], primary first, without duplicates. Empty = unclassified. */
    fun classifyAll(app: AppSignals, targets: List<Target>): List<Long> {
        if (targets.isEmpty()) return emptyList()
        val byKey = targets.filter { it.builtinKey != null }.associateBy { it.builtinKey!! }
        val result = LinkedHashSet<Long>()

        if (isDrivingApp(app)) {
            byKey[BuiltinCategory.DRIVING.key]?.let { return listOf(it.id) }
        }
        if (isAiApp(app)) byKey[BuiltinCategory.AI.key]?.let { result += it.id }
        if (isGoogleApp(app.packageName)) byKey[BuiltinCategory.GOOGLE.key]?.let { result += it.id }

        if (result.isNotEmpty()) {
            // Secondary only from the curated package table.
            knownPackageCategory(app.packageName)?.let { cat -> byKey[cat.key]?.let { result += it.id } }
        } else {
            generalCategory(app, targets, byKey)?.let { result += it }
        }
        return result.toList()
    }

    private fun generalCategory(app: AppSignals, targets: List<Target>, byKey: Map<String, Target>): Long? {
        val scores = HashMap<Long, Int>()
        fun score(target: Target?, value: Int) {
            if (target == null) return
            if ((scores[target.id] ?: 0) < value) scores[target.id] = value
        }

        // 1. Known packages.
        knownPackageCategory(app.packageName)?.let { score(byKey[it.key], 100) }

        val packageTokens = tokenize(app.packageName).filterNot { it in ignoredPackageTokens }
        val labelTokens = tokenize(app.label)
        val normalizedLabel = normalize(app.label)

        // 2. User keywords (also allow multi-word phrases inside the label).
        for (target in targets) {
            for (raw in target.userKeywords) {
                val keyword = normalize(raw.trim())
                if (keyword.isEmpty()) continue
                val matches = keyword in packageTokens || keyword in labelTokens ||
                    (keyword.contains(' ') && normalizedLabel.contains(keyword)) ||
                    (keyword.contains('.') && app.packageName.lowercase(Locale.ROOT).startsWith(keyword))
                if (matches) score(target, 90)
            }
        }

        // 3/4. Built-in keyword rules.
        packageTokens.forEach { token -> keywords[token]?.let { score(byKey[it.key], 60) } }
        labelTokens.forEach { token -> keywords[token]?.let { score(byKey[it.key], 50) } }

        // 5. Category declared by the app in its manifest.
        systemCategoryMap[app.systemCategory]?.let { score(byKey[it.key], 40) }

        if (scores.isEmpty()) return null
        // Highest score; ties resolved by category order for determinism.
        val order = targets.mapIndexed { index, t -> t.id to index }.toMap()
        return scores.entries
            .sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { order[it.key] ?: Int.MAX_VALUE })
            .first().key
    }
}
