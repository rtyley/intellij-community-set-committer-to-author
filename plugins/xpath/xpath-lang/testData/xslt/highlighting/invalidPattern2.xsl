<xsl:stylesheet version="1.1" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:param name="x" />

  <xsl:template match='*[. = $x]' />
</xsl:stylesheet>