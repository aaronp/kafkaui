import 'package:flutter/material.dart';

import 'list_topics_widget.dart';

class HomeWidget extends StatelessWidget {
  static const path = '/home';

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 3,
      child: Scaffold(
        appBar: AppBar(
          bottom: TabBar(
            tabs: [
              Tab(icon: Icon(Icons.whatshot), text : "Cluster"),
              Tab(icon: Icon(Icons.agriculture), text : "Consumers"),
              Tab(icon: Icon(Icons.topic), text : "Topics"),
            ],
          ),
          title: Text('Kafka'),
        ),
        body: TabBarView(
          children: [
            Icon(Icons.directions_car),
            Icon(Icons.directions_transit),
            ListTopicsWidget(),
          ],
        ),
      ),
    );
  }
}
