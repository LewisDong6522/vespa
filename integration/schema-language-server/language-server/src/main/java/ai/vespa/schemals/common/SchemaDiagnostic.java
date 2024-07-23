package ai.vespa.schemals.common;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.index.Symbol;

/**
 * SchemaDiagnostic
 */
public class SchemaDiagnostic {
    public static enum DiagnosticCode {
        GENERIC,
        SCHEMA_NAME_SAME_AS_FILE,
        DOCUMENT_NAME_SAME_AS_SCHEMA,
        ACCESS_UNIMPORTED_FIELD
    };

    /**
     * Cache of enum values 
     */
    private final static DiagnosticCode[] diagnosticCodeValues = DiagnosticCode.values();

    public static DiagnosticCode codeFromInt(Integer value) {
        if (value == null || value < 0 || value > diagnosticCodeValues.length) return DiagnosticCode.GENERIC;
        return diagnosticCodeValues[value];
    }

    public static class Builder {
        private String message;
        private DiagnosticCode code;
        private DiagnosticSeverity severity;
        private Range range;

        public Builder() {
            this.message = "Unknown error.";
            this.code = DiagnosticCode.GENERIC;
            this.severity = DiagnosticSeverity.Error;
        }

        public Builder setMessage(String message) {
            this.message = message;
            return this;
        }

        public Builder setCode(DiagnosticCode code) {
            this.code = code;
            return this;
        }

        public Builder setSeverity(DiagnosticSeverity severity) {
            this.severity = severity;
            return this;
        }

        public Builder setRange(Range range) {
            this.range = range;
            return this;
        }

        public Diagnostic build() {
            Diagnostic diagnostic = new Diagnostic(this.range, this.message, this.severity, "");
            diagnostic.setCode(this.code.ordinal());
            diagnostic.setSource("schemals");
            return diagnostic;
        }
    }
}
