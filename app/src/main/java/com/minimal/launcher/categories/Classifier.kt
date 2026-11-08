package com.minimal.launcher.categories

import java.text.Normalizer
import java.util.Locale

/**
 * Local, offline, deterministic app classifier.
 *
 * Signals (highest score wins):
 *  1. Known package names (exact or prefix match)             -> 100
 *  2. User keywords configured on a category                  -> 90
 *  3. Built-in keyword rules on package tokens                -> 60
 *  4. Built-in keyword rules on label words                   -> 50
 *  5. Android's ApplicationInfo.category declared by the app  -> 40
 *
 * Returns the id of the best matching category or null (= unclassified).
 * Manual assignments are resolved elsewhere and always take precedence.
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
        CATEGORY_GAME to BuiltinCategory.GAMES,
        CATEGORY_AUDIO to BuiltinCategory.ENTERTAINMENT,
        CATEGORY_VIDEO to BuiltinCategory.ENTERTAINMENT,
        CATEGORY_IMAGE to BuiltinCategory.PHOTOGRAPHY,
        CATEGORY_SOCIAL to BuiltinCategory.SOCIAL,
        CATEGORY_NEWS to BuiltinCategory.ENTERTAINMENT,
        CATEGORY_MAPS to BuiltinCategory.NAVIGATION,
        CATEGORY_PRODUCTIVITY to BuiltinCategory.WORK,
        CATEGORY_ACCESSIBILITY to BuiltinCategory.TOOLS,
    )

    /** Package name (or package prefix ending with '.') -> category. */
    val knownPackages: Map<String, BuiltinCategory> = buildMap {
        fun put(category: BuiltinCategory, vararg packages: String) = packages.forEach { put(it, category) }
        put(
            BuiltinCategory.COMMUNICATION,
            "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thoughtcrime.securesms",
            "com.facebook.orca", "com.google.android.gm", "com.google.android.apps.messaging",
            "com.google.android.dialer", "com.android.dialer", "com.google.android.contacts", "com.android.contacts",
            "com.android.mms", "com.samsung.android.messaging", "com.samsung.android.dialer", "com.discord",
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
            "com.google.android.apps.dynamite", "com.samsung.android.app.notes",
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
            "com.pandora.android", "com.google.android.apps.magazines", "com.google.android.apps.youtube.unplugged",
        )
        put(
            BuiltinCategory.GAMES,
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
            "com.sec.android.app.sbrowser", "com.android.vending", "com.google.android.apps.translate",
            "com.google.android.googlequicksearchbox", "com.google.android.apps.recorder",
            "com.google.android.apps.authenticator2", "com.google.android.apps.weather", "com.android.stk",
            "com.google.android.apps.safetyhub", "com.google.android.apps.wellbeing", "com.google.android.apps.subscriptions.red",
            "com.google.android.apps.bard", "com.google.android.apps.adm", "com.samsung.android.app.galaxyfinder",
            "com.sec.android.app.myfiles", "com.sec.android.app.popupcalculator", "com.sec.android.app.clockpackage",
            "com.google.android.apps.googleassistant", "com.google.android.apps.searchlite", "com.anthropic.claude",
            "com.openai.chatgpt",
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
            "com.google.android.apps.mapslite", "com.google.earth",
        )
        put(
            BuiltinCategory.PHOTOGRAPHY,
            "com.google.android.GoogleCamera", "com.android.camera", "com.android.camera2",
            "com.sec.android.app.camera", "com.google.android.apps.photos", "com.android.gallery3d",
            "com.sec.android.gallery3d", "com.niksoftware.snapseed", "com.adobe.lrmobile", "com.picsart.studio",
            "com.vsco.cam", "com.google.android.apps.photosgo", "com.lemon.lvoverseas", "com.canva.editor",
        )
        put(
            BuiltinCategory.EDUCATION,
            "com.duolingo", "com.google.android.apps.classroom", "org.coursera.android", "com.udemy.android",
            "org.khanacademy.android", "com.quizlet.quizletandroid", "co.brainly", "org.wikipedia",
            "com.amazon.kindle", "com.google.android.apps.books", "com.babbel.mobile.android.en",
            "com.photomath.photomath", "com.google.android.apps.education.bloom",
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
            "evernote", "trello", "asana", "todoist", "jira", "pdf", "scanner", "productivity", "tasks", "tareas",
            "work", "trabajo", "business", "crm", "workspace", "agenda",
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
            "movies", "peliculas", "series", "stream", "streaming", "news", "noticias", "anime", "comics", "media",
        )
        put(
            BuiltinCategory.GAMES,
            "game", "games", "juego", "juegos", "puzzle", "arcade", "casino", "chess", "ajedrez", "solitaire",
            "sudoku", "racing", "minecraft", "roblox", "fortnite", "pubg", "clash", "candy", "craft",
        )
        put(
            BuiltinCategory.TOOLS,
            "settings", "ajustes", "configuracion", "calculator", "calculadora", "clock", "reloj", "files",
            "archivos", "filemanager", "flashlight", "linterna", "recorder", "grabadora", "weather", "clima",
            "vpn", "authenticator", "keyboard", "teclado", "backup", "cleaner", "compass", "brujula", "translate",
            "traductor", "browser", "navegador", "chrome", "firefox", "opera", "brave", "tools", "herramientas",
            "utility", "utilities", "qr", "barcode", "launcher", "security", "seguridad", "antivirus", "assistant",
            "asistente", "search", "store", "downloads", "descargas",
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
            "booking", "hotel", "hotels", "airbnb", "trip", "metro", "bus", "parking",
        )
        put(
            BuiltinCategory.PHOTOGRAPHY,
            "camera", "camara", "photos", "photo", "fotos", "foto", "gallery", "galeria", "snapseed", "lightroom",
            "picsart", "vsco", "selfie", "editor", "collage", "filters", "filtros", "photography", "fotografia",
        )
        put(
            BuiltinCategory.EDUCATION,
            "duolingo", "classroom", "coursera", "udemy", "khan", "edu", "education", "educacion", "learn",
            "learning", "aprender", "school", "escuela", "dictionary", "diccionario", "books", "libros", "kindle",
            "quizlet", "brainly", "wikipedia", "language", "idiomas", "math", "matematicas", "university",
            "universidad", "course", "cursos", "study", "estudio",
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

    fun classify(app: AppSignals, targets: List<Target>): Long? {
        if (targets.isEmpty()) return null
        val byKey = targets.filter { it.builtinKey != null }.associateBy { it.builtinKey!! }
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
