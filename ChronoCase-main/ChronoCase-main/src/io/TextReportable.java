package io;

import java.util.List;

/**
 * Something that can render itself as a plain-text report, one line
 * per entry. Implemented by the case, the investigation session and
 * the solver result, letting the FileManager export any of them
 * without depending on their internals. Model classes stay free of
 * file I/O; they only expose data or rendering.
 */
public interface TextReportable {

    /** The report lines, without any file header. */
    List<String> renderReport();
}
