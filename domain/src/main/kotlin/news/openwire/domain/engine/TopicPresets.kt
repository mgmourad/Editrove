// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import java.text.Normalizer

/**
 * Curated topics with ready-made keywords, offered as the reader types a topic name
 * ("Middle East" → Iran, Israel, Gaza, Lebanon, Syria, Saudi Arabia, …). Works offline;
 * [KeywordMiner] covers anything not in the list.
 */
object TopicPresets {

    data class Preset(val name: String, val keywords: List<String>, val aliases: List<String> = emptyList()) {
        val keywordText: String get() = keywords.joinToString(", ")
    }

    private fun p(name: String, keywords: String, vararg aliases: String) =
        Preset(name, keywords.split(',').map { it.trim() }.filter { it.isNotEmpty() }, aliases.toList())

    val ALL: List<Preset> = listOf(
        // World regions & conflicts
        p("Middle East", "Middle East, Iran, Israel, Gaza, Lebanon, Syria, Saudi Arabia, Yemen, Iraq, Jordan, Egypt, Gulf, Houthis, Hezbollah", "mideast", "mena", "gulf"),
        p("Israel & Gaza", "Israel, Gaza, Hamas, West Bank, IDF, Netanyahu, ceasefire, hostages", "palestine", "israel palestine"),
        p("Iran", "Iran, Tehran, Khamenei, IRGC, Iranian nuclear, Strait of Hormuz"),
        p("Ukraine & Russia", "Ukraine, Russia, Zelensky, Putin, Kyiv, Kremlin, Donbas, Crimea", "russia ukraine war", "ukraine war"),
        p("China", "China, Beijing, Xi Jinping, Chinese economy, Taiwan, South China Sea, CCP"),
        p("Taiwan", "Taiwan, Taipei, Taiwan Strait, Lai Ching-te, TSMC"),
        p("Europe", "European Union, EU, Brussels, European Commission, Germany, France, Italy, Spain, NATO", "eu"),
        p("United Kingdom", "UK, Britain, Starmer, Westminster, Downing Street, London, Parliament", "britain", "uk"),
        p("Africa", "Africa, Nigeria, Kenya, South Africa, Ethiopia, Sudan, Sahel, African Union"),
        p("Latin America", "Latin America, Mexico, Brazil, Argentina, Colombia, Venezuela, Chile, Peru", "south america", "latam"),
        p("Mexico", "Mexico, Sheinbaum, Mexico City, cartel, US-Mexico border"),
        p("Canada", "Canada, Ottawa, Carney, Canadian, Toronto, Quebec, Alberta"),
        p("India", "India, Modi, New Delhi, Indian economy, Mumbai, BJP"),
        p("Japan & Korea", "Japan, Tokyo, South Korea, Seoul, North Korea, Pyongyang, Kim Jong Un", "korea", "japan"),
        p("Southeast Asia", "ASEAN, Vietnam, Philippines, Indonesia, Thailand, Malaysia, Singapore, Myanmar"),
        p("Australia & New Zealand", "Australia, Canberra, Albanese, New Zealand, Wellington, Sydney", "australia"),
        p("Egypt", "Egypt, Cairo, Sisi, Suez Canal, Egyptian economy"),
        p("Saudi Arabia & the Gulf", "Saudi Arabia, MBS, UAE, Qatar, Riyadh, Abu Dhabi, Dubai, OPEC", "gulf states"),
        p("Turkey", "Turkey, Türkiye, Erdogan, Ankara, Istanbul"),

        // US politics & policy
        p("White House", "White House, Trump administration, executive order, Oval Office, press secretary", "trump administration"),
        p("Congress", "Congress, Senate, House of Representatives, Speaker, filibuster, bill, lawmakers"),
        p("Supreme Court", "Supreme Court, SCOTUS, justices, ruling, oral arguments, Chief Justice Roberts", "scotus"),
        p("Elections", "election, polls, primary, campaign, midterms, voters, ballot"),
        p("Immigration", "immigration, border, asylum, deportation, ICE, migrants, visas"),
        p("National Security", "Pentagon, national security, military, defense, NATO, intelligence, CIA"),
        p("Trade & Tariffs", "tariffs, trade war, trade deal, imports, exports, WTO, customs", "tariffs", "trade"),

        // Business & economy
        p("Markets & Macro", "stock market, Wall Street, S&P 500, Dow, Nasdaq, Treasury yields, bonds", "markets", "stocks"),
        p("The Fed & Interest Rates", "Federal Reserve, interest rates, Powell, rate cut, inflation, FOMC", "fed", "interest rates"),
        p("Jobs & Economy", "jobs report, unemployment, GDP, recession, consumer spending, economy"),
        p("Real Estate & Housing", "housing market, mortgage rates, home prices, real estate, rent"),
        p("Startups & VC", "startup, venture capital, funding round, IPO, unicorn, Y Combinator", "venture capital", "ipo"),
        p("Oil & Energy Markets", "oil prices, OPEC, crude, natural gas, LNG, energy prices", "oil"),
        p("Retail & Consumer", "retail, consumer, Walmart, Amazon, Target, holiday shopping"),
        p("Banking & Finance", "banks, JPMorgan, Goldman Sachs, banking, credit, lending"),
        p("Crypto & Web3", "cryptocurrency, bitcoin, ethereum, crypto, stablecoin, Coinbase, blockchain", "bitcoin", "crypto"),

        // Technology
        p("AI & Systems", "artificial intelligence, OpenAI, Anthropic, Nvidia, LLM, generative AI, Gemini, ChatGPT", "ai", "artificial intelligence"),
        p("Big Tech", "Apple, Google, Microsoft, Amazon, Meta, antitrust, Big Tech"),
        p("Semiconductors", "chips, semiconductors, Nvidia, TSMC, Intel, AMD, ASML, chipmaking", "chips"),
        p("Cybersecurity", "cybersecurity, hack, ransomware, data breach, cyberattack, vulnerability", "security", "hacking"),
        p("Smartphones & Gadgets", "iPhone, Android, Samsung Galaxy, Pixel, smartphone, foldable, gadgets", "phones"),
        p("Apple", "Apple, iPhone, iOS, Mac, Tim Cook, Apple Vision"),
        p("Electric Vehicles", "electric vehicles, EV, Tesla, Rivian, BYD, charging, batteries", "ev", "evs"),
        p("Social Media", "social media, TikTok, Instagram, X, Threads, YouTube, content moderation"),
        p("Gaming & Esports", "video games, gaming, PlayStation, Xbox, Nintendo, esports, Steam", "games", "video games"),

        // Science & health
        p("Spaceflight", "SpaceX, NASA, rocket launch, Starship, Artemis, Blue Origin, ISS, satellite", "space", "nasa"),
        p("Climate & Environment", "climate change, global warming, emissions, COP, wildfires, extreme weather, environment", "climate"),
        p("Clean Energy", "renewable energy, solar, wind power, nuclear power, batteries, hydrogen, clean energy"),
        p("Health & Medicine", "health, medicine, FDA, vaccine, cancer, drug, clinical trial, hospitals", "health", "medicine"),
        p("Public Health", "CDC, WHO, outbreak, pandemic, measles, flu, bird flu, public health"),
        p("Mental Health", "mental health, depression, anxiety, therapy, wellbeing"),
        p("Weight-Loss Drugs", "Ozempic, Wegovy, GLP-1, Mounjaro, Zepbound, obesity drugs", "glp-1", "ozempic"),
        p("Science Discoveries", "scientists, study, discovery, research, physics, biology, archaeology", "science"),

        // Sports
        p("NFL", "NFL, football, quarterback, touchdown, Super Bowl, playoffs"),
        p("NBA", "NBA, basketball, playoffs, NBA Finals, LeBron"),
        p("MLB", "MLB, baseball, World Series, pitcher, home run"),
        p("NHL", "NHL, hockey, Stanley Cup"),
        p("Soccer", "soccer, football, Premier League, Champions League, La Liga, MLS, World Cup", "football", "premier league"),
        p("World Cup 2026", "World Cup 2026, FIFA, USMNT, World Cup qualifiers", "world cup"),
        p("Formula 1", "Formula 1, F1, Grand Prix, Verstappen, Hamilton, McLaren, Ferrari", "f1"),
        p("Tennis", "tennis, ATP, WTA, Wimbledon, US Open, Grand Slam"),
        p("Golf", "golf, PGA Tour, LIV Golf, Masters, Ryder Cup"),
        p("College Football", "college football, NCAA, CFP, Heisman, SEC, Big Ten"),
        p("Olympics", "Olympics, Olympic Games, IOC, LA 2028, Team USA"),

        // Culture & life
        p("Movies & TV", "movies, box office, streaming, Netflix, HBO, Disney, film, TV series", "film", "tv"),
        p("Music", "music, album, tour, Grammys, Billboard, concert"),
        p("Books", "books, novel, authors, bestseller, publishing, Booker Prize"),
        p("Food & Dining", "restaurants, food, recipes, chefs, dining, Michelin"),
        p("Travel", "travel, airlines, flights, airports, tourism, hotels", "airlines"),
        p("Overlanding & 4x4", "overlanding, off-road, 4x4, overland, Jeep, Land Cruiser, camping gear", "off-road", "4x4"),
        p("Cars", "cars, automakers, Toyota, Ford, GM, auto industry, new models", "autos"),
        p("Fashion", "fashion, Paris Fashion Week, designers, luxury, LVMH"),
        p("Personal Finance", "personal finance, savings, retirement, 401(k), credit cards, taxes"),
        p("Education", "education, schools, universities, students, teachers, college admissions"),
        p("Religion", "religion, Pope, Vatican, church, faith"),
    )

    private fun fold(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(MARKS, "").lowercase().replace(NON_ALNUM, " ").trim()

    /** Presets matching what's typed so far, best first (prefix > word prefix > contains > alias). */
    fun suggest(query: String, limit: Int = 6): List<Preset> {
        val q = fold(query)
        if (q.length < 2) return emptyList()
        return ALL.mapNotNull { pr ->
            val n = fold(pr.name)
            val aliases = pr.aliases.map(::fold)
            val score = when {
                n == q || aliases.any { it == q } -> 0
                n.startsWith(q) -> 1
                n.split(' ').any { it.startsWith(q) } -> 2
                aliases.any { it.startsWith(q) } -> 3
                n.contains(q) || aliases.any { it.contains(q) } -> 4
                pr.keywords.any { fold(it).startsWith(q) } -> 5
                else -> null
            }
            score?.let { pr to it }
        }.sortedWith(compareBy({ it.second }, { it.first.name.length })).take(limit).map { it.first }
    }

    /** A handful of popular presets for the suggestion chips. */
    val POPULAR: List<Preset> get() = listOf("Middle East", "AI & Systems", "Markets & Macro", "Spaceflight", "Climate & Environment", "Health & Medicine", "Formula 1", "Crypto & Web3")
        .mapNotNull { n -> ALL.firstOrNull { it.name == n } }
}

/**
 * Suggests keywords for a topic that isn't in [TopicPresets], from live headlines about
 * it: the names and phrases that keep coming up (people, places, organisations).
 */
object KeywordMiner {
    private val STOP = setOf(
        "the", "a", "an", "and", "or", "of", "to", "in", "on", "for", "with", "at", "by", "from", "as", "is", "are", "was", "were",
        "be", "it", "its", "this", "that", "after", "over", "new", "says", "say", "said", "how", "why", "what", "who", "will", "could",
        "would", "can", "may", "more", "than", "into", "about", "amid", "up", "out", "not", "but", "his", "her", "their", "our", "your",
        "news", "live", "updates", "update", "watch", "video", "report", "reports", "latest", "today", "week", "year", "first", "just",
        "here", "there", "inside", "opinion", "analysis", "exclusive", "breaking", "we", "you", "he", "she", "they", "i", "us", "has", "have",
    )

    /** Up to [limit] keywords, most frequent first, excluding the topic's own words. */
    fun fromHeadlines(topic: String, titles: List<String>, limit: Int = 10): List<String> {
        val own = topic.lowercase().split(WORD_SPLIT).filter { it.isNotEmpty() }.toSet()
        val counts = LinkedHashMap<String, Int>()
        fun bump(k: String) { counts[k] = (counts[k] ?: 0) + 1 }
        for (title in titles) {
            val clean = title.substringBeforeLast(" - ").trim()
            val words = clean.split(TOKEN_SPLIT).map { it.trim('\'', '’', '.', '-') }.filter { it.isNotEmpty() }
            val seen = HashSet<String>()
            var i = 0
            while (i < words.size) {
                // Runs of capitalised words → proper names ("Saudi Arabia", "Jensen Huang").
                if (isName(words[i], i == 0)) {
                    var j = i
                    while (j + 1 < words.size && isName(words[j + 1], false) && j - i < 2) j++
                    val phrase = words.subList(i, j + 1).joinToString(" ")
                    val key = phrase.lowercase()
                    if (key !in STOP && key.split(' ').none { it in own } && phrase.length > 2 && seen.add(key)) bump(phrase)
                    i = j + 1
                } else i++
            }
        }
        val minHits = if (titles.size >= 12) 2 else 1
        return counts.entries.filter { it.value >= minHits }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key.contains(' ') })
            .map { it.key }
            .distinctBy { it.lowercase() }
            .take(limit)
    }

    /** The names and terms in one headline (proper names; for Arabic, its content words), minus [own] words. */
    internal fun terms(title: String, own: Set<String>): Set<String> {
        val clean = title.substringBeforeLast(" - ").trim()
        val words = clean.split(TOKEN_SPLIT).map { it.trim('\'', '’', '.', '-') }.filter { it.isNotEmpty() }
        val out = LinkedHashSet<String>()
        if (clean.count { it in '\u0600'..'\u06FF' } > clean.length / 4) {
            // No capitals in Arabic: content words (and pairs of them) carry the names.
            val content = words.filter { it.length >= 3 && it !in AR_STOP && it.lowercase() !in own }
            content.forEach(out::add)
            content.zipWithNext().forEach { (a, b) -> out += "$a $b" }
            return out
        }
        var i = 0
        while (i < words.size) {
            if (isName(words[i], i == 0)) {
                var j = i
                while (j + 1 < words.size && isName(words[j + 1], false) && j - i < 2) j++
                val phrase = words.subList(i, j + 1).joinToString(" ")
                val key = phrase.lowercase()
                if (key !in STOP && key.split(' ').none { it in own } && phrase.length > 2) out += phrase
                i = j + 1
            } else i++
        }
        return out
    }

    private val AR_STOP = setOf("في", "من", "على", "إلى", "عن", "مع", "بعد", "قبل", "هذا", "هذه", "التي", "الذي", "حول", "خلال", "بين", "عند", "كما", "أن", "إن", "ما", "لا", "لم", "لن", "قد", "كل", "أو", "ثم", "حتى", "منذ", "عبر", "يوم", "اليوم", "أمس", "عاجل", "فيديو", "بالصور", "شاهد")

    private fun isName(w: String, sentenceStart: Boolean): Boolean {
        if (w.length < 2 || w.lowercase() in STOP) return false
        if (w.all { it.isUpperCase() || it.isDigit() || it == '&' } && w.length <= 6 && w.any { it.isLetter() }) return true // acronyms: NATO, OPEC, F1
        if (!w.first().isUpperCase()) return false
        return !sentenceStart || w.length > 3
    }
}

private val MARKS = Regex("\\p{M}+")
private val WORD_SPLIT = Regex("[^\\p{L}\\p{N}]+")
private val TOKEN_SPLIT = Regex("[^\\p{L}\\p{N}'’.&-]+")
private val NON_ALNUM = Regex("[^a-z0-9]+")
