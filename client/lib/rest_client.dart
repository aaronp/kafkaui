import 'dart:convert';

import 'package:http/http.dart' as http;

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
}
