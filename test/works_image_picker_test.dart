import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:works_image_picker/works_image_picker.dart';

void main() {
  const MethodChannel channel = MethodChannel('works_image_picker');

  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    channel.setMockMethodCallHandler((MethodCall methodCall) async {
      return '42';
    });
  });

  tearDown(() {
    channel.setMockMethodCallHandler(null);
  });

//  test('getPlatformVersion', () async {
//    expect(await WorksImagePicker.platformVersion, '42');
//  });
}
