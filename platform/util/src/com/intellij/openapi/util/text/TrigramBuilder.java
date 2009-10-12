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

/*
 * @author max
 */
package com.intellij.openapi.util.text;

import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class TrigramBuilder {
  private TrigramBuilder() {
  }

  public static TIntHashSet buildTrigram(CharSequence text) {
    TIntHashSet caseInsensitive = new TIntHashSet();

    int tc1 = 0;
    int tc2 = 0;
    int tc3;

    for (int i = 0; i < text.length(); i++) {
      char c = StringUtil.toLowerCase(text.charAt(i));

      tc3 = (tc2 << 8) + c;
      tc2 = (tc1 << 8) + c;
      tc1 = c;

      if (i >= 2) {
        if (!blackList.contains(tc3)) {
          caseInsensitive.add(tc3);
        }
      }
    }

    return caseInsensitive;
  }

  public static void main(String[] args) throws IOException {
    File root = new File(args[0]);

    Stats stats = new Stats();
    walk(root, stats);

    System.out.println("Scanned " + stats.files + " files, total of " + stats.lines + " lines in " + (stats.time / 1000000) + " ms.");
    System.out.println("Size:" + stats.bytes);
    System.out.println("Total trigrams: " + stats.allTrigrams.size());
    System.out.println("Max per file: " + stats.maxtrigrams);

    System.out.println("Sample query 1: " + lookup(stats.filesMap, "trigram"));
    System.out.println("Sample query 2: " + lookup(stats.filesMap, "some text that most probably doesn't exist"));
    System.out.println("Sample query 3: " + lookup(stats.filesMap, "ProfilingUtil.captureCPUSnapshot();"));

    System.out.println("Stop words:");

    listWithBarier(stats, stats.files * 2 / 4);
    listWithBarier(stats, stats.files * 3 / 4);
    listWithBarier(stats, stats.files * 4 / 5);
  }

  private static void listWithBarier(Stats stats, final int barrier) {
    final int[] stopCount = {0};
    stats.filesMap.forEachEntry(new TIntObjectProcedure<List<File>>() {
      public boolean execute(int a, List<File> b) {
        if (b.size() > barrier) {
          System.out.println(a);
          stopCount[0]++;
        }
        return true;
      }
    });

    System.out.println("Total of " + stopCount[0]);
  }

  private static Collection<File> lookup(TIntObjectHashMap<List<File>> trigramsDatabase, String query) {
    final Set<File> result = new HashSet<File>();
    int[] graphs = buildTrigram(query).toArray();
    boolean first = true;

    for (int graph : graphs) {
      if (first) {
        result.addAll(trigramsDatabase.get(graph));
        first = false;
      }
      else {
        result.retainAll(trigramsDatabase.get(graph));
      }
    }

    return result;
  }

  private static void lex(File root, Stats stats) throws IOException {
    stats.files++;
    BufferedReader reader = new BufferedReader(new FileReader(root));
    String s;
    StringBuilder buf = new StringBuilder();
    while ((s = reader.readLine()) != null) {
      stats.lines++;
      buf.append(s).append("\n");
    }

    stats.bytes += buf.length();

    long start = System.nanoTime();
    TIntHashSet localTrigrams = lexText(buf);
    stats.time += System.nanoTime() - start;
    stats.maxtrigrams = Math.max(stats.maxtrigrams, localTrigrams.size());
    int[] graphs = localTrigrams.toArray();
    stats.allTrigrams.addAll(graphs);
    for (int graph : graphs) {
      List<File> list = stats.filesMap.get(graph);
      if (list == null) {
        list = new ArrayList<File>();
        stats.filesMap.put(graph, list);
      }
      list.add(root);
    }
  }

  private static TIntHashSet lexText(StringBuilder buf) {
    return buildTrigram(buf);
  }

  private static class Stats {
    public int files;
    public int lines;
    public int maxtrigrams;
    public long time;
    public long bytes;
    public final TIntHashSet allTrigrams = new TIntHashSet();
    public final TIntObjectHashMap<List<File>> filesMap = new TIntObjectHashMap<List<File>>();
    public final Set<String> extensions = new HashSet<String>();
  }

  private static final Set<String> allowedExtension = new HashSet<String>(
    Arrays.asList("iml", "xml", "java", "html", "bat", "policy", "properties", "sh", "dtd", "ipr", "txt", "plist", "form", "xsl", "css",
                  "jsp", "jspx", "xhtml", "tld", "htm", "tag", "jspf", "js", "ft", "xsd", "xls", "rb", "php", "ftl", "c", "y", "erb", "rjs",
                  "rhtml", "sql", "cfml", "groovy", "text", "gsp", "h", "cc", "cpp", "wsdl"));

  private static void walk(File root, Stats stats) throws IOException {
    String name = root.getName();
    if (root.isDirectory()) {
      if (name.startsWith(".") || name.equals("out")) return;
      System.out.println("Lexing in " + root.getPath());
      for (File file : root.listFiles()) {
        walk(file, stats);
      }
    }
    else {
      String ext = FileUtil.getExtension(name);
      if (!allowedExtension.contains(ext)) return;
      if (root.length() > 100 * 1024) return;

      stats.extensions.add(ext);
      lex(root, stats);
    }
  }

  private static final TIntHashSet blackList = new TIntHashSet(
    new int[]{2105391, 2105376, 3158061, 2105458, 2105408, 2105469, 2105446, 2105459, 2105443, 2105404, 2105445, 2105661, 2105453, 2105932,
      2105449, 7369833, 7367785, 7367781, 7366958, 7366002, 7365998, 7365987, 7365920, 7364978, 7364963, 3153960, 5267826, 6845039, 6845556,
      8063520, 8063498, 2107951, 2113904, 2114113, 4552803, 3148320, 4549733, 2118501, 7354927, 5600361, 5600101, 687370, 6649972, 6649968,
      6649955, 6649632, 684149, 6648949, 6648936, 6648933, 6648930, 6648916, 6648915, 6648912, 6648905, 6648901, 6648899, 6648898, 6648692,
      6648677, 6648675, 6648623, 6648622, 6648616, 6648608, 6648438, 6648436, 6648435, 6648434, 6648430, 6648429, 6648422, 6648421, 6648417,
      6648379, 6648366, 6648361, 6648360, 6648352, 6648181, 682349, 6647412, 6647411, 6647397, 6647396, 6647395, 6647393, 6647141, 6646899,
      6646892, 6646885, 2125670, 6646132, 2126693, 2126703, 2126709, 2126962, 6645345, 2127219, 2697531, 6645092, 6645024, 7697513, 7697512,
      7697509, 6644768, 6644585, 7697253, 7697006, 6644084, 6644068, 7696485, 7695972, 7695476, 7695468, 2117746, 7694706, 2118245, 7693627,
      7692908, 7692652, 7692647, 6637934, 7496045, 670767, 6634250, 667434, 7235372, 6631023, 6631015, 6630922, 7562540, 6630432, 7498096,
      4875636, 6629691, 6629664, 663584, 7758196, 6627881, 2767626, 6627451, 6627447, 6627444, 6627443, 6627440, 6627439, 6627436, 6627433,
      6627431, 6627430, 6627429, 6627425, 6627404, 6627393, 6627389, 6627362, 2125666, 2125938, 2125941, 7565158, 7676013, 2763274, 6711662,
      4408654, 6514035, 6972771, 6621728, 2760771, 2760768, 3813167, 3110775, 2129162, 7217261, 6516592, 3107945, 2755104, 6958709, 6958704,
      6958703, 2125682, 4065824, 7304805, 7304564, 7304562, 7304480, 7304289, 7304270, 7304224, 7303801, 7303796, 7303789, 7303783, 7303781,
      7303726, 7303712, 7303269, 3092343, 6382188, 5197395, 5197380, 7302771, 7302757, 7302702, 7302696, 7302688, 6382443, 7302512, 7302509,
      7302446, 4419437, 6382692, 7302252, 7302245, 3090986, 3090954, 7301733, 6383474, 7301476, 6383982, 7300724, 5195296, 7300640, 7300201,
      7300197, 6385252, 7299700, 4281120, 7088416, 4285300, 3082864, 7628141, 5536112, 7103859, 4481396, 7233901, 7234661, 5129289, 7234935,
      6583072, 7235429, 7235445, 6581349, 7103784, 7103803, 7103843, 7103853, 7282793, 7631477, 6580596, 6580595, 7633264, 7632737, 6579570,
      6579558, 6579488, 7631662, 7631648, 6579564, 7630906, 7630706, 7630624, 7238501, 5523781, 7629167, 7629164, 7629155, 7238715, 5133385,
      5523535, 7628901, 7628832, 4475220, 7628146, 7628132, 7628131, 7628064, 7627624, 7627124, 4467744, 5662066, 7622261, 7622241, 7620974,
      4812404, 4812403, 7619439, 5513303, 7617290, 6563881, 5861237, 2702090, 6561909, 6561908, 6561907, 6561903, 6561890, 6561889, 6912613,
      7613472, 6911589, 6911585, 4805705, 4805704, 6911081, 6911080, 6911077, 7612704, 6910835, 6910752, 7612457, 6910565, 6910068, 4804430,
      2698855, 6909555, 6909545, 6909543, 6909540, 6909537, 6909472, 6909296, 6909289, 6909029, 6909028, 6908974, 7610485, 7610484, 7610479,
      7610473, 7610469, 7610467, 7610429, 6908462, 6907497, 6907424, 6907236, 6556192, 6906981, 6906912, 4801349, 6906721, 6906656, 6906485,
      7959145, 6906222, 2695291, 7958629, 7958389, 3044724, 3044197, 3043941, 3043886, 3043443, 3043184, 7604768, 3042401, 3041893, 3041651,
      3041646, 3041125, 3040873, 2689568, 3040111, 3040108, 3039600, 3039598, 3039588, 5382211, 5141868, 3043442, 3035251, 3034735, 5136749,
      7497057, 7498087, 7239020, 7238761, 7238702, 7238697, 7238688, 7238516, 7238505, 7238446, 7238432, 6907752, 3026976, 4342099, 7237231,
      5395009, 7500649, 7236709, 7938159, 7938158, 7938145, 3025467, 7235956, 7235950, 2698272, 2238779, 7235374, 7235368, 7235360, 7234930,
      6909541, 6909556, 5129260, 7234592, 7234570, 7233904, 7233895, 6910836, 7612731, 6385262, 3017248, 4419440, 4419438, 4418657, 7299937,
      7224074, 4065852, 5469298, 5469281, 5465460, 5465445, 6517865, 6517861, 6517806, 6517792, 7219241, 6517353, 6517349, 4411214, 6516590,
      6516589, 6516580, 6515809, 6515553, 7217275, 7217271, 7217268, 7217262, 7217257, 7217254, 7217253, 7217251, 7217249, 7217202, 7217186,
      6514789, 6514030, 6513960, 7566450, 7566433, 2697504, 7566185, 7566177, 7566112, 7565413, 2961966, 2651240, 7563631, 7563566, 7562612,
      7562610, 7562542, 7562530, 7562528, 7496041, 7561575, 2647657, 7627113, 7627378, 5447759, 7551754, 7628905, 7629166, 6496374, 6496361,
      6496358, 6496354, 6496339, 7548530, 7548513, 7548494, 3288624, 7547936, 7499639, 6845472, 7547195, 7547168, 7546921, 6844274, 7104867,
      7631465, 7544949, 7544947, 7544943, 7544934, 7544929, 2631995, 2631977, 7105595, 6842739, 7544893, 7544932, 7544946, 2631968, 2631982,
      6841714, 6841646, 6841632, 7894117, 7893106, 7892332, 7107429, 6496355, 6496364, 7539232, 4738901, 7889765, 5447753, 5448224, 5074788,
      5071214, 7562098, 7562555, 7562596, 7562611, 6824052, 2961968, 7172207, 7172204, 6512994, 6513004, 6513012, 7171940, 4013344, 7566368,
      7566437, 6513952, 7566704, 7170420, 7170419, 6515046, 7169396, 7169395, 7169320, 7169312, 7168377, 6517871, 4005998, 6384745, 6384757,
      4354657, 2247785, 6385776, 2244947, 6453536, 7155305, 7300213, 5399923, 6452321, 6451809, 7233900, 6451055, 7234405, 7234675, 6450281,
      6450277, 6449765, 7502112, 7501157, 7500832, 7237492, 3289136, 7499636, 7499626, 7499369, 7499296, 5133088, 5133125, 7499113, 5198368,
      7238696, 7238753, 7498102, 7498100, 7498094, 7498082, 5134624, 7497519, 7497518, 7497076, 7497075, 7497073, 7497070, 7497062, 7497061,
      7497060, 7497059, 7496992, 7496970, 7496549, 5390670, 7496052, 7496035, 3031919, 6780257, 8194592, 8194570, 5140340, 7486218, 3043118,
      3043186, 7482991, 7482983, 7482400, 7481385, 6779493, 6906725, 4801875, 7479419, 7479412, 7479401, 7479397, 7479393, 7479357, 6777198,
      6776948, 7829367, 7829294, 7610474, 7610480, 6776180, 6776178, 6776104, 6776096, 7828256, 4804164, 7828073, 6514032, 6909806, 4805410,
      6911073, 6911087, 5720404, 7825780, 5718354, 7823730, 8194685, 6565386, 7619186, 7622770, 5007731, 5007715, 6762090, 6761504, 7627118,
      7628072, 7628140, 7628142, 7628147, 7628152, 6758516, 6758512, 6758503, 7810657, 7628914, 5523791, 7107941, 7631457, 7631984, 7105641,
      7105577, 7105568, 7632242, 6579571, 7807087, 4999491, 7104877, 7104874, 7104869, 7104865, 6580590, 7103841, 7103790, 7103776, 7103754,
      7102836, 7102835, 7102830, 7102818, 2891892, 2891891, 2891878, 2891877, 2891862, 5531000, 2886176, 6452596, 4287596, 663594, 7093002,
      2178336, 6388000, 6387297, 6387060, 6386793, 6386792, 6386789, 6386698, 6386547, 6386533, 4280914, 6386277, 6386273, 6385769, 6385761,
      4279897, 4279892, 6385255, 6385251, 6385249, 6385184, 6384997, 6384755, 6384748, 6384672, 7086163, 7086160, 6383461, 7435625, 7435617,
      6382452, 6382440, 6382437, 7302715, 7302772, 7566181, 7303289, 7303720, 7304293, 5199188, 7305075, 7080480, 4614508, 6368885, 7234668,
      6365283, 7169390, 6714487, 5661036, 2760789, 6971766, 6713202, 6644078, 6972788, 6711660, 6711653, 6711651, 7303020, 657952, 657967,
      658025, 658032, 6710642, 2764554, 6709612, 6709603, 7761769, 6627427, 7759218, 7238757, 7238771, 4542035, 7758188, 7758126, 6647924,
      6629417, 6648691, 4595777, 6692980, 6692904, 2128650, 2128239, 6644596, 2127730, 5987616, 2127721, 5592096, 6644782, 2127471, 2127457,
      2127214, 2126959, 2126952, 2126949, 4933966, 2126708, 2126704, 2126638, 2126437, 2125925, 2125921, 2125678, 2125429, 2125423, 2125413,
      2125409, 2125177, 2125157, 2125153, 2124905, 2124897, 7037287, 2124385, 2124147, 2124142, 2124141, 2124134, 2123892, 4543264, 2123631,
      2123621, 2123375, 2123369, 2123361, 2123128, 2123118, 2123116, 2123113, 684129, 2122863, 2122857, 2122853, 2122607, 2122604, 2122600,
      2122593, 2122361, 2122351, 2122341, 2122100, 2122096, 2122094, 2122087, 2122016, 2120047, 2119497, 2119489, 2119269, 2119022, 2118516,
      7628911, 2117747, 2117458, 2117446, 2116713, 2116197, 2115950, 2115923, 3869289, 3869216, 3869194, 2114415, 2114383, 5272425, 5272175,
      2113870, 4219253, 2113633, 2113614, 2112829, 2112800, 2112032, 2110000, 2109998, 4214383, 6840686, 7367777, 6578548, 2107936, 2107914,
      3157298, 7371122, 7371040, 2107508, 2107491, 7368562, 7368558, 7370082, 7369760, 7369577, 2105921, 7369327, 7369321, 7369317, 7368812,
      3158064, 2105460, 2105456, 2105441, 2105427});
}

