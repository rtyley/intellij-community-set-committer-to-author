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
package com.intellij.featureStatistics;

import com.intellij.CommonBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.*;

/**
 * @author max
 */
public class FeatureStatisticsBundle {
  private static Reference<ResourceBundle> ourBundle;
  private static final Logger LOG = Logger.getInstance(FeatureStatisticsBundle.class);

  @NonNls private static final String BUNDLE = "messages.FeatureStatisticsBundle";

  private FeatureStatisticsBundle() {
  }

  private static final Map<String, ResourceBundle> ourProviderBundles = new HashMap<String, ResourceBundle>();
  private static boolean ourProviderBundlesLoaded = false;

  public static String message(@PropertyKey(resourceBundle = BUNDLE)String key, Object... params) {
    return CommonBundle.message(getBundle(key), key, params);
  }

  private static ResourceBundle getBundle(final String key) {
    synchronized (ourProviderBundles) {
      if (!ourProviderBundlesLoaded) {
        loadProviderBundles();
        ourProviderBundlesLoaded = true;
      }
      ResourceBundle bundle = ourProviderBundles.get(key);
      if (bundle != null) {
        return bundle;
      }
    }
    final FeatureStatisticsBundleProvider[] providers = FeatureStatisticsBundleProvider.EP_NAME.getExtensions();
    for (FeatureStatisticsBundleProvider provider : providers) {
      final ResourceBundle bundle = provider.getBundle();
      if (bundle.containsKey(key)) {
        return bundle;
      }
    }

    ResourceBundle bundle = null;
    if (ourBundle != null) bundle = ourBundle.get();
    if (bundle == null) {
      bundle = ResourceBundle.getBundle(BUNDLE);
      ourBundle = new SoftReference<ResourceBundle>(bundle);
    }
    return bundle;
  }

  private static void loadProviderBundles() {
    for (FeatureStatisticsBundleEP bundleEP : Extensions.getExtensions(FeatureStatisticsBundleEP.EP_NAME)) {
      try {
        ResourceBundle bundle = ResourceBundle.getBundle(bundleEP.qualifiedName, Locale.getDefault(), bundleEP.getLoaderForClass());
        for (String key : bundle.keySet()) {
          ourProviderBundles.put(key, bundle);
        }
      }
      catch (MissingResourceException e) {
        LOG.error(e);
      }
    }
  }
}
