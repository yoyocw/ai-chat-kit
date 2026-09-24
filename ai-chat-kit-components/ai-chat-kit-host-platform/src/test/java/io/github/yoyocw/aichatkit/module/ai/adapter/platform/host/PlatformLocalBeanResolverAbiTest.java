package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import io.github.yoyocw.aichatkit.testnative.framework.common.pojo.CommonResult;
import io.github.yoyocw.aichatkit.testnative.module.system.api.permission.PermissionApiImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Missing native interface method is isolated from target and permission Bean failures. */
class PlatformLocalBeanResolverAbiTest {
    private static final String ROOT = PlatformLocalBeanResolverTest.ROOT;
    private static final String API = ROOT + ".framework.common.biz.system.oauth2.OAuth2TokenCommonApi";
    private static final String TARGET = ROOT + ".module.system.api.oauth2.OAuth2TokenApiImpl";

    @Test
    void nativeInterfaceWithoutSessionMethodIsRejectedEvenWhenTargetImplementsIt(@TempDir Path directory)
            throws Exception {
        Path api = source(directory, API, "package " + ROOT + ".framework.common.biz.system.oauth2;"
                + "public interface OAuth2TokenCommonApi {"
                + " " + CommonResult.class.getName() + " checkAccessToken(String token);"
                + "}");
        Path target = source(directory, TARGET, "package " + ROOT + ".module.system.api.oauth2;"
                + "public class OAuth2TokenApiImpl implements " + API + " {"
                + " public " + CommonResult.class.getName() + " checkAccessToken(String token) { return null; }"
                + " public " + CommonResult.class.getName() + " checkAccessTokenSession(Long id) { return null; }"
                + "}");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        assertEquals(0, compiler.run(null, null, null, "-proc:none", "-classpath",
                System.getProperty("java.class.path"), "-d", directory.toString(),
                api.toString(), target.toString()));

        try (URLClassLoader isolated = new URLClassLoader(new URL[]{directory.toUri().toURL()},
                getClass().getClassLoader()) {
            @Override protected synchronized Class<?> loadClass(String name, boolean resolve)
                    throws ClassNotFoundException {
                if (!API.equals(name) && !TARGET.equals(name)) { return super.loadClass(name, resolve); }
                Class<?> value = findLoadedClass(name);
                if (value == null) { value = findClass(name); }
                if (resolve) { resolveClass(value); }
                return value;
            }
        }) {
            Class<?> oldApi = isolated.loadClass(API);
            assertThrows(NoSuchMethodException.class,
                    () -> oldApi.getMethod("checkAccessTokenSession", Long.class));
            Object local = isolated.loadClass(TARGET).newInstance();
            assertEquals(CommonResult.class,
                    local.getClass().getMethod("checkAccessTokenSession", Long.class).getReturnType());
            DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
            beans.registerSingleton("oauth2TokenApiImpl", local);
            beans.registerSingleton("permissionApiImpl", new PermissionApiImpl());

            IllegalStateException denied = assertThrows(IllegalStateException.class,
                    () -> new PlatformLocalBeanResolver(beans, isolated, ROOT));
            assertNull(denied.getCause());
            assertFalse(denied.getMessage().contains("oauth2TokenApiImpl"));
        }
    }

    private Path source(Path directory, String type, String content) throws Exception {
        Path path = directory.resolve(type.replace('.', '/') + ".java");
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        return path;
    }
}
