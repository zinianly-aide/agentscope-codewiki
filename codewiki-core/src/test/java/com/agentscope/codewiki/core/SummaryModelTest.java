package com.agentscope.codewiki.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Basic instantiation tests for the hierarchical summary model. */
class SummaryModelTest {

    @Test
    void shouldCreateFunctionSummary() {
        FunctionSummary fs = new FunctionSummary();
        fs.setName("calculate");
        fs.setKind("method");
        fs.setSignature("public int calculate(int x)");
        fs.setReturnType("int");
        fs.getParameters().add(new FunctionSummary.Parameter("x", "int"));
        fs.setSummary("Calculates a value from x.");

        assertEquals("calculate", fs.getName());
        assertEquals("method", fs.getKind());
        assertEquals(1, fs.getParameters().size());
        assertEquals("x", fs.getParameters().get(0).getName());
    }

    @Test
    void shouldCreateClassSummary() {
        ClassSummary cs = new ClassSummary();
        cs.setName("UserService");
        cs.setKind("class");
        cs.getModifiers().add("public");
        cs.setSuperClass("BaseService");
        cs.getInterfaces().add("Serializable");

        assertEquals("UserService", cs.getName());
        assertEquals("class", cs.getKind());
        assertTrue(cs.getModifiers().contains("public"));
        assertEquals("BaseService", cs.getSuperClass());
    }

    @Test
    void shouldCreateFileSummary() {
        FileSummary fs = new FileSummary();
        fs.setName("UserService.java");
        fs.setPath("src/main/java/com/example/UserService.java");
        fs.setLanguage("Java");
        fs.setLinesOfCode(150);

        assertEquals("Java", fs.getLanguage());
        assertEquals(150, fs.getLinesOfCode());
        assertTrue(fs.getClasses().isEmpty());
    }

    @Test
    void shouldCreateModuleSummary() {
        ModuleSummary ms = new ModuleSummary();
        ms.setName("codewiki-core");
        ms.setBuildSystem("Maven");

        assertEquals("codewiki-core", ms.getName());
        assertTrue(ms.getSubModules().isEmpty());
    }

    @Test
    void shouldCreateRepositorySummary() {
        RepositorySummary rs = new RepositorySummary();
        rs.setName("my-repo");
        rs.setBranch("main");
        rs.setCommitId("abc123");
        rs.getLanguages().add("Java");
        rs.setFileCount(50);
        rs.setSummary("A sample repository.");

        assertEquals("my-repo", rs.getName());
        assertEquals("main", rs.getBranch());
        assertEquals(1, rs.getLanguages().size());
        assertEquals(50, rs.getFileCount());
    }

    @Test
    void shouldNestHierarchically() {
        // Build a small hierarchy: Repo → Module → File → Class → Function
        FunctionSummary func = new FunctionSummary();
        func.setName("greet");
        func.setKind("method");

        ClassSummary cls = new ClassSummary();
        cls.setName("Greeter");
        cls.setKind("class");
        cls.getMethods().add(func);

        FileSummary file = new FileSummary();
        file.setName("Greeter.java");
        file.setLanguage("Java");
        file.getClasses().add(cls);

        ModuleSummary mod = new ModuleSummary();
        mod.setName("core");
        mod.getFiles().add(file);

        RepositorySummary repo = new RepositorySummary();
        repo.setName("demo");
        repo.getModules().add(mod);

        // Verify hierarchy depth
        assertEquals(1, repo.getModules().size());
        assertEquals(1, repo.getModules().get(0).getFiles().size());
        assertEquals(1, repo.getModules().get(0).getFiles().get(0).getClasses().size());
        assertEquals(
                "greet",
                repo.getModules()
                        .get(0)
                        .getFiles()
                        .get(0)
                        .getClasses()
                        .get(0)
                        .getMethods()
                        .get(0)
                        .getName());
    }

    @Test
    void summaryGeneratorInterfaceIsDefined() {
        // Compile-time check — just verify the interface exists
        assertNotNull(SummaryGenerator.class);
    }

    @Test
    void summaryStorageInterfaceIsDefined() {
        assertNotNull(SummaryStorage.class);
    }
}
