package org.client.poc

import groovy.transform.Canonical
import groovy.transform.CompileStatic

@Canonical
@CompileStatic
class Book {
    String title
    String author
}
