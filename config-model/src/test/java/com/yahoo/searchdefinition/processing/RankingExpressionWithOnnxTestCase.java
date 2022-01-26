// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.io.IOUtils;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.path.Path;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.ml.ImportedModelTester;
import com.yahoo.yolean.Exceptions;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class RankingExpressionWithOnnxTestCase {

    private final Path applicationDir = Path.fromString("src/test/integration/onnx/");

    private final static String name = "mnist_softmax";
    private final static String vespaExpression = "join(reduce(join(rename(Placeholder, (d0, d1), (d0, d2)), constant(mnist_softmax_layer_Variable), f(a,b)(a * b)), sum, d2) * 1.0, constant(mnist_softmax_layer_Variable_1) * 1.0, f(a,b)(a + b))";

    @After
    public void removeGeneratedModelFiles() {
        IOUtils.recursiveDeleteDir(applicationDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
    }

    @Test
    public void testOnnxReferenceWithConstantFeature() {
        RankProfileSearchFixture search = fixtureWith("constant(mytensor)",
                "onnx_vespa('mnist_softmax.onnx')",
                "constant mytensor { file: ignored\ntype: tensor<float>(d0[1],d1[784]) }",
                null);
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");
    }

    @Test
    public void testOnnxReferenceWithQueryFeature() {
        String queryProfile = "<query-profile id='default' type='root'/>";
        String queryProfileType =
                "<query-profile-type id='root'>" +
                "  <field name='query(mytensor)' type='tensor&lt;float&gt;(d0[1],d1[784])'/>" +
                "</query-profile-type>";
        StoringApplicationPackage application = new StoringApplicationPackage(applicationDir,
                queryProfile,
                queryProfileType);
        RankProfileSearchFixture search = fixtureWith("query(mytensor)",
                "onnx_vespa('mnist_softmax.onnx')",
                null,
                null,
                "Placeholder",
                application);
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");
    }

    @Test
    public void testOnnxReferenceWithDocumentFeature() {
        StoringApplicationPackage application = new StoringApplicationPackage(applicationDir);
        RankProfileSearchFixture search = fixtureWith("attribute(mytensor)",
                "onnx_vespa('mnist_softmax.onnx')",
                null,
                "field mytensor type tensor<float>(d0[1],d1[784]) { indexing: attribute }",
                "Placeholder",
                application);
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");
    }


    @Test
    public void testOnnxReferenceWithFeatureCombination() {
        String queryProfile = "<query-profile id='default' type='root'/>";
        String queryProfileType =
                "<query-profile-type id='root'>" +
                "  <field name='query(mytensor)' type='tensor&lt;float&gt;(d0[1],d1[784],d2[10])'/>" +
                "</query-profile-type>";
        StoringApplicationPackage application = new StoringApplicationPackage(applicationDir, queryProfile, queryProfileType);
        RankProfileSearchFixture search = fixtureWith("sum(query(mytensor) * attribute(mytensor) * constant(mytensor),d2)",
                "onnx_vespa('mnist_softmax.onnx')",
                "constant mytensor { file: ignored\ntype: tensor<float>(d0[1],d1[784]) }",
                "field mytensor type tensor<float>(d0[1],d1[784]) { indexing: attribute }",
                "Placeholder",
                application);
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");
    }


    @Test
    public void testNestedOnnxReference() {
        RankProfileSearchFixture search = fixtureWith("tensor<float>(d0[1],d1[784])(0.0)",
                "5 + sum(onnx_vespa('mnist_softmax.onnx'))");
        search.assertFirstPhaseExpression("5 + reduce(" + vespaExpression + ", sum)", "my_profile");
    }

    @Test
    public void testOnnxReferenceWithSpecifiedOutput() {
        RankProfileSearchFixture search = fixtureWith("tensor<float>(d0[1],d1[784])(0.0)",
                "onnx_vespa('mnist_softmax.onnx', 'layer_add')");
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");
    }

    @Test
    public void testOnnxReferenceWithSpecifiedOutputAndSignature() {
        RankProfileSearchFixture search = fixtureWith("tensor<float>(d0[1],d1[784])(0.0)",
                "onnx_vespa('mnist_softmax.onnx', 'default.layer_add')");
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");
    }

    @Test
    public void testOnnxReferenceMissingFunction() throws ParseException {
        try {
            RankProfileSearchFixture search = new RankProfileSearchFixture(
                    new StoringApplicationPackage(applicationDir),
                    new QueryProfileRegistry(),
                    "  rank-profile my_profile {\n" +
                            "    first-phase {\n" +
                            "      expression: onnx_vespa('mnist_softmax.onnx')" +
                            "    }\n" +
                            "  }");
            search.compileRankProfile("my_profile", applicationDir.append("models"));
            search.assertFirstPhaseExpression(vespaExpression, "my_profile");
            fail("Expecting exception");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("Rank profile 'my_profile' is invalid: Could not use Onnx model from " +
                            "onnx_vespa(\"mnist_softmax.onnx\"): " +
                            "Model refers input 'Placeholder' of type tensor<float>(d0[1],d1[784]) but this function is " +
                            "not present in rank profile 'my_profile'",
                    Exceptions.toMessageString(expected));
        }
    }

    @Test
    public void testOnnxReferenceWithWrongFunctionType() {
        try {
            RankProfileSearchFixture search = fixtureWith("tensor(d0[1],d5[10])(0.0)",
                    "onnx_vespa('mnist_softmax.onnx')");
            search.assertFirstPhaseExpression(vespaExpression, "my_profile");
            fail("Expecting exception");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("Rank profile 'my_profile' is invalid: Could not use Onnx model from " +
                            "onnx_vespa(\"mnist_softmax.onnx\"): " +
                            "Model refers input 'Placeholder'. The required type of this is tensor<float>(d0[1],d1[784]), " +
                            "but this function returns tensor(d0[1],d5[10])",
                    Exceptions.toMessageString(expected));
        }
    }

    @Test
    public void testOnnxReferenceSpecifyingNonExistingOutput() {
        try {
            RankProfileSearchFixture search = fixtureWith("tensor<float>(d0[2],d1[784])(0.0)",
                    "onnx_vespa('mnist_softmax.onnx', 'y')");
            search.assertFirstPhaseExpression(vespaExpression, "my_profile");
            fail("Expecting exception");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("Rank profile 'my_profile' is invalid: Could not use Onnx model from " +
                         "onnx_vespa(\"mnist_softmax.onnx\",\"y\"): " +
                         "No expressions named 'y' in model 'mnist_softmax.onnx'. Available expressions: default.layer_add",
                         Exceptions.toMessageString(expected));
        }
    }

    @Test
    public void testImportingFromStoredExpressions() throws IOException {
        RankProfileSearchFixture search = fixtureWith("tensor<float>(d0[1],d1[784])(0.0)",
                "onnx_vespa(\"mnist_softmax.onnx\")");
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");

        // At this point the expression is stored - copy application to another location which do not have a models dir
        Path storedApplicationDirectory = applicationDir.getParentPath().append("copy");
        try {
            storedApplicationDirectory.toFile().mkdirs();
            IOUtils.copyDirectory(applicationDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile(),
                    storedApplicationDirectory.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
            StoringApplicationPackage storedApplication = new StoringApplicationPackage(storedApplicationDirectory);
            RankProfileSearchFixture searchFromStored = fixtureWith("tensor<float>(d0[2],d1[784])(0.0)",
                    "onnx_vespa('mnist_softmax.onnx')",
                    null,
                    null,
                    "Placeholder",
                    storedApplication);
            searchFromStored.assertFirstPhaseExpression(vespaExpression, "my_profile");
            // Verify that the constants exists, but don't verify the content as we are not
            // simulating file distribution in this test
        }
        finally {
            IOUtils.recursiveDeleteDir(storedApplicationDirectory.toFile());
        }
    }

    @Test
    public void testImportingFromStoredExpressionsWithFunctionOverridingConstantAndInheritance() throws IOException {
        String rankProfile =
                "  rank-profile my_profile {\n" +
                        "    function Placeholder() {\n" +
                        "      expression: tensor<float>(d0[1],d1[784])(0.0)\n" +
                        "    }\n" +
                        "    function " + name + "_layer_Variable() {\n" +
                        "      expression: tensor<float>(d1[10],d2[784])(0.0)\n" +
                        "    }\n" +
                        "    first-phase {\n" +
                        "      expression: onnx_vespa('mnist_softmax.onnx')" +
                        "    }\n" +
                        "  }" +
                        "  rank-profile my_profile_child inherits my_profile {\n" +
                        "  }";

        String vespaExpressionWithoutConstant =
                "join(reduce(join(rename(Placeholder, (d0, d1), (d0, d2)), " + name + "_layer_Variable, f(a,b)(a * b)), sum, d2) * 1.0, constant(" + name + "_layer_Variable_1) * 1.0, f(a,b)(a + b))";
        RankProfileSearchFixture search = uncompiledFixtureWith(rankProfile, new StoringApplicationPackage(applicationDir));
        search.compileRankProfile("my_profile", applicationDir.append("models"));
        search.compileRankProfile("my_profile_child", applicationDir.append("models"));
        search.assertFirstPhaseExpression(vespaExpressionWithoutConstant, "my_profile");
        search.assertFirstPhaseExpression(vespaExpressionWithoutConstant, "my_profile_child");

        assertNull("Constant overridden by function is not added",
                search.search().rankingConstants().get( name + "_Variable"));

        // At this point the expression is stored - copy application to another location which do not have a models dir
        Path storedApplicationDirectory = applicationDir.getParentPath().append("copy");
        try {
            storedApplicationDirectory.toFile().mkdirs();
            IOUtils.copyDirectory(applicationDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile(),
                    storedApplicationDirectory.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
            StoringApplicationPackage storedApplication = new StoringApplicationPackage(storedApplicationDirectory);
            RankProfileSearchFixture searchFromStored = uncompiledFixtureWith(rankProfile, storedApplication);
            searchFromStored.compileRankProfile("my_profile", applicationDir.append("models"));
            searchFromStored.compileRankProfile("my_profile_child", applicationDir.append("models"));
            searchFromStored.assertFirstPhaseExpression(vespaExpressionWithoutConstant, "my_profile");
            searchFromStored.assertFirstPhaseExpression(vespaExpressionWithoutConstant, "my_profile_child");
            assertNull("Constant overridden by function is not added",
                       searchFromStored.search().rankingConstants().get( name + "_Variable"));
        } finally {
            IOUtils.recursiveDeleteDir(storedApplicationDirectory.toFile());
        }
    }

    @Test
    public void testFunctionGeneration() {
        final String name = "small_constants_and_functions";
        final String rankProfiles =
                "  rank-profile my_profile {\n" +
                        "    function input() {\n" +
                        "      expression: tensor<float>(d0[3])(0.0)\n" +
                        "    }\n" +
                        "    first-phase {\n" +
                        "      expression: onnx_vespa('" + name + ".onnx')" +
                        "    }\n" +
                        "  }";
        final String functionName = "imported_ml_function_" + name + "_exp_output";
        final String expression = "join(" + functionName + ", reduce(join(join(reduce(" + functionName + ", sum, d0), tensor<float>(d0[1])(1.0), f(a,b)(a * b)), constant(" + name + "_epsilon), f(a,b)(a + b)), sum, d0), f(a,b)(a / b))";
        final String functionExpression = "map(input, f(a)(exp(a)))";

        RankProfileSearchFixture search = uncompiledFixtureWith(rankProfiles, new StoringApplicationPackage(applicationDir));
        search.compileRankProfile("my_profile", applicationDir.append("models"));
        search.assertFirstPhaseExpression(expression, "my_profile");
        search.assertFunction(functionExpression, functionName, "my_profile");
    }

    @Test
    public void testImportingFromStoredExpressionsWithSmallConstantsAndInheritance() throws IOException {
        final String name = "small_constants_and_functions";
        final String rankProfiles =
                "  rank-profile my_profile {\n" +
                        "    function input() {\n" +
                        "      expression: tensor<float>(d0[3])(0.0)\n" +
                        "    }\n" +
                        "    first-phase {\n" +
                        "      expression: onnx_vespa('" + name + ".onnx')" +
                        "    }\n" +
                        "  }" +
                        "  rank-profile my_profile_child inherits my_profile {\n" +
                        "  }";
        final String functionName = "imported_ml_function_" + name + "_exp_output";
        final String expression = "join(" + functionName + ", reduce(join(join(reduce(" + functionName + ", sum, d0), tensor<float>(d0[1])(1.0), f(a,b)(a * b)), constant(" + name + "_epsilon), f(a,b)(a + b)), sum, d0), f(a,b)(a / b))";
        final String functionExpression = "map(input, f(a)(exp(a)))";

        RankProfileSearchFixture search = uncompiledFixtureWith(rankProfiles, new StoringApplicationPackage(applicationDir));
        search.compileRankProfile("my_profile", applicationDir.append("models"));
        search.compileRankProfile("my_profile_child", applicationDir.append("models"));
        search.assertFirstPhaseExpression(expression, "my_profile");
        search.assertFirstPhaseExpression(expression, "my_profile_child");
        assertSmallConstant(name + "_epsilon", TensorType.fromSpec("tensor()"), search);
        search.assertFunction(functionExpression, functionName, "my_profile");
        search.assertFunction(functionExpression, functionName, "my_profile_child");

        // At this point the expression is stored - copy application to another location which do not have a models dir
        Path storedApplicationDirectory = applicationDir.getParentPath().append("copy");
        try {
            storedApplicationDirectory.toFile().mkdirs();
            IOUtils.copyDirectory(applicationDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile(),
                    storedApplicationDirectory.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
            StoringApplicationPackage storedApplication = new StoringApplicationPackage(storedApplicationDirectory);
            RankProfileSearchFixture searchFromStored = uncompiledFixtureWith(rankProfiles, storedApplication);
            searchFromStored.compileRankProfile("my_profile", applicationDir.append("models"));
            searchFromStored.compileRankProfile("my_profile_child", applicationDir.append("models"));
            searchFromStored.assertFirstPhaseExpression(expression, "my_profile");
            searchFromStored.assertFirstPhaseExpression(expression, "my_profile_child");
            assertSmallConstant(name + "_epsilon", TensorType.fromSpec("tensor()"), search);
            searchFromStored.assertFunction(functionExpression, functionName, "my_profile");
            searchFromStored.assertFunction(functionExpression, functionName, "my_profile_child");
        }
        finally {
            IOUtils.recursiveDeleteDir(storedApplicationDirectory.toFile());
        }
    }

    private void assertSmallConstant(String name, TensorType type, RankProfileSearchFixture search) {
        Value value = search.compiledRankProfile("my_profile").getConstants().get(name);
        assertNotNull(value);
        assertEquals(type, value.type());
    }

    private RankProfileSearchFixture fixtureWith(String placeholderExpression, String firstPhaseExpression) {
        return fixtureWith(placeholderExpression, firstPhaseExpression, null, null, "Placeholder",
                           new StoringApplicationPackage(applicationDir));
    }

    private RankProfileSearchFixture fixtureWith(String placeholderExpression, String firstPhaseExpression,
                                                 String constant, String field) {
        return fixtureWith(placeholderExpression, firstPhaseExpression, constant, field, "Placeholder",
                           new StoringApplicationPackage(applicationDir));
    }

    private RankProfileSearchFixture uncompiledFixtureWith(String rankProfile, StoringApplicationPackage application) {
        try {
            return new RankProfileSearchFixture(application, application.getQueryProfiles(),
                                                rankProfile, null, null);
        }
        catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private RankProfileSearchFixture fixtureWith(String functionExpression,
                                                 String firstPhaseExpression,
                                                 String constant,
                                                 String field,
                                                 String functionName,
                                                 StoringApplicationPackage application) {
        try {
            RankProfileSearchFixture fixture = new RankProfileSearchFixture(
                    application,
                    application.getQueryProfiles(),
                    "  rank-profile my_profile {\n" +
                            "    function " + functionName + "() {\n" +
                            "      expression: " + functionExpression +
                            "    }\n" +
                            "    first-phase {\n" +
                            "      expression: " + firstPhaseExpression +
                            "    }\n" +
                            "  }",
                    constant,
                    field);
            fixture.compileRankProfile("my_profile", applicationDir.append("models"));
            return fixture;
        }
        catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static class StoringApplicationPackage extends MockApplicationPackage {

        StoringApplicationPackage(Path applicationPackageWritableRoot) {
            this(applicationPackageWritableRoot, null, null);
        }

        StoringApplicationPackage(Path applicationPackageWritableRoot, String queryProfile, String queryProfileType) {
            super(new File(applicationPackageWritableRoot.toString()),
                    null, null, Collections.emptyList(), null,
                    null, null, false, queryProfile, queryProfileType);
        }

        @Override
        public ApplicationFile getFile(Path file) {
            return new MockApplicationFile(file, Path.fromString(root().toString()));
        }

        @Override
        public List<NamedReader> getFiles(Path path, String suffix) {
            File[] files = getFileReference(path).listFiles();
            if (files == null)  return List.of();
            List<NamedReader> readers = new ArrayList<>();
            for (File file : files) {
                if ( ! file.getName().endsWith(suffix)) continue;
                try {
                    readers.add(new NamedReader(file.getName(), new FileReader(file)));
                }
                catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            return readers;
        }

    }


}
