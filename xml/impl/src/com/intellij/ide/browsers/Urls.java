package com.intellij.ide.browsers;

import com.google.common.base.CharMatcher;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// We don't use Java URI due to problem — http://cns-etuat-2.localnet.englishtown.com/school/e12/#school/45383/201/221/382?c=countrycode=cc|culturecode=en-us|partnercode=mkge
// it is illegal URI (fragment before query), but we must support such URI
public final class Urls {
  private static final Logger LOG = Logger.getInstance(Urls.class);

  public static final CharMatcher SLASH_MATCHER = CharMatcher.is('/');

  // about ";" see WEB-100359
  private static final Pattern URI_PATTERN = Pattern.compile("^(?:([^:/?#]+)://)?([^/?#]*)([^?#;]*)(.*)");

  @NotNull
  public static Url newFromEncoded(@NotNull String url) {
    Url result = parse(url, true);
    LOG.assertTrue(result != null);
    return result;
  }

  @Nullable
  public static Url parse(@NotNull String url, boolean asLocalIfNoScheme) {
    if (asLocalIfNoScheme && !URLUtil.containsScheme(url)) {
      // nodejs debug — files only in local filesystem
      return new LocalFileUrl(url);
    }
    return parseNormalizedUrl(VfsUtil.toIdeaUrl(url), true);
  }

  private static Url parseNormalizedUrl(String url, boolean urlAsRaw) {
    Matcher matcher = URI_PATTERN.matcher(url);
    if (!matcher.matches()) {
      LOG.warn("Cannot parse url " + url);
      return null;
    }
    String scheme = matcher.group(1);
    String authority = StringUtil.nullize(matcher.group(2));
    String path = StringUtil.nullize(matcher.group(3));
    String parameters = matcher.group(4);
    if (authority != null && StandardFileSystems.FILE_PROTOCOL.equals(scheme)) {
      path = path == null ? authority : (authority + path);
      authority = null;
    }
    return new UrlImpl(urlAsRaw ? url : null, scheme, authority, path, parameters);
  }

  // URL must not contain fragment part
  // java.net.URI.create cannot parse "file:///Test Stuff" - but you don't need to worry about it - this method is aware
  public static Url newFromIdea(@NotNull String urlWithoutFragment) {
    int index = urlWithoutFragment.indexOf("://");
    if (index < 0) {
      // nodejs debug — files only in local filesystem
      return new LocalFileUrl(urlWithoutFragment);
    }
    else {
      return parseNormalizedUrl(VfsUtil.toIdeaUrl(urlWithoutFragment), false);
    }
  }

  // must not be used in NodeJS
  public static Url newFromVirtualFile(@NotNull VirtualFile file) {
    String path = file.getPath();
    if (file.isInLocalFileSystem()) {
      return new UrlImpl(null, file.getFileSystem().getProtocol(), null, path, null);
    }
    else {
      return parseNormalizedUrl(file.getUrl(), false);
    }
  }
}