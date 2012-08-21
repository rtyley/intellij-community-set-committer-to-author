package org.jetbrains.jps.builders;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import junit.framework.Assert;
import org.jetbrains.jps.incremental.MessageHandler;
import org.jetbrains.jps.incremental.messages.BuildMessage;

import java.util.ArrayList;
import java.util.List;

/**
* @author nik
*/
public class BuildResult implements MessageHandler {
  private final List<BuildMessage> myErrorMessages;
  private final List<BuildMessage> myInfoMessages;

  public BuildResult() {
    myErrorMessages = new ArrayList<BuildMessage>();
    myInfoMessages = new ArrayList<BuildMessage>();
  }

  @Override
  public void processMessage(BuildMessage msg) {
    if (msg.getKind() == BuildMessage.Kind.ERROR) {
      myErrorMessages.add(msg);
    }
    else {
      myInfoMessages.add(msg);
    }
  }

  public void assertFailed() {
    Assert.assertFalse("Build not failed as expected", isSuccessful());
  }

  public boolean isSuccessful() {
    return myErrorMessages.isEmpty();
  }

  public void assertSuccessful() {
    final Function<BuildMessage,String> toStringFunction = StringUtil.createToStringFunction(BuildMessage.class);
    Assert.assertTrue("Build failed. \nErrors:\n" + StringUtil.join(myErrorMessages, toStringFunction, "\n") +
                      "\nInfo messages:\n" + StringUtil.join(myInfoMessages, toStringFunction, "\n"), isSuccessful());
  }
}
