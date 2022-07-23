/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.plantuml;

import net.sourceforge.plantuml.FileFormat;

/**
 * PlantUML Image Format.
 *
 * @version $Id$
 * @since 2.1.2
 */
public enum ImageFormat
{
    /**
     * PNG image format.
     */
    png("png", FileFormat.PNG),

    /**
     * SVG image format.
     */
    svg("svg", FileFormat.SVG),

    /**
     * SVG xhtml format.
     */
    svg_xml("svg", FileFormat.SVG),

    /**
     * Plaintext format.
     */
    txt("txt", FileFormat.UTXT);

    private final String requestType;
    private final FileFormat fileFormat;

    ImageFormat(String requestType, FileFormat fileFormat)
    {
        this.requestType = requestType;
        this.fileFormat = fileFormat;
    }

    /**
     * @return return PlantUML Server request type.
     */
    public String getRequestType() {
        return this.requestType;
    }

    /**
     * @return return image file format.
     */
    public FileFormat getFileFormat()
    {
        return this.fileFormat;
    }
}
