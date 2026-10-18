package com.agentscope.codewiki.parser;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TreeSitterSymbolExtractorTest {

    private static TreeSitterSymbolExtractor extractor;

    @BeforeAll
    static void setUp() throws Exception {
        URL scriptUrl =
                TreeSitterSymbolExtractorTest.class
                        .getClassLoader()
                        .getResource("tree-sitter-bridge/extract-symbols.mjs");
        assertNotNull(scriptUrl, "Bridge script not found on classpath");
        Path scriptPath = Path.of(scriptUrl.getPath());

        // Verify node_modules exist
        Path nodeModules = scriptPath.getParent().resolve("node_modules");
        if (!Files.isDirectory(nodeModules)) {
            fail(
                    "node_modules not found. Run: cd "
                            + scriptPath.getParent()
                            + " && npm install --legacy-peer-deps");
        }

        extractor = new TreeSitterSymbolExtractor(scriptPath);
    }

    private Path samplePath(String name) throws Exception {
        URL url = getClass().getClassLoader().getResource("samples/" + name);
        assertNotNull(url, "Sample file not found: " + name);
        return Path.of(url.getPath());
    }

    @Test
    void shouldExtractSeveralFilesWithOneBridgeProcess() throws Exception {
        List<CodeSymbol> symbols =
                extractor.extractBatch(List.of(samplePath("Sample.java"), samplePath("sample.py")));
        Set<String> names = symbols.stream().map(CodeSymbol::getName).collect(Collectors.toSet());
        assertTrue(names.contains("Calculator"));
        assertTrue(names.contains("Greeter"));
        assertTrue(symbols.stream().allMatch(s -> s.getFilePath() != null));
    }

    @Test
    void shouldRejectUnsupportedExtension() {
        assertThrows(
                IllegalArgumentException.class, () -> extractor.extract(Path.of("test.unknown")));
    }

    @Test
    void shouldExtractJavaAstCallSitesAndOwningClass() throws Exception {
        Path file = Files.createTempFile("codewiki-java-ast-", ".java");
        try {
            Files.writeString(
                    file,
                    "import org.billing.BillingService;\nclass OrderController {\n"
                            + "  void submit() { process(); BillingService.charge(); }\n"
                            + "  void process() {}\n"
                            + "  String example = \"fake()\";\n"
                            + "}\n");
            List<CodeSymbol> symbols = extractor.extract(file);
            CodeSymbol method =
                    symbols.stream()
                            .filter(s -> "submit".equals(s.getName()))
                            .findFirst()
                            .orElseThrow();
            assertEquals("OrderController", method.getOwnerType());
            assertNotNull(method.getReferences());
            assertEquals(2, method.getReferences().size());
            assertEquals("process", method.getReferences().get(0).name());
            assertNull(method.getReferences().get(0).receiver());
            assertEquals("charge", method.getReferences().get(1).name());
            assertEquals("BillingService", method.getReferences().get(1).receiver());
            assertEquals(3, method.getReferences().get(1).line());
            assertTrue(method.getImports().contains("import org.billing.BillingService;"));
            CodeSymbol clazz =
                    symbols.stream()
                            .filter(s -> "OrderController".equals(s.getName()))
                            .findFirst()
                            .orElseThrow();
            assertTrue(clazz.getReferences().isEmpty());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void shouldExtractJavaSymbols() throws Exception {
        Path file = samplePath("Sample.java");
        List<CodeSymbol> symbols = extractor.extract(file);

        assertFalse(symbols.isEmpty(), "Should extract at least one symbol");

        Set<String> names = symbols.stream().map(CodeSymbol::getName).collect(Collectors.toSet());
        assertTrue(names.contains("Calculator"), "Should find Calculator class");
        assertTrue(names.contains("add"), "Should find add method");
        assertTrue(names.contains("getValue"), "Should find getValue method");
        assertTrue(names.contains("Operation"), "Should find Operation enum");
        assertTrue(names.contains("Resettable"), "Should find Resettable interface");

        // Verify structure of one symbol
        CodeSymbol calc =
                symbols.stream()
                        .filter(s -> s.getName().equals("Calculator"))
                        .findFirst()
                        .orElseThrow();
        assertEquals("class", calc.getType());
        assertTrue(calc.getStartLine() > 0);
        assertTrue(calc.getEndLine() >= calc.getStartLine());
        assertNotNull(calc.getSourceCode());
        assertTrue(calc.getSourceCode().contains("class Calculator"));
    }

    @Test
    void shouldExtractPythonSymbols() throws Exception {
        Path file = samplePath("sample.py");
        List<CodeSymbol> symbols = extractor.extract(file);

        assertFalse(symbols.isEmpty());

        Set<String> names = symbols.stream().map(CodeSymbol::getName).collect(Collectors.toSet());
        assertTrue(names.contains("Greeter"), "Should find Greeter class");
        assertTrue(names.contains("greet"), "Should find greet method");
        assertTrue(names.contains("create_greeter"), "Should find create_greeter function");
        assertTrue(names.contains("format_message"), "Should find format_message function");
    }

    @Test
    void shouldExtractTypeScriptSymbols() throws Exception {
        Path file = samplePath("sample.ts");
        List<CodeSymbol> symbols = extractor.extract(file);

        assertFalse(symbols.isEmpty());

        Set<String> names = symbols.stream().map(CodeSymbol::getName).collect(Collectors.toSet());
        assertTrue(names.contains("Config"), "Should find Config interface");
        assertTrue(names.contains("Server"), "Should find Server class");
        assertTrue(names.contains("start"), "Should find start method");
        assertTrue(names.contains("Status"), "Should find Status enum");
        assertTrue(names.contains("createServer"), "Should find createServer function");
    }

    @Test
    void allSymbolsShouldHaveRequiredFields() throws Exception {
        Path file = samplePath("Sample.java");
        List<CodeSymbol> symbols = extractor.extract(file);

        for (CodeSymbol s : symbols) {
            assertNotNull(s.getName(), "name must not be null");
            assertNotNull(s.getType(), "type must not be null for " + s);
            assertTrue(s.getStartLine() > 0, "startLine > 0 for " + s);
            assertTrue(s.getEndLine() > 0, "endLine > 0 for " + s);
            assertNotNull(s.getSourceCode(), "sourceCode must not be null for " + s);
        }
    }
}
