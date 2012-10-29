package com.intellij.tasks.generic;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileTypes.FileType;

/**
 * User: evgeny.zakrevsky
 * Date: 10/25/12
 */
public enum ResponseType {
  XML("application/xml", XmlFileType.INSTANCE),
  HTML("text/html", HtmlFileType.INSTANCE);
  //todo JSON("application/json")

  private String myMimeType;
  private FileType myFileType;

  ResponseType(final String s, final FileType fileType) {
    myMimeType = s;
    myFileType = fileType;
  }

  public String getMimeType() {
    return myMimeType;
  }

  public FileType getFileType() {
    return myFileType;
  }
}
