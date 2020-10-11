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
    assert(response.statusCode == 200,
        'Blew up w/ status: ${response.statusCode}');
    final jsonResponse = decoder.convert(response.body);
    print(jsonResponse);
    Map<String, dynamic> topicList = jsonResponse;
    final list = topicList.keys.toList();
    list.sort();
    return list;
  }

  static Future<List<String>> deleteTopic(String topic) async {
    http.Response response =
        await http.delete('$HostPort/rest/kafka/topic/$topic');
    assert(response.statusCode == 200,
        'Blew up w/ status: ${response.statusCode}');
    List<dynamic> list = decoder.convert(response.body);
    final reply = list.map((e) => e.toString()).toList();
    reply.sort();
    return reply;
  }

  static Future<List<Record>> peek(PeekRequest request) async {
    final url = '$HostPort/rest/kafka/consumer/peek';
    final jsonBody = encoder.convert(request.asJson);
    print('curl -XPOST -d ${jsonBody} $url');
    http.Response response = await http.post(url, body: jsonBody);
    assert(response.statusCode == 200,
        'Blew up w/ status: ${response.statusCode}');
    final jsonResponse = decoder.convert(response.body);
    List<dynamic> list = jsonResponse['records'];
    final records = list.map((e) => Record.fromJson(e)).toList();
    return records;
  }

  static Future<RecordMetadataResponse> push(PublishOne request) async {
    final url = '$HostPort/rest/kafka/publish';
    final jsonBody = encoder.convert(request.asJson);
    print('curl -XPOST -d ${jsonBody} $url');
    http.Response response = await http.post(url, body: jsonBody);
    assert(response.statusCode == 200,
        'Blew up w/ status: ${response.statusCode}');
    final jsonResponse = decoder.convert(response.body);
    return RecordMetadataResponse.fromJson(jsonResponse);
  }

  static Future<TopicDesc> partitionsForTopic(String topic) async {
    final url = '$HostPort/rest/kafka/partitions/$topic';
    http.Response response = await http.get(url);
    assert(response.statusCode == 200,
        'Blew up w/ status: ${response.statusCode}');
    final jsonResponse = decoder.convert(response.body);
    return TopicDesc.fromJson(jsonResponse[topic]);
  }

  static Future<List<OffsetRange>> offsetRangesForTopic(String topic) async {
    final url = '$HostPort/rest/kafka/offsets/$topic';
    http.Response response = await http.get(url);
    assert(response.statusCode == 200,
        'Blew up w/ status: ${response.statusCode}');
    final jsonResponse = decoder.convert(response.body);

    final range = List<OffsetRange>();
    jsonResponse.forEach((j) => range.add(OffsetRange.fromJson(j)));
    return range;
  }

  static Future<Set<String>> repartition(CreatePartitionRequest request) async {
    final url = '$HostPort/rest/kafka/repartition';
    final jsonBody = request.asJson;
    print('curl -XPOST -d ${jsonBody} $url');
    http.Response response = await http.post(url, body: jsonBody);
    assert(response.statusCode == 200,
        'Blew up w/ status: ${response.statusCode}');
    final Set<String> jsonResponse = decoder.convert(response.body);

    return jsonResponse;
  }

  static Future<Map<String, List<MetricEntry>>> metrics() async {
    final url = '$HostPort/rest/kafka/metrics';
    http.Response response = await http.get(url);
    assert(response.statusCode == 200,
        'Blew up w/ status: ${response.statusCode}');

    Map<String, List<MetricEntry>> metrics = {};
    final Map<String, dynamic> jsonBody = decoder.convert(response.body);
    print('jsonBody is $jsonBody');
    jsonBody.forEach((key, value) {
      List<MetricEntry> entries = [];
      value.forEach((e) => entries.add(MetricEntry.fromJson(e)));
      metrics[key] = entries;
    });
    return metrics;
  }
}
