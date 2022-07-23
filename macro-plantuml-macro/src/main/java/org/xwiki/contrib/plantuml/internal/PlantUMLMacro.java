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
package org.xwiki.contrib.plantuml.internal;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.plantuml.ImageFormat;
import org.xwiki.contrib.plantuml.PlantUMLConfiguration;
import org.xwiki.contrib.plantuml.PlantUMLGenerator;
import org.xwiki.contrib.plantuml.PlantUMLMacroParameters;
import org.xwiki.contrib.plantuml.internal.store.ImageWriter;
import org.xwiki.rendering.async.internal.AsyncRendererConfiguration;
import org.xwiki.rendering.async.internal.block.BlockAsyncRendererExecutor;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.CompositeBlock;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.ImageBlock;
import org.xwiki.rendering.block.RawBlock;
import org.xwiki.rendering.block.VerbatimBlock;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.macro.descriptor.DefaultContentDescriptor;
import org.xwiki.rendering.transformation.MacroTransformationContext;

/**
 * Asynchronous macro that generates an image from a textual description, using PlantUML.
 *
 * @version $Id$
 * @since 2.0
 */
@Component
@Named("plantuml")
@Singleton
public class PlantUMLMacro extends AbstractMacro<PlantUMLMacroParameters>
{
    /**
     * The description of the macro.
     */
    private static final String DESCRIPTION =
        "Convert various text input formats into diagram images using PlantUML.";

    /**
     * The description of the macro content.
     */
    private static final String CONTENT_DESCRIPTION = "The textual definition of the diagram";

    @Inject
    private BlockAsyncRendererExecutor executor;

    @Inject
    private Provider<PlantUMLBlockAsyncRenderer> asyncRendererProvider;

    @Inject
    private PlantUMLGenerator plantUMLGenerator;

    @Inject
    @Named("tmp")
    private ImageWriter imageWriter;

    @Inject
    private PlantUMLConfiguration configuration;

    /**
     * Create and initialize the descriptor of the macro.
     */
    public PlantUMLMacro()
    {
        super("PlantUML", DESCRIPTION, new DefaultContentDescriptor(CONTENT_DESCRIPTION),
            PlantUMLMacroParameters.class);
        setDefaultCategory(DEFAULT_CATEGORY_CONTENT);
    }

    @Override
    public boolean supportsInlineMode()
    {
        return true;
    }

    @Override
    public List<Block> execute(PlantUMLMacroParameters parameters, String content, MacroTransformationContext context)
        throws MacroExecutionException
    {
        return executeAsync(parameters, content, context);
    }

    private List<Block> executeAsync(PlantUMLMacroParameters parameters, String content,
        MacroTransformationContext context) throws MacroExecutionException
    {
        PlantUMLBlockAsyncRenderer renderer = this.asyncRendererProvider.get();
        renderer.initialize(this, parameters, content, context);

        AsyncRendererConfiguration rendererConfiguration = new AsyncRendererConfiguration();
        rendererConfiguration.setContextEntries(Collections.singleton("doc.reference"));

        // Execute the renderer
        List<Block> blocks;
        try {
            if (context.isInline()) {
                blocks = executeSync(content, parameters, context.isInline());
            } else {
                Block result = this.executor.execute(renderer, rendererConfiguration);
                blocks = result instanceof CompositeBlock ? result.getChildren() : Collections.singletonList(result);
            }
        } catch (Exception e) {
            throw new MacroExecutionException(String.format("Failed to execute the PlantUML macro for content [%s]",
                content), e);
        }

        return blocks;
    }

    List<Block> executeSync(String content, PlantUMLMacroParameters parameters, boolean isInline)
        throws MacroExecutionException
    {
        String serverUrl = computeServer(parameters);
        String imageFormat = computeFormat(parameters);
        String imageId = getImageId(imageFormat, content);

        try (OutputStream os = this.imageWriter.getOutputStream(imageId, imageFormat)) {
            this.plantUMLGenerator.outputImage(content, os, serverUrl, imageFormat);
        } catch (IOException e) {
            throw new MacroExecutionException(
                String.format("Failed to generate an image using PlantUML for content [%s]", content), e);
        }

        // Return the image block pointing to the generated image.
        Block resultBlock;
        if (ImageFormat.svg_xml.name().equals(imageFormat) || ImageFormat.txt.name().equals(imageFormat)) {
            // Read image file content.
            File imageFile = this.imageWriter.getStorageLocation(imageId, imageFormat);
            String imageContent;
            try (FileInputStream is = new FileInputStream(imageFile)) {
                byte[] imageBytes = IOUtils.readFully(is, (int) imageFile.length());
                imageContent = new String(imageBytes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new MacroExecutionException(
                        String.format("Failed to read image file generated by PlantUML for content [%s]", content), e);
            }

            // Create corresponding block to display the image content.
            if (ImageFormat.svg_xml.name().equals(imageFormat)) {
                resultBlock = new RawBlock(imageContent, Syntax.XHTML_1_0);
            } else {
                resultBlock = new VerbatimBlock(imageContent, isInline);
            }
        } else {
            ResourceReference resourceReference =
                    new ResourceReference(this.imageWriter.getURL(imageId, imageFormat).serialize(), ResourceType.URL);
            resultBlock = new ImageBlock(resourceReference, false);
        }

        // Wrap in a DIV if not inline (we need that since an IMG/SVG is an inline element otherwise)
        if (!isInline && !ImageFormat.txt.name().equals(imageFormat)) {
            resultBlock = new GroupBlock(Collections.singletonList(resultBlock));
        }
        return Collections.singletonList(resultBlock);
    }

    private String computeServer(PlantUMLMacroParameters parameters)
    {
        String serverURL = parameters.getServer();
        if (serverURL == null) {
            serverURL = this.configuration.getPlantUMLServerURL();
        }
        return serverURL;
    }

    private String computeFormat(PlantUMLMacroParameters parameters)
    {
        String imageFormat = null;
        if (parameters.getFormat() != null) {
            imageFormat = parameters.getFormat().name();
        }
        if (imageFormat == null) {
            imageFormat = this.configuration.getPlantUMLImageFormat();
        }
        if (imageFormat == null || imageFormat.length() <= 0) {
            imageFormat = "png";
        }
        return imageFormat;
    }

    private String getImageId(String... contents)
    {
        HashCodeBuilder builder = new HashCodeBuilder();
        for (String s : contents) {
            builder.append(s);
        }
        return String.valueOf(builder.toHashCode());
    }
}
