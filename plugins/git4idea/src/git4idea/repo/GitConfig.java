/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.repo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import org.ini4j.Ini;
import org.ini4j.Profile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>
 *   Contains information read from the {@code .git/config} file.
 *   To get the instance call {@link GitRepository#getConfig()}.
 *   It is updated (actually re-created) by the {@link GitRepositoryUpdater}.
 * </p>
 *
 * <p>
 *   Parsing is performed with the help of <a href="http://ini4j.sourceforge.net/">ini4j</a> library.
 * </p>
 *
 * TODO: note, that other git configuration files (such as ~/.gitconfig) are not handled yet.
 * 
 * TODO: for now, only git remotes are read 
 *
 * @author Kirill Likhodedov
 */
class GitConfig {
  
  private static final Logger LOG = Logger.getInstance(GitConfig.class);

  private static final Pattern REMOTE_SECTION = Pattern.compile("remote \"(.*)\"");
  private static final Pattern URL_SECTION = Pattern.compile("url \"(.*)\"");

  private final Collection<GitRemote> myRemotes;

  private GitConfig(Collection<GitRemote> remotes) {
    myRemotes = remotes;
  }

  /**
   * <p>
   *   Returns Git remotes defined in {@code .git/config}.
   * </p>
   * <p>
   *   <b>Note:</b> remotes can be defined separately in {@code .git/remotes} directory, by creating a file for each remote with
   *   remote parameters written in the file.
   *   This method returns ONLY remotes defined in {@code .git/config}.
   *   The method is intentionally non-public forcing to use {@link GitRepository#getRemotes()} which returns the complete list.
   * </p>
   * @return Git remotes defined in {@code .git/config}.
   */
  Collection<GitRemote> getRemotes() {
    return myRemotes;
  }

  /**
   * Creates an instance of GitConfig by reading information from the specified {@code .git/config} file.
   * @param configFile
   * @return
   * @throws GitRepoStateException if {@code .git/config} couldn't be read or has invalid format.
   * <br/>
   * If it has valid format in general, but some sections are invalid, it skips invalid sections, but reports an error.
   */
  @NotNull
  static GitConfig read(@NotNull File configFile) {
    Ini ini = new Ini();
    ini.getConfig().setMultiOption(true);  // duplicate keys (e.g. url in [remote])
    ini.getConfig().setTree(false);        // don't need tree structure: it corrupts url in section name (e.g. [url "http://github.com/"]
    try {
      ini.load(configFile);
    }
    catch (IOException e) {
      throw new GitRepoStateException("Couldn't load .git/config file at " + configFile.getPath(), e);
    }

    Collection<Remote> remotes = new ArrayList<Remote>();
    Collection<Url> urls = new ArrayList<Url>();
    for (Map.Entry<String, Profile.Section> stringSectionEntry : ini.entrySet()) {
      String sectionName = stringSectionEntry.getKey();
      Profile.Section section = stringSectionEntry.getValue();
      
      if (sectionName.startsWith("remote")) {
        Remote remote = parseRemoteSection(sectionName, section);
        if (remote != null) {
          remotes.add(remote);
        }
      }
      else if (sectionName.startsWith("url")) {
        Url url = parseUrlSection(sectionName, section);
        if (url != null) {
          urls.add(url);
        }
      }
    }

    Collection<GitRemote> gitRemotes = makeGitRemotes(remotes, urls);
    return new GitConfig(gitRemotes);

  }

  // populate GitRemotes with substituting urls when needed
  @NotNull
  private static Collection<GitRemote> makeGitRemotes(@NotNull Collection<Remote> remotes, @NotNull Collection<Url> urls) {
    Collection<GitRemote> gitRemotes = new ArrayList<GitRemote>(remotes.size());
    for (Remote remote : remotes) {
      GitRemote gitRemote = convertRemoteToGitRemote(urls, remote);
      gitRemotes.add(gitRemote);
    }
    return gitRemotes;
  }

  @NotNull
  private static GitRemote convertRemoteToGitRemote(@NotNull Collection<Url> urls, @NotNull Remote remote) {
    Pair<Collection<String>, Collection<String>> substitutedUrls = substituteUrls(urls, remote);
    Collection<String> pushUrls;
    if (remote.getPushUrls().isEmpty()) {
      pushUrls = substitutedUrls.getSecond();
    } else {
      pushUrls = remote.getPushUrls();    // explicit pushUrls are not impacted by insteadOf or pushInsteadOf
    }
    return new GitRemote(remote.myName, substitutedUrls.getFirst(), pushUrls, remote.getFetchSpec(), remote.getPushSpec());
  }

  @NotNull
  private static Pair<Collection<String>, Collection<String>> substituteUrls(@NotNull Collection<Url> urls, @NotNull Remote remote) {
    Collection<String> finalUrls = new ArrayList<String>(remote.getUrls().size());
    Collection<String> additionalPushUrls = new ArrayList<String>();
    for (final String remoteUrl : remote.getUrls()) {
      boolean substituted = false;
      for (Url url : urls) {
        String insteadOf = url.getInsteadOf();
        String pushInsteadOf = url.getPushInsteadOf();
        // null means no entry, i.e. nothing to substitute. Empty string means substituting everything
        if (insteadOf != null && remoteUrl.startsWith(insteadOf)) {
          finalUrls.add(substituteUrl(remoteUrl, url, insteadOf));
          substituted = true;
          break;
        } else if (pushInsteadOf != null && remoteUrl.startsWith(pushInsteadOf)) {
          additionalPushUrls.add(substituteUrl(remoteUrl, url, pushInsteadOf)); // pushUrl is different
          finalUrls.add(remoteUrl);                                             // but url is left intact
          substituted = true;
          break;
        } 
      }
      if (!substituted) {
        finalUrls.add(remoteUrl);
      }
    }
    return Pair.create(finalUrls, additionalPushUrls);
  }

  @NotNull
  private static String substituteUrl(@NotNull String remoteUrl, @NotNull Url url, @NotNull String insteadOf) {
    return url.myName + remoteUrl.substring(insteadOf.length());
  }

  @Nullable
  private static Remote parseRemoteSection(String sectionName, Profile.Section section) {
    RemoteBean remoteBean = section.as(RemoteBean.class);
    Matcher matcher = REMOTE_SECTION.matcher(sectionName);
    if (matcher.matches()) {
      return new Remote(matcher.group(1), remoteBean);
    }
    LOG.error(String.format("Invalid remote section format in .git/config. sectionName: %s section: %s", sectionName, section));
    return null;
  }

  @Nullable
  private static Url parseUrlSection(String sectionName, Profile.Section section) {
    UrlBean urlBean = section.as(UrlBean.class);
    Matcher matcher = URL_SECTION.matcher(sectionName);
    if (matcher.matches()) {
      return new Url(matcher.group(1), urlBean);
    }
    LOG.error(String.format("Invalid url section format in .git/config. sectionName: %s section: %s", sectionName, section));
    return null;
  }

  private static class Remote {

    private final String myName;
    private final RemoteBean myRemoteBean;

    private Remote(@NotNull String name, @NotNull RemoteBean remoteBean) {
      myRemoteBean = remoteBean;
      myName = name;
    }
    
    @NotNull
    private Collection<String> getUrls() {
      return nonNullCollection(myRemoteBean.getUrl());
    }

    @NotNull
    private Collection<String> getPushUrls() {
      return nonNullCollection(myRemoteBean.getPushUrl());
    }

    @NotNull
    public String getPushSpec() {
      return notNull(myRemoteBean.getPush());
    }

    @NotNull
    private String getFetchSpec() {
      return notNull(myRemoteBean.getFetch());
    }
    
  }

  private interface RemoteBean {
    @Nullable String getFetch();
    @Nullable String getPush();
    @Nullable String[] getUrl();
    @Nullable String[] getPushUrl();
  }

  private static class Url {
    private final String myName;
    private final UrlBean myUrlBean;

    private Url(String name, UrlBean urlBean) {
      myUrlBean = urlBean;
      myName = name;
    }

    @Nullable
    // null means to entry, i.e. nothing to substitute. Empty string means substituing everything 
    public String getInsteadOf() {
      return myUrlBean.getInsteadOf();
    }

    @Nullable
    // null means to entry, i.e. nothing to substitute. Empty string means substituing everything 
    public String getPushInsteadOf() {
      return myUrlBean.getPushInsteadOf();
    }
  }

  private interface UrlBean {
    @Nullable String getInsteadOf();
    @Nullable String getPushInsteadOf();
  }

  @NotNull
  private static String notNull(@Nullable String s) {
    return s == null ? "" : s;
  }

  @NotNull
  private static Collection<String> nonNullCollection(@Nullable String[] array) {
    return array == null ? Collections.<String>emptyList() : new ArrayList<String>(Arrays.asList(array));
  }

}
