/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom.converters;

import com.intellij.codeInsight.completion.CompletionLocation;
import com.intellij.codeInsight.completion.CompletionWeigher;
import com.intellij.codeInsight.completion.PredefinedCompletionWeigher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xml.ConvertContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenVersionComparable;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.idea.maven.model.MavenId;

import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;

public class MavenArtifactCoordinatesVersionConverter extends MavenArtifactCoordinatesConverter {

  private static final Pattern MAGIC_VERSION_PATTERN = Pattern.compile("\\s*(?:LATEST|RELEASE|[(\\[].*|.*-20\\d{6}\\.[0-2]\\d{5}-\\d+)\\s*");

  @Override
  protected boolean doIsValid(MavenId id, MavenProjectIndicesManager manager, ConvertContext context) {
    if (StringUtil.isEmpty(id.getGroupId())
        || StringUtil.isEmpty(id.getArtifactId())
        || StringUtil.isEmpty(id.getVersion())) {
      return false;
    }
    if (MAGIC_VERSION_PATTERN.matcher(id.getVersion()).matches()) return true; // todo handle ranges more sensibly
    return manager.hasVersion(id.getGroupId(), id.getArtifactId(), id.getVersion());
  }

  @Override
  protected Set<String> doGetVariants(MavenId id, MavenProjectIndicesManager manager) {
    if (StringUtil.isEmpty(id.getGroupId()) || StringUtil.isEmpty(id.getArtifactId())) return Collections.emptySet();
    return manager.getVersions(id.getGroupId(), id.getArtifactId());
  }

  @Nullable
  @Override
  public LookupElement createLookupElement(String s) {
    LookupElementBuilder lookup = LookupElementBuilder.create(s);

    lookup.putUserData(PredefinedCompletionWeigher.KEY, MavenVersionWeigher.INSTANCE);

    return lookup;
  }

  private static class MavenVersionWeigher extends CompletionWeigher {
    private static final MavenVersionWeigher INSTANCE = new MavenVersionWeigher();

    @Override
    public Comparable weigh(@NotNull LookupElement element, @NotNull CompletionLocation location) {
      return new MavenVersionComparable(element.getLookupString());
    }
  }
}