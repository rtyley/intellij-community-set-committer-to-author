package com.intellij.codeInsight.template.zencoding.nodes;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.zencoding.ZenCodingTemplate;
import com.intellij.codeInsight.template.zencoding.ZenCodingUtil;
import com.intellij.codeInsight.template.zencoding.tokens.TemplateToken;
import com.intellij.codeInsight.template.zencoding.tokens.TextToken;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class TextNode extends ZenCodingNode {
  private final String myText;

  public TextNode(@NotNull TextToken textToken) {
    final String text = textToken.getText();
    myText = text.substring(1, text.length() - 1);
  }

  public String getText() {
    return myText;
  }

  @NotNull
  @Override
  public List<GenerationNode> expand(int numberInIteration, String surroundedText, CustomTemplateCallback callback) {
    final TemplateToken templateToken = new TemplateToken("", Collections.<Pair<String, String>>emptyList());
    final String text = ZenCodingUtil.replaceNumberMarkersBy(myText.replace("${nl}", "\n"), Integer.toString(numberInIteration + 1));
    final TemplateImpl template = new TemplateImpl("", text, "");
    ZenCodingTemplate.doSetTemplate(templateToken, template, callback);
    final GenerationNode node = new GenerationNode(templateToken, new ArrayList<GenerationNode>(), numberInIteration, surroundedText);
    return Collections.singletonList(node);
  }
}
