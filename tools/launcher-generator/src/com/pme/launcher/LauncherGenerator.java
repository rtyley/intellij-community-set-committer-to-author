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

package com.pme.launcher;

import com.pme.exe.ExeReader;
import com.pme.exe.SectionReader;
import com.pme.exe.Bin;
import com.pme.exe.res.*;
import com.pme.exe.res.bmp.PictureResourceInjector;
import com.pme.exe.res.icon.IconResourceInjector;
import com.pme.exe.res.vi.VersionInfo;
import com.pme.util.OffsetTrackingInputStream;

import java.io.*;

/**
 * Date: May 6, 2006
 * Time: 10:43:01 AM
 */
public class LauncherGenerator {
  private File myTemplate;
  private File myIcon;
  private File myBmp;
  private File myExePath;
  private StringTableDirectory myStringTableDirectory;
  private DirectoryEntry myRoot;
  private ExeReader myReader;

  public LauncherGenerator(File template, File exePath) {
    myTemplate = template;
    myExePath = exePath;
  }


  public void load() throws  IOException {
    myReader = new ExeReader(myTemplate.getName());
    RandomAccessFile stream = new RandomAccessFile(myTemplate, "r");
    myReader.read(stream);
    stream.close();
    SectionReader sectionReader = myReader.getSectionReader(".rsrc");
    ResourceSectionReader resourceReader = (ResourceSectionReader) sectionReader.getMember(".rsrc");
    myRoot = resourceReader.getRoot();
    DirectoryEntry subDir = myRoot.findSubDir("IRD6");
    myStringTableDirectory = new StringTableDirectory(subDir);
  }

  public void generate() throws IOException {
    myStringTableDirectory.save();

    if (myIcon != null) {
      IconResourceInjector iconInjector = new IconResourceInjector();
      iconInjector.injectIcon(myIcon, myRoot, "IRD101");
    }

    if (myBmp != null) {
      PictureResourceInjector bmpInjector = new PictureResourceInjector();
      bmpInjector.inject(myBmp, myRoot, "IRD104");
    }

    DirectoryEntry viDir = myRoot.findSubDir("IRD16").findSubDir( "IRD1" );
    Bin.Bytes viBytes = viDir.getRawResource( 0 ).getBytes();
    ByteArrayInputStream bytesStream = new ByteArrayInputStream(viBytes.getBytes());

    VersionInfo viReader = new VersionInfo();
    viReader.read(new OffsetTrackingInputStream(new DataInputStream(bytesStream)));

    myReader.resetOffsets(0);

    myExePath.getParentFile().mkdirs();
    myExePath.createNewFile();
    RandomAccessFile exeStream = new RandomAccessFile(myExePath, "rw");
    myReader.write(exeStream);
    exeStream.close();
  }

  public void setResourceString(int id, String value) {
    myStringTableDirectory.setString(id, value);
  }
}
