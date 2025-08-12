package com.example.imagecollage.service;

import com.example.imagecollage.entity.Collage;
import com.example.imagecollage.repository.CollageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class CollageService {

    @Value("${image.upload.dir}")
    private String uploadDir;

    // --- OPTIMIZED VALUES FOR MEMORY ---
    private static final int MAX_OUTPUT_WIDTH = 4000;  // Limit final collage width (px)
    private static final int MAX_OUTPUT_HEIGHT = 4000; // Limit final collage height (px)
    private static final int RESIZE_WIDTH = 100;       // Smaller base grid width
    private static final int TILE_SIZE = 40;           // Smaller tile size
    private static final float CONTRAST_ENHANCE = 5.0f;
    private static final float VISIBILITY_FACTOR = 0.5f;
    private static final double SIGNATURE_THRESHOLD = 0.5;

    @Autowired
    private CollageRepository collageRepository;

    public byte[] createCollage(MultipartFile portraitFile, MultipartFile signatureFile) throws IOException {

        // Ensure upload dir exists
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Load portrait & signature
        BufferedImage portraitImg = ImageIO.read(portraitFile.getInputStream());
        BufferedImage signatureImg = ImageIO.read(signatureFile.getInputStream());

        // Calculate proportional height for small portrait
        int resizeHeight = (int) (RESIZE_WIDTH * (double) portraitImg.getHeight() / portraitImg.getWidth());

        // STEP 1: Resize portrait
        BufferedImage smallPortrait = new BufferedImage(RESIZE_WIDTH, resizeHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2dPortrait = smallPortrait.createGraphics();
        g2dPortrait.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2dPortrait.drawImage(portraitImg, 0, 0, RESIZE_WIDTH, resizeHeight, null);
        g2dPortrait.dispose();

        // STEP 2: Resize signature
        BufferedImage signatureTile = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2dSignature = signatureTile.createGraphics();
        g2dSignature.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2dSignature.drawImage(signatureImg, 0, 0, TILE_SIZE, TILE_SIZE, null);
        g2dSignature.dispose();

        // STEP 3: Enhance contrast for signature
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                Color c = new Color(signatureTile.getRGB(x, y));
                int r = clamp((c.getRed() - 128) * CONTRAST_ENHANCE + 128);
                int g = clamp((c.getGreen() - 128) * CONTRAST_ENHANCE + 128);
                int b = clamp((c.getBlue() - 128) * CONTRAST_ENHANCE + 128);
                signatureTile.setRGB(x, y, new Color(r, g, b).getRGB());
            }
        }

        // STEP 4: Calculate final collage size with safety cap
        int collageWidth = RESIZE_WIDTH * TILE_SIZE;
        int collageHeight = resizeHeight * TILE_SIZE;
        if (collageWidth > MAX_OUTPUT_WIDTH) collageWidth = MAX_OUTPUT_WIDTH;
        if (collageHeight > MAX_OUTPUT_HEIGHT) collageHeight = MAX_OUTPUT_HEIGHT;

        BufferedImage canvas = new BufferedImage(collageWidth, collageHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2dCanvas = canvas.createGraphics();

        // STEP 5: Draw tiles
        for (int y = 0; y < resizeHeight && y * TILE_SIZE < MAX_OUTPUT_HEIGHT; y++) {
            for (int x = 0; x < RESIZE_WIDTH && x * TILE_SIZE < MAX_OUTPUT_WIDTH; x++) {
                Color colorPortrait = new Color(smallPortrait.getRGB(x, y));

                // Adjust tile
                BufferedImage adjustedTile = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_RGB);
                for (int ty = 0; ty < TILE_SIZE; ty++) {
                    for (int tx = 0; tx < TILE_SIZE; tx++) {
                        Color colorSignature = new Color(signatureTile.getRGB(tx, ty));
                        double lum = (0.299 * colorSignature.getRed() + 0.587 * colorSignature.getGreen() + 0.114 * colorSignature.getBlue()) / 255.0;

                        int r = lum < SIGNATURE_THRESHOLD
                                ? (int) (colorPortrait.getRed() * (1 - VISIBILITY_FACTOR))
                                : colorPortrait.getRed();
                        int g = lum < SIGNATURE_THRESHOLD
                                ? (int) (colorPortrait.getGreen() * (1 - VISIBILITY_FACTOR))
                                : colorPortrait.getGreen();
                        int b = lum < SIGNATURE_THRESHOLD
                                ? (int) (colorPortrait.getBlue() * (1 - VISIBILITY_FACTOR))
                                : colorPortrait.getBlue();

                        adjustedTile.setRGB(tx, ty, new Color(clamp(r), clamp(g), clamp(b)).getRGB());
                    }
                }

                g2dCanvas.drawImage(adjustedTile, x * TILE_SIZE, y * TILE_SIZE, null);
            }
        }
        g2dCanvas.dispose();

        // STEP 6: Save & return
        String filename = "collage_" + UUID.randomUUID() + ".jpg";
        File outputFile = new File(uploadDir + filename);
        ImageIO.write(canvas, "jpg", outputFile);

        Collage collage = new Collage(filename, outputFile.getAbsolutePath());
        collageRepository.save(collage);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(canvas, "jpg", baos);
        return baos.toByteArray();
    }

    private int clamp(double value) {
        return (int) Math.max(0, Math.min(255, value));
    }
}
