package org.ownfate.app.data.words

/**
 * The second signal that turns a soft match into a block.
 *
 * Shaped like porn rather than like the internet: "free", "watch", "best" and
 * "download" are deliberately absent, because "best naked eye telescope" is a real search and
 * it has to survive.
 */
object ContextTerms {
    /**
     * The second signal. Deliberately shaped like porn rather than like the internet: "free",
     * "watch", "hot", "best" and "download" were considered and left out, because "best naked eye
     * telescope" is a real search and it has to survive.
     */
    val terms = listOf(
        "video", "videos", "vid", "vids", "hd", "xxx", "pic", "pics", "photo", "photos",
        "gif", "gifs", "clip", "clips", "tube", "webcam", "cam", "cams", "stream", "leaked",
        "teen", "teens", "milf", "onlyfans", "nsfw",
        "uncensored","explicit","xxxvideo","xxxvideos","nude pics","naked pics", "premium",
        "adult entertainment","private videos","private photos", "telegram", "discord", "unfiltered"
    )
}
