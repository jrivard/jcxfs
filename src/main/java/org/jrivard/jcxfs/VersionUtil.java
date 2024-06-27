/*
 * Copyright 2024 Jason D. Rivard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jrivard.jcxfs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

public class VersionUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(VersionUtil.class);
    public static final Charset GLOBAL_CHARSET = StandardCharsets.UTF_8;

    private static final List<String> INTERESTED_MANIFEST_KEYS =
            List.of("Implementation-Title", "Implementation-URL", "Implementation-Version");

    public static String version() {
        try {
            for (final URL url : getFileUrl("META-INF/MANIFEST.MF")) {
                final Manifest manifest = new Manifest(url.openStream());
                if (isApplicableManifest(manifest)) {
                    final Attributes attr = manifest.getMainAttributes();
                    final Optional<String> value = getAttrValue(attr, "Implementation-Version");
                    if (value.isPresent()) {
                        return value.get();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("error reading version from manifest: " + e.getMessage());
        }
        return "unknown version";
    }

    public static class ManifestVersionProvider implements CommandLine.IVersionProvider {
        public String[] getVersion() throws Exception {
            for (final URL url : getFileUrl("META-INF/MANIFEST.MF")) {
                try {
                    final Manifest manifest = new Manifest(url.openStream());
                    if (isApplicableManifest(manifest)) {
                        final Attributes attr = manifest.getMainAttributes();
                        return INTERESTED_MANIFEST_KEYS.stream()
                                .map(v -> getAttrValue(attr, v))
                                .flatMap(Optional::stream)
                                .toArray(String[]::new);
                    }
                } catch (IOException e) {
                    System.err.println("error reading version from manifest: " + e.getMessage());
                }
            }

            return new String[] {"can not load version from manifest"};
        }
    }

    private static boolean isApplicableManifest(final Manifest manifest) {
        final Attributes attributes = manifest.getMainAttributes();

        return getAttrValue(attributes, "manifest-id")
                .map(v -> v.equals("jcxfs-executable"))
                .orElse(false);
    }

    static List<URL> getFileUrl(final String name) throws IOException {
        final List<URL> returnList = new ArrayList<>();
        final Enumeration<URL> resources = VersionUtil.class.getClassLoader().getResources(name);

        if (resources != null) {
            while (resources.hasMoreElements()) {
                returnList.add(resources.nextElement());
            }
        }

        return Collections.unmodifiableList(returnList);
    }

    private static Optional<String> getAttrValue(final Attributes attributes, final String key) {
        final Object value = attributes.get(new Attributes.Name(key));
        if (value != null) {
            final String sValue = value.toString();
            if (!sValue.isEmpty()) {
                return Optional.of(sValue);
            }
        }
        return Optional.empty();
    }

    public static String isToString(final InputStream inputStream) throws IOException {
        final StringBuilder textBuilder = new StringBuilder();
        try (final Reader reader = new BufferedReader(new InputStreamReader(inputStream, VersionUtil.GLOBAL_CHARSET))) {
            int c = 0;
            while ((c = reader.read()) != -1) {
                textBuilder.append((char) c);
            }
        }
        return textBuilder.toString();
    }
}
