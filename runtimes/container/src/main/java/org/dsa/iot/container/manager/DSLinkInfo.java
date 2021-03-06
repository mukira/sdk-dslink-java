package org.dsa.iot.container.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.dsa.iot.container.utils.JarInfo;
import org.dsa.iot.container.utils.Json;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Samuel Grenier
 */
public class DSLinkInfo {

    private final Path root;
    private final String name;
    private final String logLevel;
    private final String handlerClass;
    private final String brokerUrl;
    private final String token;
    private final String logPath;

    public DSLinkInfo(Path path,
                      String name,
                      String logLevel,
                      String handlerClass,
                      String brokerUrl,
                      String token,
                      String logPath) {
        if (path == null) {
            throw new NullPointerException("path");
        } else if (name == null) {
            throw new NullPointerException("name");
        } else if (logLevel == null) {
            throw new NullPointerException("logLevel");
        } else if (handlerClass == null) {
            throw new NullPointerException("handlerClass");
        } else if (brokerUrl == null) {
            throw new NullPointerException("brokerUrl");
        }
        this.root = path;
        this.name = name;
        this.logLevel = logLevel;
        this.handlerClass = handlerClass;
        this.brokerUrl = brokerUrl;
        this.token = token;
        this.logPath = logPath;
    }

    public Path getRoot() {
        return root;
    }

    public String getName() {
        return name;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public String getLogPath() {
        return logPath;
    }

    public String getHandlerClass() {
        return handlerClass;
    }

    public String getBrokerUrl() {
        return brokerUrl;
    }

    public String getToken() {
        return token;
    }

    public JarInfo[] collectJars() throws IOException {
        JarWalker walker = new JarWalker();
        Files.walkFileTree(root.resolve("lib"), walker);
        URL[] urls = walker.getUrls();
        JarInfo[] info = new JarInfo[urls.length];
        for (int i = 0; i < info.length; i++) {
            final URL url = urls[i];
            boolean isNative = false;
            try (ZipInputStream zis = new ZipInputStream(url.openStream())) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    isNative = isEntryNative(entry);
                    if (isNative) {
                        System.err.println("Detected native library: " + url.toString());
                        break;
                    }
                }
            } catch (RuntimeException e) {
                System.err.println("Failed to scan jar: " + url.toString());
            }
            info[i] = new JarInfo(url, isNative);
        }
        return info;
    }

    private boolean isEntryNative(ZipEntry entry) {
        String name = entry.getName();
        return !entry.isDirectory()
                && (name.endsWith(".a")
                    || name.endsWith(".so")
                    || name.endsWith(".dylib")
                    || name.endsWith(".dll"));
    }

    public static DSLinkInfo load(Path root,
                                  String brokerUrl,
                                  String token,
                                  String logPath) throws IOException {
        Path dslinkJson = root.resolve("dslink.json");
        if (!Files.isRegularFile(dslinkJson)) {
            String err = "Missing dslink.json for ";
            err += root.toString();
            System.err.println(err);
            return null;
        }

        final String name;
        final String logLevel;
        final String handler;
        {
            ObjectMapper mapper = Json.getMapper();
            byte[] readJson = Files.readAllBytes(dslinkJson);
            ObjectNode node = mapper.readValue(readJson, ObjectNode.class);
            JsonNode config = node.path("configs");
            if (config.isMissingNode()) {
                String err = "Missing configs field in dslink.json for ";
                err += root.toString();
                System.err.println(err);
                return null;
            }

            name = config.path("name").get("default").asText();
            logLevel = config.path("log").get("default").asText();
            handler = config.path("handler_class").get("default").asText();
        }

        return new DSLinkInfo(root, name, logLevel,
                                handler, brokerUrl, token, logPath);
    }

    private static class JarWalker extends SimpleFileVisitor<Path> {

        private static final String[] BLACKLIST = new String[] {
                "bcprov-jdk15on-",
                "jackson-",
                "jcommander-",
                "vertx-",
                "netty-"
        };

        private List<URL> urls = new ArrayList<>();

        @Override
        public FileVisitResult visitFile(Path file,
                                         BasicFileAttributes attr) {
            Path pathFileName;
            if (file == null || (pathFileName = file.getFileName()) == null) {
                return FileVisitResult.CONTINUE;
            }
            String fileName = pathFileName.toString();
            if (attr.isRegularFile() && fileName.endsWith(".jar")) {
                for (String ignore : BLACKLIST) {
                    if (fileName.startsWith(ignore)) {
                        return FileVisitResult.CONTINUE;
                    }
                }
                try {
                    urls.add(file.toUri().toURL());
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }
            return FileVisitResult.CONTINUE;
        }

        public URL[] getUrls() {
            return this.urls.toArray(new URL[this.urls.size()]);
        }
    }
}
