package com.zeravrix.zeravrix.source

/** Entry point implemented by installable source JARs. */
interface SourceProvider {
    fun create(): Source
}
