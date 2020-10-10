import 'package:flutter/material.dart';
import 'package:flutter/widgets.dart';
import 'package:kafkaui/list_topics_widget.dart';
import 'package:kafkaui/rest_client.dart';

import 'main.dart';

void main() => runApp(
  MaterialApp(
    builder: (context, child) =>
        SafeArea(child: new Material(color: Colors.white, child: child)),
    home: Scaffold(
      // body: ConsumerDataWidget('topiceae5c8f8a3054d19a132cb3033031e49'),
      body: TopicWidget(topic : 'topiceae5c8f8a3054d19a132cb3033031e49'),
    ),
  ),
);


class TopicWidget extends StatefulWidget {
  TopicWidget({Key key, this.topic}) : super(key: key);

  String topic;

  static const path = '/topic';

  @override
  _TopicWidgetState createState() => _TopicWidgetState();
}

class _TopicWidgetState extends State<TopicWidget> {
  @override

  Widget build(BuildContext context) {
    final String topic = widget?.topic ?? ModalRoute
        .of(context)
        .settings
        .arguments;

    if (topic == null) {
      KafkaUIApp.navigatorKey.currentState.pushNamedAndRemoveUntil(ListTopicsWidget.path, ModalRoute.withName(ListTopicsWidget.path));
    }

    return Scaffold(
      appBar: AppBar(
        title: Text('Topic "$topic"'),

      ),
      body: Padding(
          padding: const EdgeInsets.all(8.0),
          child: Container(child: Text('topic: $topic'))
      ), // This trailing comma makes auto-formatting nicer for build methods.
    );
  }
}
