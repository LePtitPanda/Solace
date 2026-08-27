package com.solace.client.loading;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.solace.Solace;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FastColor;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

final class LoadingScreenTextureCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int ANIMATION_FRAME_SIZE = 96;
    private static final AtomicInteger NEXT_TEXTURE_ID = new AtomicInteger();
    private static final Map<String, ImageTexture> IMAGES = new HashMap<>();
    private static final Map<String, AnimatedTexture> ANIMATIONS = new HashMap<>();
    private static final Set<String> FAILED_IMAGES = new HashSet<>();
    private static final Set<String> FAILED_ANIMATIONS = new HashSet<>();

    private LoadingScreenTextureCache() {
    }

    @Nullable
    static ImageTexture image(String source) {
        if (source == null || source.isBlank() || FAILED_IMAGES.contains(source)) {
            return null;
        }
        ImageTexture cached = IMAGES.get(source);
        if (cached != null) {
            return cached;
        }

        try (InputStream stream = open(source); NativeImage image = NativeImage.read(stream)) {
            NativeImage texturePixels = new NativeImage(image.getWidth(), image.getHeight(), true);
            texturePixels.copyFrom(image);
            ResourceLocation location = register(texturePixels, "background");
            ImageTexture loaded = new ImageTexture(location, texturePixels.getWidth(), texturePixels.getHeight());
            IMAGES.put(source, loaded);
            return loaded;
        } catch (Exception e) {
            FAILED_IMAGES.add(source);
            LOGGER.warn("[Solace] Could not load loading-screen background {}", source, e);
            return null;
        }
    }

    @Nullable
    static AnimatedTexture animation(String source) {
        if (source == null || source.isBlank() || FAILED_ANIMATIONS.contains(source)) {
            return null;
        }
        AnimatedTexture cached = ANIMATIONS.get(source);
        if (cached != null) {
            return cached;
        }

        try (InputStream stream = open(source)) {
            AnimatedTexture loaded = decodeGif(stream);
            ANIMATIONS.put(source, loaded);
            return loaded;
        } catch (Exception e) {
            FAILED_ANIMATIONS.add(source);
            LOGGER.warn("[Solace] Could not load loading-screen animation {}", source, e);
            return null;
        }
    }

    private static ResourceLocation register(NativeImage pixels, String kind) {
        ResourceLocation location = new ResourceLocation(Solace.MOD_ID,
                "dynamic/loading_" + kind + "_" + NEXT_TEXTURE_ID.incrementAndGet());
        DynamicTexture texture = new DynamicTexture(pixels);
        texture.setFilter(false, false);
        Minecraft.getInstance().getTextureManager().register(location, texture);
        return location;
    }

    private static InputStream open(String configuredSource) throws IOException {
        String source = configuredSource;
        if (source.startsWith("[source:local]/")) {
            source = source.substring("[source:local]/".length());
        }

        Path local = resolveLocal(source);
        if (local != null && Files.isRegularFile(local)) {
            return Files.newInputStream(local);
        }

        if (source.startsWith("resource:")) {
            source = source.substring("resource:".length());
        }
        ResourceLocation location = ResourceLocation.tryParse(source);
        if (location == null) {
            throw new IOException("Not a readable file or resource location: " + configuredSource);
        }
        Resource resource = Minecraft.getInstance().getResourceManager().getResource(location).orElse(null);
        if (resource == null && !location.getPath().startsWith("textures/")) {
            String texturePath = "textures/" + location.getPath();
            if (!texturePath.contains(".")) {
                texturePath += ".png";
            }
            ResourceLocation textureLocation = new ResourceLocation(location.getNamespace(), texturePath);
            resource = Minecraft.getInstance().getResourceManager().getResource(textureLocation).orElse(null);
        }
        if (resource == null) {
            throw new IOException("Missing resource " + location);
        }
        return resource.open();
    }

    @Nullable
    private static Path resolveLocal(String source) {
        try {
            Path path = Path.of(source);
            if (path.isAbsolute()) {
                return path.normalize();
            }
            return FMLPaths.GAMEDIR.get().resolve(path).normalize();
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private static AnimatedTexture decodeGif(InputStream stream) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) {
            throw new IOException("No GIF reader is available");
        }

        ImageReader reader = readers.next();
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(stream)) {
            reader.setInput(imageInput, false, false);
            int frameCount = reader.getNumImages(true);
            if (frameCount <= 0) {
                throw new IOException("GIF contains no frames");
            }

            int[] canvasSize = logicalScreenSize(reader.getStreamMetadata());
            if (canvasSize[0] <= 0 || canvasSize[1] <= 0) {
                BufferedImage first = reader.read(0);
                canvasSize = new int[] {first.getWidth(), first.getHeight()};
            }

            BufferedImage canvas = new BufferedImage(canvasSize[0], canvasSize[1], BufferedImage.TYPE_INT_ARGB);
            List<Integer> frameEnds = new ArrayList<>(frameCount);
            int columns = (int) Math.ceil(Math.sqrt(frameCount));
            int rows = (frameCount + columns - 1) / columns;
            int sheetWidth = columns * ANIMATION_FRAME_SIZE;
            int sheetHeight = rows * ANIMATION_FRAME_SIZE;
            NativeImage sheet = new NativeImage(sheetWidth, sheetHeight, true);
            int elapsed = 0;

            for (int index = 0; index < frameCount; index++) {
                IIOMetadata metadata = reader.getImageMetadata(index);
                GifFrameInfo info = frameInfo(metadata);
                BufferedImage before = "restoreToPrevious".equals(info.disposalMethod()) ? copy(canvas) : null;
                BufferedImage frame = reader.read(index);

                Graphics2D graphics = canvas.createGraphics();
                graphics.setComposite(AlphaComposite.SrcOver);
                graphics.drawImage(frame, info.left(), info.top(), null);
                graphics.dispose();

                writeScaledFrame(sheet, canvas, index % columns, index / columns);
                elapsed += Math.max(20, info.delayMillis());
                frameEnds.add(elapsed);

                if ("restoreToBackgroundColor".equals(info.disposalMethod())) {
                    Graphics2D clear = canvas.createGraphics();
                    clear.setComposite(AlphaComposite.Clear);
                    clear.fillRect(info.left(), info.top(), info.width(), info.height());
                    clear.dispose();
                } else if (before != null) {
                    canvas = before;
                }
            }

            ResourceLocation location = register(sheet, "animation");
            int[] ends = frameEnds.stream().mapToInt(Integer::intValue).toArray();
            return new AnimatedTexture(location, ANIMATION_FRAME_SIZE, columns, sheetWidth, sheetHeight, ends, elapsed);
        } finally {
            reader.dispose();
        }
    }

    private static void writeScaledFrame(NativeImage destination, BufferedImage canvas, int column, int row) {
        BufferedImage scaled = new BufferedImage(ANIMATION_FRAME_SIZE, ANIMATION_FRAME_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(canvas, 0, 0, ANIMATION_FRAME_SIZE, ANIMATION_FRAME_SIZE, null);
        graphics.dispose();

        int startX = column * ANIMATION_FRAME_SIZE;
        int startY = row * ANIMATION_FRAME_SIZE;
        for (int y = 0; y < ANIMATION_FRAME_SIZE; y++) {
            for (int x = 0; x < ANIMATION_FRAME_SIZE; x++) {
                int argb = scaled.getRGB(x, y);
                destination.setPixelRGBA(startX + x, startY + y, FastColor.ABGR32.color(
                        argb >>> 24,
                        argb & 0xFF,
                        argb >> 8 & 0xFF,
                        argb >> 16 & 0xFF
                ));
            }
        }
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return copy;
    }

    private static int[] logicalScreenSize(@Nullable IIOMetadata metadata) {
        if (metadata == null) {
            return new int[] {0, 0};
        }
        Node root = metadata.getAsTree(metadata.getNativeMetadataFormatName());
        Node descriptor = child(root, "LogicalScreenDescriptor");
        return descriptor == null
                ? new int[] {0, 0}
                : new int[] {attribute(descriptor, "logicalScreenWidth", 0), attribute(descriptor, "logicalScreenHeight", 0)};
    }

    private static GifFrameInfo frameInfo(IIOMetadata metadata) {
        Node root = metadata.getAsTree(metadata.getNativeMetadataFormatName());
        Node descriptor = child(root, "ImageDescriptor");
        Node control = child(root, "GraphicControlExtension");
        int left = attribute(descriptor, "imageLeftPosition", 0);
        int top = attribute(descriptor, "imageTopPosition", 0);
        int width = attribute(descriptor, "imageWidth", 1);
        int height = attribute(descriptor, "imageHeight", 1);
        int delay = attribute(control, "delayTime", 10) * 10;
        String disposal = stringAttribute(control, "disposalMethod", "none");
        return new GifFrameInfo(left, top, width, height, delay, disposal);
    }

    @Nullable
    private static Node child(Node root, String name) {
        if (root == null) {
            return null;
        }
        for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (name.equals(node.getNodeName())) {
                return node;
            }
        }
        return null;
    }

    private static int attribute(@Nullable Node node, String name, int fallback) {
        String value = stringAttribute(node, name, null);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Nullable
    private static String stringAttribute(@Nullable Node node, String name, @Nullable String fallback) {
        if (node == null) {
            return fallback;
        }
        NamedNodeMap attributes = node.getAttributes();
        Node attribute = attributes == null ? null : attributes.getNamedItem(name);
        return attribute == null ? fallback : attribute.getNodeValue();
    }

    record ImageTexture(ResourceLocation location, int width, int height) {
    }

    record AnimatedTexture(ResourceLocation location, int frameSize, int columns, int sheetWidth, int sheetHeight,
                           int[] frameEnds, int totalDuration) {
        int frame(long elapsedMillis) {
            int time = (int) Math.floorMod(elapsedMillis, Math.max(1, totalDuration));
            for (int index = 0; index < frameEnds.length; index++) {
                if (time < frameEnds[index]) {
                    return index;
                }
            }
            return frameEnds.length - 1;
        }
    }

    private record GifFrameInfo(int left, int top, int width, int height, int delayMillis, String disposalMethod) {
    }
}
