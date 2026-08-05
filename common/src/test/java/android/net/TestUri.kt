package android.net

import android.os.Parcel

class TestUri(private val value: String) : Uri() {

    override fun isHierarchical() = true

    override fun isRelative() = false

    override fun getScheme() = null

    override fun getSchemeSpecificPart() = value

    override fun getEncodedSchemeSpecificPart() = value

    override fun getAuthority() = null

    override fun getEncodedAuthority() = null

    override fun getUserInfo() = null

    override fun getEncodedUserInfo() = null

    override fun getHost() = null

    override fun getPort() = -1

    override fun getPath() = null

    override fun getEncodedPath() = null

    override fun getQuery() = null

    override fun getEncodedQuery() = null

    override fun getFragment() = null

    override fun getEncodedFragment() = null

    override fun getPathSegments() = emptyList<String>()

    override fun getLastPathSegment() = null

    override fun toString() = value

    override fun buildUpon(): Builder? = null

    override fun describeContents() = 0

    override fun writeToParcel(destination: Parcel, flags: Int) = Unit
}
