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

    // --- OPTIMIZED VALUES FOR HIGH RESOLUTION AND CLARITY ---
    private static final int RESIZE_WIDTH = 300;   // Increased to create a wider, more detailed grid
    private static final int TILE_SIZE = 80;       // Increased to make each signature tile clearer
    private static final float CONTRAST_ENHANCE = 5.0f; // High contrast for bold signature lines
    private static final float VISIBILITY_FACTOR = 0.5f; // Controls signature darkness and portrait visibility
    private static final double SIGNATURE_THRESHOLD = 0.5; // Controls the sharpness of the signature lines

    @Autowired
    private CollageRepository collageRepository;

    public byte[] createCollage(MultipartFile portraitFile, MultipartFile signatureFile) throws IOException {

        // Ensure the upload directory exists
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Load portrait and signature images
        BufferedImage portraitImg = ImageIO.read(portraitFile.getInputStream());
        BufferedImage signatureImg = ImageIO.read(signatureFile.getInputStream());

        // Calculate the height of the small portrait to maintain aspect ratio
        int resizeHeight = (int) (RESIZE_WIDTH * (double) portraitImg.getHeight() / portraitImg.getWidth());

        // --- STEP 1: Process the portrait image for the grid ---
        BufferedImage smallPortrait = new BufferedImage(RESIZE_WIDTH, resizeHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2dPortrait = smallPortrait.createGraphics();
        g2dPortrait.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2dPortrait.drawImage(portraitImg, 0, 0, RESIZE_WIDTH, resizeHeight, null);
        g2dPortrait.dispose();

        // --- STEP 2: Process the signature image for the tiles ---
        BufferedImage signatureTile = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2dSignature = signatureTile.createGraphics();
        // Use NEAREST_NEIGHBOR for sharp signature lines
        g2dSignature.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2dSignature.drawImage(signatureImg, 0, 0, TILE_SIZE, TILE_SIZE, null);
        g2dSignature.dispose();

        // Apply contrast enhancement to the signature tile
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                int rgb = signatureTile.getRGB(x, y);
                Color color = new Color(rgb);

                int r = (int) Math.max(0, Math.min(255, (color.getRed() - 128) * CONTRAST_ENHANCE + 128));
                int g = (int) Math.max(0, Math.min(255, (color.getGreen() - 128) * CONTRAST_ENHANCE + 128));
                int b = (int) Math.max(0, Math.min(255, (color.getBlue() - 128) * CONTRAST_ENHANCE + 128));

                signatureTile.setRGB(x, y, new Color(r, g, b).getRGB());
            }
        }

        // --- STEP 3: Create the final high-resolution collage ---
        BufferedImage canvas = new BufferedImage(RESIZE_WIDTH * TILE_SIZE, resizeHeight * TILE_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2dCanvas = canvas.createGraphics();

        // Loop through each tile and apply color and signature
        for (int y = 0; y < resizeHeight; y++) {
            for (int x = 0; x < RESIZE_WIDTH; x++) {
                int rgbPortrait = smallPortrait.getRGB(x, y);
                Color colorPortrait = new Color(rgbPortrait);

                // Create a new tile that will be colored and pasted
                BufferedImage adjustedTile = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_RGB);
                for (int ty = 0; ty < TILE_SIZE; ty++) {
                    for (int tx = 0; tx < TILE_SIZE; tx++) {
                        int rgbSignature = signatureTile.getRGB(tx, ty);
                        Color colorSignature = new Color(rgbSignature);

                        double signatureLuminance = (0.299 * colorSignature.getRed() + 0.587 * colorSignature.getGreen() + 0.114 * colorSignature.getBlue()) / 255.0;

                        int finalR, finalG, finalB;
                        // Use a threshold to create sharp signature lines
                        if (signatureLuminance < SIGNATURE_THRESHOLD) {
                            // Darken the signature lines based on the portrait's color
                            finalR = (int) Math.max(0, Math.min(255, colorPortrait.getRed() * (1 - VISIBILITY_FACTOR)));
                            finalG = (int) Math.max(0, Math.min(255, colorPortrait.getGreen() * (1 - VISIBILITY_FACTOR)));
                            finalB = (int) Math.max(0, Math.min(255, colorPortrait.getBlue() * (1 - VISIBILITY_FACTOR)));
                        } else {
                            // Use the original portrait color for the background, preserving brightness
                            finalR = colorPortrait.getRed();
                            finalG = colorPortrait.getGreen();
                            finalB = colorPortrait.getBlue();
                        }

                        adjustedTile.setRGB(tx, ty, new Color(finalR, finalG, finalB).getRGB());
                    }
                }
                g2dCanvas.drawImage(adjustedTile, x * TILE_SIZE, y * TILE_SIZE, null);
            }
        }
        g2dCanvas.dispose();

        // --- STEP 4: Save and return the high-quality image ---
        String filename = "collage_" + UUID.randomUUID() + ".jpg";
        File outputFile = new File(uploadDir + filename);
        ImageIO.write(canvas, "jpg", outputFile);

        Collage collage = new Collage(filename, outputFile.getAbsolutePath());
        collageRepository.save(collage);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(canvas, "jpg", baos);
        return baos.toByteArray();
    }
}