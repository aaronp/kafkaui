import 'package:flutter/material.dart';
import 'package:kafkaui/rest_client.dart';

void main() => runApp(
      MaterialApp(
        builder: (context, child) =>
            SafeArea(child: new Material(color: Colors.white, child: child)),
        home: Scaffold(
          // body: ConsumerDataWidget('topiceae5c8f8a3054d19a132cb3033031e49'),
          body: TopicContentWidget.forTopic(
              'topiceae5c8f8a3054d19a132cb3033031e49'),
        ),
      ),
    );

class TopicContentWidget extends StatefulWidget {
  @override
  _TopicContentWidgetState createState() => _TopicContentWidgetState();

  TopicContentWidget(this.topic);

  String topic;

  static Widget forTopic(String topic) {
    return Column(
      children: [
        Text('Data for $topic:'),
        TopicContentWidget(topic),
      ],
    );
  }
}

class _TopicContentWidgetState extends State<TopicContentWidget> {
  @override
  Widget build(BuildContext context) {
    // RestClient.listTopics(true);
    return Expanded(child : BigList());
  }
}

class BigList extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return buildList();
  }

  Widget buildList() {
    return ListView.builder(
      itemCount: 1000,
      itemBuilder: (BuildContext context, int index) {
        return FutureBuilder(
          future: getFuture(), // <--- get a future
          builder: (BuildContext context, snapshot) { // <--- build the things.
            return Container(
              width: 80.0,
              height: 80.0,
              child: snapshot.hasData ? snapshot.data : Text('loading..'),
            );
          },
        );
      },
    );
  }

  Future<Widget> getFuture() {
    return Future.delayed(Duration(seconds: 2), () => Text('Hello World!'));
  }
}