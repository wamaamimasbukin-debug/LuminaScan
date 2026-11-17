package com.luminascan.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private final List<File> capturedPhotos = new ArrayList<>();
    private File currentPhotoFile;

    private TextView tvStatus;
    private TextView tvOutputPath;
    private ImageView ivPreview;

    private final ActivityResultLauncher<String> requestCameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    launchCamera();
                } else {
                    Toast.makeText(this, "Izin kamera ditolak", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<Intent> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && currentPhotoFile != null && currentPhotoFile.exists()) {
                    capturedPhotos.add(currentPhotoFile);
                    Bitmap bmp = BitmapFactory.decodeFile(currentPhotoFile.getAbsolutePath());
                    if (bmp != null) {
                        ivPreview.setImageBitmap(bmp);
                    }
                    updateStatus();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvOutputPath = findViewById(R.id.tvOutputPath);
        ivPreview = findViewById(R.id.ivPreview);

        Button btnCapture = findViewById(R.id.btnCapture);
        Button btnSaveSeparatePdf = findViewById(R.id.btnSaveSeparatePdf);
        Button btnMergePdf = findViewById(R.id.btnMergePdf);
        Button btnClear = findViewById(R.id.btnClear);

        btnCapture.setOnClickListener(v -> checkCameraPermissionAndCapture());
        btnSaveSeparatePdf.setOnClickListener(v -> exportPdf(false));
        btnMergePdf.setOnClickListener(v -> exportPdf(true));
        btnClear.setOnClickListener(v -> {
            capturedPhotos.clear();
            ivPreview.setImageDrawable(null);
            tvOutputPath.setText("");
            updateStatus();
        });
    }

    private void checkCameraPermissionAndCapture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File cacheDir = getCacheDir();
            currentPhotoFile = new File(cacheDir, "SCAN_" + timeStamp + ".jpg");

            Uri photoUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    currentPhotoFile
            );

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            takePictureLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Gagal membuka kamera: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void updateStatus() {
        tvStatus.setText("Jumlah halaman dipindai: " + capturedPhotos.size());
    }

    private void exportPdf(boolean mergeAll) {
        if (capturedPhotos.isEmpty()) {
            Toast.makeText(this, "Ambil foto dokumen terlebih dahulu", Toast.LENGTH_SHORT).show();
            return;
        }

        List<File> targets = new ArrayList<>();
        if (mergeAll) {
            targets.addAll(capturedPhotos);
        } else {
            targets.add(capturedPhotos.get(capturedPhotos.size() - 1));
        }

        PdfDocument pdfDocument = new PdfDocument();
        int pageNumber = 1;

        for (File photoFile : targets) {
            Bitmap bitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
            if (bitmap == null) {
                continue;
            }
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    pageNumber++
            ).create();
            PdfDocument.Page page = pdfDocument.startPage(pageInfo);
            Canvas canvas = page.getCanvas();
            canvas.drawBitmap(bitmap, 0f, 0f, null);
            pdfDocument.finishPage(page);
        }

        File docsDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (docsDir != null && !docsDir.exists()) {
            docsDir.mkdirs();
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String prefix = mergeAll ? "LuminaScan_Merged_" : "LuminaScan_Single_";
        File outFile = new File(docsDir, prefix + timeStamp + ".pdf");

        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            pdfDocument.writeTo(fos);
            tvOutputPath.setText("PDF tersimpan di:\n" + outFile.getAbsolutePath());
            Toast.makeText(this, "Berhasil menyimpan PDF", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Gagal menyimpan PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            pdfDocument.close();
        }
    }
}
