// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.searchdefinition.document.Stemming;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.searchdefinition.processing.ImportedFieldsResolver;
import com.yahoo.searchdefinition.processing.OnnxModelTypeResolver;
import com.yahoo.vespa.model.test.utils.DeployLoggerStub;
import org.junit.Test;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Schema tests that don't depend on files.
 *
 * @author bratseth
 */
public class SchemaTestCase {

    @Test
    public void testValidationOfInheritedSchema() throws ParseException {
        try {
            String schema = joinLines(
                    "schema test inherits nonesuch {" +
                    "  document test inherits nonesuch {" +
                    "  }" +
                    "}");
            DeployLoggerStub logger = new DeployLoggerStub();
            SearchBuilder.createFromStrings(logger, schema);
            assertEquals("schema 'test' inherits 'nonesuch', but this schema does not exist",
                         logger.entries.get(0).message);
            fail("Expected failure");
        }
        catch (IllegalArgumentException e) {
            assertEquals("schema 'test' inherits 'nonesuch', but this schema does not exist", e.getMessage());
        }
    }

    @Test
    public void testValidationOfSchemaAndDocumentInheritanceConsistency() throws ParseException {
        try {
            String parent = joinLines(
                    "schema parent {" +
                    "  document parent {" +
                    "    field pf1 type string {" +
                    "      indexing: summary" +
                    "    }" +
                    "  }" +
                    "}");
            String child = joinLines(
                    "schema child inherits parent {" +
                    "  document child {" +
                    "    field cf1 type string {" +
                    "      indexing: summary" +
                    "    }" +
                    "  }" +
                    "}");
            SearchBuilder.createFromStrings(new DeployLoggerStub(), parent, child);
        }
        catch (IllegalArgumentException e) {
            assertEquals("schema 'child' inherits 'parent', " +
                         "but its document type does not inherit the parent's document type"
                         , e.getMessage());
        }
    }

    @Test
    public void testSchemaInheritance() throws ParseException {
        String parentLines = joinLines(
                "schema parent {" +
                "  document parent {" +
                "    field pf1 type string {" +
                "      indexing: summary" +
                "    }" +
                "  }" +
                "  fieldset parent_set {" +
                "    fields: pf1" +
                "  }" +
                "  stemming: none" +
                "  index parent_index {" +
                "    stemming: best" +
                "  }" +
                "  field parent_field type string {" +
                "      indexing: input pf1 | lowercase | index | attribute | summary" +
                "  }" +
                "  rank-profile parent_profile {" +
                "  }" +
                "  constant parent_constant {" +
                "    file: constants/my_constant_tensor_file.json" +
                "    type: tensor<float>(x{},y{})" +
                "  }" +
                "  onnx-model parent_model {" +
                "    file: models/my_model.onnx" +
                "  }" +
                "  document-summary parent_summary {" +
                "    summary pf1 type string {}" +
                "  }" +
                "  import field parentschema_ref.name as parent_imported {}" +
                "  raw-as-base64-in-summary" +
                "}");
        String childLines = joinLines(
                "schema child inherits parent {" +
                "  document child inherits parent {" +
                "    field cf1 type string {" +
                "      indexing: summary" +
                "    }" +
                "  }" +
                "}");
        String grandchildLines = joinLines(
                "schema grandchild inherits child {" +
                "  document grandchild inherits child {" +
                "    field gf1 type string {" +
                "      indexing: summary" +
                "    }" +
                "  }" +
                "}");

        SearchBuilder builder = new SearchBuilder(new DeployLoggerStub());
        builder.processorsToSkip().add(OnnxModelTypeResolver.class); // Avoid discovering the Onnx model referenced does not exist
        builder.processorsToSkip().add(ImportedFieldsResolver.class); // Avoid discovering the document reference leads nowhere
        builder.importString(parentLines);
        builder.importString(childLines);
        builder.importString(grandchildLines);
        builder.build(true);
        var application = builder.application();

        assertInheritedFromParent(application.schemas().get("child"), application);
        assertInheritedFromParent(application.schemas().get("grandchild"), application);
    }

    private void assertInheritedFromParent(Schema schema, Application application) {
        assertEquals("pf1", schema.fieldSets().userFieldSets().get("parent_set").getFieldNames().stream().findFirst().get());
        assertEquals(Stemming.NONE, schema.getStemming());
        assertEquals(Stemming.BEST, schema.getIndex("parent_index").getStemming());
        assertNotNull(schema.getField("parent_field"));
        assertNotNull(schema.getExtraField("parent_field"));
        assertNotNull(application.rankProfileRegistry().get(schema, "parent_profile"));
        assertNotNull(schema.rankingConstants().get("parent_constant"));
        assertTrue(schema.rankingConstants().asMap().containsKey("parent_constant"));
        assertNotNull(schema.onnxModels().get("parent_model"));
        assertTrue(schema.onnxModels().asMap().containsKey("parent_model"));
        assertNotNull(schema.getSummary("parent_summary"));
        assertTrue(schema.getSummaries().containsKey("parent_summary"));
        assertNotNull(schema.getSummaryField("pf1"));
        assertNotNull(schema.getExplicitSummaryField("pf1"));
        assertNotNull(schema.getUniqueNamedSummaryFields().get("pf1"));
        assertTrue(schema.temporaryImportedFields().isPresent());
        assertNotNull(schema.temporaryImportedFields().get().fields().get("parent_imported"));
        assertTrue(schema.isRawAsBase64());
    }

}
