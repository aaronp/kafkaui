import 'package:flutter/material.dart';

import 'list_topics_widget.dart';
import 'metrics_widget.dart';
import 'topic_widget.dart';

class HomeWidget extends StatelessWidget {
  static const path = '/home';

  @override
  Widget build(BuildContext context) {
    final tabList = [
      Tab(icon: Icon(Icons.whatshot), text : "Cluster"),
      Tab(icon: Icon(Icons.agriculture), text : "Consumers"),
      Tab(icon: Icon(Icons.topic), text : "Topics"),
      Tab(icon: Icon(Icons.data_usage), text : "Data"),
    ];
    return DefaultTabController(
      length: tabList.length,
      child: Scaffold(
        appBar: AppBar(
          bottom: TabBar(tabs: tabList),
          title: Text('Kafka'),
        ),
        body: TabBarView(
          children: [
            MetricsWidget(),
            Icon(Icons.directions_transit),
            ListTopicsWidget(),
            TopicWidget(),
          ],
        ),
      ),
    );
  }
}
