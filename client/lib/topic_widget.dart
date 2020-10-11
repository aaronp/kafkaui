import 'package:dropdown_search/dropdown_search.dart';
import 'package:flutter/material.dart';
import 'package:flutter/widgets.dart';
import 'package:kafkaui/rest_client.dart';

import 'consumer_data_table.dart';
import 'partition_widget.dart';

void main() => runApp(
      MaterialApp(
        builder: (context, child) =>
            SafeArea(child: new Material(color: Colors.white, child: child)),
        home: Scaffold(
          // body: ConsumerDataWidget('topiceae5c8f8a3054d19a132cb3033031e49'),
          body: TopicWidget(),
        ),
      ),
    );

class TopicWidget extends StatefulWidget {
  TopicWidget({Key key}) : super(key: key);

  static const path = '/topic';

  @override
  _TopicWidgetState createState() => _TopicWidgetState();
}

class _TopicWidgetState extends State<TopicWidget> {
  List<String> topics = [];
  String selectedTopic = '';

  @override
  void initState() {
    super.initState();
    RestClient.listTopics(true).then((value) => setState(() {
          topics = value;
        }));
  }

  @override
  Widget build(BuildContext context) {
    if (selectedTopic.isEmpty) {
      selectedTopic = (ModalRoute.of(context).settings.arguments ?? '');
    }

    return Scaffold(
      appBar: AppBar(
        //title: selectedTopic.isEmpty ? Text('Create or Choose a Kafka Topic') : Text('Topic $selectedTopic'),
        primary: false,
        backgroundColor: Theme.of(context).secondaryHeaderColor,
        actions: [
          if (selectedTopic.isNotEmpty) IconButton(
              icon: const Icon(Icons.edit),
              tooltip: 'Reparition',
              onPressed: null),
          if (selectedTopic.isNotEmpty) IconButton(
              icon: const Icon(Icons.library_add_rounded),
              tooltip: 'Push Data',
              onPressed: null),
          IconButton(
              icon: const Icon(Icons.add_circle),
              tooltip: 'Create Topic',
              onPressed: null)
        ],
      ),
      body: Padding(
          padding: const EdgeInsets.all(8.0),
          child: topicWidget(
              selectedTopic)), // This trailing comma makes auto-formatting nicer for build methods.
    );
  }

  Widget topicWidget(String topic) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        topicDropDown(topic),
        if (topic.isNotEmpty) PartitionsForTopicWidget(topic),
        if (topic.isNotEmpty) PeekDataWidget.forTopic(topic)
      ],
    );
  }

  DropdownSearch<String> topicDropDown(String topic) {
    return DropdownSearch<String>(
        validator: (v) => v == null ? 'required field' : null,
        hint: 'Topic',
        mode: Mode.MENU,
        showSelectedItem: true,
        showSearchBox: true,
        itemAsString: (topic) => topic ?? '',
        compareFn: (r1, r2) => (topic ?? '') == (topic ?? ''),
        items: topics,
        label: 'Topic',
        showClearButton: false,
        onChanged: (topic) => setState(() {
              selectedTopic = topic;
            }),
        // popupItemDisabled: (String s) => s.startsWith('I'),
        selectedItem: selectedTopic,
        maxHeight: 450);
  }
}
