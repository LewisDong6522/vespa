// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.text.Cursor;
import com.yahoo.vespa.hosted.node.admin.task.util.text.CursorRange;

import java.util.Optional;

/**
 * Parses a template String, see {@link Template} for details.
 *
 * @author hakonhall
 */
public class TemplateParser {
    private final TemplateDescriptor descriptor;
    private final Cursor start;
    private final Cursor current;
    private final Template template;
    private final FormEndsIn formEndsIn;

    public static Template parse(TemplateDescriptor descriptor, String text) {
        return parse(new TemplateDescriptor(descriptor), new Cursor(text), FormEndsIn.EOT).template;
    }

    private static TemplateParser parse(TemplateDescriptor descriptor, Cursor start, FormEndsIn formEndsIn) {
        var parser = new TemplateParser(descriptor, start, formEndsIn);
        parser.parse();
        return parser;
    }

    enum FormEndsIn { EOT, END }

    TemplateParser(TemplateDescriptor descriptor, Cursor start, FormEndsIn formEndsIn) {
        this.descriptor = descriptor;
        this.start = new Cursor(start);
        this.current = new Cursor(start);
        this.template = new Template(start);
        this.formEndsIn = formEndsIn;
    }

    CursorRange range() { return new CursorRange(start, current); }
    Template template() { return template; }

    private void parse() {
        do {
            current.advanceTo(descriptor.startDelimiter());
            if (!current.equals(start)) {
                template.appendLiteralSection(current);
            }

            if (current.eot()) return;

            if (!parseSection()) return;
        } while (true);
    }

    /** Returns true if end was reached (according to formEndsIn). */
    private boolean parseSection() {
        current.skip(descriptor.startDelimiter());

        if (current.skip('=')) {
            parseVariableSection();
        } else {
            var startOfType = new Cursor(current);
            String type = skipId().orElseThrow(() -> new BadTemplateException(current, "Missing section name"));

            switch (type) {
                case "end":
                    if (formEndsIn == FormEndsIn.EOT)
                        throw new BadTemplateException(startOfType, "Extraneous 'end'");
                    parseEndDirective();
                    return false;
                case "list":
                    parseListSection();
                    break;
                default:
                    throw new BadTemplateException(startOfType, "Unknown section '" + type + "'");
            }
        }

        return !current.eot();
    }

    private void parseVariableSection() {
        var nameStart = new Cursor(current);
        String name = parseId();
        parseEndDelimiter(false);
        template.appendVariableSection(name, nameStart, current);
    }

    private void parseEndDirective() {
        parseEndDelimiter(true);
    }

    private void parseListSection() {
        skipWhitespace();
        var startOfName = new Cursor(current);
        String name = parseId();
        parseEndDelimiter(true);

        TemplateParser bodyParser = parse(descriptor, current, FormEndsIn.END);
        current.set(bodyParser.current);

        template.appendListSection(name, startOfName, current, bodyParser.template());
    }

    private void skipWhitespace() {
        if (!current.skipWhitespace()) {
            throw new BadTemplateException(current, "Expected whitespace");
        }
    }

    private String parseId() {
        return skipId().orElseThrow(() -> new BadTemplateException(current, "Expected identifier"));
    }

    private Optional<String> skipId() { return Token.skipId(current); }

    private void parseEndDelimiter(boolean newlineMayBeRemoved) {
        if (!current.skip(descriptor.endDelimiter()))
            throw new BadTemplateException(current, "Expected section end (" + descriptor.endDelimiter() + ")");

        if (descriptor.removeNewlineAfterSection() && newlineMayBeRemoved)
            current.skip('\n');
    }
}
