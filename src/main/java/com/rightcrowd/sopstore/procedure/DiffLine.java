package com.rightcrowd.sopstore.procedure;

/**
 * One row of a side-by-side version diff. {@code tag} is one of EQUAL / INSERT / DELETE / CHANGE;
 * {@code oldLine} / {@code newLine} are HTML-escaped content for the left/right columns.
 */
public record DiffLine(String tag, String oldLine, String newLine) {}
