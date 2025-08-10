package com.example.imagecollage.controller;

import com.example.imagecollage.service.CollageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/collage")
public class CollageController {

    @Autowired
    private CollageService collageService;

    @PostMapping
    public ResponseEntity<byte[]> createCollage(
            @RequestParam("portrait") MultipartFile portraitFile,
            @RequestParam("signature") MultipartFile signatureFile) {
        try {
            byte[] generatedImageBytes = collageService.createCollage(portraitFile, signatureFile);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            headers.setContentLength(generatedImageBytes.length);
            headers.setContentDispositionFormData("attachment", "signature_collage.jpg");

            return new ResponseEntity<>(generatedImageBytes, headers, HttpStatus.OK);
        } catch (IOException e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}