// Copyright 2019 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.imagepicker;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.Nullable;

import java.io.*;

class ImageResizer {
  private final File externalFilesDirectory;
  private final ExifDataCopier exifDataCopier;

  ImageResizer(File externalFilesDirectory, ExifDataCopier exifDataCopier) {
    this.externalFilesDirectory = externalFilesDirectory;
    this.exifDataCopier = exifDataCopier;
  }

  /**
   * If necessary, resizes the image located in imagePath and then returns the path for the scaled
   * image.
   *
   * <p>If no resizing is needed, returns the path for the original image.
   */
  Bundle resizeImageIfNeeded(
      String imagePath,
      @Nullable Double maxWidth,
      @Nullable Double maxHeight,
      @Nullable Integer imageQuality) {
    boolean shouldScale =
        maxWidth != null || maxHeight != null || isImageQualityValid(imageQuality);
    String[] pathParts = imagePath.split("/");
    String imageName = pathParts[pathParts.length - 1];
    File file;
    Bitmap bmp = decodeFile(imagePath);
    if (bmp == null) {
      return null;
    }
    try {
      int width = bmp.getWidth();
      int height = bmp.getHeight();
      if (!shouldScale) {
        file = createImageOnExternalDirectory(imageName, bmp, 100);
      } else {
        Bundle bundle = resizedImage(bmp, maxWidth, maxHeight, imageQuality, imageName);
        file = (File) bundle.getSerializable("file");
        width = bundle.getInt("width");
        height = bundle.getInt("height");
      }
      copyExif(imagePath, file.getPath());

      Bundle ret = new Bundle();
      ret.putString("path",file.getPath());
      ret.putInt("width",width);
      ret.putInt("height",height);

      return ret;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private Bundle resizedImage(
      Bitmap bmp, Double maxWidth, Double maxHeight, Integer imageQuality, String outputImageName)
      throws IOException {
    double originalWidth = bmp.getWidth() * 1.0;
    double originalHeight = bmp.getHeight() * 1.0;

    if (!isImageQualityValid(imageQuality)) {
      imageQuality = 100;
    }

    boolean hasMaxWidth = maxWidth != null;
    boolean hasMaxHeight = maxHeight != null;

    Double width = hasMaxWidth ? Math.min(originalWidth, maxWidth) : originalWidth;
    Double height = hasMaxHeight ? Math.min(originalHeight, maxHeight) : originalHeight;

    boolean shouldDownscaleWidth = hasMaxWidth && maxWidth < originalWidth;
    boolean shouldDownscaleHeight = hasMaxHeight && maxHeight < originalHeight;
    boolean shouldDownscale = shouldDownscaleWidth || shouldDownscaleHeight;

    if (shouldDownscale) {
      double downscaledWidth = (height / originalHeight) * originalWidth;
      double downscaledHeight = (width / originalWidth) * originalHeight;

      if (width < height) {
        if (!hasMaxWidth) {
          width = downscaledWidth;
        } else {
          height = downscaledHeight;
        }
      } else if (height < width) {
        if (!hasMaxHeight) {
          height = downscaledHeight;
        } else {
          width = downscaledWidth;
        }
      } else {
        if (originalWidth < originalHeight) {
          width = downscaledWidth;
        } else if (originalHeight < originalWidth) {
          height = downscaledHeight;
        }
      }
    }

    Bitmap scaledBmp = createScaledBitmap(bmp, width.intValue(), height.intValue(), false);
    File file =
        createImageOnExternalDirectory("/scaled_" + outputImageName, scaledBmp, imageQuality);
    Bundle ret = new Bundle();
    ret.putSerializable("file",file);
    ret.putInt("width",width.intValue());
    ret.putInt("height",height.intValue());
    return  ret;
  }

  private File createFile(File externalFilesDirectory, String child) {
    return new File(externalFilesDirectory, child);
  }

  private FileOutputStream createOutputStream(File imageFile) throws IOException {
    return new FileOutputStream(imageFile);
  }

  private void copyExif(String filePathOri, String filePathDest) {
    exifDataCopier.copyExif(filePathOri, filePathDest);
  }

  private Bitmap decodeFile(String path) {
    return BitmapFactory.decodeFile(path);
  }

  private Bitmap createScaledBitmap(Bitmap bmp, int width, int height, boolean filter) {
    return Bitmap.createScaledBitmap(bmp, width, height, filter);
  }

  private boolean isImageQualityValid(Integer imageQuality) {
    return imageQuality != null && imageQuality > 0 && imageQuality < 100;
  }

  private File createImageOnExternalDirectory(String name, Bitmap bitmap, int imageQuality)
      throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    boolean saveAsPNG = bitmap.hasAlpha();
    if (saveAsPNG) {
      Log.d(
          "ImageResizer",
          "image_picker: compressing is not supported for type PNG. Returning the image with original quality");
    }
    bitmap.compress(
        saveAsPNG ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG,
        imageQuality,
        outputStream);
    File imageFile = createFile(externalFilesDirectory, name);
    FileOutputStream fileOutput = createOutputStream(imageFile);
    fileOutput.write(outputStream.toByteArray());
    fileOutput.close();
    return imageFile;
  }
}
