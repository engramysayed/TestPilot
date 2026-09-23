package delivery.codegen;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/** Compiles customer-template sources so tests exercise the real downloaded helpers. */
final class CustomerTemplateCompiler {
    private CustomerTemplateCompiler() {
    }

    static URLClassLoader compile(Path classesDir, Path... sources) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is required to compile customer-template helpers");
        }
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            var units = fm.getJavaFileObjectsFromPaths(List.of(sources));
            boolean ok = compiler.getTask(
                    null,
                    fm,
                    null,
                    List.of(
                            "-classpath", System.getProperty("java.class.path"),
                            "-sourcepath", "customer-framework-template/src/main/java",
                            "-d", classesDir.toString()),
                    null,
                    units).call();
            if (!ok) {
                throw new IllegalStateException("customer template compilation failed");
            }
        }
        return new URLClassLoader(
                new URL[]{classesDir.toUri().toURL()},
                CustomerTemplateCompiler.class.getClassLoader());
    }
}
