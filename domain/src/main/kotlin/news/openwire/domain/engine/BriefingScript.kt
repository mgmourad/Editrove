// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import news.openwire.domain.model.BriefingDetail
import news.openwire.domain.model.Presenter

/**
 * Writes the words of an audio briefing the way a news presenter would say them: an opening
 * that fits the hour, the length and any breaking news; a hand-off into every story that
 * follows what is actually true of it (related to the one before, local or overseas, a new
 * section); attribution; and a sign-off. Four presenter personalities, five languages.
 *
 * Editorial safeguards: "breaking" and "developing" are said only when a newsroom labelled
 * the story so; "related" only when two stories share their subject; regional and section
 * hand-offs only when the story sits in that edition's local, national, international or
 * section feed. Clauses that would assert things the article may not say ("officials say",
 * "critics argue") are never used.
 */
object BriefingScript {

    /** Where a story happens, relative to the reader's edition. */
    enum class Scope { LOCAL, NATIONAL, INTERNATIONAL }

    /** The section a story belongs to. */
    enum class Category { POLITICS, BUSINESS, ECONOMY, TECHNOLOGY, SCIENCE, HEALTH, SPORTS, ENTERTAINMENT, ENVIRONMENT, WORLD }

    /** Part of the day (and the weekend), for openings and sign-offs that fit the moment. */
    enum class Slot { MORNING, AFTERNOON, EVENING, NIGHT, WEEKEND }

    /**
     * One story as the narrator will read it.
     *
     * @param subtitle the publisher's standfirst (the deck under the headline).
     * @param keyPoints the on-device summary — the same key points the reader's Summary box shows.
     * @param summary a public excerpt, used when there's no subtitle or article text.
     * @param relatedToPrevious it continues the subject of the story before it.
     * @param alsoReportedBy other outlets carrying the same story (merged, not read twice).
     */
    data class Story(
        val title: String,
        val source: String?,
        val summary: String? = null,
        val body: String? = null,
        val subtitle: String? = null,
        val keyPoints: List<String> = emptyList(),
        val scope: Scope? = null,
        val category: Category? = null,
        val breaking: Boolean = false,
        val developing: Boolean = false,
        val relatedToPrevious: Boolean = false,
        val alsoReportedBy: List<String> = emptyList(),
    )

    /** How the presenter speaks in one language. */
    private class Phrases(
        val greeting: (Int) -> String,
        val dayPattern: String,
        val timeLine: Map<Slot, String>,
        val opens: Map<Presenter, List<String>>,
        val programme: (day: String, topic: String, what: String) -> String,
        val brief: (topic: String, what: String) -> String,
        val concise: (topic: String, what: String) -> String,
        val what: (BriefingDetail, Int) -> String,
        val first: Map<Presenter, List<String>>,
        val next: Map<Presenter, List<String>>,
        val related: List<String>,
        val scope: Map<Scope, List<String>>,
        val category: Map<Category, String>,
        val final: List<String>,
        val breakingOpen: String,
        val breakingStory: String,
        val developing: String,
        val intoSummary: List<String>,
        val attribution: List<(String) -> String>,
        val alsoReported: (String) -> String,
        val and: String,
        val reports: (String) -> String,
        val thatReport: (String) -> String,
        val outros: Map<Presenter, List<(String) -> String>>,
        val timeOutro: Map<Slot, String>,
        val breakingOutro: String,
    )

    private val EN = Phrases(
        greeting = { h -> when (h) { in 4..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" } },
        dayPattern = "EEEE, MMMM d",
        timeLine = mapOf(
            Slot.MORNING to "Here's what you need to know as the day begins.",
            Slot.AFTERNOON to "Here's what's making headlines today.",
            Slot.EVENING to "Here's a look at today's major developments.",
            Slot.NIGHT to "Here's your late-night news briefing.",
            Slot.WEEKEND to "Here's your weekend news update.",
        ),
        opens = mapOf(
            Presenter.ANCHOR to listOf(
                "Here are the latest headlines.", "Here's your latest news briefing.", "Let's take a look at today's top stories.",
                "Welcome to your latest news update.", "Here's a look at the stories making headlines today.", "Let's get you up to speed on the latest developments.",
            ),
            Presenter.MODERN to listOf(
                "Here's what's happening right now.", "Let's get you caught up on the latest.", "It's time for your latest news briefing.",
                "Let's bring you up to speed.", "Let's dive into today's biggest stories.", "Here's a quick look at what's making news.",
            ),
            Presenter.CORRESPONDENT to listOf(
                "Here are today's top headlines.", "Let's get straight to the news.", "Here's what you need to know.",
                "These are today's key developments.", "Here's the latest.", "Today's news, starting now.",
            ),
            Presenter.CONVERSATIONAL to listOf(
                "There's a lot happening today. Let's get you up to speed.", "Let's take a moment to catch up on the latest.",
                "From major developments to the stories making waves, here's your update.", "We've gathered the latest stories to help you stay informed.",
                "Let's walk through the biggest developments making news today.",
            ),
        ),
        programme = { d, t, w -> "It's $d, and this is your $t briefing from Editrove, with $w." },
        brief = { t, w -> "This is your $t briefing from Editrove, with $w." },
        concise = { t, w -> "Your $t briefing: $w." },
        what = { detail, n -> when (detail) {
            BriefingDetail.HEADLINES -> if (n <= 3) "a quick look at the top $n headlines" else "the top $n headlines"
            BriefingDetail.SUMMARIES -> "$n stories and the key details on each"
            BriefingDetail.FULL -> "$n stories, read in full"
        } },
        first = mapOf(
            Presenter.ANCHOR to listOf("Our top story.", "We begin with our lead story.", "Leading today's headlines."),
            Presenter.MODERN to listOf("First up.", "Let's start here.", "Up first."),
            Presenter.CORRESPONDENT to listOf("The top story.", "First, the biggest story.", "We begin here."),
            Presenter.CONVERSATIONAL to listOf("Let's start with the biggest story.", "First up.", "Here's where we'll begin."),
        ),
        next = mapOf(
            Presenter.ANCHOR to listOf("In other news.", "Elsewhere today.", "Meanwhile.", "Turning to another major development.", "Also making news today.", "Now to another important story.", "In a separate development."),
            Presenter.MODERN to listOf("Next up.", "Also making headlines.", "Moving on.", "Here's another story we're watching.", "Meanwhile.", "Now to our next story."),
            Presenter.CORRESPONDENT to listOf("Next.", "Also today.", "Separately.", "In other developments.", "Elsewhere.", "Another story."),
            Presenter.CONVERSATIONAL to listOf("Let's move on.", "Here's another one worth knowing about.", "Now, to our next story.", "Shifting our focus.", "Also making news.", "Let's turn to something else."),
        ),
        related = listOf("In a related development.", "On a related note.", "Staying with that story.", "Also on this subject."),
        scope = mapOf(
            Scope.LOCAL to listOf("Closer to home.", "Now to local news.", "Turning to local news."),
            Scope.NATIONAL to listOf("Across the country.", "Now to national news.", "On the national stage."),
            Scope.INTERNATIONAL to listOf("Turning now to developments overseas.", "On the international front.", "Elsewhere around the world.", "Now to developments around the world."),
        ),
        category = mapOf(
            Category.POLITICS to "Turning to the latest in politics.", Category.BUSINESS to "In business news.", Category.ECONOMY to "Turning to the economy.",
            Category.TECHNOLOGY to "Over in technology.", Category.SCIENCE to "In science and research.", Category.HEALTH to "In health news.",
            Category.SPORTS to "Now, let's turn to sports.", Category.ENTERTAINMENT to "In entertainment news.", Category.ENVIRONMENT to "Turning to the environment.",
            Category.WORLD to "Now to developments around the world.",
        ),
        final = listOf("And finally.", "One last story before we go.", "Finally today."),
        breakingOpen = "We begin with breaking news.",
        breakingStory = "And now, breaking news.",
        developing = "This is a developing story.",
        intoSummary = listOf("Here's what we know.", "The details.", "Here's the latest."),
        attribution = listOf({ s -> "$s reports." }, { s -> "According to $s." }, { s -> "From $s." }, { s -> "$s has the story." }),
        alsoReported = { "The story is also being covered by $it." },
        and = " and ",
        reports = { "$it reports." },
        thatReport = { "That report from $it." },
        outros = mapOf(
            Presenter.ANCHOR to listOf({ t -> "And that's your $t briefing. Those are the latest headlines for now." }, { t -> "That brings us to the end of your $t briefing." }, { _ -> "And those are the stories making headlines today." }),
            Presenter.MODERN to listOf({ t -> "And that's your $t briefing. You're all caught up." }, { _ -> "That's everything you need to know for now." }, { _ -> "You've caught up on the latest. Thanks for listening to Editrove." }),
            Presenter.CORRESPONDENT to listOf({ _ -> "That's the latest at this hour." }, { _ -> "For now, those are the latest developments." }, { t -> "And that's your $t briefing." }),
            Presenter.CONVERSATIONAL to listOf({ _ -> "That's all for now. Thanks for listening." }, { _ -> "Thanks for making Editrove part of your day." }, { _ -> "Until next time, stay informed." }),
        ),
        timeOutro = mapOf(
            Slot.MORNING to "Have a great day, and stay informed.", Slot.AFTERNOON to "Enjoy the rest of your day.",
            Slot.EVENING to "Have a pleasant evening, and thanks for listening.", Slot.NIGHT to "That's all for tonight. Thanks for listening.",
            Slot.WEEKEND to "Enjoy your weekend, and stay informed.",
        ),
        breakingOutro = "For the latest on this story, check back in Editrove.",
    )

    private val AR = Phrases(
        greeting = { h -> if (h in 4..11) "صباح الخير" else "مساء الخير" },
        dayPattern = "EEEE d MMMM",
        timeLine = mapOf(
            Slot.MORNING to "إليكم ما تحتاجون إلى معرفته مع بداية اليوم.",
            Slot.AFTERNOON to "إليكم أبرز عناوين اليوم.",
            Slot.EVENING to "إليكم نظرة على أهم تطورات اليوم.",
            Slot.NIGHT to "إليكم نشرة أخبار آخر الليل.",
            Slot.WEEKEND to "إليكم نشرة أخبار عطلة نهاية الأسبوع.",
        ),
        opens = mapOf(
            Presenter.ANCHOR to listOf(
                "إليكم آخر العناوين.", "إليكم أحدث نشرة إخبارية.", "لنلقِ نظرة على أبرز أخبار اليوم.",
                "أهلاً بكم في آخر المستجدات الإخبارية.", "إليكم الأخبار التي تتصدر العناوين اليوم.", "نضعكم في صورة آخر التطورات.",
            ),
            Presenter.MODERN to listOf(
                "إليكم ما يحدث الآن.", "لنطّلع معاً على آخر المستجدات.", "حان وقت نشرتكم الإخبارية.",
                "نضعكم في الصورة.", "لنبدأ بأبرز قصص اليوم.", "نظرة سريعة على ما يتصدر الأخبار.",
            ),
            Presenter.CORRESPONDENT to listOf(
                "إليكم أبرز عناوين اليوم.", "مباشرة إلى الأخبار.", "إليكم ما تحتاجون إلى معرفته.",
                "هذه أهم تطورات اليوم.", "إليكم آخر الأخبار.", "أخبار اليوم، تبدأ الآن.",
            ),
            Presenter.CONVERSATIONAL to listOf(
                "الكثير يحدث اليوم، فلنضعكم في الصورة.", "لنأخذ لحظة للاطلاع على آخر الأخبار.",
                "من التطورات الكبرى إلى القصص التي تشغل الناس، إليكم آخر المستجدات.", "جمعنا لكم آخر الأخبار لتبقوا على اطلاع.",
                "لنستعرض معاً أبرز تطورات اليوم.",
            ),
        ),
        programme = { d, t, w -> "اليوم $d، وهذه نشرة $t من Editrove، وفيها $w." },
        brief = { t, w -> "هذه نشرة $t من Editrove، وفيها $w." },
        concise = { t, w -> "نشرة $t: $w." },
        what = { detail, n -> when (detail) {
            BriefingDetail.HEADLINES -> if (n <= 3) "نظرة سريعة على أهم $n عناوين" else "أهم $n عناوين"
            BriefingDetail.SUMMARIES -> "$n أخبار مع أبرز تفاصيل كل منها"
            BriefingDetail.FULL -> "$n أخبار كاملة"
        } },
        first = mapOf(
            Presenter.ANCHOR to listOf("خبرنا الأول.", "نبدأ بالخبر الأبرز.", "في صدارة عناوين اليوم."),
            Presenter.MODERN to listOf("أولاً.", "لنبدأ من هنا.", "البداية مع هذا الخبر."),
            Presenter.CORRESPONDENT to listOf("الخبر الأبرز.", "أولاً، أهم الأخبار.", "نبدأ من هنا."),
            Presenter.CONVERSATIONAL to listOf("لنبدأ بأهم خبر.", "أولاً.", "من هنا نبدأ."),
        ),
        next = mapOf(
            Presenter.ANCHOR to listOf("وفي خبر آخر.", "وفي مكان آخر اليوم.", "في الأثناء.", "ننتقل إلى تطور بارز آخر.", "ومن الأخبار أيضاً اليوم.", "والآن إلى خبر مهم آخر.", "وفي تطور منفصل."),
            Presenter.MODERN to listOf("التالي.", "ومن العناوين أيضاً.", "نواصل.", "وإليكم خبراً آخر نتابعه.", "في الأثناء.", "والآن إلى خبرنا التالي."),
            Presenter.CORRESPONDENT to listOf("التالي.", "وأيضاً اليوم.", "وبشكل منفصل.", "ومن التطورات الأخرى.", "وفي مكان آخر.", "خبر آخر."),
            Presenter.CONVERSATIONAL to listOf("لننتقل إلى خبر آخر.", "وهذا خبر يستحق المعرفة.", "والآن إلى خبرنا التالي.", "ننقل تركيزنا.", "ومن الأخبار أيضاً.", "لننتقل إلى موضوع آخر."),
        ),
        related = listOf("وفي تطور ذي صلة.", "وفي سياق متصل.", "وبالبقاء مع هذا الخبر.", "وفي الشأن ذاته."),
        scope = mapOf(
            Scope.LOCAL to listOf("أقرب إلينا.", "والآن إلى الأخبار المحلية.", "ننتقل إلى الشأن المحلي."),
            Scope.NATIONAL to listOf("على المستوى الوطني.", "والآن إلى أخبار البلاد.", "وفي الشأن الوطني."),
            Scope.INTERNATIONAL to listOf("ننتقل الآن إلى التطورات في الخارج.", "على الصعيد الدولي.", "وحول العالم.", "والآن إلى تطورات من حول العالم."),
        ),
        category = mapOf(
            Category.POLITICS to "ننتقل إلى آخر المستجدات السياسية.", Category.BUSINESS to "وفي أخبار الأعمال.", Category.ECONOMY to "ننتقل إلى الاقتصاد.",
            Category.TECHNOLOGY to "وفي عالم التكنولوجيا.", Category.SCIENCE to "وفي العلوم والأبحاث.", Category.HEALTH to "وفي الأخبار الصحية.",
            Category.SPORTS to "والآن إلى الرياضة.", Category.ENTERTAINMENT to "وفي أخبار الفن والترفيه.", Category.ENVIRONMENT to "ننتقل إلى شؤون البيئة.",
            Category.WORLD to "والآن إلى تطورات من حول العالم.",
        ),
        final = listOf("وأخيراً.", "خبر أخير قبل أن نختم.", "وختاماً لهذا اليوم."),
        breakingOpen = "نبدأ بخبر عاجل.",
        breakingStory = "والآن، خبر عاجل.",
        developing = "هذا خبر قيد التطور.",
        intoSummary = listOf("إليكم ما نعرفه.", "التفاصيل.", "وإليكم آخر المستجدات."),
        attribution = listOf({ s -> "بحسب $s." }, { s -> "نقلاً عن $s." }, { s -> "من $s." }, { s -> "والتفاصيل لدى $s." }),
        alsoReported = { "ويتناول الخبر أيضاً $it." },
        and = " و",
        reports = { "بحسب $it." },
        thatReport = { "كان ذلك تقرير $it." },
        outros = mapOf(
            Presenter.ANCHOR to listOf({ t -> "وبهذا نختتم نشرة $t. هذه آخر العناوين حتى الآن." }, { t -> "وبهذا نصل إلى ختام نشرة $t." }, { _ -> "كانت هذه أبرز الأخبار التي تصدرت العناوين اليوم." }),
            Presenter.MODERN to listOf({ t -> "وبهذا تنتهي نشرة $t. أصبحتم على اطلاع بكل جديد." }, { _ -> "هذا كل ما تحتاجون إلى معرفته الآن." }, { _ -> "أصبحتم على اطلاع بآخر الأخبار. شكراً لاستماعكم إلى Editrove." }),
            Presenter.CORRESPONDENT to listOf({ _ -> "هذه آخر الأخبار في هذه الساعة." }, { _ -> "تلك هي آخر التطورات حتى الآن." }, { t -> "وبهذا تنتهي نشرة $t." }),
            Presenter.CONVERSATIONAL to listOf({ _ -> "هذا كل شيء الآن. شكراً لاستماعكم." }, { _ -> "شكراً لأنكم جعلتم Editrove جزءاً من يومكم." }, { _ -> "إلى اللقاء، وابقوا على اطلاع." }),
        ),
        timeOutro = mapOf(
            Slot.MORNING to "نتمنى لكم يوماً سعيداً.", Slot.AFTERNOON to "نتمنى لكم بقية يوم طيبة.",
            Slot.EVENING to "نتمنى لكم أمسية هانئة، وشكراً لاستماعكم.", Slot.NIGHT to "هذا كل شيء الليلة. شكراً لاستماعكم.",
            Slot.WEEKEND to "نتمنى لكم عطلة نهاية أسبوع سعيدة.",
        ),
        breakingOutro = "لمتابعة آخر مستجدات هذا الخبر، عودوا إلى Editrove.",
    )

    private val FR = Phrases(
        greeting = { h -> when (h) { in 4..11 -> "Bonjour"; in 12..16 -> "Bon après-midi"; else -> "Bonsoir" } },
        dayPattern = "EEEE d MMMM",
        timeLine = mapOf(
            Slot.MORNING to "Voici ce qu’il faut savoir pour bien commencer la journée.",
            Slot.AFTERNOON to "Voici ce qui fait l’actualité aujourd’hui.",
            Slot.EVENING to "Voici les grands faits de la journée.",
            Slot.NIGHT to "Voici votre point d’actualité de fin de soirée.",
            Slot.WEEKEND to "Voici votre point d’actualité du week-end.",
        ),
        opens = mapOf(
            Presenter.ANCHOR to listOf(
                "Voici les derniers titres.", "Voici votre dernier point d’actualité.", "Faisons le tour des grands titres du jour.",
                "Bienvenue dans votre point d’actualité.", "Voici les sujets qui font la une aujourd’hui.", "Faisons le point sur les derniers développements.",
            ),
            Presenter.MODERN to listOf(
                "Voici ce qui se passe en ce moment.", "Faisons le point sur l’actualité.", "C’est l’heure de votre point d’actualité.",
                "On vous met à jour.", "Plongeons dans les grands sujets du jour.", "Un rapide tour de l’actualité.",
            ),
            Presenter.CORRESPONDENT to listOf(
                "Voici les titres du jour.", "Place à l’actualité.", "Voici l’essentiel.",
                "Voici les faits marquants du jour.", "Voici les dernières informations.", "L’actualité du jour, c’est maintenant.",
            ),
            Presenter.CONVERSATIONAL to listOf(
                "Il se passe beaucoup de choses aujourd’hui. On fait le point ensemble.", "Prenons un moment pour rattraper l’actualité.",
                "Des grands événements aux sujets qui font parler, voici votre point.", "Nous avons rassemblé l’actualité pour vous tenir informé.",
                "Passons en revue les grands faits du jour.",
            ),
        ),
        programme = { d, t, w -> "Nous sommes le $d, et voici votre point $t d’Editrove, avec $w." },
        brief = { t, w -> "Voici votre point $t d’Editrove, avec $w." },
        concise = { t, w -> "Point $t : $w." },
        what = { detail, n -> when (detail) {
            BriefingDetail.HEADLINES -> if (n <= 3) "un tour rapide des $n principaux titres" else "les $n principaux titres"
            BriefingDetail.SUMMARIES -> "$n sujets, avec l’essentiel de chacun"
            BriefingDetail.FULL -> "$n articles, lus en entier"
        } },
        first = mapOf(
            Presenter.ANCHOR to listOf("Notre titre principal.", "Nous commençons par notre sujet phare.", "En tête de l’actualité."),
            Presenter.MODERN to listOf("Pour commencer.", "On commence ici.", "D’abord."),
            Presenter.CORRESPONDENT to listOf("Le titre principal.", "D’abord, le sujet majeur.", "Nous commençons ici."),
            Presenter.CONVERSATIONAL to listOf("Commençons par le sujet le plus important.", "Pour commencer.", "On commence par ceci."),
        ),
        next = mapOf(
            Presenter.ANCHOR to listOf("Dans le reste de l’actualité.", "Ailleurs aujourd’hui.", "Pendant ce temps.", "Passons à un autre fait marquant.", "Également à la une aujourd’hui.", "Place maintenant à un autre sujet important.", "Dans une autre affaire."),
            Presenter.MODERN to listOf("Ensuite.", "Également à la une.", "On continue.", "Un autre sujet que nous suivons.", "Pendant ce temps.", "Place à notre sujet suivant."),
            Presenter.CORRESPONDENT to listOf("Ensuite.", "Également aujourd’hui.", "Par ailleurs.", "Autres faits du jour.", "Ailleurs.", "Autre sujet."),
            Presenter.CONVERSATIONAL to listOf("Passons à la suite.", "Voici un autre sujet à connaître.", "Place à notre sujet suivant.", "Changeons de sujet.", "Également dans l’actualité.", "Passons à autre chose."),
        ),
        related = listOf("Dans un développement lié.", "Sur le même sujet.", "Toujours sur ce sujet.", "Dans le même registre."),
        scope = mapOf(
            Scope.LOCAL to listOf("Plus près de chez nous.", "Place à l’actualité locale.", "Passons à l’actualité locale."),
            Scope.NATIONAL to listOf("Dans le pays.", "Place à l’actualité nationale.", "Sur la scène nationale."),
            Scope.INTERNATIONAL to listOf("Passons maintenant à l’étranger.", "Sur la scène internationale.", "Ailleurs dans le monde.", "Place à l’actualité internationale."),
        ),
        category = mapOf(
            Category.POLITICS to "Passons à l’actualité politique.", Category.BUSINESS to "Côté entreprises.", Category.ECONOMY to "Passons à l’économie.",
            Category.TECHNOLOGY to "Côté technologie.", Category.SCIENCE to "En sciences et recherche.", Category.HEALTH to "Côté santé.",
            Category.SPORTS to "Place au sport.", Category.ENTERTAINMENT to "Côté culture et divertissement.", Category.ENVIRONMENT to "Passons à l’environnement.",
            Category.WORLD to "Place à l’actualité internationale.",
        ),
        final = listOf("Et pour finir.", "Un dernier sujet avant de nous quitter.", "Enfin aujourd’hui."),
        breakingOpen = "Nous commençons par une information de dernière minute.",
        breakingStory = "Et maintenant, une information de dernière minute.",
        developing = "C’est une information en cours d’évolution.",
        intoSummary = listOf("Voici ce que l’on sait.", "Les détails.", "Voici les dernières informations."),
        attribution = listOf({ s -> "Selon $s." }, { s -> "D’après $s." }, { s -> "Source : $s." }, { s -> "$s rapporte l’information." }),
        alsoReported = { "Le sujet est également couvert par $it." },
        and = " et ",
        reports = { "Selon $it." },
        thatReport = { "C’était un article de $it." },
        outros = mapOf(
            Presenter.ANCHOR to listOf({ t -> "C’était votre point $t. Ce sont les derniers titres pour le moment." }, { t -> "Ainsi s’achève votre point $t." }, { _ -> "Voilà les sujets qui font la une aujourd’hui." }),
            Presenter.MODERN to listOf({ t -> "C’était votre point $t. Vous êtes à jour." }, { _ -> "Voilà l’essentiel pour le moment." }, { _ -> "Vous êtes à jour. Merci d’avoir écouté Editrove." }),
            Presenter.CORRESPONDENT to listOf({ _ -> "C’est l’essentiel de l’actualité à cette heure." }, { _ -> "Voilà les derniers développements pour l’instant." }, { t -> "C’était votre point $t." }),
            Presenter.CONVERSATIONAL to listOf({ _ -> "C’est tout pour le moment. Merci de votre écoute." }, { _ -> "Merci de faire d’Editrove un moment de votre journée." }, { _ -> "À la prochaine, et restez informé." }),
        ),
        timeOutro = mapOf(
            Slot.MORNING to "Excellente journée à vous.", Slot.AFTERNOON to "Bonne fin de journée.",
            Slot.EVENING to "Bonne soirée, et merci de votre écoute.", Slot.NIGHT to "C’est tout pour ce soir. Merci de votre écoute.",
            Slot.WEEKEND to "Bon week-end.",
        ),
        breakingOutro = "Pour suivre ce sujet, revenez sur Editrove.",
    )

    private val ES = Phrases(
        greeting = { h -> when (h) { in 4..11 -> "Buenos días"; in 12..19 -> "Buenas tardes"; else -> "Buenas noches" } },
        dayPattern = "EEEE d 'de' MMMM",
        timeLine = mapOf(
            Slot.MORNING to "Esto es lo que necesitas saber para empezar el día.",
            Slot.AFTERNOON to "Esto es lo que está en los titulares hoy.",
            Slot.EVENING to "Estos son los principales acontecimientos del día.",
            Slot.NIGHT to "Este es tu resumen informativo de la noche.",
            Slot.WEEKEND to "Este es tu resumen informativo del fin de semana.",
        ),
        opens = mapOf(
            Presenter.ANCHOR to listOf(
                "Estos son los últimos titulares.", "Este es tu último resumen informativo.", "Repasemos las principales noticias del día.",
                "Te damos la bienvenida a tu actualización informativa.", "Estas son las noticias que marcan el día.", "Te ponemos al día con los últimos acontecimientos.",
            ),
            Presenter.MODERN to listOf(
                "Esto es lo que está pasando ahora mismo.", "Vamos a ponerte al día.", "Es hora de tu resumen informativo.",
                "Te ponemos al día.", "Repasemos las noticias más importantes del día.", "Un vistazo rápido a lo que es noticia.",
            ),
            Presenter.CORRESPONDENT to listOf(
                "Estos son los titulares de hoy.", "Vamos directo a las noticias.", "Esto es lo que necesitas saber.",
                "Estos son los hechos clave del día.", "Esto es lo último.", "Las noticias del día, empiezan ahora.",
            ),
            Presenter.CONVERSATIONAL to listOf(
                "Hoy están pasando muchas cosas. Vamos a ponerte al día.", "Tomemos un momento para ponernos al día.",
                "De los grandes acontecimientos a las historias que dan que hablar, aquí tienes tu resumen.", "Reunimos las últimas noticias para que estés al tanto.",
                "Repasemos juntos los principales acontecimientos de hoy.",
            ),
        ),
        programme = { d, t, w -> "Hoy es $d, y este es tu resumen de $t de Editrove, con $w." },
        brief = { t, w -> "Este es tu resumen de $t de Editrove, con $w." },
        concise = { t, w -> "Resumen de $t: $w." },
        what = { detail, n -> when (detail) {
            BriefingDetail.HEADLINES -> if (n <= 3) "un vistazo rápido a los $n titulares principales" else "los $n titulares principales"
            BriefingDetail.SUMMARIES -> "$n noticias, con los datos clave de cada una"
            BriefingDetail.FULL -> "$n noticias, leídas completas"
        } },
        first = mapOf(
            Presenter.ANCHOR to listOf("Nuestra noticia principal.", "Comenzamos con nuestra noticia destacada.", "Encabezando los titulares de hoy."),
            Presenter.MODERN to listOf("Primero.", "Empezamos aquí.", "Para empezar."),
            Presenter.CORRESPONDENT to listOf("La noticia principal.", "Primero, lo más importante.", "Comenzamos aquí."),
            Presenter.CONVERSATIONAL to listOf("Empecemos con la noticia más importante.", "Para empezar.", "Por aquí empezamos."),
        ),
        next = mapOf(
            Presenter.ANCHOR to listOf("En otras noticias.", "En otro orden de cosas hoy.", "Mientras tanto.", "Pasamos a otro acontecimiento importante.", "También es noticia hoy.", "Ahora, otra noticia importante.", "En un hecho aparte."),
            Presenter.MODERN to listOf("A continuación.", "También en los titulares.", "Seguimos.", "Otra noticia que seguimos.", "Mientras tanto.", "Ahora, nuestra siguiente noticia."),
            Presenter.CORRESPONDENT to listOf("Siguiente.", "También hoy.", "Por separado.", "En otros acontecimientos.", "En otro orden.", "Otra noticia."),
            Presenter.CONVERSATIONAL to listOf("Sigamos.", "Aquí va otra que vale la pena conocer.", "Ahora, nuestra siguiente noticia.", "Cambiamos de tema.", "También es noticia.", "Pasemos a otra cosa."),
        ),
        related = listOf("En un hecho relacionado.", "En la misma línea.", "Siguiendo con esta noticia.", "Sobre el mismo tema."),
        scope = mapOf(
            Scope.LOCAL to listOf("Más cerca de casa.", "Ahora, las noticias locales.", "Pasamos a la actualidad local."),
            Scope.NATIONAL to listOf("En el país.", "Ahora, las noticias nacionales.", "En el ámbito nacional."),
            Scope.INTERNATIONAL to listOf("Pasamos ahora al exterior.", "En el ámbito internacional.", "En otras partes del mundo.", "Ahora, la actualidad internacional."),
        ),
        category = mapOf(
            Category.POLITICS to "Pasamos a lo último en política.", Category.BUSINESS to "En noticias de empresas.", Category.ECONOMY to "Pasamos a la economía.",
            Category.TECHNOLOGY to "En el mundo de la tecnología.", Category.SCIENCE to "En ciencia e investigación.", Category.HEALTH to "En noticias de salud.",
            Category.SPORTS to "Ahora, vamos con los deportes.", Category.ENTERTAINMENT to "En noticias de entretenimiento.", Category.ENVIRONMENT to "Pasamos al medio ambiente.",
            Category.WORLD to "Ahora, la actualidad internacional.",
        ),
        final = listOf("Y para terminar.", "Una última noticia antes de despedirnos.", "Por último hoy."),
        breakingOpen = "Comenzamos con una noticia de última hora.",
        breakingStory = "Y ahora, última hora.",
        developing = "Es una noticia en desarrollo.",
        intoSummary = listOf("Esto es lo que sabemos.", "Los detalles.", "Esto es lo último."),
        attribution = listOf({ s -> "Informa $s." }, { s -> "Según $s." }, { s -> "De $s." }, { s -> "$s tiene la noticia." }),
        alsoReported = { "La noticia también la cubren $it." },
        and = " y ",
        reports = { "Informa $it." },
        thatReport = { "Esa fue la información de $it." },
        outros = mapOf(
            Presenter.ANCHOR to listOf({ t -> "Y este fue tu resumen de $t. Estos son los últimos titulares por ahora." }, { t -> "Así llegamos al final de tu resumen de $t." }, { _ -> "Y estas son las noticias que marcan el día." }),
            Presenter.MODERN to listOf({ t -> "Y este fue tu resumen de $t. Ya estás al día." }, { _ -> "Eso es todo lo que necesitas saber por ahora." }, { _ -> "Ya estás al día. Gracias por escuchar Editrove." }),
            Presenter.CORRESPONDENT to listOf({ _ -> "Eso es lo último a esta hora." }, { _ -> "Por ahora, estos son los últimos acontecimientos." }, { t -> "Y este fue tu resumen de $t." }),
            Presenter.CONVERSATIONAL to listOf({ _ -> "Eso es todo por ahora. Gracias por escuchar." }, { _ -> "Gracias por hacer de Editrove parte de tu día." }, { _ -> "Hasta la próxima, y mantente informado." }),
        ),
        timeOutro = mapOf(
            Slot.MORNING to "Que tengas un gran día.", Slot.AFTERNOON to "Que disfrutes el resto del día.",
            Slot.EVENING to "Que tengas una buena noche, y gracias por escuchar.", Slot.NIGHT to "Eso es todo por esta noche. Gracias por escuchar.",
            Slot.WEEKEND to "Disfruta del fin de semana.",
        ),
        breakingOutro = "Para seguir esta noticia, vuelve a Editrove.",
    )

    private val DE = Phrases(
        greeting = { h -> when (h) { in 4..10 -> "Guten Morgen"; in 11..17 -> "Guten Tag"; else -> "Guten Abend" } },
        dayPattern = "EEEE, d. MMMM",
        timeLine = mapOf(
            Slot.MORNING to "Das sollten Sie zum Start in den Tag wissen.",
            Slot.AFTERNOON to "Das sind die Schlagzeilen des Tages.",
            Slot.EVENING to "Ein Blick auf die wichtigsten Entwicklungen des Tages.",
            Slot.NIGHT to "Hier ist Ihr Nachrichtenüberblick zur späten Stunde.",
            Slot.WEEKEND to "Hier ist Ihr Nachrichtenüberblick zum Wochenende.",
        ),
        opens = mapOf(
            Presenter.ANCHOR to listOf(
                "Hier sind die neuesten Schlagzeilen.", "Hier ist Ihr aktueller Nachrichtenüberblick.", "Werfen wir einen Blick auf die Top-Themen des Tages.",
                "Willkommen zu Ihrem Nachrichten-Update.", "Das sind die Themen, die heute Schlagzeilen machen.", "Wir bringen Sie auf den neuesten Stand.",
            ),
            Presenter.MODERN to listOf(
                "Das passiert gerade.", "Wir bringen Sie auf den aktuellen Stand.", "Zeit für Ihren Nachrichtenüberblick.",
                "Hier ist Ihr Update.", "Los geht’s mit den wichtigsten Themen des Tages.", "Ein kurzer Blick auf die Nachrichtenlage.",
            ),
            Presenter.CORRESPONDENT to listOf(
                "Hier sind die Schlagzeilen des Tages.", "Direkt zu den Nachrichten.", "Das müssen Sie wissen.",
                "Das sind die wichtigsten Entwicklungen des Tages.", "Hier ist das Neueste.", "Die Nachrichten des Tages, jetzt.",
            ),
            Presenter.CONVERSATIONAL to listOf(
                "Heute passiert viel. Wir bringen Sie auf den neuesten Stand.", "Nehmen wir uns einen Moment für die aktuellen Nachrichten.",
                "Von großen Entwicklungen bis zu den Themen, über die man spricht: hier ist Ihr Update.", "Wir haben die aktuellen Nachrichten für Sie zusammengestellt.",
                "Gehen wir gemeinsam die wichtigsten Entwicklungen des Tages durch.",
            ),
        ),
        programme = { d, t, w -> "Heute ist $d, und das ist Ihr Briefing $t von Editrove, mit $w." },
        brief = { t, w -> "Das ist Ihr Briefing $t von Editrove, mit $w." },
        concise = { t, w -> "Briefing $t, mit $w." },
        what = { detail, n -> when (detail) {
            BriefingDetail.HEADLINES -> if (n <= 3) "einem kurzen Blick auf die $n wichtigsten Schlagzeilen" else "den $n wichtigsten Schlagzeilen"
            BriefingDetail.SUMMARIES -> "$n Meldungen und den wichtigsten Details"
            BriefingDetail.FULL -> "$n Meldungen in voller Länge"
        } },
        first = mapOf(
            Presenter.ANCHOR to listOf("Unser Top-Thema.", "Wir beginnen mit unserer Hauptmeldung.", "An der Spitze der heutigen Schlagzeilen."),
            Presenter.MODERN to listOf("Zuerst.", "Wir starten hier.", "Als Erstes."),
            Presenter.CORRESPONDENT to listOf("Die Hauptmeldung.", "Zuerst das wichtigste Thema.", "Wir beginnen hier."),
            Presenter.CONVERSATIONAL to listOf("Fangen wir mit dem wichtigsten Thema an.", "Zuerst.", "Hier fangen wir an."),
        ),
        next = mapOf(
            Presenter.ANCHOR to listOf("In weiteren Nachrichten.", "Anderswo heute.", "Unterdessen.", "Nun zu einer weiteren wichtigen Entwicklung.", "Ebenfalls heute in den Nachrichten.", "Nun zu einem weiteren wichtigen Thema.", "In einer anderen Angelegenheit."),
            Presenter.MODERN to listOf("Weiter geht’s.", "Ebenfalls in den Schlagzeilen.", "Weiter.", "Ein weiteres Thema, das wir verfolgen.", "Unterdessen.", "Nun zu unserer nächsten Meldung."),
            Presenter.CORRESPONDENT to listOf("Weiter.", "Ebenfalls heute.", "Davon unabhängig.", "Weitere Entwicklungen.", "Anderswo.", "Eine weitere Meldung."),
            Presenter.CONVERSATIONAL to listOf("Machen wir weiter.", "Das hier sollten Sie auch wissen.", "Nun zu unserer nächsten Meldung.", "Wechseln wir das Thema.", "Ebenfalls in den Nachrichten.", "Kommen wir zu etwas anderem."),
        ),
        related = listOf("Im Zusammenhang damit.", "Beim selben Thema.", "Dazu passend.", "Bleiben wir bei diesem Thema."),
        scope = mapOf(
            Scope.LOCAL to listOf("Hier vor Ort.", "Nun zu den lokalen Nachrichten.", "Zu den Nachrichten aus der Region."),
            Scope.NATIONAL to listOf("Im Inland.", "Nun zu den Nachrichten aus dem Inland.", "Auf nationaler Ebene."),
            Scope.INTERNATIONAL to listOf("Nun zu den Entwicklungen im Ausland.", "Auf internationaler Ebene.", "Anderswo auf der Welt.", "Nun zu den Nachrichten aus aller Welt."),
        ),
        category = mapOf(
            Category.POLITICS to "Nun zum Neuesten aus der Politik.", Category.BUSINESS to "Aus der Unternehmenswelt.", Category.ECONOMY to "Nun zur Wirtschaftslage.",
            Category.TECHNOLOGY to "Aus der Welt der Technik.", Category.SCIENCE to "Aus Wissenschaft und Forschung.", Category.HEALTH to "Aus dem Bereich Gesundheit.",
            Category.SPORTS to "Und nun zum Sport.", Category.ENTERTAINMENT to "Aus Kultur und Unterhaltung.", Category.ENVIRONMENT to "Nun zur Umwelt.",
            Category.WORLD to "Nun zu den Nachrichten aus aller Welt.",
        ),
        final = listOf("Und zum Schluss.", "Eine letzte Meldung, bevor wir uns verabschieden.", "Zum Abschluss für heute."),
        breakingOpen = "Wir beginnen mit einer Eilmeldung.",
        breakingStory = "Und nun eine Eilmeldung.",
        developing = "Die Lage entwickelt sich noch.",
        intoSummary = listOf("Das wissen wir.", "Die Einzelheiten.", "Der aktuelle Stand."),
        attribution = listOf({ s -> "$s berichtet." }, { s -> "Laut $s." }, { s -> "Von $s." }, { s -> "$s hat die Geschichte." }),
        alsoReported = { "Ebenfalls darüber berichtet: $it." },
        and = " und ",
        reports = { "$it berichtet." },
        thatReport = { "Das war der Bericht von $it." },
        outros = mapOf(
            Presenter.ANCHOR to listOf({ t -> "Das war Ihr Briefing $t. Das sind die neuesten Schlagzeilen für den Moment." }, { t -> "Damit endet Ihr Briefing $t." }, { _ -> "Das waren die Themen, die heute Schlagzeilen machen." }),
            Presenter.MODERN to listOf({ t -> "Das war Ihr Briefing $t. Sie sind auf dem neuesten Stand." }, { _ -> "Das ist alles, was Sie jetzt wissen müssen." }, { _ -> "Sie sind auf dem neuesten Stand. Danke, dass Sie Editrove zuhören." }),
            Presenter.CORRESPONDENT to listOf({ _ -> "Das ist der aktuelle Stand zu dieser Stunde." }, { _ -> "Das sind vorerst die neuesten Entwicklungen." }, { t -> "Das war Ihr Briefing $t." }),
            Presenter.CONVERSATIONAL to listOf({ _ -> "Das war’s für den Moment. Danke fürs Zuhören." }, { _ -> "Danke, dass Editrove Teil Ihres Tages ist." }, { _ -> "Bis zum nächsten Mal, und bleiben Sie informiert." }),
        ),
        timeOutro = mapOf(
            Slot.MORNING to "Einen schönen Tag noch.", Slot.AFTERNOON to "Genießen Sie den Rest des Tages.",
            Slot.EVENING to "Einen schönen Abend und danke fürs Zuhören.", Slot.NIGHT to "Das war’s für heute Abend. Danke fürs Zuhören.",
            Slot.WEEKEND to "Ein schönes Wochenende.",
        ),
        breakingOutro = "Mehr zu diesem Thema finden Sie in Editrove.",
    )

    private fun phrases(lang: String) = when (Languages.of(lang)?.code) { "ar" -> AR; "fr" -> FR; "es" -> ES; "de" -> DE; else -> EN }

    private fun <T> List<T>.pick(n: Int): T = this[Math.floorMod(n, size)]

    /** Morning, afternoon, evening, late night — or the weekend (daytime Saturday and Sunday). */
    fun slot(now: LocalDateTime): Slot {
        val h = now.hour
        val weekend = now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY
        return when {
            h >= 22 || h < 4 -> Slot.NIGHT
            weekend && h in 8..20 -> Slot.WEEKEND
            h < 12 -> Slot.MORNING
            h < 17 -> Slot.AFTERNOON
            else -> Slot.EVENING
        }
    }

    /**
     * The opening: it fits the [presenter], the time of day (used sparingly: every other
     * briefing for the anchor), the length (a short briefing is "a quick look"), and breaking news.
     */
    fun intro(
        topic: String,
        count: Int,
        detail: BriefingDetail,
        now: LocalDateTime,
        lang: String = "en",
        presenter: Presenter = Presenter.ANCHOR,
        breaking: Boolean = false,
        seed: Int = 0,
    ): String {
        val p = phrases(lang)
        val t = topic.trim()
        val w = p.what(detail, count)
        val slot = slot(now)
        return when (presenter) {
            Presenter.ANCHOR -> {
                val locale = Locale(Languages.of(lang)?.code ?: "en")
                val day = now.format(DateTimeFormatter.ofPattern(p.dayPattern, locale))
                // The time of day every other time; otherwise a classic broadcast open.
                val line = if (seed % 2 == 0 || breaking) p.timeLine.getValue(slot) else p.opens.getValue(presenter).pick(seed / 2)
                "${p.greeting(now.hour)}. $line ${p.programme(day, t, w)}"
            }
            Presenter.MODERN -> "${p.opens.getValue(presenter).pick(seed)} ${p.brief(t, w)}"
            Presenter.CORRESPONDENT -> "${p.opens.getValue(presenter).pick(seed)} ${p.concise(t, w)}"
            Presenter.CONVERSATIONAL -> {
                val line = if (slot == Slot.WEEKEND || slot == Slot.NIGHT) p.timeLine.getValue(slot) else p.opens.getValue(presenter).pick(seed)
                "${p.greeting(now.hour)}. $line ${p.brief(t, w)}"
            }
        }
    }

    /**
     * The hand-off into story [index] (0-based) of [total], chosen by what's true of it:
     * breaking (labelled), related to the story before, the last one, a move between local,
     * national and international news, a new section, else a neutral transition.
     */
    fun lead(
        index: Int,
        total: Int,
        detail: BriefingDetail,
        lang: String = "en",
        presenter: Presenter = Presenter.ANCHOR,
        story: Story? = null,
        previous: Story? = null,
        seed: Int = 0,
    ): String {
        val p = phrases(lang)
        val k = seed + index
        return when {
            index == 0 && story?.breaking == true -> p.breakingOpen
            index == 0 -> p.first.getValue(presenter).pick(seed)
            story?.breaking == true -> p.breakingStory
            story?.relatedToPrevious == true -> p.related.pick(k)
            index == total - 1 && total > 2 -> p.final.pick(k)
            story?.scope != null && previous?.scope != null && story.scope != previous.scope -> p.scope.getValue(story.scope).pick(k)
            story?.category != null && previous != null && story.category != previous.category -> p.category.getValue(story.category)
            else -> p.next.getValue(presenter).pick(index - 1 + seed)
        }
    }

    private fun attribution(source: String?, index: Int, p: Phrases): String? =
        source?.takeIf { it.isNotBlank() }?.let { p.attribution.pick(index)(it) }

    private fun alsoReported(names: List<String>, p: Phrases): String? {
        val n = names.filter { it.isNotBlank() }.distinct().take(2)
        if (n.isEmpty()) return null
        return p.alsoReported(n.joinToString(p.and))
    }

    /** Everything said for one story, as separate lines (natural pauses between them). */
    fun story(
        index: Int,
        total: Int,
        story: Story,
        detail: BriefingDetail,
        lang: String = "en",
        presenter: Presenter = Presenter.ANCHOR,
        previous: Story? = null,
        seed: Int = 0,
    ): List<String> = buildList {
        val p = phrases(lang)
        add(lead(index, total, detail, lang, presenter, story, previous, seed))
        val title = sentence(clean(story.title))
        add(title)
        if (story.developing) add(p.developing)
        // Headlines and Summaries both read the subtitle (or, without one, the public excerpt).
        val subtitle = deck(story)?.takeIf { FeedWeaver.calculateTitleSimilarity(it, title) < 0.7 }
        when (detail) {
            BriefingDetail.HEADLINES -> {
                subtitle?.let(::add)
                attribution(story.source, index, p)?.let(::add)
            }
            BriefingDetail.SUMMARIES -> {
                subtitle?.let(::add)
                attribution(story.source, index, p)?.let(::add)
                // The Summary box's key points, minus any that just repeat the subtitle.
                val points = story.keyPoints.map { sentence(clean(it)) }.filter { pt ->
                    pt.length > 20 && (subtitle == null || FeedWeaver.calculateTitleSimilarity(pt, subtitle) < 0.6)
                }
                if (points.isNotEmpty()) {
                    add(p.intoSummary.pick(index))
                    addAll(points)
                }
            }
            BriefingDetail.FULL -> {
                val body = story.body?.let(::clean)?.takeIf { it.length > 200 } ?: (story.subtitle ?: story.summary)?.let(::clean)
                if (body != null) {
                    story.source?.takeIf { it.isNotBlank() }?.let { add(p.reports(it)) }
                    add(body)
                    story.source?.takeIf { it.isNotBlank() }?.let { add(p.thatReport(it)) }
                } else attribution(story.source, index, p)?.let(::add)
            }
        }
        alsoReported(story.alsoReportedBy.filter { !it.equals(story.source, ignoreCase = true) }, p)?.let(::add)
    }

    /** The standfirst to read: the subtitle, else the excerpt; at most two sentences. */
    private fun deck(story: Story): String? {
        val raw = (story.subtitle?.takeIf { it.isNotBlank() } ?: story.summary)?.let(::clean)?.takeIf { it.length > 20 } ?: return null
        val sentences = Summarizer.split(raw)
        val short = if (sentences.size > 2) sentences.take(2).joinToString(" ") else raw
        return sentence(short.take(400))
    }

    private fun sentence(t: String) = if (t.endsWith("。") || t.endsWith(".") || t.endsWith("?") || t.endsWith("!") || t.endsWith("”") || t.endsWith("\"")) t else "$t."

    /**
     * The sign-off: the presenter's own, a time-of-day farewell for the anchor and the
     * friendly host, and a pointer back to Editrove when the briefing carried breaking news.
     */
    fun outro(
        topic: String,
        lang: String = "en",
        presenter: Presenter = Presenter.ANCHOR,
        now: LocalDateTime? = null,
        breaking: Boolean = false,
        seed: Int = 0,
    ): String {
        val p = phrases(lang)
        val parts = mutableListOf(p.outros.getValue(presenter).pick(seed)(topic.trim()))
        if (breaking) parts += p.breakingOutro
        if (now != null && (presenter == Presenter.ANCHOR || presenter == Presenter.CONVERSATIONAL)) parts += p.timeOutro.getValue(slot(now))
        return parts.joinToString(" ")
    }

    /** Strips leftovers that read badly aloud (URLs, bracketed credits, repeated whitespace). */
    fun clean(text: String): String = text
        .replace(RX_1, "")
        .replace(RX_2, "")
        .replace(RX_3, ". ")
        .replace(RX_4, " ")
        .trim()
}

// Compiled once: Android compiles regexes natively (ICU), so building them per call is costly.
private val RX_1 = Regex("https?://\\S+")
private val RX_2 = Regex("\\[[^]]{0,60}]")
private val RX_3 = Regex("\\s*[|•]\\s*")
private val RX_4 = Regex("\\s+")
