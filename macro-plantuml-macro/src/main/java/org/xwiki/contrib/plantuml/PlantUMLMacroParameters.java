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

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.xwiki.properties.annotation.PropertyAdvanced;
import org.xwiki.properties.annotation.PropertyDescription;
import org.xwiki.properties.annotation.PropertyDisplayType;

/**
 * Parameters for the {@link org.xwiki.contrib.plantuml.internal.PlantUMLMacro} Macro.
 *
 * @version $Id$
 * @since 2.0
 */
public class PlantUMLMacroParameters
{
    private String serverURL;
    private ImageFormat imageFormat;
    private boolean imageTag;
    private boolean scaleFit;

    /**
     * @param serverURL see {@link #getServer()}
     */
    @PropertyAdvanced
    @PropertyDescription("the PlantUML Server URL")
    public void setServer(String serverURL)
    {
        this.serverURL = serverURL;
    }

    /**
     * @return the (optional) PlantUML server URL (e.g. {@code http://www.plantuml.com/plantuml})
     */
    public String getServer()
    {
        return this.serverURL;
    }

    /**
     * @param imageFormat see {@link ImageFormat}
     */
    @PropertyDisplayType(ImageFormat.class)
    @PropertyDescription("the PlantUML Image Format")
    public void setFormat(ImageFormat imageFormat)
    {
        this.imageFormat = imageFormat;
    }

    /**
     * @return the (optional) PlantUML image format.
     */
    public ImageFormat getFormat()
    {
        return this.imageFormat;
    }

    /**
     * @param scaleFit see {@link #isScaleFit()}
     */
    @PropertyDescription("Fit SVG Image to Page Width Limit")
    public void setScaleFit(boolean scaleFit) {
        this.scaleFit = scaleFit;
        this.imageTag = this.imageTag && !scaleFit;
    }

    /**
     * @return {@code true} if fit SVG image to page width limit.
     */
    public boolean isScaleFit() {
        return scaleFit;
    }

    /**
     * @param imageTag see {@link #isImageTag()}
     */
    @PropertyDescription("Use <img/> For SVG Image")
    public void setImageTag(boolean imageTag) {
        this.imageTag = imageTag;
        this.scaleFit = this.scaleFit && !imageTag;
    }

    /**
     * @return {@code true} if use &lt;img/&gt; for SVG image.
     */
    public boolean isImageTag() {
        return imageTag;
    }

    @Override
    public boolean equals(Object object)
    {
        if (object == null) {
            return false;
        }
        if (object == this) {
            return true;
        }
        if (object.getClass() != getClass()) {
            return false;
        }
        PlantUMLMacroParameters rhs = (PlantUMLMacroParameters) object;
        return new EqualsBuilder()
            .append(getServer(), rhs.getServer())
            .append(getFormat(), rhs.getFormat())
            .isEquals();
    }

    @Override
    public int hashCode()
    {
        return new HashCodeBuilder(5, 37)
            .append(getServer())
            .append(getFormat())
            .toHashCode();
    }
}
