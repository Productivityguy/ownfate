package org.ownfate.app.data.words

/**
 * Unambiguous stems and brands. STRONG tier: blocks on sight.
 *
 * These are matched as prefixes of folded tokens or as welded short runs, so "pornvideos" and
 * "p o r n" both fire. Long-tail domains keep a broader substring match. Add whole brands
 * here; do not add ordinary English that merely contains the letters (hardcore, jav, nude).
 */
object StrongTerms {
    val terms = listOf(
        // core stems, these swallow most of the long tail
        "porn", "nsfw", "hentai", "camgirl", "camwhore", "nudify",
        "deepnude", "sextape", "sexvid", "sexcam", "sexchat", "livesex", "hdsex", "freesex",
        "milfs", "gangbang", "blowjob", "handjob", "creampie", "cumshot", "deepthroat",
        // "hardcore"/"softcore" were here and had to go: hardcore Minecraft, hardcore gaming,
        // hardcore workout. Substring-matched, so they poisoned ordinary YouTube searches while
        // adding nothing, every site they described is already named below.
        "bukkake", "fisting", "bdsm", "fapp",
        "jerkoff", "masturbat", "upskirt",
        "stripcam", "escortservice",
        // tube and cam brands seen most in the blocklist
        "xvideo", "xvideos", "xnxx", "xhamster", "pornhub", "redtube", "youporn", "youjizz", "tnaflix", "bellesahouse", "bellesafilms",
        "spankbang", "tube8", "eporner", "porntube", "spankwire", "keezmovies", "chaturbate", "bongacams", "stripchat", "camsoda",
        "myfreecams", "livejasmin", "cam4", "vlxx", "txxx", "upornia", "hclips",
        "hotmovs", "drtuber", "sunporno", "hqporner", "sxyprn", "daftsex", "porntrex","DogfartNetwork", "Scamangels", "RealityJunkies",
        "pornone", "porngo", "porn300", "brazzers", "bangbros", "realitykings", "naughtyamerica", "sexmex","publicagent", "Tiny4K",
        "mofos", "digitalplayground", "evilangel", "teamskeet", "twistys", "lustery", "ersties","blacked", "faketaxi","brattysis",
        "familystrokes", "letsdoit", "sislovesme","puretaboo", "pervmom","mypervyfamily","familyxxx", "erotikvonbenan", "freeuse", "teenfidelity",
        "stepsiblingscaught", "wowgirls", "oopsfamily", "hotguysfuck","zizivids", "myfamilypies", "mydirtyhobby", "princesscum", "matureNL", 
        "girlsway", "magicasianpussy", "dreamtranny", "mamacitaZ", "horneyhostel", "momxxx", "NFbusty", "ultrafilms", "pervz",
        "dane jones", "momwantstobreed", "nubilesporn", "candyAI", "nubilefilms", "jeffsmodels", "sweetsinner", "momlover","hushpass",
        "nookies", "love her feet", "swappz", "brattymilf","eroticplanet", "box of porn", "transfixed", "shoplyfter", "enjoyX", "lesbea",
        "filthykings", "nurumessage", "hunt4K", "fakehub", "shaggingMoms", "transAngels", "AVTits", "AV69", "familySwap", "cum4k",
        "DoTheWife", "familysinners", "schoolGirlsHD", "JAVHub", "Gloryhole", "TeenMegaWorld", "JaysPOV", "bellesaPlus", "ATKGirlfriends",
        "AuntJudysXXX", "ATKHairy", "HookupHotshot", "HouseofFyre", "PUBA", "GrobbyGirls", "AVJiali", "julesjordan", "ferame", "JapanHDV",
        "tushy", "inkasex", "latina milf","fakehostel", "vip4k", "dadcrush", "interracialpass", "thewhiteboxxx", "myfriendshotmom",
        "redgifs", "ixxx", "xozilla", "hdzog", "hellporno", "yesporn", "tubegalore",
        "4tube", "fuqer", "empflix", "alphaporno", "porndoe", "porndig", "pornhat", "pornid",
        "onlyfans", "fansly", "manyvids", "clips4sale", "justforfans",
        // leak and aggregator ecosystems
        "thothub", "coomer", "kemono", "fapello", "leakedzone", "nudostar", "dirtyship",
        "simpcity", "influencersgonewild", "gonewild", "imagefap", "imagetwist",
        // anime / manga adult
        "gelbooru", "danbooru", "safebooru", "doujin", "nhentai", "javhd", "javbus",
        "javlibrary", "missav", "sukebei", "javhihi", "javmost", "javgg", "paheal", "fakku",
        "nutaku", "lewdcorner", "f95zone",
        "e621", "yiff", "nsfwhub", "leaklinks",
        "ahegao", "futanari", "paizuri", "oppai", "nakadashi", "gokkun", "lolicon", "shotacon",
        "netorare",
        // Major tube/cam/studio brands seen in current traffic rankings. Distinctive tokens only:
        // a generic phrase here would poison ordinary searches the way "hardcore" did.
        "erome", "jerkmate", "flirt4free", "pimpbunny", "luxuretv", "superchatlive",
        "qorno", "aznude", "sexkomix", "dlsite", "hanime",
        // Distinctive site brands that are not already covered by a stem above. Dotted or
        // two-word brand forms that could collide with ordinary text live in AdultKeywords.
        "fetlife", "babepedia", "listcrawler", "fabswingers", "xxbrits",
        "promptchan", "streamate", "camfuze", "njavtv", "xgroovy",
        "videoxx", "bakusai", "clothoff", "n8ked", "chatiw",
        // Spelled with the TLD on purpose. "motherless" on its own is an ordinary English word,
        // and as a stem it blocked "sometimes i feel like a motherless child lyrics".
        "motherlesscom"
    )
}
