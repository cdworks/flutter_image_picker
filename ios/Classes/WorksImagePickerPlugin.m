#import "WorksImagePickerPlugin.h"
#import <AVFoundation/AVFoundation.h>
#import <MobileCoreServices/MobileCoreServices.h>
#import <Photos/Photos.h>
#import <UIKit/UIKit.h>

#import "WorksImagePickerImageUtil.h"
#import "WorksImagePickerMetaDataUtil.h"
#import "WorksImagePickerPhotoAssetUtil.h"


@interface WorksImagePickerPlugin()<UINavigationControllerDelegate, UIImagePickerControllerDelegate>

@property(copy, nonatomic) FlutterResult result;

@end

static const int WorksSOURCE_CAMERA = 0;
static const int WorksSOURCE_GALLERY = 1;

@implementation WorksImagePickerPlugin {
  NSDictionary *_arguments;
  UIImagePickerController *_imagePickerController;
  UIViewController *_viewController;
}
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  
    FlutterMethodChannel *channel =
        [FlutterMethodChannel methodChannelWithName:@"works_image_picker"
                                    binaryMessenger:[registrar messenger]];
    UIViewController *viewController =
        [UIApplication sharedApplication].delegate.window.rootViewController;
    WorksImagePickerPlugin *instance =
        [[WorksImagePickerPlugin alloc] initWithViewController:viewController];
    [registrar addMethodCallDelegate:instance channel:channel];
    
}

- (instancetype)initWithViewController:(UIViewController *)viewController {
  self = [super init];
  if (self) {
    _viewController = viewController;
  }
  return self;
}

- (void)handleMethodCall:(FlutterMethodCall *)call result:(FlutterResult)result {
  if (self.result) {
    self.result([FlutterError errorWithCode:@"multiple_request"
                                    message:@"Cancelled by a second request"
                                    details:nil]);
    self.result = nil;
  }

  if ([@"pickImage" isEqualToString:call.method]) {
    _imagePickerController = [[UIImagePickerController alloc] init];
    
    _imagePickerController.modalPresentationStyle = UIModalPresentationCurrentContext;
    _imagePickerController.delegate = self;
    _imagePickerController.mediaTypes = @[ (NSString *)kUTTypeImage ];

    self.result = result;
    _arguments = call.arguments;

    int imageSource = [[_arguments objectForKey:@"source"] intValue];

    switch (imageSource) {
      case WorksSOURCE_CAMERA:
        [self checkCameraAuthorization];
        break;
      case WorksSOURCE_GALLERY:
        [self checkPhotoAuthorization];
        break;
      default:
        result([FlutterError errorWithCode:@"invalid_source"
                                   message:@"Invalid image source."
                                   details:nil]);
        break;
    }
  } else if ([@"pickVideo" isEqualToString:call.method]) {
    _imagePickerController = [[UIImagePickerController alloc] init];
    _imagePickerController.modalPresentationStyle = UIModalPresentationCurrentContext;
//      _imagePickerController.videoQuality = UIImagePickerControllerQualityTypeIFrame960x540;
      _imagePickerController.videoMaximumDuration = 25;
    _imagePickerController.delegate = self;
    _imagePickerController.mediaTypes = @[
      (NSString *)kUTTypeMovie, (NSString *)kUTTypeAVIMovie, (NSString *)kUTTypeVideo,
      (NSString *)kUTTypeMPEG4
    ];
//    _imagePickerController.videoQuality = UIImagePickerControllerQualityTypeIFrame960x540;

    self.result = result;
    _arguments = call.arguments;

    int imageSource = [[_arguments objectForKey:@"source"] intValue];

    switch (imageSource) {
      case WorksSOURCE_CAMERA:
        [self checkCameraAuthorization];
        break;
      case WorksSOURCE_GALLERY:
        [self checkPhotoAuthorization];
        break;
      default:
        result([FlutterError errorWithCode:@"invalid_source"
                                   message:@"Invalid video source."
                                   details:nil]);
        break;
    }
  } else {
    result(FlutterMethodNotImplemented);
  }
}

- (void)showCamera {
  @synchronized(self) {
    if (_imagePickerController.beingPresented) {
      return;
    }
  }
  // Camera is not available on simulators
  if ([UIImagePickerController isSourceTypeAvailable:UIImagePickerControllerSourceTypeCamera]) {
    _imagePickerController.sourceType = UIImagePickerControllerSourceTypeCamera;
    [_viewController presentViewController:_imagePickerController animated:YES completion:nil];
  } else {
    [[[UIAlertView alloc] initWithTitle:@"Error"
                                message:@"Camera not available."
                               delegate:nil
                      cancelButtonTitle:@"OK"
                      otherButtonTitles:nil] show];
    self.result(nil);
    self.result = nil;
    _arguments = nil;
  }
}

- (void)checkCameraAuthorization {
  AVAuthorizationStatus status = [AVCaptureDevice authorizationStatusForMediaType:AVMediaTypeVideo];

  switch (status) {
    case AVAuthorizationStatusAuthorized:
      [self showCamera];
      break;
    case AVAuthorizationStatusNotDetermined: {
      [AVCaptureDevice requestAccessForMediaType:AVMediaTypeVideo
                               completionHandler:^(BOOL granted) {
                                 if (granted) {
                                   dispatch_async(dispatch_get_main_queue(), ^{
                                     if (granted) {
                                       [self showCamera];
                                     }
                                   });
                                 } else {
                                   dispatch_async(dispatch_get_main_queue(), ^{
                                     [self errorNoCameraAccess:AVAuthorizationStatusDenied];
                                   });
                                 }
                               }];
    }; break;
    case AVAuthorizationStatusDenied:
    case AVAuthorizationStatusRestricted:
    default:
      [self errorNoCameraAccess:status];
      break;
  }
}

- (void)checkPhotoAuthorization {
  PHAuthorizationStatus status = [PHPhotoLibrary authorizationStatus];
  switch (status) {
    case PHAuthorizationStatusNotDetermined: {
      [PHPhotoLibrary requestAuthorization:^(PHAuthorizationStatus status) {
        if (status == PHAuthorizationStatusAuthorized) {
          dispatch_async(dispatch_get_main_queue(), ^{
            [self showPhotoLibrary];
          });
        } else {
          [self errorNoPhotoAccess:status];
        }
      }];
      break;
    }
    case PHAuthorizationStatusAuthorized:
      [self showPhotoLibrary];
      break;
    case PHAuthorizationStatusDenied:
    case PHAuthorizationStatusRestricted:
    default:
      [self errorNoPhotoAccess:status];
      break;
  }
}

- (void)errorNoCameraAccess:(AVAuthorizationStatus)status {
  switch (status) {
    case AVAuthorizationStatusRestricted:
      self.result([FlutterError errorWithCode:@"camera_access_restricted"
                                      message:@"The user is not allowed to use the camera."
                                      details:nil]);
      break;
    case AVAuthorizationStatusDenied:
    default:
      self.result([FlutterError errorWithCode:@"camera_access_denied"
                                      message:@"The user did not allow camera access."
                                      details:nil]);
      break;
  }
}

- (void)errorNoPhotoAccess:(PHAuthorizationStatus)status {
  switch (status) {
    case PHAuthorizationStatusRestricted:
      self.result([FlutterError errorWithCode:@"photo_access_restricted"
                                      message:@"The user is not allowed to use the photo."
                                      details:nil]);
      break;
    case PHAuthorizationStatusDenied:
    default:
      self.result([FlutterError errorWithCode:@"photo_access_denied"
                                      message:@"The user did not allow photo access."
                                      details:nil]);
      break;
  }
}

- (void)showPhotoLibrary {
  // No need to check if SourceType is available. It always is.
  _imagePickerController.sourceType = UIImagePickerControllerSourceTypePhotoLibrary;
  [_viewController presentViewController:_imagePickerController animated:YES completion:nil];
}

- (void)imagePickerController:(UIImagePickerController *)picker
    didFinishPickingMediaWithInfo:(NSDictionary<NSString *, id> *)info {
  NSURL *videoURL = [info objectForKey:UIImagePickerControllerMediaURL];
  [_imagePickerController dismissViewControllerAnimated:YES completion:nil];
  // The method dismissViewControllerAnimated does not immediately prevent
  // further didFinishPickingMediaWithInfo invocations. A nil check is necessary
  // to prevent below code to be unwantly executed multiple times and cause a
  // crash.
  if (!self.result) {
    return;
  }
  if (videoURL != nil) {
//    if (@available(iOS 13.0, *)) {
//      NSString *fileName = [videoURL lastPathComponent];
//      NSURL *destination =
//          [NSURL fileURLWithPath:[NSTemporaryDirectory() stringByAppendingPathComponent:fileName]];
//
//      if ([[NSFileManager defaultManager] isReadableFileAtPath:[videoURL path]]) {
//        NSError *error;
//        if (![[videoURL path] isEqualToString:[destination path]]) {
//          [[NSFileManager defaultManager] copyItemAtURL:videoURL toURL:destination error:&error];
//
//          if (error) {
//            self.result([FlutterError errorWithCode:@"flutter_image_picker_copy_video_error"
//                                            message:@"Could not cache the video file."
//                                            details:nil]);
//            self.result = nil;
//            return;
//          }
//        }
//        videoURL = destination;
//      }
//    }
      
      NSDictionary* videoInfo = [self _convert2Mp4:videoURL];
      
      NSFileManager *fileman = [NSFileManager defaultManager];
      if ([fileman fileExistsAtPath:videoURL.path]) {
          NSError *error = nil;
          [fileman removeItemAtURL:videoURL error:&error];
          if (error) {
              NSLog(@"failed to remove file, error:%@.", error);
          }
      }
      
      if(!videoInfo)
      {
          self.result([FlutterError errorWithCode:@"flutter_image_picker_copy_video_error"
                                          message:@"Could not cache the video file."
                                          details:nil]);
          self.result = nil;
          return;
      }
      
      
    self.result(videoInfo);
    self.result = nil;

  } else {
    UIImage *image = [info objectForKey:UIImagePickerControllerEditedImage];
    if (image == nil) {
      image = [info objectForKey:UIImagePickerControllerOriginalImage];
    }

    NSNumber *maxWidth = [_arguments objectForKey:@"maxWidth"];
    NSNumber *maxHeight = [_arguments objectForKey:@"maxHeight"];
    NSNumber *imageQuality = [_arguments objectForKey:@"imageQuality"];

    if (![imageQuality isKindOfClass:[NSNumber class]]) {
      imageQuality = @1;
    } else if (imageQuality.intValue < 0 || imageQuality.intValue > 100) {
      imageQuality = [NSNumber numberWithInt:1];
    } else {
      imageQuality = @([imageQuality floatValue] / 100);
    }

    if (maxWidth != (id)[NSNull null] || maxHeight != (id)[NSNull null]) {
      image = [WorksImagePickerImageUtil scaledImage:image maxWidth:maxWidth maxHeight:maxHeight];
    }

    PHAsset *originalAsset = [WorksImagePickerPhotoAssetUtil getAssetFromImagePickerInfo:info];
    if (!originalAsset) {
      // Image picked without an original asset (e.g. User took a photo directly)
      [self saveImageWithPickerInfo:info image:image imageQuality:imageQuality];
    } else {
      __weak typeof(self) weakSelf = self;
      [[PHImageManager defaultManager]
          requestImageDataForAsset:originalAsset
                           options:nil
                     resultHandler:^(NSData *_Nullable imageData, NSString *_Nullable dataUTI,
                                     UIImageOrientation orientation, NSDictionary *_Nullable info) {
                       // maxWidth and maxHeight are used only for GIF images.
                       [weakSelf saveImageWithOriginalImageData:imageData
                                                          image:image
                                                       maxWidth:maxWidth
                                                      maxHeight:maxHeight
                                                   imageQuality:imageQuality];
                     }];
    }
  }
  _arguments = nil;
}

- (NSString*)dataPath
{
    NSString *dataPath = [NSString stringWithFormat:@"%@/Library/appdata/chatbuffer", NSHomeDirectory()];
    NSFileManager *fm = [NSFileManager defaultManager];
    if(![fm fileExistsAtPath:dataPath]){
        [fm createDirectoryAtPath:dataPath
      withIntermediateDirectories:YES
                       attributes:nil
                            error:nil];
    }
    return dataPath;
}

- (UIImage *)thumbnailWithImage:(UIImage *)image size:(CGSize)asize

{
    
    UIImage *newimage;
    
    if (nil == image) {
        
        newimage = nil;
        
    }
    
    else{
        
        UIGraphicsBeginImageContext(asize);
        
        [image drawInRect:CGRectMake(0, 0, asize.width, asize.height)];
        
        newimage = UIGraphicsGetImageFromCurrentImageContext();
        
        UIGraphicsEndImageContext();
        
    }
    
    return newimage;
    
}


- (UIImage *)thumbnailWithImage:(UIImage *)image sizeScale:(CGSize)sizeScale

{
    
    UIImage *newimage;
    
    if (nil == image) {
        
        newimage = nil;
        
    }
    
    else{
        
        CGSize oldsize = image.size;
        
        CGRect rect;
        
        if (sizeScale.width / sizeScale.height > oldsize.width / oldsize.height) {
            
            rect.size.width = sizeScale.height*oldsize.width/oldsize.height;
            
            rect.size.height = sizeScale.height;
            
            rect.origin.x = (sizeScale.width - rect.size.width) / 2;
            
            rect.origin.y = 0;
            
        }
        
        else{
            
            rect.size.width = sizeScale.width;
            
            rect.size.height = sizeScale.width*oldsize.height/oldsize.width;
            
            rect.origin.x = 0;
            
            rect.origin.y = (sizeScale.height - rect.size.height) / 2;
            
        }
        
        UIGraphicsBeginImageContext(sizeScale);
        
        CGContextRef context = UIGraphicsGetCurrentContext();
        
        CGContextSetFillColorWithColor(context, [[UIColor clearColor] CGColor]);
        
        UIRectFill(CGRectMake(0, 0, sizeScale.width, sizeScale.height));//clear background
        
        [image drawInRect:rect];
        
        newimage = UIGraphicsGetImageFromCurrentImageContext();
        
        UIGraphicsEndImageContext();
        
    }
    
    return newimage;
    
}


- (NSDictionary *)_convert2Mp4:(NSURL *)movUrl
{
    NSDictionary* info;
    NSURL *mp4Url = nil;
    __block BOOL isSucess = NO;
    NSString* thumbUrl;
    AVURLAsset *avAsset = [AVURLAsset URLAssetWithURL:movUrl options:nil];
    
    AVAssetImageGenerator *gen = [[AVAssetImageGenerator alloc] initWithAsset:avAsset];
    
    gen.appliesPreferredTrackTransform = YES;
    
    CMTime time = CMTimeMakeWithSeconds(0.0, 600);
    
    NSError *error = nil;
    
    CMTime actualTime;
    
    CGImageRef image = [gen copyCGImageAtTime:time actualTime:&actualTime error:&error];
    
    CGFloat width = CGImageGetWidth(image);
    CGFloat height = CGImageGetHeight(image);
    CGFloat thumbWidth;
    CGFloat thumbHeight;
    if(width > height)
    {
        thumbWidth = 480;
    }
    else
    {
        thumbWidth = 320;
    }
    
    thumbHeight = height / width * thumbWidth;
    
    UIImage *thumb = [self thumbnailWithImage:[[UIImage alloc] initWithCGImage:image] size:CGSizeMake(thumbWidth, thumbHeight)];
    
    CGImageRelease(image);
    double seconds = ceil(avAsset.duration.value/avAsset.duration.timescale);
    
    thumbUrl = [NSString stringWithFormat:@"%@/%d%d.jpg", [self dataPath], (int)[[NSDate date] timeIntervalSince1970], arc4random() % 100000];
    
    BOOL sucessed = [UIImageJPEGRepresentation(thumb, 0.6) writeToFile:thumbUrl atomically:YES];
    
    if(!sucessed)
    {
        thumbUrl = nil;
    }
    
    NSArray *compatiblePresets = [AVAssetExportSession exportPresetsCompatibleWithAsset:avAsset];
    
    if ([compatiblePresets containsObject:AVAssetExportPresetHighestQuality]) {
        AVAssetExportSession *exportSession = [[AVAssetExportSession alloc]initWithAsset:avAsset
                                                                              presetName:AVAssetExportPresetHighestQuality];
        NSString *mp4Path = [NSString stringWithFormat:@"%@/%d%d.mp4", [self dataPath], (int)[[NSDate date] timeIntervalSince1970], arc4random() % 100000];
        mp4Url = [NSURL fileURLWithPath:mp4Path];
        exportSession.outputURL = mp4Url;
        exportSession.shouldOptimizeForNetworkUse = YES;
        exportSession.outputFileType = AVFileTypeMPEG4;
        dispatch_semaphore_t wait = dispatch_semaphore_create(0l);
        [exportSession exportAsynchronouslyWithCompletionHandler:^{
            switch ([exportSession status]) {
                case AVAssetExportSessionStatusFailed: {
                    NSLog(@"failed, error:%@.", exportSession.error);
                } break;
                case AVAssetExportSessionStatusCancelled: {
                    NSLog(@"cancelled.");
                } break;
                case AVAssetExportSessionStatusCompleted: {
                    NSLog(@"completed.");
                    isSucess = YES;
                } break;
                default: {
                    NSLog(@"others.");
                } break;
            }
            dispatch_semaphore_signal(wait);
        }];
        long timeout = dispatch_semaphore_wait(wait, DISPATCH_TIME_FOREVER);
        if (timeout) {
            NSLog(@"timeout.");
        }
        if (wait) {
            //dispatch_release(wait);
            wait = nil;
        }
    }
    
    if(isSucess)
    {
        if(thumbUrl)
        {
            info = @{@"path":mp4Url.path,@"duration":@(seconds),@"thumbUrl":thumbUrl,@"thumbWidth":@(thumb.size.width),@"thumbHeight":@(thumb.size.height)};
        }
        else{
            info = @{@"path":mp4Url.path,@"duration":@(seconds)};
        }
        
    }
    
    return info;
}

- (void)imagePickerControllerDidCancel:(UIImagePickerController *)picker {
  [_imagePickerController dismissViewControllerAnimated:YES completion:nil];
  self.result(nil);

  self.result = nil;
  _arguments = nil;
}

- (void)saveImageWithOriginalImageData:(NSData *)originalImageData
                                 image:(UIImage *)image
                              maxWidth:(NSNumber *)maxWidth
                             maxHeight:(NSNumber *)maxHeight
                          imageQuality:(NSNumber *)imageQuality {
  NSString *savedPath =
      [WorksImagePickerPhotoAssetUtil saveImageWithOriginalImageData:originalImageData
                                                             image:image
                                                          maxWidth:maxWidth
                                                         maxHeight:maxHeight
                                                      imageQuality:imageQuality];
  [self handleSavedPath:savedPath size:image ? image.size:CGSizeZero];
}

- (void)saveImageWithPickerInfo:(NSDictionary *)info
                          image:(UIImage *)image
                   imageQuality:(NSNumber *)imageQuality {
  NSString *savedPath = [WorksImagePickerPhotoAssetUtil saveImageWithPickerInfo:info
                                                                        image:image
                                                                 imageQuality:imageQuality];
    [self handleSavedPath:savedPath size:image ? image.size:CGSizeZero];
}

- (void)handleSavedPath:(NSString *)path size:(CGSize)size{
  if (path) {
      self.result(@{@"path":path,@"width":@(size.width),@"height":@(size.height)});
  } else {
    self.result([FlutterError errorWithCode:@"create_error"
                                    message:@"Temporary file could not be created"
                                    details:nil]);
  }
  self.result = nil;
}
@end
