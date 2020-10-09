import 'package:flutter/material.dart';
import 'package:flutter/widgets.dart';
import 'package:kafkaui/rest_client.dart';

import 'model.dart';

void main() => runApp(
      MaterialApp(
        builder: (context, child) =>
            SafeArea(child: new Material(color: Colors.white, child: child)),
        home: Scaffold(
          // body: ConsumerDataWidget('topiceae5c8f8a3054d19a132cb3033031e49'),
          body:
              PartitionsForTopicWidget('topiceae5c8f8a3054d19a132cb3033031e49'),
        ),
      ),
    );

class PartitionsForTopicWidget extends StatefulWidget {
  PartitionsForTopicWidget(this.topic);

  String topic;

  @override
  _PartitionsForTopicWidgetState createState() =>
      _PartitionsForTopicWidgetState();
}

class _PartitionsForTopicWidgetState extends State<PartitionsForTopicWidget> {
  TopicDesc topicDesc = null;

  @override
  void initState() {
    super.initState();
    RestClient.partitionsForTopic(widget.topic).then((value) => setState(() {
          topicDesc = value;
        }));
  }

  @override
  Widget build(BuildContext context) {
    if (topicDesc == null || topicDesc.partitions == null) {
      return Center(child: CircularProgressIndicator());
    } else {
      final kids = topicDesc.partitions
          .map((e) => TopicPartitionInfoDescWidget(e))
          .toList();
      return Wrap(spacing: 20, runSpacing: 20, children: kids);
    }
  }
}

class TopicPartitionInfoDescWidget extends StatelessWidget {
  TopicPartitionInfoDescWidget(this.info);

  final TopicPartitionInfoDesc info;

  @override
  Widget build(BuildContext context) {
    return Container(
        width: 300,
        height: 500,
        child: Card(
            clipBehavior: Clip.antiAlias,
            shadowColor: Colors.grey,
            elevation: 10,
            child: Column(
              mainAxisAlignment: MainAxisAlignment.start,
              children: [
                ListTile(
                  leading: Icon(Icons.pie_chart),
                  title: Text('Partition ${info.partition}'),
                  subtitle: Text(
                    'Leader: ${info?.leader?.id} at ${info?.leader?.host}:${info?.leader?.port}',
                    style: TextStyle(color: Colors.black.withOpacity(0.6)),
                  ),
                ),
                Container(width: 550, height: 100, child: replicasList()),
              ],
            )));
  }

  ListView replicasList() {
    return ListView.builder(
        itemCount: info.replicas.length,
        shrinkWrap: true,
        padding: const EdgeInsets.all(8),
        itemBuilder: (ctxt, index) {
          return nodeWidget('Replica', info.replicas[index], index, Colors.blue);
        });
  }

  ListView isrList() {
    return ListView.builder(
        itemCount: info.isr.length,
        shrinkWrap: true,
        padding: const EdgeInsets.all(8),
        itemBuilder: (ctxt, index) {
          return nodeWidget('ISR', info.isr[index], index, Colors.amber);
        });
  }

  Container nodeWidget(String label, NodeDesc isr, int index, Color color) {
    var rack = '';
    if (isr.rack != null) {
      rack = ' on rack "${isr.rack}"';
    }
    return Container(
        height: 40,
        color: color,
        child: Padding(
          padding: const EdgeInsets.all(8.0),
          child: Text(
              '$label #${index + 1} [id:${isr.id}] on ${isr.host}:${isr.port}$rack',
              style: TextStyle(color: Colors.black.withOpacity(0.6))),
        ));
  }
}
