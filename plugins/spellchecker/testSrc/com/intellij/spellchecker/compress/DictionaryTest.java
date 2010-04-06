/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.spellchecker.compress;

import com.intellij.openapi.util.Pair;
import com.intellij.spellchecker.DefaultBundledDictionariesProvider;
import com.intellij.spellchecker.StreamLoader;
import com.intellij.spellchecker.dictionary.Dictionary;
import com.intellij.spellchecker.dictionary.Loader;
import com.intellij.spellchecker.engine.Transformation;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.util.Consumer;
import gnu.trove.THashSet;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
public class DictionaryTest extends TestCase {

  private Dictionary dictionary;

  private Map<String, Integer> sizes = new HashMap<String, Integer>();
    private Map<String, Integer> times = new HashMap<String, Integer>();

  {
    sizes.put("jetbrains.dic", 1000);
    sizes.put("english.dic", 140000);
    sizes.put("russian-utf8.dic", 1200000);
  }

  {
    times.put("jetbrains.dic", 100);
    times.put("english.dic", 2700);
    times.put("russian-utf8.dic", 32000);
  }

  public void testDictionary() throws IOException {

    DefaultBundledDictionariesProvider dp = new DefaultBundledDictionariesProvider();
    final String[] names = dp.getBundledDictionaries();
    for (String name : names) {
      StreamLoader loader = createLoader(name);
      loadDictionaryTest(loader, sizes.get(name), name);
      loadHalfDictionaryTest(50000, name);
    }

  }


  public void loadDictionaryTest(final Loader loader, int wordCount, final String name) throws IOException {
    final Transformation transform = new Transformation();

    IdeaTestUtil
      .assertTiming("Dictionary load depends on words count. Approximate word count: " + wordCount + ".", times.get(name), new Runnable() {
        public void run() {
          dictionary = CompressedDictionary.create(loader, transform);
        }
      });

    final Set<String> wordsToStoreAndCheck = createWordSets(name, 50000, 1).getFirst();
    IdeaTestUtil.assertTiming("Invoke 'contains'  " + wordsToStoreAndCheck.size() + " times", 1000, new Runnable() {
      public void run() {
        for (String s : wordsToStoreAndCheck) {
          assertTrue(dictionary.contains(s));
        }
      }
    });

  }

  private static StreamLoader createLoader(String name) {
    return new StreamLoader(DefaultBundledDictionariesProvider.class.getResourceAsStream(name), name);
  }

  private static Loader createLoader(final Set<String> words) {
    return new Loader() {
      public void load(@NotNull Consumer<String> consumer) {
        for (String word : words) {
          consumer.consume(word);
        }
      }

      public String getName() {
        return "test";
      }
    };
  }

  @SuppressWarnings({"unchecked"})
  private static Pair<Set<String>, Set<String>> createWordSets(String name, final int maxCount, final int mod) {
    Loader loader = createLoader(name);
    final Set<String> wordsToStore = new THashSet<String>();
    final Set<String> wordsToCheck = new THashSet<String>();
    final Transformation transform = new Transformation();
    loader.load(new Consumer<String>() {
      private int counter = 0;

      public void consume(String s) {
        if (counter > maxCount) {
          return;
        }
        String transformed = transform.transform(s);
        if (transformed != null) {

          if (counter % mod == 0) {
            wordsToStore.add(transformed);
          }
          else {
            wordsToCheck.add(transformed);
          }
          counter++;
        }
      }
    });

    return new Pair(wordsToStore, wordsToCheck);
  }


  public void loadHalfDictionaryTest(final int maxCount, final String name) throws IOException {

    final Pair<Set<String>, Set<String>> sets = createWordSets(name, maxCount, 2);
    CompressedDictionary dictionary = CompressedDictionary.create(createLoader(sets.getFirst()), new Transformation());
    for (String s : sets.getSecond()) {
      if (!sets.getFirst().contains(s)) {
        assertFalse(dictionary.contains(s));
      }
    }
  }


}
