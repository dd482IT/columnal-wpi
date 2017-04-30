<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
    <xsl:param name="myOutputDir"/>
    <xsl:template match="/dialog">
        <xsl:variable name="rootId" select="@id"/>
        <xsl:for-each select="help">
            <xsl:result-document method="html" href="file:///{$myOutputDir}/{$rootId}-{@id}.html">
                <html>
                    <head>
                        <link rel="stylesheet" href="help.css"/>
                    </head>
                    <body>
                        <p><b><xsl:value-of select="short"/></b></p>
                        <p><xsl:value-of select="full"/></p>
                    </body>
                </html>
            </xsl:result-document>
        </xsl:for-each>
    </xsl:template>
</xsl:stylesheet>