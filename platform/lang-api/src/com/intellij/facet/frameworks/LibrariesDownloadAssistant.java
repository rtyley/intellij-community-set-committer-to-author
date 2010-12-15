package com.intellij.facet.frameworks;

import com.intellij.facet.frameworks.actions.GetVersionInfoAction;
import com.intellij.facet.frameworks.beans.Artifact;
import com.intellij.facet.frameworks.beans.ArtifactItem;
import com.intellij.facet.frameworks.beans.Artifacts;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.xmlb.XmlSerializationException;
import com.intellij.util.xmlb.XmlSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class LibrariesDownloadAssistant {
  private static final Logger LOG = Logger.getInstance("#com.intellij.LibrariesDownloadAssistant");

  private LibrariesDownloadAssistant() {
  }

  @Nullable
  public static Artifact[] getVersions(@NotNull String groupId, @NotNull URL... localUrls) {
    final Artifact[] versions = getDownloadServiceVersions(groupId);
    return versions == null ? getVersions(localUrls) : versions;
  }

  @Nullable
  public static Artifact[] getDownloadServiceVersions(@NotNull String id) {
    final URL url = createVersionsUrl(id);
    if (url == null) return null;
    final Artifacts allArtifacts = deserialize(url);
    return allArtifacts == null ? null : allArtifacts.getArtifacts();
  }

  @Nullable
  private static URL createVersionsUrl(@NotNull String id) {
    final String serviceUrl = LibrariesDownloadConnectionService.getInstance().getServiceUrl();
    if (StringUtil.isNotEmpty(serviceUrl)) {
      try {
        final String url = serviceUrl + "/" + id + "/";
        HttpConfigurable.getInstance().prepareURL(url);

        return new URL(url);
      }
      catch (MalformedURLException e) {
        LOG.error(e);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

    return null;
  }

  @NotNull
  public static Artifact[] getVersions(@NotNull URL... urls) {
    Set<Artifact> versions = new HashSet<Artifact>();
    for (URL url : urls) {
      final Artifacts allArtifacts = deserialize(url);
      if (allArtifacts != null) {
        final Artifact[] vers = allArtifacts.getArtifacts();
        if (vers != null) {
          versions.addAll(Arrays.asList(vers));
        }
      }
    }

    return versions.toArray(new Artifact[versions.size()]);
  }

  @Nullable
  private static Artifacts deserialize(@Nullable URL url) {
    if (url == null) return null;

    Artifacts allArtifacts = null;
    try {
      allArtifacts = XmlSerializer.deserialize(url, Artifacts.class);
    }
    catch (XmlSerializationException e) {
      final Throwable cause = e.getCause();
      if (!(cause instanceof IOException)) {
        LOG.error(e);
      }
    }
    return allArtifacts;
  }

  @Nullable
  public static Artifact getVersion(@NotNull String id, @NotNull String versionId) {
    final URL url = GetVersionInfoAction.create(id, versionId).getUrl();
    if (url == null) return null;

    final Artifacts allArtifacts = XmlSerializer.deserialize(url, Artifacts.class);

    if (allArtifacts == null) return null;

    final Artifact[] versions = allArtifacts.getArtifacts();

    assert versions.length == 1;

    return versions[0];
  }

  @Nullable
  public static Artifact findVersion(@NotNull final String versionId, @NotNull final URL... urls) {
    return findVersion(getVersions(urls), versionId);
  }

  @Nullable
  public static Artifact findVersion(@NotNull final String groupId, @NotNull final String versionId) {
    return findVersion(getVersions(groupId), versionId);
  }

  @Nullable
  public static Artifact findVersion(@Nullable Artifact[] versions, @NotNull final String versionId) {
    return versions == null ? null : ContainerUtil.find(versions, new Condition<Artifact>() {
      public boolean value(final Artifact springVersion) {
        return versionId.equals(springVersion.getVersion());
      }
    });
  }

  @NotNull
  public static LibraryInfo[] getLibraryInfos(@NotNull final URL url, @NotNull final String versionId) {
    final Artifact version = findVersion(getVersions(url), versionId);
    return version != null ? getLibraryInfos(version) : LibraryInfo.EMPTY_ARRAY;
  }

  @NotNull
  public static LibraryInfo[] getLibraryInfos(@Nullable Artifact version) {
    if (version == null) return LibraryInfo.EMPTY_ARRAY;

    final List<LibraryInfo> infos = convert(version.getItems());

    return infos.toArray(new LibraryInfo[infos.size()]);
  }

  @NotNull
  public static List<LibraryInfo> convert(@NotNull ArtifactItem[] jars) {
    return ContainerUtil.mapNotNull(jars, new Function<ArtifactItem, LibraryInfo>() {
      @Override
      public LibraryInfo fun(ArtifactItem artifactItem) {
        final String downloadUrl = artifactItem.getUrl();
        return new LibraryInfo(artifactItem.getName(), downloadUrl, downloadUrl, artifactItem.getMD5(), artifactItem.getRequiredClasses());
      }
    });
  }
}
