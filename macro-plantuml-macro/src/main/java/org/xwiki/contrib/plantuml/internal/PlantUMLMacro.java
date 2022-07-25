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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.ImageBlock;
import org.xwiki.rendering.block.RawBlock;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.macro.descriptor.DefaultContentDescriptor;
import org.xwiki.rendering.syntax.Syntax;
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
            if (renderer.isRenderingImmediately()) {
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
        boolean isDisplayBlock = false;
        if ((ImageFormat.svg.name().equals(imageFormat) && !parameters.isImageTag()) || ImageFormat.txt.name().equals(imageFormat)) {
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
            if (ImageFormat.svg.name().equals(imageFormat)) {
                if (parameters.isScaleFit()) {
                    Pattern svgPattern = Pattern.compile("(?i)([<]\\s*svg\\b[^>]*[>])");
                    Matcher svgMatcher = svgPattern.matcher(imageContent);
                    if (svgMatcher.find()) {
                        String svgTag = svgMatcher.group();
                        svgTag = svgTag.replaceAll("(?i)\\b(width|height)\\b\\s*:[^;]*;", "$1:auto;max-$1:100%;");
                        imageContent = svgMatcher.replaceFirst(svgTag);
                    }
                }
                resultBlock = new RawBlock(imageContent, Syntax.XHTML_1_0);
                resultBlock = new FormatBlock(Collections.singletonList(resultBlock), Format.NONE);
                resultBlock.setParameter("style", "display: inline-block; vertical-align: middle;");
                isDisplayBlock = false;
            } else {
                // Convert full-corner box drawing characters (U+2500~U+257F) to half-corner.
                char[] boxDrawings = new char[] {
                        '-',    /* U+2500: ─ */
                        '-',    /* U+2501: ━ */
                        '|',    /* U+2502: │ */
                        '|',    /* U+2503: ┃ */
                        '-',    /* U+2504: ┄ */
                        '-',    /* U+2505: ┅ */
                        '|',    /* U+2506: ┆ */
                        '|',    /* U+2507: ┇ */
                        '-',    /* U+2508: ┈ */
                        '-',    /* U+2509: ┉ */
                        '|',    /* U+250A: ┊ */
                        '|',    /* U+250B: ┋ */
                        ',',    /* U+250C: ┌ */
                        ',',    /* U+250D: ┍ */
                        ',',    /* U+250E: ┎ */
                        ',',    /* U+250F: ┏ */
                        '.',    /* U+2510: ┐ */
                        '.',    /* U+2511: ┑ */
                        '.',    /* U+2512: ┒ */
                        '.',    /* U+2513: ┓ */
                        '`',    /* U+2514: └ */
                        '`',    /* U+2515: ┕ */
                        '`',    /* U+2516: ┖ */
                        '`',    /* U+2517: ┗ */
                        '\'',   /* U+2518: ┘ */
                        '\'',   /* U+2519: ┙ */
                        '\'',   /* U+251A: ┚ */
                        '\'',   /* U+251B: ┛ */
                        '|',    /* U+251C: ├ */
                        '|',    /* U+251D: ┝ */
                        '|',    /* U+251E: ┞ */
                        '|',    /* U+251F: ┟ */
                        '|',    /* U+2520: ┠ */
                        '|',    /* U+2521: ┡ */
                        '|',    /* U+2522: ┢ */
                        '|',    /* U+2523: ┣ */
                        '|',    /* U+2524: ┤ */
                        '|',    /* U+2525: ┥ */
                        '|',    /* U+2526: ┦ */
                        '|',    /* U+2527: ┧ */
                        '|',    /* U+2528: ┨ */
                        '|',    /* U+2529: ┩ */
                        '|',    /* U+252A: ┪ */
                        '|',    /* U+252B: ┫ */
                        '-',    /* U+252C: ┬ */
                        '-',    /* U+252D: ┭ */
                        '-',    /* U+252E: ┮ */
                        '-',    /* U+252F: ┯ */
                        '-',    /* U+2530: ┰ */
                        '-',    /* U+2531: ┱ */
                        '-',    /* U+2532: ┲ */
                        '-',    /* U+2533: ┳ */
                        '-',    /* U+2534: ┴ */
                        '-',    /* U+2535: ┵ */
                        '-',    /* U+2536: ┶ */
                        '-',    /* U+2537: ┷ */
                        '-',    /* U+2538: ┸ */
                        '-',    /* U+2539: ┹ */
                        '-',    /* U+253A: ┺ */
                        '-',    /* U+253B: ┻ */
                        '+',    /* U+253C: ┼ */
                        '+',    /* U+253D: ┽ */
                        '+',    /* U+253E: ┾ */
                        '+',    /* U+253F: ┿ */
                        '+',    /* U+2540: ╀ */
                        '+',    /* U+2541: ╁ */
                        '+',    /* U+2542: ╂ */
                        '+',    /* U+2543: ╃ */
                        '+',    /* U+2544: ╄ */
                        '+',    /* U+2545: ╅ */
                        '+',    /* U+2546: ╆ */
                        '+',    /* U+2547: ╇ */
                        '+',    /* U+2548: ╈ */
                        '+',    /* U+2549: ╉ */
                        '+',    /* U+254A: ╊ */
                        '+',    /* U+254B: ╋ */
                        '-',    /* U+254C: ╌ */
                        '-',    /* U+254D: ╍ */
                        '|',    /* U+254E: ╎ */
                        '|',    /* U+254F: ╏ */
                        '=',    /* U+2550: ═ */
                        '#',    /* U+2551: ║ */
                        '#',    /* U+2552: ╒ */
                        ',',    /* U+2553: ╓ */
                        '#',    /* U+2554: ╔ */
                        '#',    /* U+2555: ╕ */
                        '.',    /* U+2556: ╖ */
                        '#',    /* U+2557: ╗ */
                        '#',    /* U+2558: ╘ */
                        '`',    /* U+2559: ╙ */
                        '#',    /* U+255A: ╚ */
                        '#',    /* U+255B: ╛ */
                        '\'',   /* U+255C: ╜ */
                        '#',    /* U+255D: ╝ */
                        '#',    /* U+255E: ╞ */
                        '#',    /* U+255F: ╟ */
                        '#',    /* U+2560: ╠ */
                        '#',    /* U+2561: ╡ */
                        '#',    /* U+2562: ╢ */
                        '#',    /* U+2563: ╣ */
                        '=',    /* U+2564: ╤ */
                        '-',    /* U+2565: ╥ */
                        '=',    /* U+2566: ╦ */
                        '=',    /* U+2567: ╧ */
                        '-',    /* U+2568: ╨ */
                        '=',    /* U+2569: ╩ */
                        '#',    /* U+256A: ╪ */
                        '#',    /* U+256B: ╫ */
                        '#',    /* U+256C: ╬ */
                        ',',    /* U+256D: ╭ */
                        '.',    /* U+256E: ╮ */
                        '\'',   /* U+256F: ╯ */
                        '`',    /* U+2570: ╰ */
                        '/',    /* U+2571: ╱ */
                        '\\',    /* U+2572: ╲ */
                        '"',    /* U+2573: ╳ */
                        '-',    /* U+2574: ╴ */
                        '|',    /* U+2575: ╵ */
                        '-',    /* U+2576: ╶ */
                        '|',    /* U+2577: ╷ */
                        '-',    /* U+2578: ╸ */
                        '|',    /* U+2579: ╹ */
                        '-',    /* U+257A: ╺ */
                        '|',    /* U+257B: ╻ */
                        '-',    /* U+257C: ╼ */
                        '|',    /* U+257D: ╽ */
                        '-',    /* U+257E: ╾ */
                        '|',    /* U+257F: ╿ */
                };

                StringBuilder sb = new StringBuilder();
                sb.append("<span style=\"display: inline-block; vertical-align: middle; white-space: pre; font-family: SimHei,SimSun,STHeiti,Menlo,Monaco,Consolas,Courier New,monospace;\">");
                char[] imageChars = imageContent.toCharArray();
                for (int index = 0; index < imageChars.length; ++index) {
                    int distance = imageChars[index] - '\u2500';
                    if (distance >= 0 && distance < boxDrawings.length) {
                        imageChars[index] = boxDrawings[distance];
                    }
                    if (imageChars[index] == '&') {
                        sb.append("&amp;");
                    } else if (imageChars[index] == '<') {
                        sb.append("&lt;");
                    } else if (imageChars[index] == '>') {
                        sb.append("&gt;");
                    } else if (imageChars[index] == ' ') {
                        sb.append("&nbsp;");
                    } else if (imageChars[index] == '\n' || imageChars[index] == '\r') {
                        sb.append("<br/>");
                    } else {
                        sb.append(imageChars[index]);
                    }
                }
                sb.append("</span>");
                imageContent = sb.toString();

//                imageContent = imageContent.replaceAll("\\s", "&nbsp;");
//                imageContent = "<span style=\"display: inline-block; white-space: pre; font-family: SimHei,SimSun,STHeiti,Menlo,Monaco,Consolas,Courier New,monospace;\">"
//                        .concat(imageContent).concat("</span>");

//                String style = "white-space: pre; font-family: SimHei,SimSun,STHeiti,Menlo,Monaco,Consolas,\"Courier New\",monospace;";
////                resultBlock = new VerbatimBlock(imageContent, false);
////                resultBlock.setParameter("style", style);
////                isDisplayBlock = true;
//
//                if (!isInline) {
//                    style = "display: block; ".concat(style);
//                }

                resultBlock = new RawBlock(imageContent, Syntax.HTML_5_0);
//                resultBlock = new FormatBlock(Collections.singletonList(resultBlock), Format.NONE);
//                resultBlock.setParameter("-plantuml-inline-txt", "true");
                isDisplayBlock = false;
            }
        } else {
            ResourceReference resourceReference =
                    new ResourceReference(this.imageWriter.getURL(imageId, imageFormat).serialize(), ResourceType.URL);
            resultBlock = new ImageBlock(resourceReference, false);
            isDisplayBlock = false;
        }

        // Wrap in a DIV if not inline (we need that since an IMG/SVG is an inline element otherwise)
        if (isInline && isDisplayBlock) {
            resultBlock = new FormatBlock(Collections.singletonList(resultBlock), Format.NONE,
                    Collections.singletonMap("-plantuml-inline-wrapper", "true"));
        }
        if (!isInline && !isDisplayBlock) {
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
