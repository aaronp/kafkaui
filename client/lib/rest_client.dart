import 'dart:convert';

import 'package:http/http.dart' as http;

import 'model.dart';

class RestClient {
  static JsonDecoder decoder = JsonDecoder();
  static JsonEncoder encoder = JsonEncoder.withIndent('  ');

  static const HostPort = 'http://localhost:8080';

  static Future<List<String>> listTopics(bool internal) async {
    http.Response response =
        await http.get('$HostPort/rest/kafka/topics?internal=$internal');
    Map<String, dynamic> topicList = decoder.convert(response.body);
    final list = topicList.keys.toList();
    list.sort();
    return list;
  }

  static Future<List<String>> deleteTopic(String topic) async {
    http.Response response = await http.delete('$HostPort/rest/kafka/topic/$topic');
    // http.Response response = await http.post('$HostPort/rest/kafka/topic/delete', body: {topic});
    List<dynamic> list = decoder.convert(response.body);
    final reply = list.map((e) => e.toString()).toList();
    reply.sort();
    return reply;
  }

  static  Future<List<Record>> peek(PeekRequest request) async {
    final url = '$HostPort/rest/kafka/consumer/peek';
    final jsonBody = encoder.convert(request.asJson);
    print('curl -XPOST -d ${jsonBody} $url');
    http.Response response = await http.post(url, body: jsonBody);
    final jsonResponse = decoder.convert(response.body);
    List<dynamic> list = jsonResponse['records'];
    final records = list.map((e) => Record.fromJson(e)).toList();
    return records;
  }

  static  Future<RecordMetadataResponse> push(PublishOne request) async {
    final url = '$HostPort/rest/kafka/publish';
    final jsonBody = encoder.convert(request.asJson);
    print('curl -XPOST -d ${jsonBody} $url');
    http.Response response = await http.post(url, body: jsonBody);
    final jsonResponse = decoder.convert(response.body);
    return RecordMetadataResponse.fromJson(jsonResponse);
  }

}
