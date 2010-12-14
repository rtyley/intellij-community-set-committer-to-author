package org.jetbrains.android.dom;

import com.android.sdklib.SdkConstants;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.inspections.AndroidUnknownAttributeInspection;
import org.jetbrains.android.sdk.Android15TestProfile;
import org.jetbrains.android.sdk.AndroidSdkTestProfile;

import java.util.List;

/**
 * @author coyote
 */
public class AndroidManifestDomTest extends AndroidDomTest {
  public AndroidManifestDomTest() {
    super(false, "dom/manifest");
  }

  @Override
  public AndroidSdkTestProfile getTestProfile() {
    return new Android15TestProfile();
  }

  @Override
  protected String getPathToCopy(String testFileName) {
    return SdkConstants.FN_ANDROID_MANIFEST_XML;
  }

  public void testAttributeNameCompletion1() throws Throwable {
    doTestCompletionVariants("an1.xml", "android:icon", "android:label", "android:priority");
  }

  public void testAttributeNameCompletion2() throws Throwable {
    doTestCompletionVariants("an2.xml", "debuggable", "description");
  }

  public void testAttributeNameCompletion3() throws Throwable {
    toTestCompletion("an3.xml", "an3_after.xml");
  }

  public void testAttributeNameCompletion4() throws Throwable {
    toTestCompletion("an4.xml", "an4_after.xml");
  }

  public void testAttributeByLocalNameCompletion() throws Throwable {
    toTestCompletion("attrByLocalName.xml", "attrByLocalName_after.xml");
  }

  public void testTagNameCompletion2() throws Throwable {
    doTestCompletionVariants("tn2.xml", "manifest");
  }

  public void testHighlighting() throws Throwable {
    doTestHighlighting("hl.xml");
  }

  public void testHighlighting2() throws Throwable {
    doTestHighlighting("hl2.xml");
  }

  public void testTagNameCompletion3() throws Throwable {
    toTestCompletion("tn3.xml", "tn3_after.xml");
  }

  public void testTagNameCompletion4() throws Throwable {
    toTestCompletion("tn4.xml", "tn4_after.xml");
  }

  public void testAttributeValueCompletion1() throws Throwable {
    doTestCompletionVariants("av1.xml", "behind", "landscape", "nosensor", "portrait", "sensor", "unspecified", "user");
  }

  public void testResourceCompletion1() throws Throwable {
    doTestCompletionVariants("av2.xml", "@android:", "@style/style1");
  }

  public void testResourceCompletion2() throws Throwable {
    doTestCompletionVariants("av3.xml", "@android:", "@string/hello", "@string/hello1", "@string/welcome", "@string/welcome1",
                           "@string/itStr");
  }

  public void testResourceCompletion3() throws Throwable {
    List<String> list = getAllResources();
    list.add("@android:");
    doTestCompletionVariants("av4.xml", ArrayUtil.toStringArray(list));
  }

  public void testTagNameCompletion1() throws Throwable {
    doTestCompletionVariants("tn1.xml", "uses-permission", "uses-sdk", "uses-configuration");
  }

  public void testSoftTagsAndAttrs() throws Throwable {
    myFixture.disableInspections(new AndroidUnknownAttributeInspection());
    doTestHighlighting("soft.xml");
  }

  public void testUnknownAttribute() throws Throwable {
    doTestHighlighting("unknownAttribute.xml");
  }

  /*public void testNamespaceCompletion() throws Throwable {
    toTestCompletion("ns.xml", "ns_after.xml");
  }*/

  public void testInnerActivityHighlighting() throws Throwable {
    copyFileToProject("A.java", "src/p1/p2/A.java");
    doTestHighlighting(getTestName(false) + ".xml");
  }

  public void testInnerActivityCompletion() throws Throwable {
    copyFileToProject("A.java", "src/p1/p2/A.java");
    doTestCompletion();
  }

  public void testUsesPermissionCompletion() throws Throwable {
    doTestCompletion();
  }

  private void doTestCompletion() throws Throwable {
    toTestCompletion(getTestName(false) + ".xml", getTestName(false) + "_after.xml");
  }
}
