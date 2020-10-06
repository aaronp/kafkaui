import 'package:http/http.dart' as http;
import 'dart:convert';
import 'model.dart';

class ConfigClient {

  static JsonDecoder decoder = JsonDecoder();
  static JsonEncoder encoder = JsonEncoder.withIndent('  ');

  static const HostPort = 'http://localhost:8080';
  // static const HostPort = 'http://localhost:8080';

  static Future<String> defaultConfig() async {
    http.Response response = await http.get('$HostPort/rest/config');
    final json = decoder.convert(response.body);

    final pretty = encoder.convert(json);

    print("PRETTY:");
    print(pretty);
    return pretty;
  }

  static Future<String> configForName(String name) async {
    http.Response response = await http.get('$HostPort/rest/config?name=$name');
    // print('got ${response.statusCode}: ${response.body}');
    // final Books got = response.body;
    // final Books got = json.decode(response.body);
    // print('cast is ${got.bookList.length} : ${got}');
    final json = decoder.convert(response.body);

    final pretty = encoder.convert(json);

    print("PRETTY:");
    print(pretty);
    return pretty;
  }
}
