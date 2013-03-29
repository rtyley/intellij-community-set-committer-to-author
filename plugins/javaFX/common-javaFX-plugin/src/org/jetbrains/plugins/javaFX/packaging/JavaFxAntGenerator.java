/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.javaFX.packaging;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * User: anna
 * Date: 3/28/13
 */
public class JavaFxAntGenerator {
  public static List<SimpleTag> createJarAndDeployTasks(AbstractJavaFxPackager packager,
                                                        String artifactFileName,
                                                        String artifactName,
                                                        String tempDirPath) {
    final List<SimpleTag> topLevelTagsCollector = new ArrayList<SimpleTag>(); 
    final String preloaderJar = packager.getPreloaderJar();
    final String preloaderClass = packager.getPreloaderClass();
    String preloaderFiles = null;
    if (!StringUtil.isEmptyOrSpaces(preloaderJar) && !StringUtil.isEmptyOrSpaces(preloaderClass)) {
      preloaderFiles = artifactName + "_preloader_files";
      topLevelTagsCollector.add(new SimpleTag("fx:fileset",
                                              new Pair<String, String>("id", preloaderFiles),
                                              new Pair<String, String>("requiredFor", "preloader"),
                                              new Pair<String, String>("dir", tempDirPath),
                                              new Pair<String, String>("includes", preloaderJar)));
    }

    //register application
    final String appId = artifactName + "_id";
    Pair[] applicationParams = {
                                  new Pair<String, String>("id", appId),
                                  new Pair<String, String>("name", artifactName),
                                  new Pair<String, String>("mainClass", packager.getAppClass())
                               };
    if (preloaderFiles != null) {
      applicationParams = ArrayUtil.append(applicationParams, new Pair<String, String>("preloaderClass", preloaderClass));
    }

    final SimpleTag applicationTag = new SimpleTag("fx:application", applicationParams);

    appendValuesFromPropertiesFile(applicationTag, packager.getHtmlParamFile(), "fx:htmlParam", false);
    //also loads fx:argument values
    appendValuesFromPropertiesFile(applicationTag, packager.getParamFile(), "fx:param", true);

    topLevelTagsCollector.add(applicationTag);

    //create jar task
    final SimpleTag createJarTag = new SimpleTag("fx:jar",
                                     new Pair<String, String>("destfile", tempDirPath + File.separator + artifactFileName));
    createJarTag.add(new SimpleTag("fx:application", new Pair<String, String>("refid", appId)));
    final List<Pair> fileset2Jar = new ArrayList<Pair>();
    fileset2Jar.add(new Pair<String, String>("dir", tempDirPath));
    if (preloaderJar != null) {
      fileset2Jar.add(new Pair<String, String>("excludes", preloaderJar));
    }
    createJarTag.add(new SimpleTag("fileset", fileset2Jar.toArray(new Pair[fileset2Jar.size()])));
    if (preloaderFiles != null) {
      final SimpleTag createJarResourcesTag = new SimpleTag("fx:resources");
      createJarResourcesTag.add(new SimpleTag("fx:fileset", new Pair<String, String>("refid", preloaderFiles)));
      createJarTag.add(createJarResourcesTag);
    }
    topLevelTagsCollector.add(createJarTag);

    //deploy task
    final SimpleTag deployTag = new SimpleTag("fx:deploy",
                                              new Pair<String, String>("width", packager.getWidth()),
                                              new Pair<String, String>("height", packager.getHeight()),
                                              new Pair<String, String>("updatemode", packager.getUpdateMode()),
                                              new Pair<String, String>("outdir", tempDirPath + File.separator + "deploy"),
                                              new Pair<String, String>("outfile", artifactName));
    deployTag.add(new SimpleTag("fx:application", new Pair<String, String>("refid", appId)));

    final List<Pair> infoPairs = new ArrayList<Pair>();
    appendIfNotEmpty(infoPairs, "title", packager.getTitle());
    appendIfNotEmpty(infoPairs, "vendor", packager.getVendor());
    appendIfNotEmpty(infoPairs, "description", packager.getDescription());
    if (!infoPairs.isEmpty()) {
      deployTag.add(new SimpleTag("fx:info", infoPairs.toArray(new Pair[infoPairs.size()])));
    }

    final SimpleTag deployResourcesTag = new SimpleTag("fx:resources");
    deployResourcesTag.add(new SimpleTag("fx:fileset", new Pair<String, String>("dir", tempDirPath),
                                                       new Pair<String, String>("includes", artifactFileName)));
    if (preloaderFiles != null) {
      deployResourcesTag.add(new SimpleTag("fx:fileset", new Pair<String, String>("refid", preloaderFiles)));
    }

    deployTag.add(deployResourcesTag);

    topLevelTagsCollector.add(deployTag);
    return topLevelTagsCollector;
  }

  private static void appendIfNotEmpty(final List<Pair> pairs, final String propertyName, final String propValue) {
    if (!StringUtil.isEmptyOrSpaces(propValue)) {
      pairs.add(Pair.create(propertyName, propValue));
    }
  }

  private static void appendValuesFromPropertiesFile(final SimpleTag applicationTag,
                                                     final String paramFile,
                                                     final String paramTagName,
                                                     final boolean allowNoNamed) {
    if (!StringUtil.isEmptyOrSpaces(paramFile)) {
      final Properties properties = new Properties();
      try {
        final FileInputStream paramsInputStream = new FileInputStream(new File(paramFile));
        try {
          properties.load(paramsInputStream);
          for (Object o : properties.keySet()) {
            final String propName = (String)o;
            final String propValue = properties.getProperty(propName);
            if (!StringUtil.isEmptyOrSpaces(propValue)) {
              applicationTag
                .add(new SimpleTag(paramTagName, new Pair<String, String>("name", propName), new Pair<String, String>("value", propValue)));
            }
            else if (allowNoNamed) {
              applicationTag.add(new SimpleTag("fx:argument", propName) {
                @Override
                public void generate(StringBuilder buf) {
                  buf.append("<").append(getName()).append(">").append(propName).append("</").append(getName()).append(">");
                }
              });
            }
          }
        }
        finally {
          paramsInputStream.close();
        }
      }
      catch (IOException ignore) {
      }
    }
  }

  public static class SimpleTag {
    private final String myName;
    private final Pair[] myPairs;
    private final List<SimpleTag> mySubTags = new ArrayList<SimpleTag>();
    private final String myValue;

    public SimpleTag(String name, Pair... pairs) {
      myName = name;
      myPairs = pairs;
      myValue = null;
    }

    public SimpleTag(String name, String value) {
      myName = name;
      myPairs = new Pair[0];
      myValue = value;
    }

    public void add(SimpleTag tag) {
      mySubTags.add(tag);
    }

    public String getName() {
      return myName;
    }

    public Pair[] getPairs() {
      return myPairs;
    }

    public String getValue() {
      return myValue;
    }

    public List<SimpleTag> getSubTags() {
      return mySubTags;
    }

    public void generate(StringBuilder buf) {
      buf.append("<").append(getName());
      for (Pair pair : getPairs()) {
        buf.append(" ").append(pair.first).append("=\"").append(pair.second).append("\"");
      }
      buf.append(">\n");
      for (SimpleTag tag : getSubTags()) {
        tag.generate(buf);
      }
      buf.append("</").append(getName()).append(">\n");
    }
  }
}
