import 'package:flutter/material.dart';
import 'package:flutter/widgets.dart';
import 'package:kafkaui/rest_client.dart';

import 'list_topics_widget.dart';
import 'topic_widget.dart';

class TopicsWidget extends StatefulWidget {
  @override
  _TopicsWidgetState createState() => _TopicsWidgetState();
}

class _TopicsWidgetState extends State<TopicsWidget> {
  String selectedWidget = '';

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        ListTopicsWidget(),
      ],
    );
  }
}
