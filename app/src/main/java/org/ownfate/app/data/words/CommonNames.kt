package org.ownfate.app.data.words

/**
 * Names that are also ordinary people or public figures.
 *
 * A bare name is never enough. These are a SOFT signal and only block when the page also
 * carries porn-shaped context. A string listed here and in [AdultPerformers] resolves to the
 * common name, because the matcher prefers the soft entry.
 */
object CommonNames {
    val terms = listOf(
        "AJ Lee", "Alex Adams", "Alex Grey", "Alex Harper", "Alex Jones", "Alex Mack", "Alexa Vega",
        "Alice Green", "Alice Kelly", "Alice Miller", "Alicia Silverstone", "Amy Lee",
        "Andreea Mantea", "Andrew Marshall", "Ashley Graham", "Ashley Lane", "Beverly Hills",
        "Bill Bailey", "Bobbi Dylan", "Cameron Canada", "Carla Brown", "Carly Rae", "Charlie Dean",
        "Chris Johnson", "Claire Adams", "Claudia Garcia", "David Lee", "Diana Prince", "Diego Perez",
        "Emily Parker", "Emma Brown", "Faith Adams", "Gabby Smith", "Gabriela Lopez", "Hillary Scott",
        "Jack Napier", "Jason Brown", "John Price", "Justin Long", "Kate Quinn", "Kathy Anderson",
        "Katie Morgan", "Kelli Smith", "Lara Croft", "LaToya Jackson", "Leslie Taylor", "MC Lyte",
        "Michael Chapman", "Michelle Anderson", "Michelle Monaghan", "Morgan Lee", "Oliver Davis",
        "Peter Green", "Princess Leia", "Rachael Leigh Cook", "Rebecca Black", "Ricky Johnson",
        "Rose Carter", "Ryan Conner", "Sarah James", "Sharon White", "Sophia Grace", "Sophia Smith",
        "Stanley Johnson", "Steve Holmes", "Summer Day", "Thomas Lee", "Thomas Stone", "Tony Martinez",
        "Victor Ray", "Wendy Moon", "Xavier Miller",
        // Ordinary names and phrases moved out of AdultPerformers: they are still SOFT signals,
        // but a bare mention no longer ejects the page without a second signal and an appeal.
        "Ashley Adams", "Belladonna", "Emma Louise", "Grandma Libby", "Honey Moon", "Ice Cold",
        "Jessica Miller", "Michael Fly", "Michelle Martinez", "morning star", "Mary Jane",
        "Olivia Grace", "Queen B", "Rachel James", "Red Fox", "The Red Fox", "Sara Khan",
        "Sarah Banks", "Small Hands", "Sugar Baby", "Sunny Day", "Sweet Cat", "The Pope",
        "Tiffany Taylor", "Uncle George",
    )
}
