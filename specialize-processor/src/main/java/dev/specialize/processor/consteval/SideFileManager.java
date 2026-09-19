package dev.specialize.processor.consteval;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

/**
 * The file manager of a side compilation: classes are looked up through the build's own file manager (its class
 * path, platform and modules, whatever the build tool configured), while output and the optional source root are ours.
 */
final class SideFileManager extends ForwardingJavaFileManager<JavaFileManager> {
    private static final Set<Location> LOCAL = Set.of(StandardLocation.CLASS_OUTPUT, StandardLocation.SOURCE_OUTPUT, StandardLocation.SOURCE_PATH);

    private final StandardJavaFileManager local;

    SideFileManager(JavaFileManager build, StandardJavaFileManager local) {
        super(build);
        this.local = local;
    }

    private JavaFileManager route(Location location) {
        return LOCAL.contains(location) ? local : fileManager;
    }

    @Override
    public ClassLoader getClassLoader(Location location) {
        return route(location).getClassLoader(location);
    }

    @Override
    public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
        return route(location).list(location, packageName, kinds, recurse);
    }

    @Override
    public String inferBinaryName(Location location, JavaFileObject file) {
        return route(location).inferBinaryName(location, file);
    }

    @Override
    public boolean hasLocation(Location location) {
        return route(location).hasLocation(location);
    }

    @Override
    public JavaFileObject getJavaFileForInput(Location location, String className, JavaFileObject.Kind kind) throws IOException {
        return route(location).getJavaFileForInput(location, className, kind);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
        return route(location).getJavaFileForOutput(location, className, kind, sibling);
    }

    @Override
    public boolean handleOption(String current, Iterator<String> remaining) {
        return false;
    }

    @Override
    public void flush() throws IOException {
        local.flush();
    }

    /** Only ours: the build's file manager outlives this compilation. */
    @Override
    public void close() throws IOException {
        local.close();
    }
}
