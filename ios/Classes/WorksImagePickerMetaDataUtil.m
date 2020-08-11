// Copyright 2019 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "WorksImagePickerMetaDataUtil.h"
#import <Photos/Photos.h>

static const uint8_t workskFirstByteJPEG = 0xFF;
static const uint8_t workskFirstBytePNG = 0x89;
static const uint8_t workskFirstByteGIF = 0x47;

NSString *const workskFLTImagePickerDefaultSuffix = @".jpg";
const WorksFLTImagePickerMIMEType workskFLTImagePickerMIMETypeDefault = WorksFLTImagePickerMIMETypeJPEG;

@implementation WorksImagePickerMetaDataUtil

+ (WorksFLTImagePickerMIMEType)getImageMIMETypeFromImageData:(NSData *)imageData {
  uint8_t firstByte;
  [imageData getBytes:&firstByte length:1];
  switch (firstByte) {
    case workskFirstByteJPEG:
      return WorksFLTImagePickerMIMETypeJPEG;
    case workskFirstBytePNG:
      return WorksFLTImagePickerMIMETypePNG;
    case workskFirstByteGIF:
      return WorksFLTImagePickerMIMETypeGIF;
  }
  return WorksFLTImagePickerMIMETypeOther;
}

+ (NSString *)imageTypeSuffixFromType:(WorksFLTImagePickerMIMEType)type {
  switch (type) {
    case WorksFLTImagePickerMIMETypeJPEG:
      return @".jpg";
    case WorksFLTImagePickerMIMETypePNG:
      return @".png";
    case WorksFLTImagePickerMIMETypeGIF:
      return @".gif";
    default:
      return nil;
  }
}

+ (NSDictionary *)getMetaDataFromImageData:(NSData *)imageData {
  CGImageSourceRef source = CGImageSourceCreateWithData((CFDataRef)imageData, NULL);
  NSDictionary *metadata =
      (NSDictionary *)CFBridgingRelease(CGImageSourceCopyPropertiesAtIndex(source, 0, NULL));
  CFRelease(source);
  return metadata;
}

+ (NSData *)updateMetaData:(NSDictionary *)metaData toImage:(NSData *)imageData {
  NSMutableData *mutableData = [NSMutableData data];
  CGImageSourceRef cgImage = CGImageSourceCreateWithData((__bridge CFDataRef)imageData, NULL);
  CGImageDestinationRef destination = CGImageDestinationCreateWithData(
      (__bridge CFMutableDataRef)mutableData, CGImageSourceGetType(cgImage), 1, nil);
  CGImageDestinationAddImageFromSource(destination, cgImage, 0, (__bridge CFDictionaryRef)metaData);
  CGImageDestinationFinalize(destination);
  CFRelease(cgImage);
  CFRelease(destination);
  return mutableData;
}

+ (NSData *)convertImage:(UIImage *)image
               usingType:(WorksFLTImagePickerMIMEType)type
                 quality:(nullable NSNumber *)quality {
  if (quality && type != WorksFLTImagePickerMIMETypeJPEG) {
    NSLog(@"image_picker: compressing is not supported for type %@. Returning the image with "
          @"original quality",
          [WorksImagePickerMetaDataUtil imageTypeSuffixFromType:type]);
  }

  switch (type) {
    case WorksFLTImagePickerMIMETypeJPEG: {
      CGFloat qualityFloat = (quality != nil) ? quality.floatValue : 1;
      return UIImageJPEGRepresentation(image, qualityFloat);
    }
    case WorksFLTImagePickerMIMETypePNG:
      return UIImagePNGRepresentation(image);
    default: {
      // converts to JPEG by default.
      CGFloat qualityFloat = (quality != nil) ? quality.floatValue : 1;
      return UIImageJPEGRepresentation(image, qualityFloat);
    }
  }
}

@end
