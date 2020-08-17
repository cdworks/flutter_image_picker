# works_image_picker

基于image_picker插件，当录像或者拍照的时候，获取更多额外数据，详见示例代码.目前仅用于环信发送照片和录像。

##usage

# 引入

```yaml
  dependencies:
    flutter:
      sdk: flutter
    works_image_picker:
    #本地路径
      path: /**/flutter_image_picker
#或者git地址
#	  git:
#       url: git://github.com/cdworks/flutter_image_picker.git
```

#示例代码

```dart

//拍照
final Map imgInfo = await WorksImagePicker.pickImage(source: ImageSource.camera,imageQuality: Platform.isIOS ? 100 : 80);
if(imgInfo == null)
  return;
final String imgPath = imgInfo['path']; //图片本地路径
if(imgPath == null)
  return;
final double imgWidth = imgInfo['width'].toDouble(); //图片宽度
final double imgHeight = imgInfo['height'].toDouble(); //图片高度

//录像
final Map videoInfo = await WorksImagePicker.pickVideo(source: ImageSource.camera);
if(videoInfo == null)
  return;
final String videoPath = videoInfo['path']; //视频本地路径
if(videoPath == null)
  return;
final double duration = videoInfo['duration'];  //视频持续时间
final String thumbUrl = videoInfo['thumbUrl'];   //视频缩略图地址
final double thumbWidth = videoInfo['thumbWidth'] ?? 0;  //视频缩略图宽度
final double thumbHeight = videoInfo['thumbHeight'] ?? 0;  //视频缩略图高度

```