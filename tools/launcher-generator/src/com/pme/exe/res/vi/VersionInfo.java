/*
 * Copyright 2006 ProductiveMe Inc.
 * Copyright 2013 JetBrains s.r.o.
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

package com.pme.exe.res.vi;

import com.pme.exe.Bin;
import com.pme.util.OffsetTrackingInputStream;

import java.io.DataInput;
import java.io.IOException;

public class VersionInfo extends Bin.Structure {
  public VersionInfo() {
    super("VersionInfo");
    addMember(new Word("wLength"));
    addMember(new Word("wValueLength"));
    addMember(new Word("wType"));
    addMember(new Bytes("szKey", 32));
    addMember(new Padding(4));
    addMember(new FixedFileInfo());
    addMember(new Padding(4));
    addMember(new StringFileInfo());
    addMember(new VarFileInfo());
  }

  @Override
  public void read(DataInput stream) throws IOException {
    long startOffset = -1;
    if (stream instanceof OffsetTrackingInputStream) {
      startOffset = ((OffsetTrackingInputStream) stream).getOffset();
    }
    super.read(stream);
    String signature = ((Bytes) getMember("szKey")).getAsWChar();
    assert signature.equals("VS_VERSION_INFO"): "Expected signature VS_VERSION_INFO, found '" + signature + "'";
    if (stream instanceof OffsetTrackingInputStream) {
      long offset = ((OffsetTrackingInputStream) stream).getOffset();
      long length = getValue("wLength");
      assert startOffset + length == offset: "Length specified in version info header (" + length +
          ") does not match actual version info length (" + (offset - startOffset) + ")";
    }
  }
}
