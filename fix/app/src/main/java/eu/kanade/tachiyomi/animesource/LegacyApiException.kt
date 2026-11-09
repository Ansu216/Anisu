package eu.kanade.tachiyomi.animesource

/**
 * Thrown by members that exist only to keep extensions of every library version linking. Whichever
 * generation of the API an extension was built for, the members it does not implement end up here and
 * the host falls back to the other generation instead of crashing.
 */
class NotImplementedByExtensionException(member: String) :
    UnsupportedOperationException("The extension does not implement $member")
