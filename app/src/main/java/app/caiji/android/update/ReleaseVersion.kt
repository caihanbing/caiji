package app.caiji.android.update

internal data class ReleaseVersion(
    val major: Long,
    val minor: Long,
    val patch: Long
) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int =
        compareValuesBy(this, other, ReleaseVersion::major, ReleaseVersion::minor, ReleaseVersion::patch)

    companion object {
        private val tagPattern = Regex("^v(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$")

        fun parse(tag: String): ReleaseVersion? {
            val match = tagPattern.matchEntire(tag) ?: return null
            return runCatching {
                ReleaseVersion(
                    match.groupValues[1].toLong(),
                    match.groupValues[2].toLong(),
                    match.groupValues[3].toLong()
                )
            }.getOrNull()
        }
    }
}
